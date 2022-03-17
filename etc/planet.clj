(require '[clojure.core.matrix :refer :all]
         '[clojure.core.async :refer (go-loop chan <! <!! >! >!! poll! close!)]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.planet :refer :all]
         '[sfsim25.quadtree :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.planet :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(def radius 6378000.0)
(def polar-radius 6357000.0)
(def max-height 35000.0)
(def tilesize 33)
(def color-tilesize 129)

(def light1 (atom 0.1756884862652619))
(def light2 (atom 0))
(def position (atom nil))
(reset! position (matrix [0 (* 0 radius) (+ polar-radius 2500)]))
(def orientation (atom (q/rotation (/ Math/PI 2) (matrix [1 0 0]))))
(def z-near 100)
(def z-far (* 0.2 radius))

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def E (make-vector-texture-2d {:width size :height size :data data}))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def T (make-vector-texture-2d {:width size :height size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (Math/pow (/ (count data) 3) 0.25)))
(def S (make-vector-texture-2d {:width (* size size) :height (* size size) :data data}))

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere shaders/ray-sphere shaders/transmittance-forward shaders/horizon-angle
                           shaders/ray-scatter-forward shaders/elevation-to-index shaders/oriented-matrix shaders/interpolate-4d
                           shaders/orthogonal-vector shaders/clip-angle shaders/convert-4d-index shaders/interpolate-2d
                           shaders/convert-2d-index]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def atmosphere-vao (make-vertex-array-object program-atmosphere indices vertices [:point 3]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere :transmittance 0)
(uniform-sampler program-atmosphere :ray_scatter 1)

(def tree-state (chan))
(def changes (chan))
(def tree (atom {}))

(go-loop []
         (if-let [tree (<! tree-state)]
                 (let [increase? (partial increase-level? tilesize radius polar-radius (.getWidth desktop) 60 10 2 @position)]
                   (>! changes (update-level-of-detail tree increase? true))
                   (recur))))

(def program-planet
  (make-program :vertex [vertex-planet]
                :tess-control [tess-control-planet]
                :tess-evaluation [tess-evaluation-planet]
                :geometry [geometry-planet]
                :fragment [fragment-planet shaders/ray-sphere ground-radiance shaders/transmittance-forward
                           transmittance-track shaders/horizon-angle shaders/interpolate-2d shaders/interpolate-4d
                           ray-scatter-track shaders/elevation-to-index shaders/convert-2d-index shaders/ray-scatter-forward
                           shaders/oriented-matrix shaders/convert-4d-index shaders/orthogonal-vector shaders/clip-angle]))

(use-program program-planet)
(uniform-sampler program-planet :transmittance    0)
(uniform-sampler program-planet :ray_scatter      1)
(uniform-sampler program-planet :surface_radiance 2)
(uniform-sampler program-planet :heightfield      3)
(uniform-sampler program-planet :colors           4)
(uniform-sampler program-planet :normals          5)
(uniform-sampler program-planet :water            6)

(defn load-tile-into-opengl
  [tile]
  (let [indices    [0 2 3 1]
        vertices   (make-cube-map-tile-vertices (:face tile) (:level tile) (:y tile) (:x tile) tilesize color-tilesize)
        vao        (make-vertex-array-object program-planet indices vertices [:point 3 :heightcoord 2 :colorcoord 2])
        color-tex  (make-rgb-texture (:colors tile))
        height-tex (make-float-texture-2d {:width tilesize :height tilesize :data (:scales tile)})
        normal-tex (make-vector-texture-2d {:width color-tilesize :height color-tilesize :data (:normals tile)})
        water-tex  (make-ubyte-texture-2d {:width color-tilesize :height color-tilesize :data (:water tile)})]
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
    (uniform-int program-planet :high_detail (dec tilesize))
    (uniform-int program-planet :low_detail (quot (dec tilesize) 2))
    (uniform-int program-planet :neighbours neighbours)
    (use-textures T S E (:height-tex tile) (:color-tex tile) (:normal-tex tile) (:water-tex tile))
    (render-patches (:vao tile))))

(defn render-tree
  [node]
  (if node
    (if (is-leaf? node)
      (render-tile node)
      (doseq [selector [:0 :1 :2 :3 :4 :5]]
        (render-tree (selector node))))))

(>!! tree-state @tree)

(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) z-near z-far (Math/toRadians 60)))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             transform (transformation-matrix (quaternion->matrix @orientation) @position)]
         (onscreen-render (.getWidth desktop) (.getHeight desktop)
                          (clear (matrix [0 1 0]))
                          ; Render planet
                          (when-let [data (poll! changes)]
                            (unload-tiles-from-opengl (:drop data))
                            (>!! tree-state (reset! tree (load-tiles-into-opengl (:tree data) (:load data)))))
                          (use-program program-planet)
                          (uniform-matrix4 program-planet :projection projection)
                          (uniform-matrix4 program-planet :inverse_transform (inverse transform))
                          (uniform-int program-planet :size size)
                          (uniform-float program-planet :power 2.0)
                          (uniform-float program-planet :albedo 0.9)
                          (uniform-float program-planet :reflectivity 0.5)
                          (uniform-float program-planet :specular 50)
                          (uniform-float program-planet :radius radius)
                          (uniform-float program-planet :polar_radius polar-radius)
                          (uniform-float program-planet :max_height max-height)
                          (uniform-vector3 program-planet :water_color (matrix [0.09 0.11 0.34]))
                          (uniform-vector3 program-planet :position @position)
                          (uniform-vector3 program-planet :light_direction (mmul (rotation-z @light2) (matrix [0 (Math/cos @light1) (Math/sin @light1)])))
                          (uniform-float program-planet :amplification 5)
                          (render-tree @tree)
                          ; Render atmosphere
                          (use-program program-atmosphere)
                          (uniform-matrix4 program-atmosphere :projection projection)
                          (uniform-matrix4 program-atmosphere :transform transform)
                          (uniform-vector3 program-atmosphere :light (mmul (rotation-z @light2) (matrix [0 (Math/cos @light1) (Math/sin @light1)])))
                          (uniform-float program-atmosphere :radius radius)
                          (uniform-float program-atmosphere :polar_radius polar-radius)
                          (uniform-float program-atmosphere :max_height max-height)
                          (uniform-float program-atmosphere :specular 50)
                          (uniform-float program-atmosphere :power 2.0)
                          (uniform-int program-atmosphere :size size)
                          (uniform-float program-atmosphere :amplification 5)
                          (uniform-vector3 program-atmosphere :origin @position)
                          (use-textures T S)
                          (render-quads atmosphere-vao))
         (swap! t0 + dt)
         (swap! light2 + (* 0.001 0.1 dt))))

(close! tree-state)
(close! changes)

(destroy-program program-planet)
(destroy-program program-atmosphere)
(destroy-texture S)
(destroy-texture T)
(destroy-vertex-array-object atmosphere-vao)
(Display/destroy)

(set! *unchecked-math* false)
