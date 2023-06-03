(require '[fastmath.vector :refer (vec3 mag emult add mult)]
         '[fastmath.matrix :refer (mulv inverse)]
         '[clojure.core.async :refer (go-loop chan <! <!! >! >!! poll! close!)]
         '[clojure.math :refer (cos sin sqrt pow to-radians PI)]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.planet :refer :all]
         '[sfsim25.quadtree :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.planet :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.glfw GLFW GLFWKeyCallback]
        '[org.lwjgl.opengl GL])

(set! *unchecked-math* true)

(def width 960)
(def height 540)

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow width height "scratch" 0 0))

(def radius 6378000.0)
(def polar-radius 6357000.0)
(def max-height 35000.0)
(def tilesize 33)
(def color-tilesize 129)

(def light1 (atom (to-radians 0)))
;(def position (atom (vec3 0 (* -0 radius) (+ (* 1 polar-radius) 100))))
(def orientation (atom (q/rotation (to-radians 90) (vec3 1 0 0))))
(def position (atom (vec3 0 (* -2 radius) 0)))
(def z-near 100)
(def z-far (* 2.0 radius))

(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def surface-height-size 16)
(def surface-sun-elevation-size 63)

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def E (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data data}))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere attenuation-outer ray-scatter-outer transmittance-outer shaders/ray-sphere
                           shaders/transmittance-forward shaders/ray-scatter-forward shaders/elevation-to-index
                           shaders/height-to-index shaders/horizon-distance shaders/limit-quot shaders/sun-elevation-to-index
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d shaders/interpolate-2d
                           shaders/convert-2d-index shaders/interpolate-4d shaders/is-above-horizon shaders/ray-shell
                           attenuation-track transmittance-track ray-scatter-track phase-function]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def atmosphere-vao (make-vertex-array-object program-atmosphere indices vertices [:point 3]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere "transmittance" 0)
(uniform-sampler program-atmosphere "ray_scatter" 1)
(uniform-sampler program-atmosphere "mie_strength" 2)

(def tree-state (chan))
(def changes (chan))
(def tree (atom {}))

(go-loop []
         (if-let [tree (<! tree-state)]
                 (let [increase? (partial increase-level? tilesize radius polar-radius width 60 10 5 @position)]
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
                           shaders/make-2d-index-from-4d shaders/is-above-horizon shaders/ray-shell
                           shaders/clip-shell-intersections phase-function shaders/surface-radiance-forward
                           transmittance-outer surface-radiance-function]))

(use-program program-planet)
(uniform-sampler program-planet "transmittance"    0)
(uniform-sampler program-planet "ray_scatter"      1)
(uniform-sampler program-planet "mie_strength"     2)
(uniform-sampler program-planet "surface_radiance" 3)
(uniform-sampler program-planet "heightfield"      4)
(uniform-sampler program-planet "colors"           5)
(uniform-sampler program-planet "normals"          6)
(uniform-sampler program-planet "water"            7)

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
    (uniform-int program-planet "neighbours" neighbours)
    (use-textures T S M E (:height-tex tile) (:color-tex tile) (:normal-tex tile) (:water-tex tile))
    (render-patches (:vao tile))))

(defn render-tree
  [node]
  (if node
    (if (is-leaf? node)
      (if-not (empty? node) (render-tile node))
      (doseq [selector [:0 :1 :2 :3 :4 :5]]
        (render-tree (selector node))))))

(>!! tree-state @tree)

(def projection (projection-matrix width height z-near z-far (to-radians 60)))

(use-program program-planet)
(uniform-matrix4 program-planet "projection" projection)
(uniform-int program-planet "high_detail" (dec tilesize))
(uniform-int program-planet "low_detail" (quot (dec tilesize) 2))
(uniform-int program-planet "height_size" height-size)
(uniform-int program-planet "elevation_size" elevation-size)
(uniform-int program-planet "light_elevation_size" light-elevation-size)
(uniform-int program-planet "heading_size" heading-size)
(uniform-int program-planet "transmittance_height_size" transmittance-height-size)
(uniform-int program-planet "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-planet "surface_height_size" surface-height-size)
(uniform-int program-planet "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-float program-planet "albedo" 0.9)
(uniform-float program-planet "reflectivity" 0.1)
(uniform-float program-planet "specular" 1000)
(uniform-float program-planet "radius" radius)
(uniform-float program-planet "polar_radius" polar-radius)
(uniform-float program-planet "max_height" max-height)
(uniform-vector3 program-planet "water_color" (vec3 0.09 0.11 0.34))
(uniform-float program-planet "amplification" 6)

(use-program program-atmosphere)
(uniform-matrix4 program-atmosphere "projection" projection)
(uniform-float program-atmosphere "z_near" z-near)
(uniform-float program-atmosphere "z_far" z-far)
(uniform-float program-atmosphere "radius" radius)
(uniform-float program-atmosphere "polar_radius" polar-radius)
(uniform-float program-atmosphere "max_height" max-height)
(uniform-float program-atmosphere "specular" 1000)
(uniform-int program-atmosphere "height_size" height-size)
(uniform-int program-atmosphere "elevation_size" elevation-size)
(uniform-int program-atmosphere "light_elevation_size" light-elevation-size)
(uniform-int program-atmosphere "heading_size" heading-size)
(uniform-int program-atmosphere "transmittance_height_size" transmittance-height-size)
(uniform-int program-atmosphere "transmittance_elevation_size" transmittance-elevation-size)
(uniform-int program-atmosphere "surface_height_size" surface-height-size)
(uniform-int program-atmosphere "surface_sun_elevation_size" surface-sun-elevation-size)
(uniform-float program-atmosphere "amplification" 6)

(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (if (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (if (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(def t0 (atom (System/currentTimeMillis)))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1        (System/currentTimeMillis)
             dt        (- t1 @t0)
             transform (transformation-matrix (quaternion->matrix @orientation) @position)
             ra        (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb        (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
             rc        (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))
             v         (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 500 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -500 0))
             l         (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
         (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
         (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
         (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
         (swap! light1 + (* l 0.1 dt))
         (onscreen-render window
                          (clear (vec3 0 1 0))
                          ; Render planet
                          (when-let [data (poll! changes)]
                                    (unload-tiles-from-opengl (:drop data))
                                    (>!! tree-state (reset! tree (load-tiles-into-opengl (:tree data) (:load data)))))
                          (use-program program-planet)
                          (uniform-matrix4 program-planet "inverse_transform" (inverse transform))
                          (uniform-vector3 program-planet "position" @position)
                          (uniform-vector3 program-planet "light_direction" (vec3 (cos @light1) (sin @light1) 0))
                          (render-tree @tree)
                          ; Render atmosphere
                          (use-program program-atmosphere)
                          (uniform-matrix4 program-atmosphere "transform" transform)
                          (uniform-vector3 program-atmosphere "light_direction" (vec3 (cos @light1) (sin @light1) 0))
                          (use-textures T S M)
                          (render-quads atmosphere-vao))
         (GLFW/glfwPollEvents)
         (print (format "\rdt: %5.3f, h: %6.0f            "
                        (* 0.001 dt)
                        (- (mag (emult @position (vec3 1 1 (/ radius polar-radius)))) radius)))
         (flush)
         (swap! t0 + dt)))

(close! tree-state)
(close! changes)

; unload all planet tiles (vaos and textures)

(destroy-program program-planet)
(destroy-program program-atmosphere)
(destroy-texture M)
(destroy-texture S)
(destroy-texture T)
(destroy-texture E)
(destroy-vertex-array-object atmosphere-vao)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)

(set! *unchecked-math* false)
