(require '[clojure.math :refer (to-radians cos sin tan PI sqrt log)]
         '[fastmath.matrix :refer (inverse)]
         '[fastmath.vector :refer (vec3 add mult mag)]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.planet :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.bluenoise :as bluenoise]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all]
         '[sfsim25.shaders :as shaders])

(import '[org.lwjgl.opengl GL]
        '[org.lwjgl.glfw GLFW GLFWKeyCallback])

(set! *unchecked-math* true)

(def width 960)
(def height 540)

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow width height "scratch" 0 0))

(def fragment
"#version 410 core
uniform float stepsize;
uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float dense_height;
uniform float max_height;
uniform float lod_offset;
uniform float anisotropic;
uniform float specular;
uniform float amplification;
uniform vec3 light_direction;
in VS_OUT
{
  vec3 origin;
  vec3 direction;
} fs_in;
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
float cloud_density(vec3 point, float lod);
float phase(float g, float mu);
float opacity_cascade_lookup(vec4 point);
float sampling_offset();
vec3 transmittance_track(vec3 p, vec3 q);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
vec3 ray_scatter_outer(vec3 light_direction, vec3 point, vec3 direction);
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
int number_of_samples(float a, float b, float max_step);
float sample_point(float a, float idx, float step_size);
float step_size(float a, float b, int num_samples);
float lod_at_distance(float dist, float lod_offset);

bool planet_shadow(vec3 point, vec3 light_direction)  // To be replaced with shadow map
{
  if (dot(point, light_direction) < 0) {
    vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
    return planet_intersection.y > 0;
  } else
    return false;
}
float cloud_shadow(vec3 point, vec3 light_direction)
{
  if (planet_shadow(point, light_direction))
    return 0.0;
  else
    return opacity_cascade_lookup(vec4(point, 1));
}
vec4 cloud_transfer(vec3 point, vec3 direction, vec2 atmosphere, float step, vec4 cloud_scatter, float lod)
{
  float density = cloud_density(point, lod);
  if (density > 0) {
    float t = exp(-density * step);
    vec3 intensity = cloud_shadow(point, light_direction) * transmittance_outer(point, light_direction);
    vec3 scatter_amount = (anisotropic * phase(0.76, dot(direction, light_direction)) + 1 - anisotropic) * intensity;
    vec3 in_scatter = ray_scatter_track(light_direction, fs_in.origin + atmosphere.x * direction, point) * amplification;
    vec3 transmittance = transmittance_track(fs_in.origin + atmosphere.x * direction, point);
    cloud_scatter.rgb = cloud_scatter.rgb + cloud_scatter.a * (1 - t) * scatter_amount * transmittance + cloud_scatter.a * (1 - t) * in_scatter;
    cloud_scatter.a *= t;
  };
  return cloud_scatter;
}
void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, fs_in.origin, direction);
  if (atmosphere.y > 0) {
    vec3 background;
    vec3 ray_scatter;
    vec3 transmittance;
    vec2 planet = ray_sphere(vec3(0, 0, 0), radius, fs_in.origin, direction);  // replace using planetary shader
    if (planet.y > 0) {
      vec3 point = fs_in.origin + planet.x * direction;
      float intensity = cloud_shadow(point, light_direction);
      vec3 normal = normalize(point);
      float cos_incidence = max(dot(light_direction, normal), 0);
      float highlight;
      if (cos_incidence > 0) {
        highlight = pow(max(dot(reflect(light_direction, normal), direction), 0), specular);
      } else {
        highlight = 0.0;
      };
      vec3 ground = ground_radiance(point, light_direction, 1.0, cos_incidence, highlight, vec3(1, 1, 1), vec3(0.09, 0.11, 0.34));
      transmittance = transmittance_track(fs_in.origin + atmosphere.x * direction, point);
      background = ground * intensity * amplification;
      ray_scatter = ray_scatter_track(light_direction, fs_in.origin + atmosphere.x * direction, point) * amplification;
      atmosphere.y = planet.x - atmosphere.x;
    } else {
      transmittance = transmittance_outer(fs_in.origin + atmosphere.x * direction, direction);
      float glare = pow(max(0, dot(direction, light_direction)), specular);
      background = vec3(glare, glare, glare);
      ray_scatter = ray_scatter_outer(light_direction, fs_in.origin + atmosphere.x * direction, direction) * amplification;
    };
    int count = number_of_samples(atmosphere.x, atmosphere.x + atmosphere.y, stepsize);
    float step = step_size(atmosphere.x, atmosphere.x + atmosphere.y, count);
    vec4 cloud_scatter = vec4(0, 0, 0, 1);
    float offset = sampling_offset();
    for (int i=0; i<count; i++) {
      float l = sample_point(atmosphere.x, i + offset, step);
      vec3 point = fs_in.origin + l * direction;
      float r = length(point);
      if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
        float lod = lod_at_distance(l, lod_offset);
        cloud_scatter = cloud_transfer(point, direction, atmosphere, step, cloud_scatter, lod);
      }
      if (cloud_scatter.a <= 0.01)
        break;
    };
    fragColor = (background * transmittance + ray_scatter) * cloud_scatter.a + cloud_scatter.rgb;
  } else {
    float glare = pow(max(0, dot(direction, light_direction)), specular);
    fragColor = vec3(glare, glare, glare);
  };
}")

(def fov (to-radians 60.0))
(def radius 6378000.0)
(def max-height 35000.0)
(def dense-height 6000.0)
(def threshold (atom 29.0))
(def anisotropic (atom 0.2))
(def cloud-bottom 1500)
(def cloud-top 6000)
(def cloud-multiplier (atom 18.0))
(def cover-multiplier (atom 38.0))
(def cap (atom 0.010))
(def detail-scale 20000)
(def cloud-scale 800000)
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def octaves (mapv #(/ % sum-series) series))
(def series (take 5 (iterate #(* % 0.8) 1.0)))
(def sum-series (apply + series))
(def perlin-octaves (mapv #(/ % sum-series) series))
(def z-near 10.0)
(def z-far (* radius 2))
(def mix 0.5)
(def opacity-step (atom 1000.0))
(def step (atom 400.0))
(def worley-size 64)
(def shadow-size 512)
(def noise-size 64)
(def depth 30000.0)
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def surface-height-size 16)
(def surface-sun-elevation-size 63)
(def position (atom (vec3 0 (* -0 radius) (+ radius cloud-bottom -750))))
(def orientation (atom (q/rotation (to-radians 105) (vec3 1 0 0))))
;(def position (atom (vec3 0 (* -2 radius) 0)))
;(def orientation (atom (q/rotation (to-radians 90) (vec3 1 0 0))))
(def light (atom (* 0.25 PI)))

(GLFW/glfwMakeContextCurrent window)
(GLFW/glfwShowWindow window)
(GL/createCapabilities)

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

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def E (make-vector-texture-2d :linear :clamp {:width surface-sun-elevation-size :height surface-height-size :data data}))

(def num-steps 3)

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment cloud-density shaders/remap (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           cloud-base cloud-cover cloud-noise
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile phase-function
                           shaders/convert-1d-index shaders/ray-sphere (opacity-cascade-lookup num-steps)
                           opacity-lookup shaders/convert-2d-index shaders/convert-3d-index bluenoise/sampling-offset
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index transmittance-track
                           shaders/is-above-horizon shaders/transmittance-forward shaders/height-to-index
                           shaders/interpolate-2d shaders/horizon-distance shaders/elevation-to-index shaders/limit-quot
                           ray-scatter-track shaders/ray-scatter-forward shaders/sun-elevation-to-index shaders/interpolate-4d
                           shaders/sun-angle-to-index shaders/make-2d-index-from-4d transmittance-outer ray-scatter-outer
                           ground-radiance shaders/surface-radiance-forward surface-radiance-function linear-sampling]))

(def num-opacity-layers 7)
(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell cloud-density shaders/remap
                           cloud-base cloud-cover cloud-noise
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile shaders/convert-1d-index
                           shaders/ray-sphere bluenoise/sampling-offset linear-sampling
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index]))

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
                              (uniform-int program-shadow "cover_size" 512)
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
                              (uniform-float program-shadow "cloud_threshold" @threshold)
                              (uniform-float program-shadow "dense_height" dense-height)
                              (uniform-float program-shadow "scatter_amount" scatter-amount)
                              (uniform-float program-shadow "depth" depth)
                              (uniform-float program-shadow "opacity_step" @opacity-step)
                              (uniform-float program-shadow "cloud_max_step" (* 0.5 @opacity-step))
                              (use-textures W L P B C)
                              (render-quads shadow-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))

(def keystates (atom {}))

(def keyboard-callback
  (proxy [GLFWKeyCallback] []
         (invoke [window k scancode action mods]
           (if (= action GLFW/GLFW_PRESS)
             (swap! keystates assoc k true))
           (if (= action GLFW/GLFW_RELEASE)
             (swap! keystates assoc k false)))))

(GLFW/glfwSetKeyCallback window keyboard-callback)

(def t0 (atom (System/currentTimeMillis)))
(def n (atom 0))
(while (not (GLFW/glfwWindowShouldClose window))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)
             ra (if (@keystates GLFW/GLFW_KEY_KP_2) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_8) -0.001 0))
             rb (if (@keystates GLFW/GLFW_KEY_KP_4) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_6) -0.001 0))
             rc (if (@keystates GLFW/GLFW_KEY_KP_1) 0.001 (if (@keystates GLFW/GLFW_KEY_KP_3) -0.001 0))
             v  (if (@keystates GLFW/GLFW_KEY_PAGE_UP) 10 (if (@keystates GLFW/GLFW_KEY_PAGE_DOWN) -10 0))
             l  (if (@keystates GLFW/GLFW_KEY_KP_ADD) 0.005 (if (@keystates GLFW/GLFW_KEY_KP_SUBTRACT) -0.005 0))
             tr (if (@keystates GLFW/GLFW_KEY_Q) 0.001 (if (@keystates GLFW/GLFW_KEY_A) -0.001 0))
             to (if (@keystates GLFW/GLFW_KEY_W) 0.05 (if (@keystates GLFW/GLFW_KEY_S) -0.05 0))
             ta (if (@keystates GLFW/GLFW_KEY_E) 0.0001 (if (@keystates GLFW/GLFW_KEY_D) -0.0001 0))
             tm (if (@keystates GLFW/GLFW_KEY_R) 0.001 (if (@keystates GLFW/GLFW_KEY_F) -0.001 0))
             tc (if (@keystates GLFW/GLFW_KEY_T) 0.00001 (if (@keystates GLFW/GLFW_KEY_G) -0.00001 0))
             ts (if (@keystates GLFW/GLFW_KEY_Y) 0.05 (if (@keystates GLFW/GLFW_KEY_H) -0.05 0))
             tg (if (@keystates GLFW/GLFW_KEY_U) 0.001 (if (@keystates GLFW/GLFW_KEY_J) -0.001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (vec3 1 0 0)))
         (swap! orientation q/* (q/rotation (* dt rb) (vec3 0 1 0)))
         (swap! orientation q/* (q/rotation (* dt rc) (vec3 0 0 1)))
         (swap! position add (mult (q/rotate-vector @orientation (vec3 0 0 -1)) (* dt v)))
         (swap! light + (* l 0.1 dt))
         (swap! threshold + (* dt tr))
         (swap! opacity-step + (* dt to))
         (swap! anisotropic + (* dt ta))
         (swap! cloud-multiplier + (* dt tm))
         (swap! cover-multiplier + (* dt tg))
         (swap! cap + (* dt tc))
         (swap! step + (* dt ts))
         (let [norm-pos   (mag @position)
               dist       (- norm-pos radius cloud-top)
               z-near     (max 10.0 (* 0.7 dist))
               z-far      (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                             (sqrt (- (sqr norm-pos) (sqr radius))))
               indices    [0 1 3 2]
               vertices   (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
               vao        (make-vertex-array-object program indices vertices [:point 3])
               light-dir  (vec3 0 (cos @light) (sin @light))
               projection (projection-matrix width height z-near (+ z-far 1) fov)
               lod-offset (/ (log (/ (tan (/ fov 2)) (/ width 2) (/ detail-scale worley-size))) (log 2))
               transform  (transformation-matrix (quaternion->matrix @orientation) @position)
               matrix-cas (shadow-matrix-cascade projection transform light-dir depth mix z-near z-far num-steps)
               splits     (map #(split-mixed mix z-near z-far num-steps %) (range (inc num-steps)))
               scatter-am (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic))
               tex-cas    (shadow-cascade matrix-cas light-dir scatter-am)]
           (onscreen-render window
                            (clear (vec3 0 1 0))
                            (use-program program)
                            (uniform-sampler program "worley" 0)
                            (uniform-sampler program "perlin" 1)
                            (uniform-sampler program "profile" 2)
                            (uniform-sampler program "bluenoise" 3)
                            (uniform-sampler program "cover" 4)
                            (uniform-sampler program "transmittance" 5)
                            (uniform-sampler program "ray_scatter" 6)
                            (uniform-sampler program "mie_strength" 7)
                            (uniform-sampler program "surface_radiance" 8)
                            (doseq [i (range num-steps)]
                                   (uniform-sampler program (str "offset" i) (+ (* 2 i) 9))
                                   (uniform-sampler program (str "opacity" i) (+ (* 2 i) 10)))
                            (doseq [[idx item] (map-indexed vector splits)]
                                   (uniform-float program (str "split" idx) item))
                            (doseq [[idx item] (map-indexed vector matrix-cas)]
                                   (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
                                   (uniform-float program (str "depth" idx) (:depth item)))
                            (uniform-int program "cover_size" 512)
                            (uniform-matrix4 program "projection" projection)
                            (uniform-matrix4 program "transform" transform)
                            (uniform-matrix4 program "inverse_transform" (inverse transform))
                            (uniform-float program "z_near" (+ 1 z-near))
                            (uniform-float program "z_far" z-far)
                            (uniform-int program "profile_size" profile-size)
                            (uniform-int program "noise_size" noise-size)
                            (uniform-int program "height_size" height-size)
                            (uniform-int program "elevation_size" elevation-size)
                            (uniform-int program "light_elevation_size" light-elevation-size)
                            (uniform-int program "heading_size" heading-size)
                            (uniform-int program "transmittance_elevation_size" transmittance-elevation-size)
                            (uniform-int program "transmittance_height_size" transmittance-height-size)
                            (uniform-int program "surface_sun_elevation_size" surface-sun-elevation-size)
                            (uniform-int program "surface_height_size" surface-height-size)
                            (uniform-float program "albedo" 0.9)
                            (uniform-float program "reflectivity" 0.1)
                            (uniform-float program "stepsize" @step)
                            (uniform-int program "num_opacity_layers" num-opacity-layers)
                            (uniform-int program "shadow_size" shadow-size)
                            (uniform-float program "radius" radius)
                            (uniform-float program "max_height" max-height)
                            (uniform-float program "cloud_bottom" cloud-bottom)
                            (uniform-float program "cloud_top" cloud-top)
                            (uniform-float program "cloud_multiplier" @cloud-multiplier)
                            (uniform-float program "cover_multiplier" @cover-multiplier)
                            (uniform-float program "cap" @cap)
                            (uniform-float program "opacity_step" @opacity-step)
                            (uniform-float program "cloud_threshold" @threshold)
                            (uniform-float program "detail_scale" detail-scale)
                            (uniform-float program "cloud_scale" cloud-scale)
                            (uniform-float program "lod_offset" lod-offset)
                            (uniform-float program "dense_height" dense-height)
                            (uniform-float program "anisotropic" @anisotropic)
                            (uniform-float program "specular" 1000)
                            (uniform-float program "amplification" 6)
                            (uniform-vector3 program "light_direction" light-dir)
                            (apply use-textures W L P B C T S M E (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cas))
                            (render-quads vao))
           (doseq [{:keys [offset layer]} tex-cas]
                  (destroy-texture offset)
                  (destroy-texture layer))
           (destroy-vertex-array-object vao))
         (GLFW/glfwPollEvents)
         (swap! n inc)
         (when (zero? (mod @n 10))
           (print (format "\rthres (q/a) %.1f, o.-step (w/s) %.0f, aniso (e/d) %.3f, mult (r/f) %.1f, cov (u/j) %.1f, cap (t/g) %.3f, step (y/h) %.0f, dt %.3f"
                          @threshold @opacity-step @anisotropic @cloud-multiplier @cover-multiplier @cap @step (* dt 0.001)))
           (flush))
         (swap! t0 + dt)))

(destroy-texture E)
(destroy-texture M)
(destroy-texture S)
(destroy-texture T)
(destroy-texture C)
(destroy-texture B)
(destroy-texture P)
(destroy-texture W)
(destroy-vertex-array-object shadow-vao)
(destroy-program program-shadow)
(destroy-program program)

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)

(set! *unchecked-math* false)
