;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.planet
  "Module with functionality to render a planet"
  (:require
    [clojure.math :refer (sqrt cos)]
    [comb.template :as template]
    [fastmath.matrix :refer (mulm eye inverse)]
    [fastmath.vector :refer (vec3 mag)]
    [malli.core :as m]
    [sfsim.atmosphere :refer (attenuation-point cloud-overlay setup-atmosphere-uniforms vertex-atmosphere atmosphere-luts)]
    [sfsim.clouds :refer (cloud-point lod-offset setup-cloud-render-uniforms setup-cloud-sampling-uniforms
                          fragment-atmosphere-clouds cloud-data overall-shading overall-shading-parameters)]
    [sfsim.plume :refer (cloud-plume-segment setup-static-plume-uniforms)]
    [sfsim.cubemap :refer (cube-map-corners)]
    [sfsim.matrix :refer (transformation-matrix fmat4 fvec3 shadow-data shadow-box shadow-patch)]
    [sfsim.quadtree :refer (is-leaf? increase-level? quadtree-update update-level-of-detail tile-info tiles-path-list
                            quadtree-extract)]
    [sfsim.quaternion :refer (quaternion)]
    [sfsim.render :refer (uniform-int uniform-vector3 uniform-matrix4 render-patches make-program use-program
                          uniform-sampler destroy-program shadow-cascade uniform-float make-vertex-array-object
                          destroy-vertex-array-object vertex-array-object setup-shadow-and-opacity-maps
                          setup-shadow-and-opacity-maps setup-shadow-matrices use-textures render-quads render-config
                          render-vars diagonal-field-of-view make-render-vars clear framebuffer-render)
     :as render]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer (make-rgb-texture-array make-vector-texture-2d make-ubyte-texture-2d destroy-texture
                           texture-2d texture-3d make-float-texture-3d generate-mipmap make-empty-texture-2d
                           make-empty-float-texture-2d)]
    [sfsim.worley :refer (worley-size)]
    [sfsim.util :refer (N N0 sqr slurp-floats)])
  (:import
    (org.lwjgl.opengl
      GL30)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-cube-map-tile-vertices
  "Create vertex array object for drawing cube map tiles"
  {:malli/schema [:=> [:cat :keyword N0 N0 N0 N N] [:vector :double]]}
  [face level y x height-tilesize color-tilesize]
  (let [[a b c d] (cube-map-corners face level y x)
        h0        (/ 0.5 ^long height-tilesize)
        h1        (- 1.0 h0)
        c0        (/ 0.5 ^long color-tilesize)
        c1        (- 1.0 c0)]
    [(a 0) (a 1) (a 2) h0 h0 c0 c0
     (b 0) (b 1) (b 2) h1 h0 c1 c0
     (c 0) (c 1) (c 2) h0 h1 c0 c1
     (d 0) (d 1) (d 2) h1 h1 c1 c1]))


(def vertex-planet
  "Pass through vertices, height field coordinates, and color texture coordinates"
  (slurp "resources/shaders/planet/vertex.glsl"))


(def tess-control-planet
  "Tessellation control shader to control outer tessellation of quad using a uniform integer"
  (slurp "resources/shaders/planet/tess-control.glsl"))


(defn tess-evaluation-planet
  "Tessellation evaluation shader to generate output points of tessellated quads"
  {:malli/schema [:=> [:cat N0] render/shaders]}
  [num-scene-shadows]
  [(template/eval (slurp "resources/shaders/planet/tess-evaluation.glsl") {:num-scene-shadows num-scene-shadows})])


(def tess-evaluation-planet-shadow
  "Tessellation evaluation shader to output shadow map points of tessellated quads"
  [shaders/shrink-shadow-index (slurp "resources/shaders/planet/tess-evaluation-shadow.glsl")])


(defn geometry-planet
  "Geometry shader outputting triangles with color texture coordinates and 3D points"
  {:malli/schema [:=> [:cat N0] render/shaders]}
  [num-scene-shadows]
  [(template/eval (slurp "resources/shaders/planet/geometry.glsl") {:num-scene-shadows num-scene-shadows})])


(def surface-radiance-function
  "Shader function to determine ambient light scattered by the atmosphere"
  [shaders/surface-radiance-forward shaders/interpolate-2d (slurp "resources/shaders/planet/surface-radiance.glsl")])


(defn fragment-planet
  "Fragment shader to render planetary surface"
  {:malli/schema [:=> [:cat N N0] render/shaders]}
  [num-steps num-scene-shadows]
  [(overall-shading num-steps (overall-shading-parameters num-scene-shadows))
   (shaders/percentage-closer-filtering "average_scene_shadow" "scene_shadow_lookup" "scene_shadow_size"
                                        [["sampler2DShadow" "shadow_map"]])
   (shaders/shadow-lookup "scene_shadow_lookup" "scene_shadow_size") surface-radiance-function shaders/remap shaders/phong
   attenuation-point cloud-overlay (shaders/lookup-3d "land_noise" "worley")
   (template/eval (slurp "resources/shaders/planet/fragment.glsl") {:num-scene-shadows num-scene-shadows})])


(def fragment-planet-shadow
  "Fragment shader to render planetary shadow map"
  (slurp "resources/shaders/planet/fragment-shadow.glsl"))


(defn fragment-planet-clouds
  "Fragment shader to render clouds below horizon"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [(cloud-point num-steps perlin-octaves cloud-octaves) (cloud-plume-segment false true)
   (slurp "resources/shaders/planet/fragment-clouds.glsl")])


(def scene-shadow (m/schema [:map [:sfsim.model/matrices shadow-patch] [:sfsim.model/shadows texture-2d]]))


(defn render-tile
  "Render a planetary tile using the specified texture keys and neighbour tessellation"
  {:malli/schema [:=> [:cat :int [:map [::vao vertex-array-object]] fmat4 [:vector scene-shadow] [:vector :keyword]] :nil]}
  [program tile world-to-camera scene-shadows texture-keys]
  (let [neighbours    (bit-or (if (:sfsim.quadtree/up    tile) 1 0)
                              (if (:sfsim.quadtree/left  tile) 2 0)
                              (if (:sfsim.quadtree/down  tile) 4 0)
                              (if (:sfsim.quadtree/right tile) 8 0))
        tile-center   (:sfsim.quadtree/center tile)
        tile-to-world (transformation-matrix (eye 3) tile-center)]
    (uniform-int program "neighbours" neighbours)
    (uniform-vector3 program "tile_center" tile-center)
    (uniform-matrix4 program "tile_to_camera" (mulm world-to-camera tile-to-world))
    (doseq [^long i (range (count scene-shadows))]
      (let [matrices             (:sfsim.model/matrices (nth scene-shadows i))
            world-to-object      (:sfsim.matrix/world-to-object matrices)
            object-to-shadow-map (:sfsim.matrix/object-to-shadow-map matrices)]
        (uniform-matrix4 program (str "tile_to_shadow_map_" (inc i))
                         (mulm object-to-shadow-map (mulm world-to-object tile-to-world)))))
    (use-textures (zipmap (range) (mapv tile texture-keys)))
    (render-patches (::vao tile))))


(defn render-tree
  "Call each tile in tree to be rendered"
  {:malli/schema [:=> [:cat :int [:maybe :map] :any [:vector scene-shadow] [:vector :keyword]] :nil]}
  [program node world-to-camera scene-shadows texture-keys]
  (when-not (empty? node)
    (if (is-leaf? node)
      (render-tile program node world-to-camera scene-shadows texture-keys)
      (doseq [selector [:sfsim.cubemap/face0 :sfsim.cubemap/face1 :sfsim.cubemap/face2 :sfsim.cubemap/face3
                        :sfsim.cubemap/face4 :sfsim.cubemap/face5
                        :sfsim.quadtree/quad0 :sfsim.quadtree/quad1 :sfsim.quadtree/quad2 :sfsim.quadtree/quad3]]
        (render-tree program (selector node) world-to-camera scene-shadows texture-keys)))))


(def planet-config
  (m/schema [:map [::radius :double] [::max-height :double] [::albedo :double] [::dawn-start :double]
             [::dawn-end :double] [::tilesize N] [::color-tilesize N] [::reflectivity :double]
             [::water-color fvec3]]))


(def planet-shadow-renderer (m/schema [:map [::program :int] [:sfsim.opacity/data shadow-data]]))


(defn make-planet-shadow-renderer
  "Create program for rendering cascaded shadow maps of planet"
  {:malli/schema [:=> [:cat [:map [:sfsim.opacity/data shadow-data] [::config planet-config]]] planet-shadow-renderer]}
  [data]
  (let [shadow-data (:sfsim.opacity/data data)
        tilesize    (::tilesize (::config data))
        program     (make-program :sfsim.render/vertex [vertex-planet]
                                  :sfsim.render/tess-control [tess-control-planet]
                                  :sfsim.render/tess-evaluation [tess-evaluation-planet-shadow]
                                  :sfsim.render/geometry [(geometry-planet 0)]
                                  :sfsim.render/fragment [fragment-planet-shadow])]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (uniform-int program "high_detail" (dec ^long tilesize))
    (uniform-int program "low_detail" (quot (dec ^long tilesize) 2))
    (uniform-int program "shadow_size" (:sfsim.opacity/shadow-size shadow-data))
    {::program program
     :sfsim.opacity/data shadow-data}))


(defn render-shadow-cascade
  "Render planetary shadow cascade"
  {:malli/schema [:=> [:cat :map [:* :any]] [:vector texture-2d]]}
  [{::keys [program] :as other} & {:keys [tree] :as data}]
  (shadow-cascade (:sfsim.opacity/shadow-size (:sfsim.opacity/data other)) (:sfsim.opacity/matrix-cascade data) program
                  (fn render-planet-shadow [world-to-camera] (render-tree program tree world-to-camera [] [::surf-tex]))))


(defn destroy-shadow-cascade
  "Destroy cascade of shadow maps"
  {:malli/schema [:=> [:cat [:vector texture-2d]] :nil]}
  [shadows]
  (doseq [shadow shadows]
    (destroy-texture shadow)))


(defn destroy-planet-shadow-renderer
  "Destroy renderer for planet shadow"
  {:malli/schema [:=> [:cat planet-shadow-renderer] :nil]}
  [{::keys [program]}]
  (destroy-program program))


(def cloud-planet-renderer
  (m/schema [:map [::program :int] [:sfsim.atmosphere/luts atmosphere-luts]
             [:sfsim.clouds/data cloud-data] [:sfsim.render/config render-config]]))


(def model-vars (m/schema [:map [:sfsim.model/time :double]
                                [:sfsim.model/pressure :double]
                                [:sfsim.model/throttle :double]]))


(defn setup-dynamic-plume-uniforms
  {:malli/schema [:=> [:cat :int render-vars model-vars] :nil]}
  [program render-vars model-vars]
  (uniform-vector3 program "object_origin" (:sfsim.render/object-origin render-vars))
  (uniform-float program "object_distance" (:sfsim.render/object-distance render-vars))
  (uniform-matrix4 program "camera_to_object" (:sfsim.render/camera-to-object render-vars))
  (uniform-float program "pressure" (:sfsim.model/pressure model-vars))
  (uniform-float program "time" (:sfsim.model/time model-vars))
  (uniform-float program "throttle" (:sfsim.model/throttle model-vars)))


(defn make-cloud-planet-renderer
  "Make a renderer to render clouds below horizon"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/config render-config] [::config planet-config]
                             [:sfsim.atmosphere/luts atmosphere-luts] [:sfsim.opacity/data shadow-data]
                             [:sfsim.clouds/data cloud-data]]] cloud-planet-renderer]}
  [data]
  (let [render-config   (:sfsim.render/config data)
        planet-config   (::config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        shadow-data     (:sfsim.opacity/data data)
        cloud-data      (:sfsim.clouds/data data)
        model-data      (:sfsim.model/data data)
        tilesize        (::tilesize planet-config)
        program         (make-program :sfsim.render/vertex [vertex-planet]
                                      :sfsim.render/tess-control [tess-control-planet]
                                      :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                      :sfsim.render/geometry [(geometry-planet 0)]
                                      :sfsim.render/fragment [(fragment-planet-clouds (:sfsim.opacity/num-steps shadow-data)
                                                                                      (:sfsim.clouds/perlin-octaves cloud-data)
                                                                                      (:sfsim.clouds/cloud-octaves cloud-data))])]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (setup-shadow-and-opacity-maps program shadow-data 8)
    (setup-cloud-render-uniforms program cloud-data 4)
    (setup-cloud-sampling-uniforms program cloud-data 7)
    (setup-atmosphere-uniforms program atmosphere-luts 1 false)
    (setup-static-plume-uniforms program model-data)
    (uniform-float program "radius" (::radius planet-config))
    (uniform-int program "high_detail" (dec ^long tilesize))
    (uniform-int program "low_detail" (quot (dec ^long tilesize) 2))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {::program program
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.clouds/data cloud-data
     :sfsim.model/data model-data
     :sfsim.render/config render-config}))


(def shadow-vars
  (m/schema [:map [:sfsim.opacity/opacity-step :double] [:sfsim.opacity/splits [:vector :double]]
             [:sfsim.opacity/biases [:vector :double]] [:sfsim.opacity/matrix-cascade [:vector shadow-box]]
             [:sfsim.opacity/shadows [:vector texture-2d]] [:sfsim.opacity/opacities [:vector texture-3d]]]))


(defn render-cloud-planet
  "Render clouds below horizon"
  {:malli/schema [:=> [:cat cloud-planet-renderer render-vars model-vars shadow-vars [:maybe :map]] :nil]}
  [{::keys [program] :as other} render-vars model-vars shadow-vars tree]
  (let [atmosphere-luts (:sfsim.atmosphere/luts other)
        cloud-data      (:sfsim.clouds/data other)
        render-config   (:sfsim.render/config other)
        world-to-camera (inverse (:sfsim.render/camera-to-world render-vars))]
    (use-program program)
    (uniform-float program "lod_offset" (lod-offset render-config cloud-data render-vars))
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (setup-dynamic-plume-uniforms program render-vars model-vars)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (uniform-float program "opacity_cutoff" (:sfsim.opacity/opacity-cutoff shadow-vars))
    (setup-shadow-matrices program shadow-vars)
    (use-textures {1 (:sfsim.atmosphere/transmittance atmosphere-luts) 2 (:sfsim.atmosphere/scatter atmosphere-luts)
                   3 (:sfsim.atmosphere/mie atmosphere-luts) 4 (:sfsim.clouds/worley cloud-data)
                   5 (:sfsim.clouds/perlin-worley cloud-data) 6 (:sfsim.clouds/cloud-cover cloud-data)
                   7 (:sfsim.clouds/bluenoise cloud-data)})
    (use-textures (zipmap (drop 8 (range)) (concat (:sfsim.opacity/shadows shadow-vars) (:sfsim.opacity/opacities shadow-vars))))
    (render-tree program tree world-to-camera [] [::surf-tex])))


(defn destroy-cloud-planet-renderer
  "Destroy program for rendering clouds below horizon"
  {:malli/schema [:=> [:cat cloud-planet-renderer] :nil]}
  [{::keys [program]}]
  (destroy-program program))


(def cloud-atmosphere-renderer
  (m/schema [:map [:sfsim.clouds/program :int] [:sfsim.atmosphere/luts atmosphere-luts]
             [:sfsim.render/config render-config] [:sfsim.clouds/data cloud-data]]))


(defn make-cloud-atmosphere-renderer
  "Make renderer to render clouds above horizon (not tested)"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/config render-config] [:sfsim.atmosphere/luts atmosphere-luts]
                             [::config planet-config] [:sfsim.opacity/data shadow-data]
                             [:sfsim.clouds/data cloud-data]]] cloud-atmosphere-renderer]}
  [other]
  (let [render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        model-data      (:sfsim.model/data other)
        planet-config   (::config other)
        shadow-data     (:sfsim.opacity/data other)
        data            (:sfsim.clouds/data other)
        program         (make-program :sfsim.render/vertex [vertex-atmosphere]
                                      :sfsim.render/fragment [(fragment-atmosphere-clouds (:sfsim.opacity/num-steps shadow-data)
                                                                                          (:sfsim.clouds/perlin-octaves data)
                                                                                          (:sfsim.clouds/cloud-octaves data))])]
    (use-program program)
    (setup-atmosphere-uniforms program atmosphere-luts 0 false)
    (setup-cloud-render-uniforms program data 3)
    (setup-cloud-sampling-uniforms program data 6)
    (setup-shadow-and-opacity-maps program shadow-data 7)
    (setup-static-plume-uniforms program model-data)
    (uniform-float program "radius" (::radius planet-config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {:sfsim.clouds/program program
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.render/config render-config
     :sfsim.model/data model-data
     :sfsim.clouds/data data}))


(defn render-cloud-atmosphere
  "Render clouds above horizon (not tested)"
  {:malli/schema [:=> [:cat cloud-atmosphere-renderer render-vars model-vars shadow-vars] :nil]}
  [{:sfsim.clouds/keys [program data] :as other} render-vars model-vars shadow-vars]
  (let [render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        indices         [0 1 3 2]
        vertices        [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao             (make-vertex-array-object program indices vertices ["ndc" 2])
        world-to-camera (inverse (:sfsim.render/camera-to-world render-vars))]
    (use-program program)
    (uniform-float program "lod_offset" (lod-offset render-config data render-vars))
    (uniform-matrix4 program "inverse_projection" (inverse (:sfsim.render/projection render-vars)))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "camera_to_world" (:sfsim.render/camera-to-world render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (setup-dynamic-plume-uniforms program render-vars model-vars)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (uniform-float program "opacity_cutoff" (:sfsim.opacity/opacity-cutoff shadow-vars))
    (setup-shadow-matrices program shadow-vars)
    (use-textures {0 (:sfsim.atmosphere/transmittance atmosphere-luts) 1 (:sfsim.atmosphere/scatter atmosphere-luts)
                   2 (:sfsim.atmosphere/mie atmosphere-luts) 3 (:sfsim.clouds/worley data) 4 (:sfsim.clouds/perlin-worley data)
                   5 (:sfsim.clouds/cloud-cover data) 6 (:sfsim.clouds/bluenoise data)})
    (use-textures (zipmap (drop 7 (range)) (concat (:sfsim.opacity/shadows shadow-vars) (:sfsim.opacity/opacities shadow-vars))))
    (render-quads vao)
    (destroy-vertex-array-object vao)))


(defn destroy-cloud-atmosphere-renderer
  "Destroy cloud rendering OpenGL program (not tested)"
  {:malli/schema [:=> [:cat cloud-atmosphere-renderer] :nil]}
  [{:sfsim.clouds/keys [program]}]
  (destroy-program program))


(defn make-planet-program
  "Make program to render planet"
  {:malli/schema [:=> [:cat :map :int] :int]}
  [data num-scene-shadows]
  (let [config          (::config data)
        tilesize        (::tilesize config)
        render-config   (:sfsim.render/config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        shadow-data     (:sfsim.opacity/data data)
        num-steps       (:sfsim.opacity/num-steps shadow-data)
        program         (make-program :sfsim.render/vertex [vertex-planet]
                                      :sfsim.render/tess-control [tess-control-planet]
                                      :sfsim.render/tess-evaluation [(tess-evaluation-planet num-scene-shadows)]
                                      :sfsim.render/geometry [(geometry-planet num-scene-shadows)]
                                      :sfsim.render/fragment [(fragment-planet num-steps num-scene-shadows)])]
    (use-program program)
    (uniform-sampler program "surface"   0)
    (uniform-sampler program "day_night" 1)
    (uniform-sampler program "normals"   2)
    (uniform-sampler program "water"     3)
    (uniform-sampler program "clouds"    8)
    (uniform-sampler program "worley"    9)
    (setup-atmosphere-uniforms program atmosphere-luts 4 true)
    (setup-shadow-and-opacity-maps program shadow-data 10)
    (uniform-int program "scene_shadow_size" (:sfsim.opacity/scene-shadow-size shadow-data))
    (uniform-float program "shadow_bias" (:sfsim.opacity/shadow-bias shadow-data))
    (doseq [^long i (range num-scene-shadows)]
      (uniform-sampler program (str "scene_shadow_map_" (inc i)) (+ i 10 (* 2 ^long num-steps))))
    (uniform-int program "high_detail" (dec ^long tilesize))
    (uniform-int program "low_detail" (quot (dec ^long tilesize) 2))
    (uniform-float program "dawn_start" (::dawn-start config))
    (uniform-float program "dawn_end" (::dawn-end config))
    (uniform-float program "specular" (:sfsim.render/specular render-config))
    (uniform-float program "radius" (::radius config))
    (uniform-float program "albedo" (::albedo config))
    (uniform-float program "reflectivity" (::reflectivity config))
    (uniform-float program "land_noise_scale" (::land-noise-scale config))
    (uniform-float program "land_noise_strength" (::land-noise-strength config))
    (uniform-float program "water_threshold" (::water-threshold config))
    (uniform-vector3 program "water_color" (::water-color config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    program))


(def planet-renderer (m/schema [:map [::programs [:map-of :int :int]] [:sfsim.atmosphere/luts atmosphere-luts] [::config planet-config]]))


(defn make-planet-renderer
  "Program to render planet with cloud overlay"
  {:malli/schema [:=> [:cat [:map [::config planet-config] [:sfsim.render/config render-config]
                             [:sfsim.atmosphere/luts atmosphere-luts] [:sfsim.opacity/data shadow-data]]] planet-renderer]}
  [data]
  (let [config          (::config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        shadow-data     (:sfsim.opacity/data data)
        variations      (:sfsim.opacity/scene-shadow-counts shadow-data)
        programs        (mapv #(make-planet-program data %) variations)
        worley-floats   (slurp-floats "data/clouds/worley-cover.raw")
        worley-data     #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data worley-floats}
        worley          (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat worley-data)]
    (generate-mipmap worley)
    {::programs (zipmap variations programs)
     ::worley worley
     :sfsim.atmosphere/luts atmosphere-luts
     ::config config}))


(defn render-planet
  "Render planet"
  {:malli/schema [:=> [:cat planet-renderer render-vars shadow-vars [:vector scene-shadow] texture-2d [:maybe :map]] :nil]}
  [{::keys [programs worley] :as other} render-vars shadow-vars scene-shadows clouds tree]
  (let [atmosphere-luts   (:sfsim.atmosphere/luts other)
        world-to-camera   (inverse (:sfsim.render/camera-to-world render-vars))
        num-steps         (count (:sfsim.opacity/shadows shadow-vars))
        num-scene-shadows (count scene-shadows)
        program           (programs num-scene-shadows)]
    (use-program program)
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (uniform-int program "window_width" (:sfsim.render/window-width render-vars))
    (uniform-int program "window_height" (:sfsim.render/window-height render-vars))
    (setup-shadow-matrices program shadow-vars)
    (use-textures {4 (:sfsim.atmosphere/transmittance atmosphere-luts) 5 (:sfsim.atmosphere/scatter atmosphere-luts)
                   6 (:sfsim.atmosphere/mie atmosphere-luts) 7 (:sfsim.atmosphere/surface-radiance atmosphere-luts)
                   8 clouds 9 worley})
    (use-textures (zipmap (drop 10 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                   (:sfsim.opacity/opacities shadow-vars))))
    (doseq [^long i (range num-scene-shadows)]
      (use-textures {(+ i 10 (* 2 num-steps)) (:sfsim.model/shadows (nth scene-shadows i))}))
    (render-tree program tree world-to-camera scene-shadows [::surf-tex ::day-night-tex ::normal-tex ::water-tex])))


(defn destroy-planet-renderer
  "Destroy planet rendering program"
  {:malli/schema [:=> [:cat planet-renderer] :nil]}
  [{::keys [programs worley]}]
  (destroy-texture worley)
  (doseq [program (vals programs)] (destroy-program program)))


(defn load-tile-into-opengl
  "Load textures of single tile into OpenGL"
  {:malli/schema [:=> [:cat :map tile-info] tile-info]}
  [{::keys [programs config]} tile]
  (let [program        (programs 0)
        tilesize       (::tilesize config)
        color-tilesize (::color-tilesize config)
        indices        [0 2 3 1]
        vertices       (make-cube-map-tile-vertices (:sfsim.quadtree/face tile) (:sfsim.quadtree/level tile)
                                                    (:sfsim.quadtree/y tile) (:sfsim.quadtree/x tile) tilesize color-tilesize)
        vao            (make-vertex-array-object program indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
        day-night-tex  (make-rgb-texture-array :sfsim.texture/linear :sfsim.texture/clamp [(::day tile) (::night tile)])
        surf-tex       (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                               #:sfsim.image{:width tilesize :height tilesize :data (::surface tile)})
        normal-tex     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp (::normals tile))
        water-tex      (make-ubyte-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width color-tilesize :height color-tilesize :data (::water tile)})]
    (assoc (dissoc tile ::day ::night ::surface ::normals ::water)
           ::vao vao ::day-night-tex day-night-tex ::surf-tex surf-tex ::normal-tex normal-tex ::water-tex water-tex)))


(defn load-tiles-into-opengl
  "Load tiles into OpenGL"
  {:malli/schema [:=> [:cat :map :map [:sequential [:vector :keyword]]] :map]}
  [planet-renderer tree paths]
  (quadtree-update tree paths (partial load-tile-into-opengl planet-renderer)))


(defn unload-tile-from-opengl
  "Remove textures of single tile from OpenGL"
  {:malli/schema [:=> [:cat tile-info] :nil]}
  [tile]
  (destroy-texture (::day-night-tex tile))
  (destroy-texture (::surf-tex tile))
  (destroy-texture (::normal-tex tile))
  (destroy-texture (::water-tex tile))
  (destroy-vertex-array-object (::vao tile)))


(defn unload-tiles-from-opengl
  "Remove tile textures from OpenGL"
  {:malli/schema [:=> [:cat [:sequential tile-info]] :nil]}
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))


(defn background-tree-update
  "Method to call in a backround thread for loading tiles"
  {:malli/schema [:=> [:cat :map :map N fvec3] :map]}
  [{::keys [config]} tree width position]
  (let [tilesize  (::tilesize config)
        increase? (partial increase-level? tilesize (::radius config) width 60.0 10 7 position)]; TODO: use params for values
    (update-level-of-detail tree (::radius config) increase? true)))


(def tree (m/schema [:map [:tree :some] [:changes :some]]))


(defn make-tile-tree
  "Create empty tile tree and empty change object"
  {:malli/schema [:=> :cat tree]}
  []
  {:tree    (atom {})
   :changes (atom (future {:tree {} :drop [] :load []}))})


(defn update-tile-tree
  "Schedule background tile tree updates"
  {:malli/schema [:=> [:cat :map tree N fvec3] :any]}
  [planet-renderer {:keys [tree changes]} width position]
  (when (realized? @changes)
    (let [data @@changes]
      (unload-tiles-from-opengl (:drop data))
      (reset! tree (load-tiles-into-opengl planet-renderer (:tree data) (:load data)))
      (reset! changes (future (background-tree-update planet-renderer @tree width position))))))


(defn destroy-tile-tree
  "Unload all tiles from opengl"
  {:malli/schema [:=> [:cat tree] :nil]}
  [tile-tree]
  (let [tree      @(:tree tile-tree)
        drop-list (tiles-path-list tree)]
    (unload-tiles-from-opengl (quadtree-extract tree drop-list))))


(defn get-current-tree
  "Get current state of tile tree"
  {:malli/schema [:=> [:cat tree] :map]}
  [{:keys [tree]}]
  @tree)


(defn render-depth
  "Determine maximum shadow depth for cloud shadows"
  ^double [^double radius ^double max-height ^double cloud-top]
  (+ (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
     (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))))


(defn make-planet-render-vars
  "Create hash map with render variables for rendering current frame of planet"
  {:malli/schema [:=> [:cat [:map [::radius :double]] [:map [:sfsim.clouds/cloud-top :double]]
                       [:map [:sfsim.render/fov :double]] N N fvec3 quaternion fvec3 fvec3 quaternion model-vars] render-vars]}
  [planet-config cloud-data render-config window-width window-height camera-position camera-orientation light-direction
   object-position object-orientation model-vars]
  (let [distance        (mag camera-position)
        radius          (::radius planet-config)
        cloud-top       (:sfsim.clouds/cloud-top cloud-data)
        fov             (:sfsim.render/fov render-config)
        min-z-near      (:sfsim.render/min-z-near render-config)
        time_           (:sfsim.model/time model-vars)
        pressure        (:sfsim.model/pressure model-vars)
        height          (- ^double distance ^double radius)
        diagonal-fov    (diagonal-field-of-view window-width window-height fov)
        z-near          (max (* (- height ^double cloud-top) (cos (* 0.5 diagonal-fov))) ^double min-z-near)
        z-far           (render-depth radius height cloud-top)]
    (make-render-vars render-config window-width window-height camera-position camera-orientation light-direction
                      object-position object-orientation z-near z-far time_ pressure)))


(def fragment-planet-geometry
"#version 450 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} fs_in;
layout (location = 0) out vec4 camera_point;
layout (location = 1) out float dist;
void main()
{
  camera_point = vec4(normalize(fs_in.camera_point.xyz), 0.0);
  dist = length(fs_in.camera_point.xyz);
}")


(defn make-planet-geometry-renderer
  "Create renderer for rendering planet points in camera coordinate system"
  [data]
  (let [program  (make-program :sfsim.render/vertex [vertex-planet]
                               :sfsim.render/tess-control [tess-control-planet]
                               :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                               :sfsim.render/geometry [(geometry-planet 0)]
                               :sfsim.render/fragment [fragment-planet-geometry])
        tilesize (::tilesize (::config data))]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (uniform-int program "high_detail" (dec ^long tilesize))
    (uniform-int program "low_detail" (quot (dec ^long tilesize) 2))
    {::program program}))


(defn destroy-planet-geometry-renderer
  "Destroy planet geometry renderer"
  [{::keys [program]}]
  (destroy-program program))


(defn render-planet-geometry
  "Render geometry (planet points and distances)"
  [{::keys [program]} render-vars tree]
  (use-program program)
  (uniform-matrix4 program "projection" (:sfsim.render/overlay-projection render-vars))
  (render-tree program tree (inverse (:sfsim.render/camera-to-world render-vars)) [] [:sfsim.planet/surf-tex]))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
