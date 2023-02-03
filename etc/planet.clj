(require '[clojure.core.matrix :refer (matrix add mul mmul inverse)]
         '[clojure.core.async :refer (go-loop chan <! <!! >! >!! poll! close!)]
         '[clojure.math :refer (cos sin sqrt pow to-radians PI)]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.planet :refer :all]
         '[sfsim25.quadtree :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.planet :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
;(Display/setFullscreen true)
(Display/create)

(Keyboard/create)

(def radius 6378000.0)
(def polar-radius 6357000.0)
(def max-height 35000.0)
(def tilesize 33)
(def color-tilesize 129)

(def threshold (atom 0.15))
(def anisotropic (atom 0.3))
(def multiplier (atom 1.2))

(def light1 (atom 0.149))
(def light2 (atom 0))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 polar-radius) 1500)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def z-near 1000)
(def z-far (* 2.0 radius))

(def keystates (atom {}))

(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def surface-height-size 16)
(def surface-sun-elevation-size 63)
(def worley-size 64)

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def E (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data data}))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def data (float-array [0.0 0.6 0.6 0.6 0.6 0.6 0.6 0.6 0.6 0.5 0.3 0]))
(def P (atom (make-float-texture-1d :linear :clamp data)))

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere attenuation-outer ray-scatter-outer transmittance-outer shaders/ray-sphere
                           shaders/transmittance-forward shaders/ray-scatter-forward shaders/elevation-to-index
                           shaders/height-to-index shaders/horizon-distance shaders/limit-quot shaders/sun-elevation-to-index
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d shaders/interpolate-2d
                           shaders/convert-2d-index shaders/interpolate-4d shaders/is-above-horizon sky-outer shaders/ray-shell
                           cloud-track attenuation-track cloud-density transmittance-track cloud-shadow ray-scatter-track
                           cloud-track-base exponential-sampling phase-function]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def atmosphere-vao (make-vertex-array-object program-atmosphere indices vertices [:point 3]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere :transmittance 0)
(uniform-sampler program-atmosphere :ray_scatter 1)
(uniform-sampler program-atmosphere :mie_strength 2)
(uniform-sampler program-atmosphere :worley 3)
(uniform-sampler program-atmosphere :cloud_profile 4)

(def tree-state (chan))
(def changes (chan))
(def tree (atom {}))

(go-loop []
         (if-let [tree (<! tree-state)]
                 (let [increase? (partial increase-level? tilesize radius polar-radius (Display/getWidth) 60 10 5 @position)]
                   (>! changes (update-level-of-detail tree increase? true))
                   (recur))))

(def program-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet attenuation-track shaders/ray-sphere ground-radiance shaders/transmittance-forward
                           transmittance-track shaders/height-to-index shaders/horizon-distance shaders/sun-elevation-to-index
                           shaders/limit-quot shaders/sun-angle-to-index shaders/interpolate-2d shaders/interpolate-4d
                           ray-scatter-track shaders/elevation-to-index shaders/convert-2d-index shaders/ray-scatter-forward
                           shaders/make-2d-index-from-4d shaders/is-above-horizon sky-track shaders/ray-shell
                           shaders/clip-shell-intersections cloud-track cloud-density cloud-shadow cloud-track-base
                           exponential-sampling phase-function shaders/surface-radiance-forward]))

(use-program program-planet)
(uniform-sampler program-planet :transmittance    0)
(uniform-sampler program-planet :ray_scatter      1)
(uniform-sampler program-planet :mie_strength     2)
(uniform-sampler program-planet :surface_radiance 3)
(uniform-sampler program-planet :worley           4)
(uniform-sampler program-planet :cloud_profile    5)
(uniform-sampler program-planet :heightfield      6)
(uniform-sampler program-planet :colors           7)
(uniform-sampler program-planet :normals          8)
(uniform-sampler program-planet :water            9)

(defn load-tile-into-opengl
  [tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program-planet indices vertices [:point 3 :heightcoord 2 :colorcoord 2])
        color-tex  (make-rgb-texture :linear :clamp (:colors tile))
        height-tex (make-float-texture-2d :linear :clamp {:width tilesize :height tilesize :data (:scales tile)})
        normal-tex (make-vector-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:normals tile)})
        water-tex  (make-ubyte-texture-2d :linear :clamp {:width color-tilesize :height color-tilesize :data (:water tile)})]
    (assoc (dissoc tile :colors :scales :normals :water)
           :vao vao :color-tex color-tex :height-tex height-tex :normal-tex normal-tex :water-tex water-tex)))

(defn load-tiles-into-opengl
  [tree paths]
  (quadtree-update tree paths load-tile-into-opengl))

(defn unload-tile-from-opengl
  [tile]
  (destroy-texture (:color-tex tile))
  (destroy-texture (:height-tex tile))
  (destroy-texture (:normal-tex tile))
  (destroy-texture (:water-tex tile))
  (destroy-vertex-array-object (:vao tile)))

(defn unload-tiles-from-opengl
  [tiles]
  (doseq [tile tiles] (unload-tile-from-opengl tile)))

(defn render-tile
  [tile]
  (let [neighbours (bit-or (if (:sfsim25.quadtree/up    tile) 1 0)
                           (if (:sfsim25.quadtree/left  tile) 2 0)
                           (if (:sfsim25.quadtree/down  tile) 4 0)
                           (if (:sfsim25.quadtree/right tile) 8 0))]
    (uniform-int program-planet :neighbours neighbours)
    (use-textures T S M E W @P (:height-tex tile) (:color-tex tile) (:normal-tex tile) (:water-tex tile))
    (render-patches (:vao tile))))

(defn render-tree
  [node]
  (if node
    (if (is-leaf? node)
      (render-tile node)
      (doseq [selector [:0 :1 :2 :3 :4 :5]]
        (render-tree (selector node))))))

(>!! tree-state @tree)

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))

(use-program program-planet)
(uniform-matrix4 program-planet :projection projection)
(uniform-int program-planet :high_detail (dec tilesize))
(uniform-int program-planet :low_detail (quot (dec tilesize) 2))
(uniform-int program-planet :height_size height-size)
(uniform-int program-planet :elevation_size elevation-size)
(uniform-int program-planet :light_elevation_size light-elevation-size)
(uniform-int program-planet :heading_size heading-size)
(uniform-int program-planet :transmittance_height_size transmittance-height-size)
(uniform-int program-planet :transmittance_elevation_size transmittance-elevation-size)
(uniform-int program-planet :surface_height_size surface-height-size)
(uniform-int program-planet :surface_sun_elevation_size surface-sun-elevation-size)
(uniform-float program-planet :albedo 0.9)
(uniform-float program-planet :reflectivity 0.1)
(uniform-float program-planet :specular 500)
(uniform-float program-planet :radius radius)
(uniform-float program-planet :polar_radius polar-radius)
(uniform-float program-planet :max_height max-height)
(uniform-float program-planet :cloud_bottom 0)
(uniform-float program-planet :cloud_top -1)
(uniform-float program-planet :cloud_scale 5000)
(uniform-int program-planet :cloud_size worley-size)
(uniform-int program-planet :cloud_samples 96)
(uniform-float program-planet :cloud_scatter_amount 0.2)
(uniform-float program-planet :cloud_max_step 1.06)
(uniform-int program-planet :cloud_base_samples 8)
(uniform-float program-planet :transparency_cutoff 0.1)
(uniform-vector3 program-planet :water_color (matrix [0.09 0.11 0.34]))
(uniform-float program-planet :amplification 6)

(use-program program-atmosphere)
(uniform-matrix4 program-atmosphere :projection projection)
(uniform-float program-atmosphere :radius radius)
(uniform-float program-atmosphere :polar_radius polar-radius)
(uniform-float program-atmosphere :max_height max-height)
(uniform-float program-atmosphere :cloud_bottom 0)
(uniform-float program-atmosphere :cloud_top -1)
(uniform-float program-atmosphere :cloud_scale 5000)
(uniform-int program-atmosphere :cloud_size worley-size)
(uniform-int program-atmosphere :cloud_samples 96)
(uniform-float program-atmosphere :cloud_scatter_amount 0.2)
(uniform-float program-atmosphere :cloud_max_step 1.06)
(uniform-int program-atmosphere :cloud_base_samples 8)
(uniform-float program-atmosphere :transparency_cutoff 0.1)
(uniform-float program-atmosphere :specular 500)
(uniform-int program-atmosphere :height_size height-size)
(uniform-int program-atmosphere :elevation_size elevation-size)
(uniform-int program-atmosphere :light_elevation_size light-elevation-size)
(uniform-int program-atmosphere :heading_size heading-size)
(uniform-int program-atmosphere :transmittance_height_size transmittance-height-size)
(uniform-int program-atmosphere :transmittance_elevation_size transmittance-elevation-size)
(uniform-int program-atmosphere :surface_height_size surface-height-size)
(uniform-int program-atmosphere :surface_sun_elevation_size surface-sun-elevation-size)
(uniform-float program-atmosphere :amplification 6)


(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1        (System/currentTimeMillis)
             dt        (- t1 @t0)
             transform (transformation-matrix (quaternion->matrix @orientation) @position)
             ra        (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
             rb        (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             rc        (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
             v         (if (@keystates Keyboard/KEY_PRIOR) 500 (if (@keystates Keyboard/KEY_NEXT) -500 0))
             tr        (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             ts        (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))
             tm        (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))
             l         (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! threshold + (* dt tr))
         (swap! anisotropic + (* dt ts))
         (swap! multiplier + (* dt tm))
         (swap! light1 + (* l 0.1 dt))
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 1 0]))
                          (let [data (float-array (map #(+ @threshold %) [0.0 0.1 0.2 0.3 0.4 0.5 0.5 0.4 0.3 0.2 0.1 0.0]))]
                            (destroy-texture @P)
                            (reset! P (make-float-texture-1d :linear :clamp data)))
                          ; Render planet
                          (when-let [data (poll! changes)]
                            (unload-tiles-from-opengl (:drop data))
                            (>!! tree-state (reset! tree (load-tiles-into-opengl (:tree data) (:load data)))))
                          (use-program program-planet)
                          (uniform-float program-planet :threshold @threshold)
                          (uniform-float program-planet :anisotropic @anisotropic)
                          (uniform-float program-planet :cloud_multiplier (* 0.01 @multiplier))
                          (uniform-matrix4 program-planet :inverse_transform (inverse transform))
                          (uniform-vector3 program-planet :position @position)
                          (uniform-vector3 program-planet :light_direction (mmul (rotation-z @light2) (matrix [0 (cos @light1) (sin @light1)])))
                          (render-tree @tree)
                          ; Render atmosphere
                          (use-program program-atmosphere)
                          (uniform-float program-atmosphere :threshold @threshold)
                          (uniform-float program-atmosphere :anisotropic @anisotropic)
                          (uniform-float program-atmosphere :cloud_multiplier (* 0.01 @multiplier))
                          (uniform-matrix4 program-atmosphere :transform transform)
                          (uniform-vector3 program-atmosphere :origin @position)
                          (uniform-vector3 program-atmosphere :light_direction (mmul (rotation-z @light2) (matrix [0 (cos @light1) (sin @light1)])))
                          (use-textures T S M W @P)
                          (render-quads atmosphere-vao))
         (print "\rthreshold (q/a)" (format "%.3f" @threshold)
                "anisotropic (w/s)" (format "%.3f" @anisotropic)
                "multiplier (e/d)" (format "%.3f" @multiplier)
                "dt" (format "%5.3f    " (* 0.001 dt)) "              ")
         (flush)
         (swap! t0 + dt)))

(close! tree-state)
(close! changes)

; unload all planet tiles (vaos and textures)

(destroy-program program-planet)
(destroy-program program-atmosphere)
(destroy-texture W)
(destroy-texture @P)
(destroy-texture S)
(destroy-texture T)
(destroy-texture E)
(destroy-vertex-array-object atmosphere-vao)
(Display/destroy)

(set! *unchecked-math* false)
