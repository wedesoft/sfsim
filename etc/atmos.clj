(require '[clojure.math :refer (to-radians cos sin PI sqrt)]
         '[clojure.core.matrix :refer (matrix add mul inverse mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.bluenoise :as bluenoise]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all]
         '[sfsim25.shaders :as shaders])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 2) (/ 1080 2)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
(Display/create)

(def density
"#version 410 core
uniform float cloud_scale;
uniform float cap;
uniform float threshold;
uniform float multiplier;
float cloud_octaves(vec3 point);
float cloud_profile(vec3 point);
float cloud_density(vec3 point, float lod)
{
  float noise = cloud_octaves(point / cloud_scale) * cloud_profile(point);
  float density = min(max(noise - threshold, 0) * multiplier, cap);
  return density;
}")

(def sampling-offset
"#version 410 core
float sampling_offset()
{
  return 0.5;
}")

(def fragment
"#version 410 core
uniform float stepsize;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float dense_height;
uniform float anisotropic;
uniform vec3 light_direction;
uniform vec3 origin;
in VS_OUT
{
  vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
float cloud_density(vec3 point, float lod);
float phase(float g, float mu);
void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + dense_height, origin, direction);
  vec2 planet = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  if (atmosphere.y > 0) {
    vec3 background;
    if (planet.y > 0) {
      background = vec3(0, 0, 1);
      atmosphere.y = planet.x - atmosphere.x;
    } else
      background = vec3(0, 0, 0);
    float transparency = 1.0;
    int count = int(ceil(atmosphere.y / stepsize));
    float step = atmosphere.y / count;
    vec3 cloud_scatter = vec3(0, 0, 0);
    for (int i=0; i<count; i++) {
      vec3 point = origin + (atmosphere.x + (i + 0.5) * step) * direction;
      float r = length(point);
      if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
        float density = cloud_density(point, 0.0);
        float t = exp(-density * step);
        float scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic);
        cloud_scatter = cloud_scatter + transparency * (1 - t) * scatter_amount;
        transparency *= t;
      }
      if (transparency <= 0.05)
        break;
    };
    fragColor = background * transparency + cloud_scatter;
  } else
    fragColor = vec3(0, 0, 0);
}")

(def fov 60.0)
(def radius 6378000.0)
(def dense-height 25000.0)
(def anisotropic 0.065)
(def cloud-bottom 1500)
(def cloud-top 4000)
(def multiplier 1.5e-2)
(def cap 5e-3)
(def threshold 0.5)
(def cloud-scale 30000)
(def octaves [0.5 0.25 0.125 0.125])
(def z-near 100)
(def z-far (* radius 2))
(def mix 0.8)
(def worley-size 64)
(def profile-size 12)
(def depth 30000.0)
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 5000)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def light (atom (* 0.03 PI)))

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
; (generate-mipmap W)
(def data (float-array [0.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 0.7 0.5 0.3 0.0]))
(def P (make-float-texture-1d :linear :clamp data))

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment density (shaders/noise-octaves "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d "lookup_3d" "worley") cloud-profile phase-function
                           shaders/convert-1d-index shaders/ray-sphere]))

(def num-opacity-layers 7)
(def num-steps 5)
(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell density
                           (shaders/noise-octaves "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d "lookup_3d" "worley") cloud-profile shaders/convert-1d-index
                           shaders/ray-sphere sampling-offset]))

(def keystates (atom {}))

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
             rc (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
             v  (if (@keystates Keyboard/KEY_PRIOR) 5 (if (@keystates Keyboard/KEY_NEXT) -5 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! light + (* l 0.1 dt))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (let [norm-pos   (norm @position)
               dist       (- norm-pos radius cloud-top)
               z-near     (max 10.0 dist)
               z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                             (sqrt (- (sqr norm-pos) (sqr radius))))
               indices    [0 1 3 2]
               vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
               vao        (make-vertex-array-object program indices vertices [:point 3])
               light-dir  (matrix [0 (cos @light) (sin @light)])
               projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) (to-radians fov))
               transform  (transformation-matrix (quaternion->matrix @orientation) @position)
               matrix-cas (shadow-matrix-cascade projection transform light-dir depth mix z-near z-far num-steps)
               splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 1 0]))
                            (use-program program)
                            (uniform-sampler program "worley" 0)
                            (uniform-sampler program "profile" 1)
                            (uniform-matrix4 program "projection" projection)
                            (uniform-matrix4 program "transform" transform)
                            (uniform-int program "profile_size" profile-size)
                            (uniform-float program "stepsize" 200)
                            (uniform-float program "radius" radius)
                            (uniform-float program "cloud_bottom" cloud-bottom)
                            (uniform-float program "cloud_top" cloud-top)
                            (uniform-float program "multiplier" multiplier)
                            (uniform-float program "cap" cap)
                            (uniform-float program "threshold" threshold)
                            (uniform-float program "cloud_scale" cloud-scale)
                            (uniform-float program "dense_height" dense-height)
                            (uniform-float program "anisotropic" anisotropic)
                            (uniform-vector3 program "origin" @position)
                            (uniform-vector3 program "light_direction" light-dir)
                            (use-textures W P)
                            (render-quads vao))
           (destroy-vertex-array-object vao))
         (swap! t0 + dt)))

(destroy-texture P)
(destroy-texture W)
(destroy-program program-shadow)
(destroy-program program)

(Display/destroy)
