(require '[clojure.core.matrix :refer (matrix div sub mul add mget mmul) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin to-radians)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.shaders :as s]
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
(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)
(Keyboard/create)

(def z-near 10)
(def z-far 1000)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))
(def origin (atom (matrix [0 0 100])))
(def orientation (atom (q/rotation (to-radians 0) (matrix [1 0 0]))))

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
uniform sampler3D tex;
in VS_OUT
{
  highp vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), origin, direction);
  if (intersection.y > 0) {
    float t = 1.0 - exp(-intersection.y / 30);
    fragColor = vec3(t, t, t);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def program
  (make-program :vertex [vertex-shader]
                :fragment [fragment-shader s/ray-box]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

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

(def tex (make-float-texture-3d {:width size :height size :depth size :data (float-array mixed)}))

;(show-floats {:width size :height size :data (float-array (take (* size size) mixed))})

(use-program program)
(uniform-sampler program :tex 0)

(def keystates (atom {}))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates Keyboard/KEY_NUMPAD8) 0.001 (if (@keystates Keyboard/KEY_NUMPAD2) -0.001 0))
             rb (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             rc (if (@keystates Keyboard/KEY_NUMPAD3) 0.001 (if (@keystates Keyboard/KEY_NUMPAD1) -0.001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (reset! origin (mmul (quaternion->matrix @orientation) (matrix [0 0 100])))
         (swap! t0 + dt))
       (onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 0 0]))
                 (use-program program)
                 (use-textures tex)
                 (uniform-matrix4 program :projection projection)
                 (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @origin))
                 (uniform-vector3 program :origin @origin)
                 (render-quads vao)))

(destroy-texture tex)
(destroy-vertex-array-object vao)
(destroy-program program)
(Display/destroy)
