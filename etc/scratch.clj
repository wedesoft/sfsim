(require '[clojure.core.matrix :refer (matrix div sub mul add mget mmul) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin to-radians)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.quaternion :as q]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
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
(def multiplier (atom 1.0))
(def multiplier2 (atom 1.0))
(def initial (atom 1.0))
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
uniform float multiplier;
uniform float multiplier2;
uniform float initial;
in VS_OUT
{
  highp vec3 direction;
} fs_in;
out vec3 fragColor;
float phase(float g, float mu);
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
float interpolate_3d(sampler3D tex, vec3 point, vec3 box_min, vec3 box_max);
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float cos_light = dot(light, direction);
  vec3 bg = 0.7 * pow(max(cos_light, 0), 1000) + vec3 (0.3, 0.3, 0.5);
  vec2 intersection = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), origin, direction);
  if (intersection.y > 0) {
    for (int i=63; i>=0; i--) {
      vec3 point = origin + (intersection.x + (i + 0.5) / 64 * intersection.y) * direction;
      float s = interpolate_3d(tex, point, vec3(-30, -30, -30), vec3(30, 30, 30));
      if (s > threshold) {
        float dacc = multiplier * (s - threshold) * intersection.y / 64;
        vec2 intersection2 = ray_box(vec3(-30, -30, -30), vec3(30, 30, 30), point, light);
        float bright = initial;
        for (int j=0; j<6; j++) {
          vec3 point2 = point + (intersection2.x + (j + 0.5) / 6 * intersection2.y) * light;
          float s2 = interpolate_3d(tex, point2, vec3(-30, -30, -30), vec3(30, 30, 30));
          if (s2 > threshold) {
            bright = bright * exp(-multiplier2 * (s2 - threshold) * intersection2.y / 6);
          }
        }
        float scatt = phase(0.76, dot(direction, light));
        scatt = shadowing * scatt + (1 - shadowing);
        float m = exp(-dacc / 30);
        bg = m * bg + bright * scatt * (1 - m);
      }
    }
    fragColor = bg;
  } else
    fragColor = bg;
}")

(def program
  (make-program :vertex [vertex-shader]
                :fragment [fragment-shader s/ray-box s/convert-3d-index s/interpolate-3d phase-function]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def size 64)

;(def values1 (worley-noise 30 size))
;(def values2 (worley-noise 120 size))
;(def mixed (float-array (pmap #(* %1 (+ 0.25 (* 0.75 %2))) values1 values2)))
;(spit-floats "values1.raw" (float-array values1))
;(spit-floats "values2.raw" (float-array values2))
;(spit-floats "mixed.raw" mixed)

(def mixed (slurp-floats "mixed.raw"))

(def tex (make-float-texture-3d {:width size :height size :depth size :data mixed}))

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
             tr (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             ts (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))
             tm (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))
             t2 (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))
             ti (if (@keystates Keyboard/KEY_T) 0.001 (if (@keystates Keyboard/KEY_G) -0.001 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.0005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.0005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! threshold + (* dt tr))
         (swap! shadowing + (* dt ts))
         (swap! multiplier + (* dt tm))
         (swap! multiplier2 + (* dt t2))
         (swap! initial + (* dt ti))
         (reset! origin (mmul (quaternion->matrix @orientation) (matrix [0 0 100])))
         (swap! light + (* l dt))
         (swap! t0 + dt))
       (print "\rthreshold" (format "%.3f" @threshold)
              "shadowing" (format "%.3f" @shadowing)
              "multiplier" (format "%.3f" @multiplier)
              "multiplier2" (format "%.3f" @multiplier2)
              "initila" (format "%.3f" @initial) "              ")
       (onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 0 0]))
                 (use-program program)
                 (use-textures tex)
                 (uniform-matrix4 program :projection projection)
                 (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @origin))
                 (uniform-vector3 program :origin @origin)
                 (uniform-float program :threshold @threshold)
                 (uniform-float program :shadowing @shadowing)
                 (uniform-float program :multiplier @multiplier)
                 (uniform-float program :multiplier2 @multiplier2)
                 (uniform-float program :initial @initial)
                 (uniform-vector3 program :light (matrix [0 (cos @light) (sin @light)]))
                 (render-quads vao)))

(destroy-texture tex)
(destroy-vertex-array-object vao)
(destroy-program program)
(Display/destroy)
