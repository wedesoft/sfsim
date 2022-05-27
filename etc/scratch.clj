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

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)
(Keyboard/create)

(def z-near 10)
(def z-far 1000)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))
(def origin (atom (matrix [0 0 100])))
(def orientation (atom (q/rotation (to-radians 0) (matrix [1 0 0]))))
(def threshold (atom 0.57))
(def shadowing (atom 0.37))
(def light (atom 0.0))

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
uniform vec3 light;
uniform sampler3D tex;
uniform float threshold;
uniform float shadowing;
float M_PI = 3.14159265358;
in VS_OUT
{
  highp vec3 direction;
} fs_in;
out vec3 fragColor;
float phase(float g, float mu);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float cos_light = dot(light, direction);
  vec3 bg = 0.7 * pow(max(cos_light, 0), 1000) + vec3 (0.3, 0.3, 0.5);
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), origin, direction);
  if (intersection.y > 0) {
    float acc = 0.0;
    float cld = 0.5;
    for (int i=63; i>=0; i--) {
      vec3 point = origin + (intersection.x + (i + 0.5) / 64 * intersection.y) * direction;
      float s = texture(tex, (point + vec3(30, 30, 30)) / 60).r;
      if (s > threshold) {
        float dacc = (s - threshold) * intersection.y / 64;
        acc += dacc;
        vec2 intersection2 = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), point, light);
        float acc2 = 0.0;
        for (int j=0; j<6; j++) {
          vec3 point2 = point + (intersection2.x + (j + 0.5) / 6 * intersection2.y) * light;
          float s2 = texture(tex, (point2 + vec3(30, 30, 30)) / 60).r;
          if (s2 > threshold) {
            acc2 += (s2 - threshold) * intersection2.y / 6;
          }
        }
        float mu = dot(direction, light);
        float g = 0.76;
        float scatt = phase(g, mu);
        scatt = shadowing * scatt + (1 - shadowing);
        float cld_bright = exp(-acc2 / 30) * scatt;
        cld = (cld_bright * dacc + cld * (acc - dacc)) / acc;
      }
    }
    float t = exp(-acc / 30);
    fragColor = vec3(cld, cld, cld) * (1 - t) + bg * t;
  } else
    fragColor = bg;
}")

(def program
  (make-program :vertex [vertex-shader]
                :fragment [fragment-shader s/ray-box phase-function]))

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
             rc (if (@keystates Keyboard/KEY_NUMPAD3) 0.001 (if (@keystates Keyboard/KEY_NUMPAD1) -0.001 0))
             tr (if (@keystates Keyboard/KEY_MULTIPLY) 0.001 (if (@keystates Keyboard/KEY_DIVIDE) -0.001 0))
             ts (if (@keystates Keyboard/KEY_NUMPAD7) 0.001 (if (@keystates Keyboard/KEY_NUMPAD0) -0.001 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.0005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.0005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! threshold + (* dt tr))
         (swap! shadowing + (* dt ts))
         (reset! origin (mmul (quaternion->matrix @orientation) (matrix [0 0 100])))
         (swap! light + (* l dt))
         (swap! t0 + dt))
       (onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 0 0]))
                 (use-program program)
                 (use-textures tex)
                 (uniform-matrix4 program :projection projection)
                 (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @origin))
                 (uniform-vector3 program :origin @origin)
                 (uniform-float program :threshold @threshold)
                 (uniform-float program :shadowing @shadowing)
                 (uniform-vector3 program :light (matrix [0 (cos @light) (sin @light)]))
                 (render-quads vao)))

(destroy-texture tex)
(destroy-vertex-array-object vao)
(destroy-program program)
(Display/destroy)
