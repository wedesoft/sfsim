(ns sfsim.planet
    "Module with functionality to render a planet"
    (:require [fastmath.matrix :refer (mulm eye inverse)]
              [malli.core :as m]
              [sfsim.matrix :refer (transformation-matrix fmat4 fvec3 shadow-data shadow-box)]
              [sfsim.cubemap :refer (cube-map-corners)]
              [sfsim.quadtree :refer (is-leaf? increase-level? quadtree-update update-level-of-detail tile-info)]
              [sfsim.texture :refer (make-rgb-texture make-vector-texture-2d make-ubyte-texture-2d destroy-texture texture-2d
                                     texture-3d)]
              [sfsim.render :refer (uniform-int uniform-vector3 uniform-matrix4 render-patches make-program use-program
                                    uniform-sampler destroy-program shadow-cascade uniform-float make-vertex-array-object
                                    destroy-vertex-array-object vertex-array-object setup-shadow-and-opacity-maps
                                    setup-shadow-and-opacity-maps use-textures render-quads render-config render-vars)
                              :as render]
              [sfsim.atmosphere :refer (transmittance-outer attenuation-track cloud-overlay setup-atmosphere-uniforms
                                        vertex-atmosphere atmosphere-luts)]
              [sfsim.util :refer (N N0)]
              [sfsim.clouds :refer (overall-shadow cloud-planet lod-offset setup-cloud-render-uniforms
                                      setup-cloud-sampling-uniforms fragment-atmosphere-clouds cloud-data)]
              [sfsim.shaders :as shaders]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn make-cube-map-tile-vertices
  "Create vertex array object for drawing cube map tiles"
  {:malli/schema [:=> [:cat N0 N0 N0 N0 N N] [:vector :double]]}
  [face level y x height-tilesize color-tilesize]
  (let [[a b c d] (cube-map-corners face level y x)
        h0        (/ 0.5 height-tilesize)
        h1        (- 1.0 h0)
        c0        (/ 0.5 color-tilesize)
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

(def tess-evaluation-planet
  "Tessellation evaluation shader to generate output points of tessellated quads"
  (slurp "resources/shaders/planet/tess-evaluation.glsl"))

(def tess-evaluation-planet-shadow
  "Tessellation evaluation shader to output shadow map points of tessellated quads"
  [shaders/shrink-shadow-index (slurp "resources/shaders/planet/tess-evaluation-shadow.glsl")])

(def geometry-planet
  "Geometry shader outputting triangles with color texture coordinates and 3D points"
  (slurp "resources/shaders/planet/geometry.glsl"))

(def surface-radiance-function
  "Shader function to determine ambient light scattered by the atmosphere"
  [shaders/surface-radiance-forward shaders/interpolate-2d (slurp "resources/shaders/planet/surface-radiance.glsl")])

(defn fragment-planet
  "Fragment shader to render planetary surface"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [shaders/ray-sphere shaders/is-above-horizon transmittance-outer surface-radiance-function shaders/remap attenuation-track
   cloud-overlay (overall-shadow num-steps) (slurp "resources/shaders/planet/fragment.glsl")])

(def fragment-planet-shadow
  "Fragment shader to render planetary shadow map"
  (slurp "resources/shaders/planet/fragment-shadow.glsl"))

(defn fragment-planet-clouds
  "Fragment shader to render clouds below horizon"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [(cloud-planet num-steps perlin-octaves cloud-octaves) (slurp "resources/shaders/planet/fragment-clouds.glsl")])

(defn render-tile
  "Render a planetary tile using the specified texture keys and neighbour tessellation"
  {:malli/schema [:=> [:cat :int [:map [::vao vertex-array-object]] fmat4 [:vector :keyword]] :nil]}
  [program tile world-to-camera ^clojure.lang.PersistentVector texture-keys]
  (let [neighbours  (bit-or (if (:sfsim.quadtree/up    tile) 1 0)
                            (if (:sfsim.quadtree/left  tile) 2 0)
                            (if (:sfsim.quadtree/down  tile) 4 0)
                            (if (:sfsim.quadtree/right tile) 8 0))
        tile-center (:sfsim.quadtree/center tile)]
    (uniform-int program "neighbours" neighbours)
    (uniform-vector3 program "tile_center" tile-center)
    (uniform-matrix4 program "tile_to_camera" (mulm world-to-camera (transformation-matrix (eye 3) tile-center)))
    (use-textures (zipmap (range) (map tile texture-keys)))
    (render-patches (::vao tile))))

(defn render-tree
  "Call each tile in tree to be rendered"
  {:malli/schema [:=> [:cat :int [:maybe :map] :any [:vector :keyword]] :nil]}
  [program node world-to-camera texture-keys]
  (when-not (empty? node)
            (if (is-leaf? node)
              (render-tile program node world-to-camera texture-keys)
              (doseq [selector [:sfsim.quadtree/face0 :sfsim.quadtree/face1 :sfsim.quadtree/face2 :sfsim.quadtree/face3
                                :sfsim.quadtree/face4 :sfsim.quadtree/face5
                                :sfsim.quadtree/quad0 :sfsim.quadtree/quad1 :sfsim.quadtree/quad2 :sfsim.quadtree/quad3]]
                     (render-tree program (selector node) world-to-camera texture-keys)))))

(def planet-config (m/schema [:map [::radius :double] [::max-height :double] [::albedo :double] [::dawn-start :double]
                                   [::dawn-end :double] [::tilesize N] [::color-tilesize N] [::reflectivity :double]
                                   [::water-color fvec3]]))

(def planet-shadow-renderer (m/schema [:map [::program :int] [:sfsim.opacity/data shadow-data]]))

(defn make-planet-shadow-renderer
  "Create program for rendering cascaded shadow maps of planet (untested)"
  {:malli/schema [:=> [:cat [:map [:sfsim.opacity/data shadow-data] [::config planet-config]]] planet-shadow-renderer]}
  [data]
  (let [shadow-data (:sfsim.opacity/data data)
        tilesize    (::tilesize (::config data))
        program     (make-program :sfsim.render/vertex [vertex-planet]
                                  :sfsim.render/tess-control [tess-control-planet]
                                  :sfsim.render/tess-evaluation [tess-evaluation-planet-shadow]
                                  :sfsim.render/geometry [geometry-planet]
                                  :sfsim.render/fragment [fragment-planet-shadow])]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "shadow_size" (:sfsim.opacity/shadow-size shadow-data))
    {::program program
     :sfsim.opacity/data shadow-data}))

(defn render-shadow-cascade
  "Render planetary shadow cascade (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] [:vector texture-2d]]}
  [{::keys [program] :as other} & {:keys [tree] :as data}]
  (shadow-cascade (:sfsim.opacity/shadow-size (:sfsim.opacity/data other)) (:sfsim.opacity/matrix-cascade data) program
                  (fn render-planet-shadow [world-to-camera] (render-tree program tree world-to-camera [::surf-tex]))))

(defn destroy-shadow-cascade
  "Destroy cascade of shadow maps (untested)"
  {:malli/schema [:=> [:cat [:vector texture-2d]] :nil]}
  [shadows]
  (doseq [shadow shadows]
         (destroy-texture shadow)))

(defn destroy-planet-shadow-renderer
  "Destroy renderer for planet shadow (untested)"
  {:malli/schema [:=> [:cat planet-shadow-renderer] :nil]}
  [{::keys [program]}]
  (destroy-program program))

(def cloud-planet-renderer (m/schema [:map [::program :int] [:sfsim.atmosphere/luts atmosphere-luts]
                                           [:sfsim.clouds/data cloud-data] [:sfsim.render/config render-config]]))

(defn make-cloud-planet-renderer
  "Make a renderer to render clouds below horizon (untested)"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/config render-config] [::config planet-config]
                                  [:sfsim.atmosphere/luts atmosphere-luts] [:sfsim.opacity/data shadow-data]
                                  [:sfsim.clouds/data cloud-data]]] cloud-planet-renderer]}
  [data]
  (let [render-config   (:sfsim.render/config data)
        planet-config   (::config data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        shadow-data     (:sfsim.opacity/data data)
        cloud-data      (:sfsim.clouds/data data)
        tilesize        (::tilesize planet-config)
        program         (make-program :sfsim.render/vertex [vertex-planet]
                                      :sfsim.render/tess-control [tess-control-planet]
                                      :sfsim.render/tess-evaluation [tess-evaluation-planet]
                                      :sfsim.render/geometry [geometry-planet]
                                      :sfsim.render/fragment [(fragment-planet-clouds (:sfsim.opacity/num-steps shadow-data)
                                                                                      (:sfsim.clouds/perlin-octaves cloud-data)
                                                                                      (:sfsim.clouds/cloud-octaves cloud-data))])]
    (use-program program)
    (uniform-sampler program "surface"          0)
    (setup-shadow-and-opacity-maps program shadow-data 8)
    (setup-cloud-render-uniforms program cloud-data 4)
    (setup-cloud-sampling-uniforms program cloud-data 7)
    (setup-atmosphere-uniforms program atmosphere-luts 1 false)
    (uniform-float program "radius" (::radius planet-config))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {::program program
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.clouds/data cloud-data
     :sfsim.render/config render-config}))

(def shadow-vars (m/schema [:map [:sfsim.opacity/opacity-step :double] [:sfsim.opacity/splits [:vector :double]]
                                 [:sfsim.opacity/matrix-cascade [:vector shadow-box]]
                                 [:sfsim.opacity/shadows [:vector texture-2d]] [:sfsim.opacity/opacities [:vector texture-3d]]]))

(defn render-cloud-planet
  "Render clouds below horizon (untested)"
  {:malli/schema [:=> [:cat cloud-planet-renderer render-vars shadow-vars [:maybe :map]] :nil]}
  [{::keys [program] :as other} render-vars shadow-vars tree]
  (let [atmosphere-luts (:sfsim.atmosphere/luts other)
        cloud-data      (:sfsim.clouds/data other)
        render-config   (:sfsim.render/config other)
        world-to-camera (inverse (:sfsim.render/camera-to-world render-vars))]
    (use-program program)
    (uniform-float program "lod_offset" (lod-offset render-config cloud-data render-vars))
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
           (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
           (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item)))
    (use-textures {1 (:sfsim.atmosphere/transmittance atmosphere-luts) 2 (:sfsim.atmosphere/scatter atmosphere-luts)
                   3 (:sfsim.atmosphere/mie atmosphere-luts) 4 (:sfsim.clouds/worley cloud-data)
                   5 (:sfsim.clouds/perlin-worley cloud-data) 6 (:sfsim.clouds/cloud-cover cloud-data)
                   7 (:sfsim.clouds/bluenoise cloud-data)})
    (use-textures (zipmap (drop 8 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                   (:sfsim.opacity/opacities shadow-vars))))
    (render-tree program tree world-to-camera [::surf-tex])))

(defn destroy-cloud-planet-renderer
  "Destroy program for rendering clouds below horizon (untested)"
  {:malli/schema [:=> [:cat cloud-planet-renderer] :nil]}
  [{::keys [program]}]
  (destroy-program program))

(def cloud-atmosphere-renderer (m/schema [:map [:sfsim.clouds/program :int] [:sfsim.atmosphere/luts atmosphere-luts]
                                               [:sfsim.render/config render-config] [:sfsim.clouds/data cloud-data]]))

(defn make-cloud-atmosphere-renderer
  "Make renderer to render clouds above horizon (not tested)"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/config render-config] [:sfsim.atmosphere/luts atmosphere-luts]
                                  [:sfsim.planet/config planet-config] [:sfsim.opacity/data shadow-data]
                                  [:sfsim.clouds/data cloud-data]]] cloud-atmosphere-renderer]}
  [other]
  (let [render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        planet-config   (:sfsim.planet/config other)
        shadow-data     (:sfsim.opacity/data other)
        data            (:sfsim.clouds/data other)
        tilesize        (:sfsim.planet/tilesize planet-config)
        program         (make-program :sfsim.render/vertex [vertex-atmosphere]
                                      :sfsim.render/fragment [(fragment-atmosphere-clouds (:sfsim.opacity/num-steps shadow-data)
                                                                                          (:sfsim.clouds/perlin-octaves data)
                                                                                          (:sfsim.clouds/cloud-octaves data))])]
    (use-program program)
    (setup-shadow-and-opacity-maps program shadow-data 7)
    (setup-cloud-render-uniforms program data 3)
    (setup-cloud-sampling-uniforms program data 6)
    (setup-atmosphere-uniforms program atmosphere-luts 0 false)
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {:sfsim.clouds/program program
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.render/config render-config
     :sfsim.clouds/data data}))

(defn render-cloud-atmosphere
  "Render clouds above horizon (not tested)"
  {:malli/schema [:=> [:cat cloud-atmosphere-renderer render-vars shadow-vars] :nil]}
  [{:sfsim.clouds/keys [program data] :as other} render-vars shadow-vars]
  (let [render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        indices         [0 1 3 2]
        vertices        (mapv #(* % (:sfsim.render/z-far render-vars)) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
        vao             (make-vertex-array-object program indices vertices ["point" 3])
        world-to-camera (inverse (:sfsim.render/camera-to-world render-vars))]
    (use-program program)
    (uniform-float program "lod_offset" (lod-offset render-config data render-vars))
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "camera_to_world" (:sfsim.render/camera-to-world render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
           (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
           (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item)))
    (use-textures {0 (:sfsim.atmosphere/transmittance atmosphere-luts) 1 (:sfsim.atmosphere/scatter atmosphere-luts)
                   2 (:sfsim.atmosphere/mie atmosphere-luts) 3 (:sfsim.clouds/worley data) 4 (:sfsim.clouds/perlin-worley data)
                   5 (:sfsim.clouds/cloud-cover data) 6 (:sfsim.clouds/bluenoise data)})
    (use-textures (zipmap (drop 7 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                   (:sfsim.opacity/opacities shadow-vars))))
    (render-quads vao)
    (destroy-vertex-array-object vao)))

(defn destroy-cloud-atmosphere-renderer
  "Destroy cloud rendering OpenGL program (not tested)"
  {:malli/schema [:=> [:cat cloud-atmosphere-renderer] :nil]}
  [{:sfsim.clouds/keys [program]}]
  (destroy-program program))

(def planet-renderer (m/schema [:map [::program :int] [:sfsim.atmosphere/luts atmosphere-luts] [::config planet-config]]))

(defn make-planet-renderer
  "Program to render planet with cloud overlay (untested)"
  {:malli/schema [:=> [:cat [:map [::config planet-config] [:sfsim.render/config render-config]
                                  [:sfsim.atmosphere/luts atmosphere-luts] [:sfsim.opacity/data shadow-data]]] planet-renderer]}
  [{::keys [config] :as other}]
  (let [tilesize        (::tilesize config)
        render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        shadow-data     (:sfsim.opacity/data other)
        program         (make-program :sfsim.render/vertex [vertex-planet]
                                      :sfsim.render/tess-control [tess-control-planet]
                                      :sfsim.render/tess-evaluation [tess-evaluation-planet]
                                      :sfsim.render/geometry [geometry-planet]
                                      :sfsim.render/fragment [(fragment-planet (:sfsim.opacity/num-steps shadow-data))])]
    (use-program program)
    (uniform-sampler program "surface"          0)
    (uniform-sampler program "day"              1)
    (uniform-sampler program "night"            2)
    (uniform-sampler program "normals"          3)
    (uniform-sampler program "water"            4)
    (uniform-sampler program "clouds"           9)
    (setup-shadow-and-opacity-maps program shadow-data 10)
    (setup-atmosphere-uniforms program atmosphere-luts 5 true)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-float program "dawn_start" (::dawn-start config))
    (uniform-float program "dawn_end" (::dawn-end config))
    (uniform-float program "specular" (:sfsim.render/specular render-config))
    (uniform-float program "radius" (::radius config))
    (uniform-float program "albedo" (::albedo config))
    (uniform-float program "reflectivity" (::reflectivity config))
    (uniform-vector3 program "water_color" (::water-color config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {::program program
     :sfsim.atmosphere/luts atmosphere-luts
     ::config config}))

(defn render-planet
  "Render planet (untested)"
  {:malli/schema [:=> [:cat planet-renderer render-vars shadow-vars texture-2d [:maybe :map]] :nil]}
  [{::keys [program] :as other} render-vars shadow-vars clouds tree]
  (let [atmosphere-luts (:sfsim.atmosphere/luts other)
        world-to-camera (inverse (:sfsim.render/camera-to-world render-vars))]
    (use-program program)
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-matrix4 program "world_to_camera" world-to-camera)
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (uniform-int program "window_width" (:sfsim.render/window-width render-vars))
    (uniform-int program "window_height" (:sfsim.render/window-height render-vars))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
           (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
           (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item)))
    (use-textures {5 (:sfsim.atmosphere/transmittance atmosphere-luts) 6 (:sfsim.atmosphere/scatter atmosphere-luts)
                   7 (:sfsim.atmosphere/mie atmosphere-luts) 8 (:sfsim.atmosphere/surface-radiance atmosphere-luts) 9 clouds})
    (use-textures (zipmap (drop 10 (range)) (concat (:sfsim.opacity/shadows shadow-vars)
                                                    (:sfsim.opacity/opacities shadow-vars))))
    (render-tree program tree world-to-camera [::surf-tex ::day-tex ::night-tex ::normal-tex ::water-tex])))

(defn destroy-planet-renderer
  "Destroy planet rendering program (untested)"
  {:malli/schema [:=> [:cat planet-renderer] :nil]}
  [{::keys [program]}]
  (destroy-program program))

(defn load-tile-into-opengl
  "Load textures of single tile into OpenGL (untested)"
  {:malli/schema [:=> [:cat :map tile-info] tile-info]}
  [{::keys [program config]} tile]
  (let [tilesize       (::tilesize config)
        color-tilesize (::color-tilesize config)
        indices        [0 2 3 1]
        vertices       (make-cube-map-tile-vertices (:sfsim.quadtree/face tile) (:sfsim.quadtree/level tile)
                                                    (:sfsim.quadtree/y tile) (:sfsim.quadtree/x tile) tilesize color-tilesize)
        vao            (make-vertex-array-object program indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
        day-tex        (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp (::day tile))
        night-tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp (::night tile))
        surf-tex       (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                               #:sfsim.image{:width tilesize :height tilesize :data (::surface tile)})
        normal-tex     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp (::normals tile))
        water-tex      (make-ubyte-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width color-tilesize :height color-tilesize :data (::water tile)})]
    (assoc (dissoc tile ::day ::night ::surface ::normals ::water)
           ::vao vao ::day-tex day-tex ::night-tex night-tex ::surf-tex surf-tex ::normal-tex normal-tex ::water-tex water-tex)))

(defn load-tiles-into-opengl
  "Load tiles into OpenGL (untested)"
  {:malli/schema [:=> [:cat :map :map [:sequential [:vector :keyword]]] :map]}
  [planet-renderer tree paths]
  (quadtree-update tree paths (partial load-tile-into-opengl planet-renderer)))

(defn unload-tile-from-opengl
  "Remove textures of single tile from OpenGL (untested)"
  {:malli/schema [:=> [:cat tile-info] :nil]}
  [tile]
  (destroy-texture (::day-tex tile))
  (destroy-texture (::night-tex tile))
  (destroy-texture (::surf-tex tile))
  (destroy-texture (::normal-tex tile))
  (destroy-texture (::water-tex tile))
  (destroy-vertex-array-object (::vao tile)))

(defn unload-tiles-from-opengl
  "Remove tile textures from OpenGL (untested)"
  {:malli/schema [:=> [:cat [:sequential tile-info]] :nil]}
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))

(defn background-tree-update
  "Method to call in a backround thread for loading tiles (untested)"
  {:malli/schema [:=> [:cat :map :map N fvec3] :map]}
  [{::keys [config]} tree width position]
  (let [tilesize  (::tilesize config)
        increase? (partial increase-level? tilesize (::radius config) width 60.0 10 6 position)]; TODO: use params for values
    (update-level-of-detail tree (::radius config) increase? true)))

(def tree (m/schema [:map [:tree :some] [:changes :some]]))

(defn make-tile-tree
  "Create empty tile tree and empty change object (untested)"
  {:malli/schema [:=> :cat tree]}
  []
  {:tree    (atom {})
   :changes (atom (future {:tree {} :drop [] :load []}))})

(defn update-tile-tree
  "Schedule background tile tree updates (untested)"
  {:malli/schema [:=> [:cat :map tree N fvec3] :any]}
  [planet-renderer {:keys [tree changes]} width position]
  (when (realized? @changes)
    (let [data @@changes]
      (unload-tiles-from-opengl (:drop data))
      (reset! tree (load-tiles-into-opengl planet-renderer (:tree data) (:load data)))
      (reset! changes (future (background-tree-update planet-renderer @tree width position))))))

(defn get-current-tree
  "Get current state of tile tree (untested)"
  {:malli/schema [:=> [:cat tree] :map]}
  [{:keys [tree]}]
  @tree)

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
