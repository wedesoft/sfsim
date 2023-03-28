(require '[clojure.math :refer (sin cos PI sqrt exp log pow)]
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

(def flow-field
"#version 410 core
#define M_PI 3.1415926535897932384626433832795
uniform float curl_scale;
uniform float prevailing;
uniform float whirl;
float octaves_north(vec3 idx, float lod);
float octaves_south(vec3 idx, float lod);
float spin(float y)
{
  float angle = asin(y);
  return sin(y * 3);
}
float flow_field(vec3 point)
{
  float m = spin(point.y);
  float w1 = octaves_north(point / (2 * curl_scale), 0.0) * whirl;
  float w2 = octaves_south(point / (2 * curl_scale), 0.0) * whirl;
  return (w1 + prevailing) * (1 + m) / 2 - (w2 + prevailing) * (1 - m) / 2;
}")

(def cover-noise
"#version 410 core
uniform float cover_scale;
float clouds(vec3 idx, float lod);
float cover(vec3 point)
{
  return clouds(point / (2 * cover_scale), 0.0);
}")

(defn cloud-cover-cubemap [& {:keys [size worley-size worley-south worley-north worley-cover flow-octaves cloud-octaves
                                     whirl prevailing curl-scale cover-scale num-iterations flow-scale]}]
  (let [result      (promise)
        warp        (atom (identity-cubemap size))
        update-warp (make-iterate-cubemap-warp-program
                      "current" "curl"
                      [(curl-vector "curl" "gradient") (shaders/gradient-3d "gradient" "flow_field" "epsilon")
                       (shaders/noise-octaves "octaves_north" "worley_north" flow-octaves)
                       (shaders/noise-octaves "octaves_south" "worley_south" flow-octaves)
                       shaders/rotate-vector shaders/oriented-matrix shaders/orthogonal-vector shaders/project-vector
                       flow-field])
        lookup      (make-cubemap-warp-program
                      "current" "cover"
                      [(shaders/noise-octaves "clouds" "worley" cloud-octaves) cover-noise])
        epsilon       (/ 1.0 worley-size (pow 2.0 (count flow-octaves)))]
    (use-program update-warp)
    (uniform-sampler update-warp "current" 0)
    (uniform-sampler update-warp "worley_north" 1)
    (uniform-sampler update-warp "worley_south" 2)
    (uniform-float update-warp "epsilon" epsilon)
    (uniform-float update-warp "whirl" whirl)
    (uniform-float update-warp "prevailing" prevailing)
    (uniform-float update-warp "curl_scale" curl-scale)
    (dotimes [iteration num-iterations]
      (let [updated (iterate-cubemap size flow-scale update-warp (use-textures @warp worley-north worley-south))]
        (destroy-texture @warp)
        (reset! warp updated)))
    (deliver
      result
      (cubemap-warp size lookup
                    (uniform-sampler lookup "current" 0)
                    (uniform-sampler lookup "worley" 1)
                    (uniform-float lookup "cover_scale" cover-scale)
                    (use-textures @warp worley-cover)))
    (destroy-program lookup)
    (destroy-program update-warp)
    (destroy-texture @warp)
    @result))

(def worley-size 64)

(when-not (.exists (clojure.java.io/file "w1.raw")) (spit-floats "w1.raw" (float-array (worley-noise 8 64 true))))
(when-not (.exists (clojure.java.io/file "w2.raw")) (spit-floats "w2.raw" (float-array (worley-noise 8 64 true))))
(when-not (.exists (clojure.java.io/file "w3.raw")) (spit-floats "w3.raw" (float-array (worley-noise 8 64 true))))

(def data (slurp-floats "w1.raw"))
(def worley-north (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "w2.raw"))
(def worley-south (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(def data (slurp-floats "w3.raw"))
(def worley-cover (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def cloud-cover
  (cloud-cover-cubemap :size 512
                       :worley-size worley-size
                       :worley-south worley-south
                       :worley-north worley-north
                       :worley-cover worley-cover
                       :flow-octaves [0.5 0.25 0.125]
                       :cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                       :whirl 1.0
                       :prevailing 0.1
                       :curl-scale 4.0
                       :cover-scale 2.0
                       :num-iterations 30
                       :flow-scale 1e-2))

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
(def threshold (atom 0.4))
(def multiplier (atom 4.0))

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
             ta (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))]
         (swap! alpha + (* dt ra))
         (swap! beta + (* dt rb))
         (swap! threshold + (* dt tr))
         (swap! multiplier + (* dt ta))
         (let [mat (mmul (rotation-y @beta) (rotation-x @alpha))]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 0 0]))
                            (use-program program)
                            (uniform-sampler program "cubemap" 0)
                            (uniform-matrix3 program "rotation" mat)
                            (uniform-float program "threshold" @threshold)
                            (uniform-float program "multiplier" @multiplier)
                            (use-textures cloud-cover)
                            (render-quads vao)))
         (print (format "\rthreshold = %.3f, multiplier = %.3f, fps = %.1f"
                        @threshold @multiplier (/ 1000.0 dt)))
         (flush)
         (Thread/sleep 10)
         (swap! t0 + dt)))

(destroy-program program)
(destroy-texture cloud-cover)
(destroy-texture worley-cover)
(destroy-texture worley-south)
(destroy-texture worley-north)

(Display/destroy)

(System/exit 0)
