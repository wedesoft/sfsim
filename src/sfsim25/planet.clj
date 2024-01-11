(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [fastmath.matrix :refer (mulm eye)]
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
              [sfsim25.clouds :refer (overall-shadow cloud-planet)]
              [sfsim25.shaders :as shaders])
    (:import [fastmath.matrix Mat4x4]))

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
  [& {:keys [tilesize shadow-size]}]
  (let [program (make-program :vertex [vertex-planet]
                              :tess-control [tess-control-planet]
                              :tess-evaluation [tess-evaluation-planet-shadow]
                              :geometry [geometry-planet]
                              :fragment [fragment-planet-shadow])]
    (use-program program)
    (uniform-sampler program "surface" 0)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "shadow_size" shadow-size)
    {:program program
     :shadow-size shadow-size}))

(defn render-shadow-cascade
  "Render planetary shadow cascade (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] [:vector texture-2d]]}
  [{:keys [program shadow-size]} & {:keys [matrix-cascade tree]}]
  (shadow-cascade shadow-size matrix-cascade program (fn [transform] (render-tree program tree transform [:surf-tex]))))

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
  [& {:keys [num-steps radius max-height depth tilesize amplification
             num-opacity-layers shadow-size transmittance scatter mie cloud-data]}]
  (let [program (make-program :vertex [vertex-planet]
                              :tess-control [tess-control-planet]
                              :tess-evaluation [tess-evaluation-planet]
                              :geometry [geometry-planet]
                              :fragment [(fragment-planet-clouds num-steps
                                                                 (:perlin-octaves cloud-data)
                                                                 (:cloud-octaves cloud-data))])]
    (use-program program)
    (uniform-sampler program "surface"          0)
    (uniform-sampler program "transmittance"    1)
    (uniform-sampler program "ray_scatter"      2)
    (uniform-sampler program "mie_strength"     3)
    (uniform-sampler program "worley"           4)
    (uniform-sampler program "perlin"           5)
    (uniform-sampler program "bluenoise"        6)
    (uniform-sampler program "cover"            7)
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "shadow_map" i) (+ i 8)))
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "opacity" i) (+ i 8 num-steps)))
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-float program "cloud_bottom" (:cloud-bottom cloud-data))
    (uniform-float program "cloud_top" (:cloud-top cloud-data))
    (uniform-float program "cloud_scale" (:cloud-scale cloud-data))
    (uniform-float program "detail_scale" (:detail-scale cloud-data))
    (uniform-float program "depth" depth)
    (uniform-int program "cover_size" (:width (:cloud-cover cloud-data)))
    (uniform-int program "noise_size" (:width (:bluenoise cloud-data)))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" (:hyperdepth scatter))
    (uniform-int program "elevation_size" (:depth scatter))
    (uniform-int program "light_elevation_size" (:height scatter))
    (uniform-int program "heading_size" (:width scatter))
    (uniform-int program "transmittance_height_size" (:height transmittance))
    (uniform-int program "transmittance_elevation_size" (:width transmittance))
    (uniform-float program "cloud_multiplier" (:cloud-multiplier cloud-data))
    (uniform-float program "cover_multiplier" (:cover-multiplier cloud-data))
    (uniform-float program "cap" (:cap cloud-data))
    (uniform-float program "anisotropic" (:anisotropic cloud-data))
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-float program "amplification" amplification)
    (uniform-float program "opacity_cutoff" (:opacity-cutoff cloud-data))
    (uniform-int program "num_opacity_layers" num-opacity-layers)
    (uniform-int program "shadow_size" shadow-size)
    {:program program
     :transmittance transmittance
     :scatter scatter
     :mie mie
     :cloud-data cloud-data}))

(defn render-cloud-planet
  "Render clouds below horizon (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] :nil]}
  [{:keys [program transmittance scatter mie cloud-data]}
   & {:keys [cloud-step cloud-threshold lod-offset projection origin transform light-direction opacity-step splits matrix-cascade
             shadows opacities tree]}]
  (use-program program)
  (uniform-float program "cloud_step" cloud-step)
  (uniform-float program "cloud_threshold" cloud-threshold)
  (uniform-float program "lod_offset" lod-offset)
  (uniform-matrix4 program "projection" projection)
  (uniform-vector3 program "origin" origin)
  (uniform-matrix4 program "transform" transform)
  (uniform-vector3 program "light_direction" light-direction)
  (uniform-float program "opacity_step" opacity-step)
  (doseq [[idx item] (map-indexed vector splits)]
         (uniform-float program (str "split" idx) item))
  (doseq [[idx item] (map-indexed vector matrix-cascade)]
         (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
         (uniform-float program (str "depth" idx) (:depth item)))
  (use-textures {1 transmittance 2 scatter 3 mie 4 (:worley cloud-data) 5 (:perlin-worley cloud-data) 6 (:bluenoise cloud-data)
                 7 (:cloud-cover cloud-data)})
  (use-textures (zipmap (drop 8 (range)) (concat shadows opacities)))
  (render-tree program tree transform [:surf-tex]))

(defn destroy-cloud-planet-renderer
  "Destroy program for rendering clouds below horizon (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(defn make-planet-renderer
  "Program to render planet with cloud overlay (untested)"
  {:malli/schema [:=> [:cat [:* :any]] :map]}
  [& {:keys [width height num-steps tilesize color-tilesize albedo dawn-start dawn-end reflectivity specular radius max-height
             water-color amplification num-opacity-layers shadow-size transmittance scatter mie surface-radiance]}]
  (let [program (make-program :vertex [vertex-planet]
                              :tess-control [tess-control-planet]
                              :tess-evaluation [tess-evaluation-planet]
                              :geometry [geometry-planet]
                              :fragment [(fragment-planet num-steps)])]
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
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "shadow_map" i) (+ i 10)))
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "opacity" i) (+ i 10 num-steps)))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" (:hyperdepth scatter))
    (uniform-int program "elevation_size" (:depth scatter))
    (uniform-int program "light_elevation_size" (:height scatter))
    (uniform-int program "heading_size" (:width scatter))
    (uniform-int program "transmittance_height_size" (:height transmittance))
    (uniform-int program "transmittance_elevation_size" (:width transmittance))
    (uniform-int program "surface_height_size" (:height surface-radiance))
    (uniform-int program "surface_sun_elevation_size" (:width surface-radiance))
    (uniform-float program "albedo" albedo)
    (uniform-float program "dawn_start" dawn-start)
    (uniform-float program "dawn_end" dawn-end)
    (uniform-float program "reflectivity" reflectivity)
    (uniform-float program "specular" specular)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-vector3 program "water_color" water-color)
    (uniform-float program "amplification" amplification)
    (uniform-int program "num_opacity_layers" num-opacity-layers)
    (uniform-int program "shadow_size" shadow-size)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    {:width width
     :height height
     :program program
     :radius radius
     :tilesize tilesize
     :color-tilesize color-tilesize
     :transmittance transmittance
     :scatter scatter
     :mie mie
     :surface-radiance surface-radiance}))

(defn render-planet
  "Render planet (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] :nil]}
  [{:keys [program transmittance scatter mie surface-radiance]}
   & {:keys [projection origin transform light-direction opacity-step window-width window-height shadow-bias splits
             matrix-cascade clouds shadows opacities tree]}]
  (use-program program)
  (uniform-matrix4 program "projection" projection)
  (uniform-vector3 program "origin" origin)
  (uniform-matrix4 program "transform" transform)
  (uniform-vector3 program "light_direction" light-direction)
  (uniform-float program "opacity_step" opacity-step)
  (uniform-int program "window_width" window-width)
  (uniform-int program "window_height" window-height)
  (uniform-float program "shadow_bias" shadow-bias)
  (doseq [[idx item] (map-indexed vector splits)]
         (uniform-float program (str "split" idx) item))
  (doseq [[idx item] (map-indexed vector matrix-cascade)]
         (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
         (uniform-float program (str "depth" idx) (:depth item)))
  (use-textures {5 transmittance 6 scatter 7 mie 8 surface-radiance 9 clouds})
  (use-textures (zipmap (drop 10 (range)) (concat shadows opacities)))
  (render-tree program tree transform [:surf-tex :day-tex :night-tex :normal-tex :water-tex]))

(defn destroy-planet-renderer
  "Destroy planet rendering program (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(defn load-tile-into-opengl
  "Load textures of single tile into OpenGL (untested)"
  {:malli/schema [:=> [:cat :map tile-info] tile-info]}
  [{:keys [program tilesize color-tilesize]} tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
        day-tex    (make-rgb-texture :linear :clamp (:day tile))
        night-tex  (make-rgb-texture :linear :clamp (:night tile))
        surf-tex   (make-vector-texture-2d :linear :clamp {:width tilesize :height tilesize :data (:surface tile)})
        normal-tex (make-vector-texture-2d :linear :clamp (:normals tile))
        water-tex  (make-ubyte-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:water tile)})]
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
  {:malli/schema [:=> [:cat :map :map fvec3] :map]}
  [{:keys [tilesize radius width]} tree position]
  (let [increase? (partial increase-level? tilesize radius width 60.0 10 6 position)]; TODO: use parameters for values
    (update-level-of-detail tree radius increase? true)))

(def tree (m/schema [:map [:tree :some] [:changes :some]]))

(defn make-tile-tree
  "Create empty tile tree and empty change object (untested)"
  {:malli/schema [:=> :cat tree]}
  []
  {:tree    (atom [])
   :changes (atom (future {:tree {} :drop [] :load []}))})

(defn update-tile-tree
  "Schedule background tile tree updates (untested)"
  {:malli/schema [:=> [:cat :map tree fvec3] :any]}
  [planet-renderer {:keys [tree changes]} position]
  (when (realized? @changes)
    (let [data @@changes]
      (unload-tiles-from-opengl (:drop data))
      (reset! tree (load-tiles-into-opengl planet-renderer (:tree data) (:load data)))
      (reset! changes (future (background-tree-update planet-renderer @tree position))))))

(defn get-current-tree
  "Get current state of tile tree (untested)"
  {:malli/schema [:=> [:cat tree] :map]}
  [{:keys [tree]}]
  @tree)

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
