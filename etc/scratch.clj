(require '[clojure.core.matrix :refer (matrix div sub mul add mget) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin to-radians)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.ray :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

; ------------------------------------------------------------------------------
(def d 3)
(def size 64)

(defn points [n] (vec (repeatedly n #(mul size (matrix (repeatedly d rand))))))

; TODO: repeat points in all directions

(def points1 (points 30))
(def points2 (points 120))

(defn values [points] (vec (cp/pfor (+ 2 (cp/ncpus)) [i (range size) j (range size) k (range size)]
                           (apply min (map (fn [point] (norm (sub point (matrix [i j k])))) points)))))

(defn normed [values] (let [largest (apply max values)] (vec (pmap #(/ % largest) values))))
(defn inverted [values] (vec (pmap #(- 1 %) values)))

(def values1 (normed (values points1)))
(def values2 (normed (values points2)))

(def mixed (inverted (pmap #(* %1 (+ 0.25 (* 0.75 %2))) values1 values2)))

(show-floats {:width size :height size :data (float-array (take (* size size) mixed))})

; ------------------------------------------------------------------------------
(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(def z-near 10)
(def z-far 1000)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))
(def origin (matrix [0 0 -100]))
(def transform (transformation-matrix (quaternion->matrix (q/rotation (to-radians 90) (matrix [1 0 0]))) origin))

(def vertex-shader "#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in highp vec3 point;
out VS_OUT
{
  highp vec3 direction;
} vs_out;
void main()
{
  vs_out.direction = (transform * vec4(point, 0)).xyz;
  gl_Position = projection * vec4(point, 1);
}")

(def fragment-shader "#version 410 core
uniform vec3 origin;
in VS_OUT
{
  highp vec3 direction;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")

(def program
  (make-program :vertex [vertex-shader]
                :fragment [fragment-shader]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)

(onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0.5 0.5 0.5]))
                 (use-program program)
                 (uniform-matrix4 program :projection projection)
                 (uniform-matrix4 program :transform transform)
                 (uniform-vector3 program :origin origin)
                 (render-quads vao))

(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
