(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [fastmath.matrix :refer (mulm eye inverse)]
              [malli.core :as m]
              [sfsim25.matrix :refer (transformation-matrix fmat4 fvec3)]
              [sfsim25.cubemap :refer (cube-map-corners)]
              [sfsim25.quadtree :refer (is-leaf? increase-level? quadtree-update update-level-of-detail tile-info)]
              [sfsim25.render :refer (uniform-int uniform-vector3 uniform-matrix4 use-textures render-patches make-program
                                      use-program uniform-sampler destroy-program shadow-cascade destroy-texture uniform-float
                                      make-vertex-array-object make-rgb-texture make-vector-texture-2d make-ubyte-texture-2d
                                      destroy-vertex-array-object vertex-array-object texture-2d) :as render]
              [sfsim25.atmosphere :refer (transmittance-outer attenuation-track cloud-overlay)]
              [sfsim25.util :refer (N N0)]
              [sfsim25.clouds :refer (overall-shadow cloud-planet lod-offset)]
              [sfsim25.shaders :as shaders]))

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

(def ground-radiance
  "Shader function to compute light emitted from ground"
  [shaders/is-above-horizon transmittance-outer surface-radiance-function shaders/remap
   (slurp "resources/shaders/planet/ground-radiance.glsl")])

(defn fragment-planet
  "Fragment shader to render planetary surface"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [shaders/ray-sphere ground-radiance attenuation-track cloud-overlay (overall-shadow num-steps)
   (slurp "resources/shaders/planet/fragment.glsl")])

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
  {:malli/schema [:=> [:cat :int [:map [:vao vertex-array-object]] fmat4 [:vector :keyword]] :nil]}
  [program tile transform ^clojure.lang.PersistentVector texture-keys]
  (let [neighbours  (bit-or (if (:sfsim25.quadtree/up    tile) 1 0)
                            (if (:sfsim25.quadtree/left  tile) 2 0)
                            (if (:sfsim25.quadtree/down  tile) 4 0)
                            (if (:sfsim25.quadtree/right tile) 8 0))
        tile-center (:center tile)]
    (uniform-int program "neighbours" neighbours)
    (uniform-vector3 program "tile_center" tile-center)
    (uniform-matrix4 program "recenter_and_transform" (mulm transform (transformation-matrix (eye 3) tile-center)))
    (use-textures (zipmap (range) (map tile texture-keys)))
    (render-patches (:vao tile))))

(defn render-tree
  "Call each tile in tree to be rendered"
  {:malli/schema [:=> [:cat :int [:maybe :map] :any [:vector :keyword]] :nil]}
  [program node transform texture-keys]
  (when-not (empty? node)
            (if (is-leaf? node)
              (render-tile program node transform texture-keys)
              (doseq [selector [:0 :1 :2 :3 :4 :5]]
                     (render-tree program (selector node) transform texture-keys)))))

(defn make-planet-shadow-renderer
  "Create program for rendering cascaded shadow maps of planet (untested)"
  {:malli/schema [:=> [:cat [:* :any]] :map]}
  [& {:keys [planet-data shadow-data]}]
  (let [tilesize (:sfsim25.planet/tilesize planet-data)
        program  (make-program :vertex [vertex-planet]
                               :tess-control [tess-control-planet]
                               :tess-evaluation [tess-evaluation-planet-shadow]
                               :geometry [geometry-planet]
                               :fragment [fragment-planet-shadow])]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "shadow_size" (:sfsim25.opacity/shadow-size shadow-data))
    {:program program
     :shadow-data shadow-data}))

(defn render-shadow-cascade
  "Render planetary shadow cascade (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] [:vector texture-2d]]}
  [{:keys [program shadow-data]} & {:keys [tree] :as data}]
  (shadow-cascade (:sfsim25.opacity/shadow-size shadow-data) (:sfsim25.opacity/matrix-cascade data) program
                  (fn [transform] (render-tree program tree transform [:surf-tex]))))

(defn destroy-shadow-cascade
  "Destroy cascade of shadow maps (untested)"
  {:malli/schema [:=> [:cat [:vector texture-2d]] :nil]}
  [shadows]
  (doseq [shadow shadows]
         (destroy-texture shadow)))

(defn destroy-planet-shadow-renderer
  "Destroy renderer for planet shadow (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(defn make-cloud-planet-renderer
  "Make a renderer to render clouds below horizon (untested)"
  {:malli/schema [:=> [:cat [:* :any]] :map]}
  [& {:keys [render-data atmosphere-luts planet-data cloud-data shadow-data]}]
  (let [tilesize (:sfsim25.planet/tilesize planet-data)
        program  (make-program :vertex [vertex-planet]
                               :tess-control [tess-control-planet]
                               :tess-evaluation [tess-evaluation-planet]
                               :geometry [geometry-planet]
                               :fragment [(fragment-planet-clouds (:sfsim25.opacity/num-steps shadow-data)
                                                                  (:sfsim25.clouds/perlin-octaves cloud-data)
                                                                  (:sfsim25.clouds/cloud-octaves cloud-data))])]
    (use-program program)
    (uniform-sampler program "surface"          0)
    (uniform-sampler program "transmittance"    1)
    (uniform-sampler program "ray_scatter"      2)
    (uniform-sampler program "mie_strength"     3)
    (uniform-sampler program "worley"           4)
    (uniform-sampler program "perlin"           5)
    (uniform-sampler program "bluenoise"        6)
    (uniform-sampler program "cover"            7)
    (doseq [i (range (:sfsim25.opacity/num-steps shadow-data))]
           (uniform-sampler program (str "shadow_map" i) (+ i 8)))
    (doseq [i (range (:sfsim25.opacity/num-steps shadow-data))]
           (uniform-sampler program (str "opacity" i) (+ i 8 (:sfsim25.opacity/num-steps shadow-data))))
    (uniform-float program "radius" (::radius planet-data))
    (uniform-float program "max_height" (:sfsim25.atmosphere/max-height atmosphere-luts))
    (uniform-float program "cloud_bottom" (:sfsim25.clouds/cloud-bottom cloud-data))
    (uniform-float program "cloud_top" (:sfsim25.clouds/cloud-top cloud-data))
    (uniform-float program "cloud_scale" (:sfsim25.clouds/cloud-scale cloud-data))
    (uniform-float program "detail_scale" (:sfsim25.clouds/detail-scale cloud-data))
    (uniform-int program "cover_size" (:width (:sfsim25.clouds/cloud-cover cloud-data)))
    (uniform-int program "noise_size" (:width (:sfsim25.clouds/bluenoise cloud-data)))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" (:hyperdepth (:scatter atmosphere-luts)))
    (uniform-int program "elevation_size" (:depth (:scatter atmosphere-luts)))
    (uniform-int program "light_elevation_size" (:height (:scatter atmosphere-luts)))
    (uniform-int program "heading_size" (:width (:scatter atmosphere-luts)))
    (uniform-int program "transmittance_height_size" (:height (:transmittance atmosphere-luts)))
    (uniform-int program "transmittance_elevation_size" (:width (:transmittance atmosphere-luts)))
    (uniform-float program "cloud_multiplier" (:sfsim25.clouds/cloud-multiplier cloud-data))
    (uniform-float program "cover_multiplier" (:sfsim25.clouds/cover-multiplier cloud-data))
    (uniform-float program "cap" (:sfsim25.clouds/cap cloud-data))
    (uniform-float program "cloud_threshold" (:sfsim25.clouds/threshold cloud-data))
    (uniform-float program "anisotropic" (:sfsim25.clouds/anisotropic cloud-data))
    (uniform-float program "amplification" (:sfsim25.render/amplification render-data))
    (uniform-float program "cloud_step" (:sfsim25.clouds/cloud-step cloud-data))
    (uniform-float program "opacity_cutoff" (:sfsim25.clouds/opacity-cutoff cloud-data))
    (uniform-int program "num_opacity_layers" (:sfsim25.opacity/num-opacity-layers shadow-data))
    (uniform-int program "shadow_size" (:sfsim25.opacity/shadow-size shadow-data))
    (uniform-float program "depth" (:sfsim25.opacity/depth shadow-data))
    {:program program
     :atmosphere-luts atmosphere-luts
     :cloud-data cloud-data
     :render-data render-data}))

(defn render-cloud-planet
  "Render clouds below horizon (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] :nil]}
  [{:keys [program atmosphere-luts cloud-data render-data]} render-vars shadow-vars & {:keys [tree]}]
  (let [transform    (inverse (:sfsim25.render/extrinsics render-vars))]
    (use-program program)
    (uniform-float program "lod_offset" (lod-offset render-data cloud-data render-vars))
    (uniform-matrix4 program "projection" (:sfsim25.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim25.render/origin render-vars))
    (uniform-matrix4 program "transform" transform)
    (uniform-vector3 program "light_direction" (:sfsim25.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim25.opacity/opacity-step shadow-vars))
    (doseq [[idx item] (map-indexed vector (:sfsim25.opacity/splits shadow-vars))]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector (:sfsim25.opacity/matrix-cascade shadow-vars))]
           (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
           (uniform-float program (str "depth" idx) (:depth item)))
    (use-textures {1 (:transmittance atmosphere-luts) 2 (:scatter atmosphere-luts) 3 (:mie atmosphere-luts)
                   4 (:sfsim25.clouds/worley cloud-data)
                   5 (:sfsim25.clouds/perlin-worley cloud-data) 6 (:sfsim25.clouds/bluenoise cloud-data)
                   7 (:sfsim25.clouds/cloud-cover cloud-data)})
    (use-textures (zipmap (drop 8 (range)) (concat (:sfsim25.opacity/shadows shadow-vars)
                                                   (:sfsim25.opacity/opacities shadow-vars))))
    (render-tree program tree transform [:surf-tex])))

(defn destroy-cloud-planet-renderer
  "Destroy program for rendering clouds below horizon (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(defn make-planet-renderer
  "Program to render planet with cloud overlay (untested)"
  {:malli/schema [:=> [:cat [:* :any]] :map]}
  [& {:keys [render-data atmosphere-luts planet-data shadow-data]}]
  (let [tilesize (:sfsim25.planet/tilesize planet-data)
        program  (make-program :vertex [vertex-planet]
                               :tess-control [tess-control-planet]
                               :tess-evaluation [tess-evaluation-planet]
                               :geometry [geometry-planet]
                               :fragment [(fragment-planet (:sfsim25.opacity/num-steps shadow-data))])]
    (use-program program)
    (uniform-sampler program "surface"          0)
    (uniform-sampler program "day"              1)
    (uniform-sampler program "night"            2)
    (uniform-sampler program "normals"          3)
    (uniform-sampler program "water"            4)
    (uniform-sampler program "transmittance"    5)
    (uniform-sampler program "ray_scatter"      6)
    (uniform-sampler program "mie_strength"     7)
    (uniform-sampler program "surface_radiance" 8)
    (uniform-sampler program "clouds"           9)
    (doseq [i (range (:sfsim25.opacity/num-steps shadow-data))]
           (uniform-sampler program (str "shadow_map" i) (+ i 10)))
    (doseq [i (range (:sfsim25.opacity/num-steps shadow-data))]
           (uniform-sampler program (str "opacity" i) (+ i 10 (:sfsim25.opacity/num-steps shadow-data))))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" (:hyperdepth (:scatter atmosphere-luts)))
    (uniform-int program "elevation_size" (:depth (:scatter atmosphere-luts)))
    (uniform-int program "light_elevation_size" (:height (:scatter atmosphere-luts)))
    (uniform-int program "heading_size" (:width (:scatter atmosphere-luts)))
    (uniform-int program "transmittance_height_size" (:height (:transmittance atmosphere-luts)))
    (uniform-int program "transmittance_elevation_size" (:width (:transmittance atmosphere-luts)))
    (uniform-int program "surface_height_size" (:height (:surface-radiance atmosphere-luts)))
    (uniform-int program "surface_sun_elevation_size" (:width (:surface-radiance atmosphere-luts)))
    (uniform-float program "dawn_start" (:sfsim25.planet/dawn-start planet-data))
    (uniform-float program "dawn_end" (:sfsim25.planet/dawn-end planet-data))
    (uniform-float program "specular" (:sfsim25.render/specular render-data))
    (uniform-float program "radius" (::radius planet-data))
    (uniform-float program "max_height" (:sfsim25.atmosphere/max-height atmosphere-luts))
    (uniform-float program "albedo" (::albedo planet-data))
    (uniform-float program "reflectivity" (::reflectivity planet-data))
    (uniform-vector3 program "water_color" (::water-color planet-data))
    (uniform-float program "amplification" (:sfsim25.render/amplification render-data))
    (uniform-int program "num_opacity_layers" (:sfsim25.opacity/num-opacity-layers shadow-data))
    (uniform-int program "shadow_size" (:sfsim25.opacity/shadow-size shadow-data))
    (uniform-float program "shadow_bias" (:sfsim25.opacity/shadow-bias shadow-data))
    {:program program
     :atmosphere-luts atmosphere-luts
     :planet-data planet-data}))

(defn render-planet
  "Render planet (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] :nil]}
  [{:keys [program atmosphere-luts]} render-vars shadow-vars & {:keys [clouds tree]}]
  (let [transform (inverse (:sfsim25.render/extrinsics render-vars))]
    (use-program program)
    (uniform-matrix4 program "projection" (:sfsim25.render/projection render-vars))
    (uniform-vector3 program "origin" (:sfsim25.render/origin render-vars))
    (uniform-matrix4 program "transform" transform)
    (uniform-vector3 program "light_direction" (:sfsim25.render/light-direction render-vars))
    (uniform-float program "opacity_step" (:sfsim25.opacity/opacity-step shadow-vars))
    (uniform-int program "window_width" (:sfsim25.render/window-width render-vars))
    (uniform-int program "window_height" (:sfsim25.render/window-height render-vars))
    (doseq [[idx item] (map-indexed vector (:sfsim25.opacity/splits shadow-vars))]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector (:sfsim25.opacity/matrix-cascade shadow-vars))]
           (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
           (uniform-float program (str "depth" idx) (:depth item)))
    (use-textures {5 (:transmittance atmosphere-luts) 6 (:scatter atmosphere-luts) 7 (:mie atmosphere-luts)
                   8 (:surface-radiance atmosphere-luts) 9 clouds})
    (use-textures (zipmap (drop 10 (range)) (concat (:sfsim25.opacity/shadows shadow-vars)
                                                    (:sfsim25.opacity/opacities shadow-vars))))
    (render-tree program tree transform [:surf-tex :day-tex :night-tex :normal-tex :water-tex])))

(defn destroy-planet-renderer
  "Destroy planet rendering program (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(defn load-tile-into-opengl
  "Load textures of single tile into OpenGL (untested)"
  {:malli/schema [:=> [:cat :map tile-info] tile-info]}
  [{:keys [program planet-data]} tile]
  (let [tilesize       (:sfsim25.planet/tilesize planet-data)
        color-tilesize (:sfsim25.planet/color-tilesize planet-data)
        indices        [0 2 3 1]
        vertices       (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao            (make-vertex-array-object program indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
        day-tex        (make-rgb-texture :linear :clamp (:day tile))
        night-tex      (make-rgb-texture :linear :clamp (:night tile))
        surf-tex       (make-vector-texture-2d :linear :clamp {:width tilesize :height tilesize :data (:surface tile)})
        normal-tex     (make-vector-texture-2d :linear :clamp (:normals tile))
        water-tex      (make-ubyte-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:water tile)})]
    (assoc (dissoc tile :day :night :surface :normals :water)
           :vao vao :day-tex day-tex :night-tex night-tex :surf-tex surf-tex :normal-tex normal-tex :water-tex water-tex)))

(defn load-tiles-into-opengl
  "Load tiles into OpenGL (untested)"
  {:malli/schema [:=> [:cat :map :map [:sequential [:vector :keyword]]] :map]}
  [planet-renderer tree paths]
  (quadtree-update tree paths (partial load-tile-into-opengl planet-renderer)))

(defn unload-tile-from-opengl
  "Remove textures of single tile from OpenGL (untested)"
  {:malli/schema [:=> [:cat tile-info] :nil]}
  [tile]
  (destroy-texture (:day-tex tile))
  (destroy-texture (:night-tex tile))
  (destroy-texture (:surf-tex tile))
  (destroy-texture (:normal-tex tile))
  (destroy-texture (:water-tex tile))
  (destroy-vertex-array-object (:vao tile)))

(defn unload-tiles-from-opengl
  "Remove tile textures from OpenGL (untested)"
  {:malli/schema [:=> [:cat [:sequential tile-info]] :nil]}
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))

(defn background-tree-update
  "Method to call in a backround thread for loading tiles (untested)"
  {:malli/schema [:=> [:cat :map :map N fvec3] :map]}
  [{:keys [planet-data]} tree width position]
  (let [tilesize  (:sfsim25.planet/tilesize planet-data)
        increase? (partial increase-level? tilesize (::radius planet-data) width 60.0 10 6 position)]; TODO: use params for values
    (update-level-of-detail tree (::radius planet-data) increase? true)))

(def tree (m/schema [:map [:tree :some] [:changes :some]]))

(defn make-tile-tree
  "Create empty tile tree and empty change object (untested)"
  {:malli/schema [:=> :cat tree]}
  []
  {:tree    (atom [])
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
