(ns sfsim25.planet
    "Module with functionality to render a planet"
    (:require [fastmath.matrix :refer (mulm eye)]
              [sfsim25.matrix :refer (transformation-matrix)]
              [sfsim25.cubemap :refer (cube-map-corners)]
              [sfsim25.quadtree :refer (is-leaf? quadtree-update)]
              [sfsim25.render :refer (uniform-int uniform-vector3 uniform-matrix4 use-textures render-patches make-program
                                      use-program uniform-sampler destroy-program shadow-cascade destroy-texture uniform-float
                                      make-vertex-array-object make-rgb-texture make-vector-texture-2d make-ubyte-texture-2d
                                      destroy-vertex-array-object)]
              [sfsim25.atmosphere :refer (transmittance-outer attenuation-track cloud-overlay)]
              [sfsim25.clouds :refer (overall-shadow cloud-planet)]
              [sfsim25.shaders :as shaders])
    (:import [fastmath.matrix Mat4x4]))

(defn make-cube-map-tile-vertices
  "Create vertex array object for drawing cube map tiles"
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
  [num-steps]
  [shaders/ray-sphere ground-radiance attenuation-track cloud-overlay (overall-shadow num-steps)
   (slurp "resources/shaders/planet/fragment.glsl")])

(def fragment-planet-shadow
  "Fragment shader to render planetary shadow map"
  (slurp "resources/shaders/planet/fragment-shadow.glsl"))

(defn fragment-planet-clouds
  "Fragment shader to render clouds below horizon"
  [num-steps perlin-octaves cloud-octaves]
  [(cloud-planet num-steps perlin-octaves cloud-octaves) (slurp "resources/shaders/planet/fragment-clouds.glsl")])

(defn render-tile
  "Render a planetary tile using the specified texture keys and neighbour tessellation"
  [^long program ^clojure.lang.IPersistentMap tile ^Mat4x4 transform ^clojure.lang.PersistentVector texture-keys]
  (let [neighbours  (bit-or (if (:sfsim25.quadtree/up    tile) 1 0)
                            (if (:sfsim25.quadtree/left  tile) 2 0)
                            (if (:sfsim25.quadtree/down  tile) 4 0)
                            (if (:sfsim25.quadtree/right tile) 8 0))
        tile-center (:center tile)]
    (uniform-int program "neighbours" neighbours)
    (uniform-vector3 program "tile_center" tile-center)
    (uniform-matrix4 program "recenter_and_transform" (mulm transform (transformation-matrix (eye 3) tile-center)))
    (apply use-textures (map tile texture-keys))
    (render-patches (:vao tile))))

(defn render-tree
  "Call each tile in tree to be rendered"
  [^long program ^clojure.lang.IPersistentMap node ^Mat4x4 transform ^clojure.lang.PersistentVector texture-keys]
  (when-not (empty? node)
            (if (is-leaf? node)
              (render-tile program node transform texture-keys)
              (doseq [selector [:0 :1 :2 :3 :4 :5]]
                     (render-tree program (selector node) transform texture-keys)))))

(defn make-planet-shadow-renderer
  "Create program for rendering cascaded shadow maps of planet"
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
  "Render planetary shadow cascade"
  [{:keys [program shadow-size]} & {:keys [matrix-cascade tree]}]
  (shadow-cascade shadow-size matrix-cascade program (fn [transform] (render-tree program tree transform [:surf-tex]))))

(defn destroy-shadow-cascade
  "Destroy cascade of shadow maps"
  [shadows]
  (doseq [shadow shadows]
         (destroy-texture shadow)))

(defn destroy-planet-shadow-renderer
  "Destroy renderer for planet shadow"
  [{:keys [program]}]
  (destroy-program program))

(defn make-cloud-planet-renderer
  "Make a renderer to render clouds below horizon"
  [& {:keys [num-steps perlin-octaves cloud-octaves radius max-height cloud-bottom cloud-top cloud-scale detail-scale depth
             cover-size noise-size tilesize height-size elevation-size light-elevation-size heading-size
             transmittance-height-size transmittance-elevation-size surface-height-size surface-sun-elevation-size albedo
             reflectivity specular cloud-multiplier cover-multiplier cap anisotropic water-color amplification
             opacity-cutoff num-opacity-layers shadow-size transmittance-tex scatter-tex mie-tex worley-tex perlin-worley-tex
             bluenoise-tex cloud-cover-tex]}]
  (let [program (make-program :vertex [vertex-planet]
                              :tess-control [tess-control-planet]
                              :tess-evaluation [tess-evaluation-planet]
                              :geometry [geometry-planet]
                              :fragment [(fragment-planet-clouds num-steps perlin-octaves cloud-octaves)])]
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
    (uniform-float program "cloud_bottom" cloud-bottom)
    (uniform-float program "cloud_top" cloud-top)
    (uniform-float program "cloud_scale" cloud-scale)
    (uniform-float program "detail_scale" detail-scale)
    (uniform-float program "depth" depth)
    (uniform-int program "cover_size" cover-size)
    (uniform-int program "noise_size" noise-size)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" height-size)
    (uniform-int program "elevation_size" elevation-size)
    (uniform-int program "light_elevation_size" light-elevation-size)
    (uniform-int program "heading_size" heading-size)
    (uniform-int program "transmittance_height_size" transmittance-height-size)
    (uniform-int program "transmittance_elevation_size" transmittance-elevation-size)
    (uniform-int program "surface_height_size" surface-height-size)
    (uniform-int program "surface_sun_elevation_size" surface-sun-elevation-size)
    (uniform-float program "albedo" albedo)
    (uniform-float program "reflectivity" reflectivity)
    (uniform-float program "specular" specular)
    (uniform-float program "cloud_multiplier" cloud-multiplier)
    (uniform-float program "cover_multiplier" cover-multiplier)
    (uniform-float program "cap" cap)
    (uniform-float program "anisotropic" anisotropic)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-vector3 program "water_color" water-color)
    (uniform-float program "amplification" amplification)
    (uniform-float program "opacity_cutoff" opacity-cutoff)
    (uniform-int program "num_opacity_layers" num-opacity-layers)
    (uniform-int program "shadow_size" shadow-size)
    {:program program
     :transmittance-tex transmittance-tex
     :scatter-tex scatter-tex
     :mie-tex mie-tex
     :worley-tex worley-tex
     :perlin-worley-tex perlin-worley-tex
     :bluenoise-tex bluenoise-tex
     :cloud-cover-tex cloud-cover-tex}))

(defn render-cloud-planet
  "Render clouds below horizon"
  [{:keys [program transmittance-tex scatter-tex mie-tex worley-tex perlin-worley-tex bluenoise-tex cloud-cover-tex]}
   & {:keys [cloud-step cloud-threshold lod-offset projection origin transform light-direction opacity-step splits
             matrix-cascade shadows opacities tree]}]
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
  (apply use-textures nil transmittance-tex scatter-tex mie-tex worley-tex perlin-worley-tex bluenoise-tex cloud-cover-tex
         (concat shadows opacities))
  (render-tree program tree transform [:surf-tex]))

(defn destroy-cloud-planet-renderer
  "Destroy program for rendering clouds below horizon"
  [{:keys [program]}]
  (destroy-program program))

(defn make-planet-renderer
  "Program to render planet with cloud overlay"
  [& {:keys [num-steps cover-size noise-size tilesize height-size elevation-size light-elevation-size heading-size
             transmittance-height-size transmittance-elevation-size surface-height-size surface-sun-elevation-size color-tilesize
             albedo dawn-start dawn-end reflectivity specular radius max-height water-color amplification opacity-cutoff
             num-opacity-layers shadow-size transmittance-tex scatter-tex mie-tex surface-radiance-tex]}]
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
    (uniform-int program "cover_size" cover-size)
    (uniform-int program "noise_size" noise-size)
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" height-size)
    (uniform-int program "elevation_size" elevation-size)
    (uniform-int program "light_elevation_size" light-elevation-size)
    (uniform-int program "heading_size" heading-size)
    (uniform-int program "transmittance_height_size" transmittance-height-size)
    (uniform-int program "transmittance_elevation_size" transmittance-elevation-size)
    (uniform-int program "surface_height_size" surface-height-size)
    (uniform-int program "surface_sun_elevation_size" surface-sun-elevation-size)
    (uniform-float program "albedo" albedo)
    (uniform-float program "dawn_start" dawn-start)
    (uniform-float program "dawn_end" dawn-end)
    (uniform-float program "reflectivity" reflectivity)
    (uniform-float program "specular" specular)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-vector3 program "water_color" water-color)
    (uniform-float program "amplification" amplification)
    (uniform-float program "opacity_cutoff" opacity-cutoff)
    (uniform-int program "num_opacity_layers" num-opacity-layers)
    (uniform-int program "shadow_size" shadow-size)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    {:program program
     :tilesize tilesize
     :color-tilesize color-tilesize
     :transmittance-tex transmittance-tex
     :scatter-tex scatter-tex
     :mie-tex mie-tex
     :surface-radiance-tex surface-radiance-tex}))

(defn render-planet
  "Render planet"
  [{:keys [program transmittance-tex scatter-tex mie-tex surface-radiance-tex]}
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
  (apply use-textures nil nil nil nil nil transmittance-tex scatter-tex mie-tex surface-radiance-tex
         clouds (concat shadows opacities))
  (render-tree program tree transform [:surf-tex :day-tex :night-tex :normal-tex :water-tex]))

(defn destroy-planet-renderer
  "Destroy planet rendering program"
  [{:keys [program]}]
  (destroy-program program))

(defn load-tile-into-opengl
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
  [planet-renderer tree paths]
  (quadtree-update tree paths (partial load-tile-into-opengl planet-renderer)))

(defn unload-tile-from-opengl
  [tile]
  (destroy-texture (:day-tex tile))
  (destroy-texture (:night-tex tile))
  (destroy-texture (:surf-tex tile))
  (destroy-texture (:normal-tex tile))
  (destroy-texture (:water-tex tile))
  (destroy-vertex-array-object (:vao tile)))

(defn unload-tiles-from-opengl
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))
