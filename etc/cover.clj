(require '[clojure.math :refer (sin cos PI sqrt exp log)]
         '[clojure.core.matrix :refer (matrix mmul)]
         '[sfsim25.render :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.worley :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 640 1) (/ 640 1)))
(Display/create)

(Keyboard/create)

(def worley-size 64)

(if-not (.exists (clojure.java.io/file "w1.raw")) (spit-floats "w1.raw" (float-array (worley-noise 8 64 true))))
(if-not (.exists (clojure.java.io/file "w2.raw")) (spit-floats "w2.raw" (float-array (worley-noise 8 64 true))))
(if-not (.exists (clojure.java.io/file "w3.raw")) (spit-floats "w3.raw" (float-array (worley-noise 8 64 true))))

(def data (slurp-floats "w1.raw"))
(def W1 (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "w2.raw"))
(def W2 (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "w3.raw"))
(def W3 (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def cubemap-size 512)
(def warp (atom (identity-cubemap cubemap-size)))

(def potential (shaders/noise-octaves "potential" [0.5 0.25 0.125]))
(def noise
"#version 410 core
#define M_PI 3.1415926535897932384626433832795
uniform sampler3D worley1;
uniform sampler3D worley2;
uniform float curl_scale;
uniform float prevailing;
uniform float whirl;
float potential(sampler3D noise, vec3 idx, float lod);
float spin(float y)
{
  float angle = asin(y);
  return sin(y * 2.5);
}
float noise(vec3 point)
{
  float m = spin(point.y);
  float w1 = potential(worley1, point / curl_scale, 0.0) * whirl;
  float w2 = potential(worley2, point / curl_scale, 0.0) * whirl;
  return (w1 + prevailing) * (1 + m) / 2 - (w2 + prevailing) * (1 - m) / 2;
}")

(def curl-adapter
"#version 410 core
uniform float epsilon;
vec3 curl(vec3 point, float epsilon);
vec3 curl_adapter(vec3 point)
{
  return curl(point, epsilon);
}")

(def update-warp
  (make-iterate-cubemap-warp-program "current" "curl_adapter"
                                     [curl-adapter (curl-vector "curl" "gradient") shaders/rotate-vector shaders/oriented-matrix
                                      shaders/orthogonal-vector (shaders/gradient-3d "gradient" "noise")
                                      shaders/project-vector noise potential]))

(def clouds (shaders/noise-octaves "clouds" [0.25 0.25 0.125 0.125 0.0625 0.0625]))
(def noise
"#version 410 core
uniform sampler3D worley;
uniform float cloud_scale;
float clouds(sampler3D noise, vec3 idx, float lod);
float noise(vec3 point)
{
  return clouds(worley, point / cloud_scale, 0.0);
}")

(def lookup (make-cubemap-warp-program "current" "noise" [clouds noise]))

(def vertex-shader
"#version 410 core
in vec3 point;
out VS_OUT
{
  vec3 point;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-shader
"#version 410 core
uniform samplerCube cubemap;
uniform mat3 rotation;
uniform float threshold;
uniform float multiplier;
in VS_OUT
{
  vec3 point;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec3 fragColor;
void main()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = rotation * vec3(fs_in.point.xy, -1 + intersection.x);
    float value = texture(cubemap, p).r;
    value = (value - threshold) * multiplier;
    fragColor = vec3(value, value, 1.0);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def program (make-program :vertex [vertex-shader] :fragment [fragment-shader shaders/ray-sphere]))
(def indices [0 1 3 2])
(def vertices [-1 -1 0, 1 -1 0, -1 1 0, 1  1 0])
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(def keystates (atom {}))
(def alpha (atom 0))
(def beta (atom 0))
(def threshold (atom 0.35))
(def multiplier (atom 1.0))
(def curl-scale-exp (atom (log 8)))
(def cloud-scale-exp (atom (log 4)))
(def prevailing (atom 0.25))
(def whirl (atom 1.0))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
             rb (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             tr (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             ta (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))
             cs (if (@keystates Keyboard/KEY_E) 0.001 (if (@keystates Keyboard/KEY_D) -0.001 0))
             os (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))
             pw (if (@keystates Keyboard/KEY_T) 0.001 (if (@keystates Keyboard/KEY_G) -0.001 0))
             ps (if (@keystates Keyboard/KEY_Y) 0.001 (if (@keystates Keyboard/KEY_H) -0.001 0))]
         (swap! alpha + ra)
         (swap! beta + rb)
         (swap! threshold + (* dt tr))
         (swap! multiplier + (* dt ta))
         (swap! whirl + (* dt pw))
         (swap! prevailing + (* dt ps))
         (swap! curl-scale-exp + (* dt cs))
         (swap! cloud-scale-exp + (* dt os))
         (let [mat (mmul (rotation-y @beta) (rotation-x @alpha))
               updated (if (or (@keystates Keyboard/KEY_SPACE)
                               (@keystates Keyboard/KEY_W) (@keystates Keyboard/KEY_S)
                               (@keystates Keyboard/KEY_R) (@keystates Keyboard/KEY_F)
                               (@keystates Keyboard/KEY_T) (@keystates Keyboard/KEY_G))
                         (identity-cubemap cubemap-size)
                         (iterate-cubemap cubemap-size (* 0.00001 (exp @curl-scale-exp)) update-warp
                                          (uniform-sampler update-warp "current" 0)
                                          (uniform-sampler update-warp "worley1" 1)
                                          (uniform-sampler update-warp "worley2" 2)
                                          (uniform-float update-warp "epsilon" (/ 1.0 worley-size 8))
                                          (uniform-float update-warp "whirl" @whirl)
                                          (uniform-float update-warp "prevailing" @prevailing)
                                          (uniform-float update-warp "curl_scale" (exp @curl-scale-exp))
                                          (use-textures @warp W1 W2)))
               warped (cubemap-warp cubemap-size lookup
                                    (uniform-sampler lookup "current" 0)
                                    (uniform-sampler lookup "worley" 1)
                                    (uniform-float lookup "cloud_scale" (exp @cloud-scale-exp))
                                    (use-textures updated W3))]
           (destroy-texture @warp)
           (reset! warp updated)
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 0 0]))
                            (use-program program)
                            (uniform-sampler program "cubemap" 0)
                            (uniform-matrix3 program "rotation" mat)
                            (uniform-float program "threshold" @threshold)
                            (uniform-float program "multiplier" @multiplier)
                            (use-textures warped)
                            (render-quads vao))
           (destroy-texture warped))
         (print (format "\rthreshold = %.3f, multiplier = %.3f, curlscale = %.3f, cloudscale = %.3f, whirl = %.3f, prevailing = %.3f, fps = %.1f"
                        @threshold @multiplier (exp @curl-scale-exp) (exp @cloud-scale-exp) @whirl @prevailing (/ 1000.0 dt)))
         (flush)
         (swap! t0 + dt)))

(destroy-program program)
(destroy-program lookup)
(destroy-program update-warp)
(destroy-texture @warp)
(destroy-texture W3)
(destroy-texture W2)
(destroy-texture W1)

(Display/destroy)

(System/exit 0)
