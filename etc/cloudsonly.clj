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
  float detail = (cloud_octaves(8 * point / cloud_scale) - 0.5) * multiplier * 4e-1;
  float density = min(max((noise - threshold) * multiplier + detail, 0), cap);
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
float opacity_cascade_lookup(vec4 point);
float sampling_offset();

float cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + dense_height, point, light_direction);
  if (atmosphere_intersection.y > 0) {
    if (dot(point, light_direction) < 0) {
      vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
      if (planet_intersection.y > 0) {
        return 0.0;
      };
    };
    float shadow = opacity_cascade_lookup(vec4(point, 1));
    return shadow;
  };
  return 1.0;
}
void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + dense_height, origin, direction);
  vec2 planet = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  if (atmosphere.y > 0) {
    vec3 background;
    if (planet.y > 0) {
      vec3 point = origin + planet.x * direction;
      float intensity = cloud_shadow(point, light_direction, 0.0);
      float cos_angle = max(dot(point, light_direction) / length(point), 0);
      background = vec3(0, 0, intensity * cos_angle);
      atmosphere.y = planet.x - atmosphere.x;
    } else
      background = vec3(0, 0, 0);
    float transparency = 1.0;
    int count = int(ceil(atmosphere.y / stepsize));
    float step = atmosphere.y / count;
    vec3 cloud_scatter = vec3(0, 0, 0);
    float offset = sampling_offset();
    for (int i=0; i<count; i++) {
      vec3 point = origin + (atmosphere.x + (i + offset) * step) * direction;
      float r = length(point);
      if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
        float density = cloud_density(point, 0.0);
        float t = exp(-density * step);
        float intensity = cloud_shadow(point, light_direction, 0.0);
        float scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic) * intensity;
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
(def anisotropic (atom (* 2 0.065)))
(def cloud-bottom 1500)
(def cloud-top 4000)
(def multiplier 8e-2)
(def cap 3e-2)
(def threshold (atom 0.6))
(def cloud-scale 50000)
(def octaves [0.5 0.25 0.125 0.125])
(def z-near 100)
(def z-far (* radius 2))
(def mix 0.8)
(def opacity-step (atom 400.0))
(def worley-size 64)
(def profile-size 12)
(def shadow-size 128)
(def noise-size 64)
(def depth 30000.0)
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 5000)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def light (atom (* 0.25 PI)))

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
; (generate-mipmap W)
(def data (float-array [0.0 1.0 1.0 1.0 1.0 1.0 1.0 0.8 0.6 0.4 0.2 0.0]))
(def P (make-float-texture-1d :linear :clamp data))

(def data (slurp-floats "data/bluenoise.raw"))
(def B (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data data}))

(def num-steps 5)

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment density (shaders/noise-octaves "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d "lookup_3d" "worley") cloud-profile phase-function
                           shaders/convert-1d-index shaders/ray-sphere (opacity-cascade-lookup num-steps)
                           opacity-lookup shaders/convert-2d-index shaders/convert-3d-index bluenoise/sampling-offset]))

(def num-opacity-layers 7)
(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell density
                           (shaders/noise-octaves "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d "lookup_3d" "worley") cloud-profile shaders/convert-1d-index
                           shaders/ray-sphere bluenoise/sampling-offset]))

(def indices [0 1 3 2])
(def shadow-vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0])
(def shadow-vao (make-vertex-array-object program-shadow indices shadow-vertices [:point 2]))

(defn shadow-cascade [matrix-cascade light-direction scatter-amount]
  (mapv
    (fn [{:keys [shadow-ndc-matrix depth]}]
        (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
              opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)]
          (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                              (use-program program-shadow)
                              (uniform-sampler program-shadow "worley" 0)
                              (uniform-sampler program-shadow "profile" 1)
                              (uniform-sampler program-shadow "bluenoise" 2)
                              (uniform-int program-shadow "shadow_size" shadow-size)
                              (uniform-int program-shadow "profile_size" profile-size)
                              (uniform-int program-shadow "noise_size" noise-size)
                              (uniform-float program-shadow "radius" radius)
                              (uniform-float program-shadow "cloud_bottom" cloud-bottom)
                              (uniform-float program-shadow "cloud_top" cloud-top)
                              (uniform-float program-shadow "cloud_scale" cloud-scale)
                              (uniform-matrix4 program-shadow "ndc_to_shadow" (inverse shadow-ndc-matrix))
                              (uniform-vector3 program-shadow "light_direction" light-direction)
                              (uniform-float program-shadow "multiplier" multiplier)
                              (uniform-float program-shadow "cap" cap)
                              (uniform-float program-shadow "threshold" @threshold)
                              (uniform-float program-shadow "dense_height" dense-height)
                              (uniform-float program-shadow "scatter_amount" scatter-amount)
                              (uniform-float program-shadow "depth" depth)
                              (uniform-float program-shadow "opacity_step" @opacity-step)
                              (uniform-float program-shadow "cloud_max_step" 100)
                              (use-textures W P B)
                              (render-quads shadow-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))(def keystates (atom {}))

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
             l  (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))
             tr (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             to (if (@keystates Keyboard/KEY_W) 0.05 (if (@keystates Keyboard/KEY_S) -0.05 0))
             ta (if (@keystates Keyboard/KEY_E) 0.0001 (if (@keystates Keyboard/KEY_D) -0.0001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! light + (* l 0.1 dt))
         (swap! threshold + (* dt tr))
         (swap! opacity-step + (* dt to))
         (swap! anisotropic + (* dt ta))
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
               splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
               scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
               tex-cas    (shadow-cascade matrix-cas light-dir scatter-am)]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 1 0]))
                            (use-program program)
                            (uniform-sampler program "worley" 0)
                            (uniform-sampler program "profile" 1)
                            (uniform-sampler program "bluenoise" 2)
                            (doseq [i (range num-steps)]
                                   (uniform-sampler program (str "offset" i) (+ (* 2 i) 3))
                                   (uniform-sampler program (str "opacity" i) (+ (* 2 i) 4)))
                            (doseq [[idx item] (map-indexed vector splits)]
                                   (uniform-float program (str "split" idx) item))
                            (doseq [[idx item] (map-indexed vector matrix-cas)]
                                   (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                   (uniform-float program (str "depth" idx) (:depth item)))
                            (uniform-matrix4 program "projection" projection)
                            (uniform-matrix4 program "transform" transform)
                            (uniform-matrix4 program "inverse_transform" (inverse transform))
                            (uniform-int program "profile_size" profile-size)
                            (uniform-int program "noise_size" noise-size)
                            (uniform-float program "stepsize" 200)
                            (uniform-int program "num_opacity_layers" num-opacity-layers)
                            (uniform-int program "shadow_size" shadow-size)
                            (uniform-float program "radius" radius)
                            (uniform-float program "cloud_bottom" cloud-bottom)
                            (uniform-float program "cloud_top" cloud-top)
                            (uniform-float program "multiplier" multiplier)
                            (uniform-float program "cap" cap)
                            (uniform-float program "opacity_step" @opacity-step)
                            (uniform-float program "threshold" @threshold)
                            (uniform-float program "cloud_scale" cloud-scale)
                            (uniform-float program "dense_height" dense-height)
                            (uniform-float program "anisotropic" @anisotropic)
                            (uniform-vector3 program "origin" @position)
                            (uniform-vector3 program "light_direction" light-dir)
                            (apply use-textures W P B (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cas))
                            (render-quads vao))
           (doseq [{:keys [offset layer]} tex-cas]
                  (destroy-texture offset)
                  (destroy-texture layer))
           (destroy-vertex-array-object vao))
         (print (format "\rthres (q/a) %.3f, o.-step (w/s) %.1f, aniso (e/d) %.3f, dt %.3f"
                        @threshold @opacity-step @anisotropic (* dt 0.001)))
         (flush)
         (swap! t0 + dt)))

(destroy-texture P)
(destroy-texture W)
(destroy-vertex-array-object shadow-vao)
(destroy-program program-shadow)
(destroy-program program)

(Display/destroy)
