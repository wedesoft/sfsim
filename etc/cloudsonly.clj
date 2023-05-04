(require '[clojure.math :refer (to-radians cos sin tan PI sqrt log)]
         '[clojure.core.matrix :refer (matrix add mul inverse mget det)]
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
;(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(def density
"#version 410 core
uniform float detail_scale;
uniform float radius;
uniform float cloud_scale;
uniform float cap;
uniform float threshold;
uniform float cloud_multiplier;
uniform float cover_multiplier;
uniform samplerCube cover;
uniform sampler3D perlin;
float cloud_octaves(vec3 point, float lod);
float sphere_noise(vec3 point);
float cloud_profile(vec3 point);
float remap(float value, float original_min, float original_max, float new_min, float new_max);
float cloud_density(vec3 point, float lod)
{
  float clouds = sphere_noise(point);
  float profile = cloud_profile(point);
  float cover_sample = texture(cover, point).r * cover_multiplier + clouds * cloud_multiplier - threshold;
  float base = cover_sample * profile;
  float density;
  if (base <= 0.0)
    density = 0.0;
  else if (base >= 1.0)
    density = cap;
  else {
    float noise = cloud_octaves(point / detail_scale, lod);
    if (noise <= 1 - base)
      density = 0.0;
    else
      density = remap(noise, 1 - base, 1.0, 0.0, cap);
  };
  return density;
}");

(def fragment
"#version 410 core
uniform float stepsize;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float dense_height;
uniform float detail;
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
      background = 2 * vec3(0.09, 0.11, 0.34) * intensity * cos_angle;
      atmosphere.y = planet.x - atmosphere.x;
    } else
      background = vec3(0, 0, 0);
    float transparency = 1.0;
    int count = int(ceil(atmosphere.y / stepsize));
    float step = atmosphere.y / count;
    vec3 cloud_scatter = vec3(0, 0, 0);
    float offset = sampling_offset();
    for (int i=0; i<count; i++) {
      float l = atmosphere.x + (i + offset) * step;
      vec3 point = origin + l * direction;
      float r = length(point);
      if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
        float lod = log2(l) + detail;
        float density = cloud_density(point, lod);
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

(def fov (to-radians 60.0))
(def radius 6378000.0)
; (def dense-height 25000.0)
(def dense-height 6000.0)
(def threshold (atom 16.666))
(def anisotropic (atom 0.2))
(def cloud-bottom 1500)
(def cloud-top 6000)
(def cloud-multiplier (atom 8.333))
(def cover-multiplier (atom 25.0))
(def cap (atom 0.012))
(def detail-scale 10000)
(def cloud-scale 800000)
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def octaves (mapv #(/ % sum-series) series))
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def perlin-octaves (mapv #(/ % sum-series) series))
(def z-near 10)
(def z-far (* radius 2))
(def mix 0.5)
(def opacity-step (atom 1000.0))
(def step (atom 400.0))
(def worley-size 64)
(def shadow-size 512)
(def noise-size 64)
(def depth 30000.0)
(def position (atom (matrix [0 (* -0 radius) (+ radius cloud-bottom -750)])))
(def orientation (atom (q/rotation (to-radians 105) (matrix [1 0 0]))))
;(def position (atom (matrix [0 (* -2 radius) 0])))
;(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def light (atom (* 0.25 PI)))

(def data (slurp-floats "data/clouds/worley-cover.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def data (float-array (map #(+ (* 0.3 %1) (* 0.7 %2))
                            (slurp-floats "data/clouds/perlin.raw")
                            (slurp-floats "data/clouds/worley-cover.raw"))))
(def L (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def data (float-array [0.0 1.0 0.7 0.65 0.6 0.55 0.4 0.3 0.15 0.0]))
(def profile-size (count data))
(def P (make-float-texture-1d :linear :clamp data))

(def data (slurp-floats "data/bluenoise.raw"))
(def B (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data data}))

(def cover (map (fn [i] {:width 512 :height 512 :data (slurp-floats (str "data/clouds/cover" i ".raw"))}) (range 6)))
(def C (make-float-cubemap :linear :clamp cover))

(def num-steps 3)

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment density shaders/remap (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile phase-function
                           shaders/convert-1d-index shaders/ray-sphere (opacity-cascade-lookup num-steps)
                           opacity-lookup shaders/convert-2d-index shaders/convert-3d-index bluenoise/sampling-offset]))

(def num-opacity-layers 7)
(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell density shaders/remap
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile shaders/convert-1d-index
                           shaders/ray-sphere bluenoise/sampling-offset]))

(def indices [0 1 3 2])
(def shadow-vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0])
(def shadow-vao (make-vertex-array-object program-shadow indices shadow-vertices [:point 2]))

(defn shadow-cascade [matrix-cascade light-direction scatter-amount]
  (mapv
    (fn [{:keys [shadow-ndc-matrix depth scale]}]
        (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
              opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)
              detail          (/ (log (/ (/ scale shadow-size) (/ detail-scale worley-size))) (log 2))]
          (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                              (use-program program-shadow)
                              (uniform-sampler program-shadow "worley" 0)
                              (uniform-sampler program-shadow "perlin" 1)
                              (uniform-sampler program-shadow "profile" 2)
                              (uniform-sampler program-shadow "bluenoise" 3)
                              (uniform-sampler program-shadow "cover" 4)
                              (uniform-float program-shadow "level_of_detail" detail)
                              (uniform-int program-shadow "shadow_size" shadow-size)
                              (uniform-int program-shadow "profile_size" profile-size)
                              (uniform-int program-shadow "noise_size" noise-size)
                              (uniform-float program-shadow "radius" radius)
                              (uniform-float program-shadow "cloud_bottom" cloud-bottom)
                              (uniform-float program-shadow "cloud_top" cloud-top)
                              (uniform-float program-shadow "detail_scale" detail-scale)
                              (uniform-float program-shadow "cloud_scale" cloud-scale)
                              (uniform-matrix4 program-shadow "ndc_to_shadow" (inverse shadow-ndc-matrix))
                              (uniform-vector3 program-shadow "light_direction" light-direction)
                              (uniform-float program-shadow "cloud_multiplier" @cloud-multiplier)
                              (uniform-float program-shadow "cover_multiplier" @cover-multiplier)
                              (uniform-float program-shadow "cap" @cap)
                              (uniform-float program-shadow "threshold" @threshold)
                              (uniform-float program-shadow "dense_height" dense-height)
                              (uniform-float program-shadow "scatter_amount" scatter-amount)
                              (uniform-float program-shadow "depth" depth)
                              (uniform-float program-shadow "opacity_step" @opacity-step)
                              (uniform-float program-shadow "cloud_max_step" (* 0.5 @opacity-step))
                              (use-textures W L P B C)
                              (render-quads shadow-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))(def keystates (atom {}))

(def t0 (atom (System/currentTimeMillis)))
(def n (atom 0))
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
             v  (if (@keystates Keyboard/KEY_PRIOR) 10 (if (@keystates Keyboard/KEY_NEXT) -10 0))
             l  (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))
             tr (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             to (if (@keystates Keyboard/KEY_W) 0.05 (if (@keystates Keyboard/KEY_S) -0.05 0))
             ta (if (@keystates Keyboard/KEY_E) 0.0001 (if (@keystates Keyboard/KEY_D) -0.0001 0))
             tm (if (@keystates Keyboard/KEY_R) 0.001 (if (@keystates Keyboard/KEY_F) -0.001 0))
             tc (if (@keystates Keyboard/KEY_T) 0.00001 (if (@keystates Keyboard/KEY_G) -0.00001 0))
             ts (if (@keystates Keyboard/KEY_Y) 0.05 (if (@keystates Keyboard/KEY_H) -0.05 0))
             tg (if (@keystates Keyboard/KEY_U) 0.001 (if (@keystates Keyboard/KEY_J) -0.001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! light + (* l 0.1 dt))
         (swap! threshold + (* dt tr))
         (swap! opacity-step + (* dt to))
         (swap! anisotropic + (* dt ta))
         (swap! cloud-multiplier + (* dt tm))
         (swap! cover-multiplier + (* dt tg))
         (swap! cap + (* dt tc))
         (swap! step + (* dt ts))
         (let [norm-pos   (norm @position)
               dist       (- norm-pos radius cloud-top)
               z-near     (max 10.0 (* 0.7 dist))
               z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                             (sqrt (- (sqr norm-pos) (sqr radius))))
               indices    [0 1 3 2]
               vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
               vao        (make-vertex-array-object program indices vertices [:point 3])
               light-dir  (matrix [0 (cos @light) (sin @light)])
               projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) fov)
               detail     (/ (log (/ (tan (/ fov 2)) (/ (Display/getWidth) 2) (/ detail-scale worley-size))) (log 2))
               transform  (transformation-matrix (quaternion->matrix @orientation) @position)
               matrix-cas (shadow-matrix-cascade projection transform light-dir depth mix z-near z-far num-steps)
               splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
               scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
               tex-cas    (shadow-cascade matrix-cas light-dir scatter-am)]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 1 0]))
                            (use-program program)
                            (uniform-sampler program "worley" 0)
                            (uniform-sampler program "perlin" 1)
                            (uniform-sampler program "profile" 2)
                            (uniform-sampler program "bluenoise" 3)
                            (uniform-sampler program "cover" 4)
                            (doseq [i (range num-steps)]
                                   (uniform-sampler program (str "offset" i) (+ (* 2 i) 5))
                                   (uniform-sampler program (str "opacity" i) (+ (* 2 i) 6)))
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
                            (uniform-float program "stepsize" @step)
                            (uniform-int program "num_opacity_layers" num-opacity-layers)
                            (uniform-int program "shadow_size" shadow-size)
                            (uniform-float program "radius" radius)
                            (uniform-float program "cloud_bottom" cloud-bottom)
                            (uniform-float program "cloud_top" cloud-top)
                            (uniform-float program "cloud_multiplier" @cloud-multiplier)
                            (uniform-float program "cover_multiplier" @cover-multiplier)
                            (uniform-float program "cap" @cap)
                            (uniform-float program "opacity_step" @opacity-step)
                            (uniform-float program "threshold" @threshold)
                            (uniform-float program "detail_scale" detail-scale)
                            (uniform-float program "cloud_scale" cloud-scale)
                            (uniform-float program "detail" detail)
                            (uniform-float program "dense_height" dense-height)
                            (uniform-float program "anisotropic" @anisotropic)
                            (uniform-vector3 program "origin" @position)
                            (uniform-vector3 program "light_direction" light-dir)
                            (apply use-textures W L P B C (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cas))
                            (render-quads vao))
           (doseq [{:keys [offset layer]} tex-cas]
                  (destroy-texture offset)
                  (destroy-texture layer))
           (destroy-vertex-array-object vao))
         (swap! n inc)
         (when (zero? (mod @n 10))
           (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, aniso (e/d) %.3f, mult (r/f) %.1f, cov (u/j) %.1f, cap (t/g) %.3f, step (y/h) %.0f, dt %.3f"
                          @threshold @opacity-step @anisotropic @cloud-multiplier @cover-multiplier @cap @step (* dt 0.001)))
           (flush))
         (swap! t0 + dt)))

(destroy-texture C)
(destroy-texture B)
(destroy-texture P)
(destroy-texture W)
(destroy-vertex-array-object shadow-vao)
(destroy-program program-shadow)
(destroy-program program)

(Display/destroy)
