(require '[clojure.core.matrix :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.planet :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment-atmosphere shaders/ray-sphere shaders/transmittance-forward shaders/horizon-angle
                           shaders/ray-scatter-forward shaders/elevation-to-index shaders/oriented-matrix shaders/interpolate-4d
                           shaders/orthogonal-vector shaders/clip-angle shaders/convert-4d-index shaders/interpolate-2d
                           shaders/convert-2d-index]))

(def radius 6378000.0)
(def polar-radius 6357000.0)
(def max-height 35000.0)

(def indices [0 1 3 2])
(def vertices (map #(* % 4 radius) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program-atmosphere indices vertices [:point 3]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere :transmittance 0)
(uniform-sampler program-atmosphere :ray_scatter 1)

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def E (make-vector-texture-2d {:width size :height size :data data}))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def T (make-vector-texture-2d {:width size :height size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (Math/pow (/ (count data) 3) 0.25)))
(def S (make-vector-texture-2d {:width (* size size) :height (* size size) :data data}))

(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) 10000 (* 4 6378000) (Math/toRadians 120)))

(def light (atom (* 1.4 Math/PI)))
(def position (atom (matrix [0 (* -1.5 radius) 0])))
(def orientation (atom (q/rotation (Math/toRadians 90) (matrix [1 0 0]))))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             transform (transformation-matrix (quaternion->matrix @orientation) @position)]
         (onscreen-render (.getWidth desktop) (.getHeight desktop)
                          (clear (matrix [1 0 0]))
                          (use-program program-atmosphere)
                          (uniform-matrix4 program-atmosphere :projection projection)
                          (uniform-matrix4 program-atmosphere :transform transform)
                          (uniform-vector3 program-atmosphere :light (matrix [0 (Math/cos @light) (Math/sin @light)]))
                          (uniform-float program-atmosphere :radius radius)
                          (uniform-float program-atmosphere :polar_radius polar-radius)
                          (uniform-float program-atmosphere :max_height max-height)
                          (uniform-float program-atmosphere :specular 50)
                          (uniform-float program-atmosphere :power 2.0)
                          (uniform-int program-atmosphere :size size)
                          (uniform-float program-atmosphere :amplification 10)
                          (uniform-vector3 program-atmosphere :origin @position)
                          (use-textures T S)
                          (render-quads vao))
         (swap! t0 + dt)
         (swap! light + (* 0.001 0.1 dt))))

(destroy-program program-atmosphere)
(destroy-texture S)
(destroy-texture T)
(destroy-vertex-array-object vao)
(Display/destroy)

(set! *unchecked-math* false)
