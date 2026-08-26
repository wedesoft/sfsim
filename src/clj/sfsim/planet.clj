;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
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
    [fastmath.vector :refer (mag)]
    [malli.core :as m]
    [sfsim.config :as config]
    [sfsim.cubemap :refer (cube-map-corners project-onto-cube determine-face cube-j cube-i tile-center)]
    [sfsim.matrix :refer (transformation-matrix fmat4 fvec3 shadow-data shadow-box shadow-patch)]
    [sfsim.quadtree :refer (is-leaf? increase-level? quadtree-update update-level-of-detail tile-info tiles-path-list
                            quadtree-extract tile-coordinates create-local-mesh)]
    [sfsim.quaternion :refer (quaternion ->Quaternion)]
    [sfsim.jolt :as jolt]
    [sfsim.render :refer (uniform-int uniform-vector3 uniform-matrix4 render-patches make-program use-program
                          uniform-sampler destroy-program shadow-cascade uniform-float make-vertex-array-object
                          destroy-vertex-array-object vertex-array-object use-textures render-vars diagonal-field-of-view make-render-vars2)
     :as render]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer (make-rgb-texture-array make-vector-texture-2d make-ubyte-texture-2d destroy-texture
                           texture-2d texture-3d make-float-texture-3d)]
    [sfsim.util :refer (N N0 sqr slurp-floats)]))


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
  "Tessellation evaluation shader to generate output points of tessellated quads with shadow positions"
  {:malli/schema [:=> [:cat N0] render/shaders]}
  [num-scene-shadows]
  [(template/eval (slurp "resources/shaders/planet/tess-evaluation.glsl") {:num-scene-shadows num-scene-shadows})])


(def tess-evaluation-planet-shadow
  "Tessellation evaluation shader to output shadow map points of tessellated quads"
  [shaders/shrink-shadow-index (slurp "resources/shaders/planet/tess-evaluation-shadow.glsl")])


(defn geometry-planet-shading
  "Geometry shader outputting triangles with color texture coordinates and 3D points and shadow coordinates"
  {:malli/schema [:=> [:cat N0] render/shaders]}
  [num-scene-shadows]
  [(template/eval (slurp "resources/shaders/planet/geometry-shading.glsl") {:num-scene-shadows num-scene-shadows})])


(def surface-radiance-function
  "Shader function to determine ambient light scattered by the atmosphere"
  [shaders/surface-radiance-forward shaders/interpolate-2d (slurp "resources/shaders/planet/surface-radiance.glsl")])


(def fragment-planet-shadow
  "Fragment shader to render planetary shadow map"
  (slurp "resources/shaders/planet/fragment-shadow.glsl"))


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
             [::specular :double] [::water-color fvec3]]))


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
                                  :sfsim.render/geometry [(geometry-planet-shading 0)]
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


(def shadow-vars
  (m/schema [:map [:sfsim.opacity/opacity-step :double] [:sfsim.opacity/splits [:vector :double]]
             [:sfsim.opacity/biases [:vector :double]] [:sfsim.opacity/matrix-cascade [:vector shadow-box]]
             [:sfsim.opacity/shadows [:vector texture-2d]] [:sfsim.opacity/opacities [:vector texture-3d]]]))


(defn load-tile-into-opengl
  "Load textures of single tile into OpenGL"
  {:malli/schema [:=> [:cat :map tile-info] tile-info]}
  [{::keys [program config]} tile]
  (let [tilesize       (::tilesize config)
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


(defn make-planet-render-vars2
  "Create hash map with render variables for rendering current frame of planet"
  {:malli/schema [:=> [:cat [:map [::radius :double]] [:map [:sfsim.clouds/cloud-top :double]]
                       [:map [:sfsim.render/fov :double]] N N fvec3 quaternion fvec3] render-vars]}
  [planet-config cloud-data render-config window-width window-height camera-position camera-orientation light-direction]
  (let [distance        (mag camera-position)
        radius          (::radius planet-config)
        cloud-top       (:sfsim.clouds/cloud-top cloud-data)
        fov             (:sfsim.render/fov render-config)
        min-z-near      (:sfsim.render/min-z-near render-config)
        height          (- ^double distance ^double radius)
        diagonal-fov    (diagonal-field-of-view window-width window-height fov)
        z-near          (max (* (- height ^double cloud-top) (cos (* 0.5 diagonal-fov))) ^double min-z-near)
        z-far           (render-depth radius height cloud-top)]
    (make-render-vars2 render-config window-width window-height camera-position camera-orientation light-direction z-near z-far)))


(def overlay-shader
  (slurp "resources/shaders/planet/overlay.glsl"))


(defn setup-overlay-uniforms
  [program overlay camera-to-world texture-offset]
  (let [overlay-to-world  (:sfsim.planet/overlay-to-world overlay)
        overlay-dx        (:sfsim.planet/overlay-dx overlay)
        overlay-dy        (:sfsim.planet/overlay-dy overlay)
        diffuse-tex       (:sfsim.planet/diffuse-tex overlay)
        normal-tex        (:sfsim.planet/normal-tex overlay)
        markings-tex      (:sfsim.planet/markings-tex overlay)
        camera-to-overlay (mulm (inverse overlay-to-world) camera-to-world)]
    (use-program program)
    (uniform-sampler program "diffuse_tex" texture-offset)
    (uniform-sampler program "normal_tex" (inc ^long texture-offset))
    (uniform-sampler program "markings_tex" (+ 2 ^long texture-offset))
    (uniform-matrix4 program "camera_to_overlay" camera-to-overlay)
    (uniform-float program "overlay_dx" overlay-dx)
    (uniform-float program "overlay_dy" overlay-dy)
    (use-textures {texture-offset diffuse-tex
                   (inc ^long texture-offset) normal-tex
                   (+ 2 ^long texture-offset) markings-tex})))


(defn fragment-planet-geometry
  [full overlay]
  [(shaders/lookup-3d "land_noise" "worley") shaders/remap overlay-shader
   (template/eval (slurp "resources/shaders/planet/fragment-geometry.glsl") {:full full :overlay overlay})])


(def planet-data
  (m/schema [:map [::config [:map [::tilesize :int]]]]))


(def planet-geometry-renderer
  (m/schema [:map  [::program :int]]))


(defn make-planet-geometry-renderer
  "Create renderer for rendering planet points in camera coordinate system"
  {:malli/schema [:=> [:cat planet-data :boolean :int [:vector :some]] planet-geometry-renderer]}
  [data full num-scene-shadows overlays]
  (let [have-overlay  (> (count overlays) 0)
        program       (make-program :sfsim.render/vertex [vertex-planet]
                                    :sfsim.render/tess-control [tess-control-planet]
                                    :sfsim.render/tess-evaluation [(tess-evaluation-planet num-scene-shadows)]
                                    :sfsim.render/geometry [(geometry-planet-shading num-scene-shadows)]
                                    :sfsim.render/fragment [(fragment-planet-geometry full have-overlay)])
        config        (::config data)
        tilesize      (::tilesize config)
        worley-floats (slurp-floats (::worley-data config))
        worley-size   (::worley-size config)
        worley-data   #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data worley-floats}
        worley        (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat worley-data)]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (when full
      (uniform-sampler program "day_night" 1)
      (uniform-sampler program "normals"   2)
      (uniform-sampler program "water"     3)
      (uniform-sampler program "worley"    4))
    (uniform-int program "high_detail" (dec ^long tilesize))
    (uniform-int program "low_detail" (quot (dec ^long tilesize) 2))
    (when full
      (uniform-float program "dawn_start" (::dawn-start config))
      (uniform-float program "dawn_end" (::dawn-end config))
      (uniform-float program "radius" (::radius config))
      (uniform-float program "albedo" (::albedo config))
      (uniform-float program "reflectivity" (::reflectivity config))
      (uniform-float program "specular" (::specular config))
      (uniform-float program "land_noise_scale" (::land-noise-scale config))
      (uniform-float program "land_noise_strength" (::land-noise-strength config))
      (uniform-float program "water_threshold" (::water-threshold config))
      (uniform-vector3 program "water_color" (::water-color config)))
    {::program program ::worley worley ::overlays overlays}))


(defn destroy-planet-geometry-renderer
  "Destroy planet geometry renderer"
  [{::keys [program worley]}]
  (destroy-texture worley)
  (destroy-program program))


(defn render-planet-geometry2
  "Render geometry (planet points and distances)"
  [{::keys [program worley overlays]} render-vars full tree]
  (let [camera-to-world (:sfsim.render/camera-to-world render-vars)
        world-to-camera (inverse camera-to-world)]
    (use-program program)
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (when full
      (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
      (use-textures {4 worley}))
    (doseq [overlay overlays]
          (setup-overlay-uniforms program overlay camera-to-world 5))
    (render-tree program tree world-to-camera [] (if full [::surf-tex ::day-night-tex ::normal-tex ::water-tex] [::surf-tex]))))


(defn update-local-mesh
  "Method for maintaining a small 3x3 local mesh in order to handle collisions"
  [local-mesh split-orientations position]
  (let [point  (project-onto-cube position)
        face   (determine-face point)
        j      (cube-j face point)
        i      (cube-i face point)
        coords (dissoc (tile-coordinates j i (::level config/planet-config) (::tilesize config/planet-config))
                       :sfsim.quadtree/dy :sfsim.quadtree/dx)]
    (if (not= coords (:coords local-mesh))
      (let [b            (:sfsim.quadtree/row coords)
            a            (:sfsim.quadtree/column coords)
            tile-y       (:sfsim.quadtree/tile-y coords)
            tile-x       (:sfsim.quadtree/tile-x coords)
            earth-radius (::radius config/planet-config)
            center       (tile-center face (::level config/planet-config) b a earth-radius)
            m            (create-local-mesh split-orientations face
                                            (::level config/planet-config)
                                            (::tilesize config/planet-config) b a tile-y tile-x
                                            earth-radius center)]
        (when-let [mesh (:mesh local-mesh)]
                  (jolt/remove-and-destroy-body mesh))
        (let [mesh (jolt/create-and-add-static-body (jolt/mesh-settings m 5.9742e+24) center (->Quaternion 1 0 0 0))]
          (jolt/set-friction mesh 0.8)
          (jolt/set-restitution mesh 0.25)
          (jolt/optimize-broad-phase)
          {:coords coords :mesh mesh}))
      local-mesh)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
