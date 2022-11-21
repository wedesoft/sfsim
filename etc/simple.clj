(require '[clojure.math :refer (cos sin sqrt pow to-radians tan)]
         '[clojure.core.matrix :refer (matrix add mul mmul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.quaternion :as q]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat GL11]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
(Display/create)

(Keyboard/create)

(def radius 6378000.0)
(def max-height 35000.0)
(def light (atom 0.041))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 1000)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def threshold (atom 0.5))
(def multiplier (atom 0.001))
(def anisotropic (atom 0.15))
(def z-near 1000)
(def z-far (* 2.0 radius))
(def worley-size 128)
(def noise-size 64)
(def keystates (atom {}))
(def fov 45.0)
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians fov)))
(def cloud-scale 5000)
(def focal-length (/ (/ (Display/getWidth) 2) (tan (to-radians (/ fov 2)))))
(def base-lod (/ (* worley-size (tan (/ (to-radians fov) 2))) (/ (Display/getWidth) 2) cloud-scale))
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def data (float-array [0.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 0.7 0.3 0.0]))
(def P (make-float-texture-1d :linear :clamp data))

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def T (make-vector-texture-2d :linear :clamp {:width transmittance-elevation-size :height transmittance-height-size :data data}))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def S (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/atmosphere/mie-strength.scatter"))
(def M (make-vector-texture-2d :linear :clamp {:width (* elevation-size heading-size) :height (* height-size light-elevation-size) :data data}))

(def data (slurp-floats "data/bluenoise.raw"))
(def B (make-float-texture-2d :nearest :repeat {:width noise-size :height noise-size :data data}))

(def fragment
"#version 410 core

uniform mat4 projection;
uniform mat4 transform;
uniform vec3 origin;
uniform vec3 light_direction;
uniform float radius;
uniform float max_height;
uniform float cloud_step;
uniform float cloud_step2;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_scale;
uniform float cloud_multiplier;
uniform int shadow_max_steps;
uniform float threshold;
uniform float anisotropic;
uniform float specular;
uniform float cutoff;
uniform float amplification;
uniform int noise_size;
uniform float base_lod;
uniform sampler3D worley;
uniform sampler2D bluenoise;
uniform sampler1D profile;
in vec3 point;
uniform sampler2D transmittance;
uniform int height_size;
uniform int elevation_size;
uniform int transmittance_height_size;
uniform int transmittance_elevation_size;
uniform sampler2D ray_scatter;
uniform int light_elevation_size;
uniform int heading_size;


in VS_OUT
{
  vec3 direction;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
vec4 clip_shell_intersections(vec4 intersections, float limit);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 transmittance_track(vec3 p, vec3 q);
bool is_above_horizon(vec3 point, vec3 direction);
float phase(float g, float mu);
vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon);
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);

void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 planet = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec3 background;
  if (planet.y > 0) {
    atmosphere.y = planet.x - atmosphere.x;
    vec3 pos = origin + direction * planet.x;
    vec3 normal = normalize(pos);
    float cos_incidence = dot(normal, light_direction);
    background = vec3(max(cos_incidence, 0), 0, 0);
  } else {
    float glare = pow(max(0, dot(direction, light_direction)), specular);
    background = vec3(glare, glare, glare);
  };
  vec3 rest = vec3(1, 1, 1);
  vec3 cloud = vec3(0, 0, 0);
  if (atmosphere.y > 0) {
    vec4 clouds = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    if (clouds.y > 0 && clouds.x < atmosphere.x + atmosphere.y) {
      float a = atmosphere.x;
      float b = clouds.x;
      vec3 transm = transmittance_track(origin + a * direction, origin + b * direction);
      vec3 atten = attenuation_track(light_direction, origin, direction, a, b, vec3(0, 0, 0)) * amplification;
      cloud = cloud + rest * atten;
      rest = rest * transm;
      int steps = int(ceil(clouds.y / cloud_step));
      float step = clouds.y / steps;
      float scatter_amount = anisotropic * phase(0.76, -1) + 1 - anisotropic;
      float offset = texture(bluenoise, vec2(gl_FragCoord.x / noise_size, gl_FragCoord.y / noise_size)).r;
      for (int i=0; i<steps; i++) {
        float dist = clouds.x + (i + offset) * step;
        float a = clouds.x + i * step;
        float b = clouds.x + (i + 1) * step;
        vec3 pos = origin + dist * direction;
        float r = length(pos);
        vec3 transm = transmittance_track(origin + a * direction, origin + b * direction);
        vec3 atten = attenuation_track(light_direction, origin, direction, a, b, vec3(0, 0, 0)) * amplification;
        float h = texture(profile, (r - radius - cloud_bottom) / (cloud_top - cloud_bottom)).r;
        float density = (textureLod(worley, pos / cloud_scale, 0.0).r - (h * threshold + 1 - h)) * cloud_multiplier;
        if (density > 0) {
          float t = exp(-step * density);
          cloud = cloud + rest * atten + rest * (1 - t) * vec3(1, 1, 1);
          rest = rest * t * transm;
        } else {
          cloud = cloud + rest * atten;
          rest = rest * transm;
        };
        if (rest.g < 0.05) {
          rest = vec3(0, 0, 0);
          break;
        };
      };
      if (rest.g >= 0.05) {
        float a = clouds.x + clouds.y;
        float b = atmosphere.x + atmosphere.y;
        vec3 transm = transmittance_track(origin + a * direction, origin + b * direction);
        vec3 atten = attenuation_track(light_direction, origin, direction, a, b, vec3(0, 0, 0)) * amplification;
        cloud = cloud + rest * atten;
        rest = rest * transm;
      };
    } else {
      float a = atmosphere.x;
      float b = atmosphere.x + atmosphere.y;
      vec3 transm = transmittance_track(origin + a * direction, origin + b * direction);
      vec3 atten = attenuation_track(light_direction, origin, direction, a, b, vec3(0, 0, 0)) * amplification;
      cloud = cloud + rest * atten;
      rest = rest * transm;
    };
  };
  background = rest * background + cloud;
  fragColor = background;
}")

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment shaders/ray-sphere shaders/ray-shell shaders/clip-shell-intersections phase-function
                           attenuation-track attenuation-outer transmittance-track transmittance-outer
                           ray-scatter-track ray-scatter-outer shaders/ray-scatter-forward shaders/interpolate-4d
                           shaders/transmittance-forward shaders/convert-4d-index shaders/elevation-to-index
                           shaders/interpolate-2d shaders/is-above-horizon shaders/convert-2d-index shaders/height-to-index
                           shaders/horizon-distance shaders/sun-elevation-to-index shaders/limit-quot shaders/sun-angle-to-index]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)
(uniform-matrix4 program :projection projection)
(uniform-float program :radius radius)
(uniform-float program :max_height max-height)
(uniform-float program :cloud_step 500)
(uniform-float program :cloud_step2 100)
(uniform-float program :cloud_bottom 1500)
(uniform-float program :cloud_top 3000)
(uniform-float program :cloud_scale cloud-scale)
(uniform-int program :cloud_size worley-size)
(uniform-int program :shadow_max_steps 80)
(uniform-float program :specular 200)
(uniform-float program :cutoff 0.0)
(uniform-int program :noise_size 64)
(uniform-float program :base_lod base-lod)
(uniform-int program :height_size height-size)
(uniform-int program :elevation_size elevation-size)
(uniform-int program :light_elevation_size light-elevation-size)
(uniform-int program :heading_size heading-size)
(uniform-int program :transmittance_height_size transmittance-height-size)
(uniform-int program :transmittance_elevation_size transmittance-elevation-size)
(uniform-float program :amplification 6.0)
(uniform-sampler program :worley 0)
(uniform-sampler program :bluenoise 1)
(uniform-sampler program :profile 2)
(uniform-sampler program :transmittance 3)
(uniform-sampler program :ray_scatter 4)
(uniform-sampler program :mie_strength 5)

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1        (System/currentTimeMillis)
             dt        (- t1 @t0)
             tr        (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             tm        (if (@keystates Keyboard/KEY_E) 0.0001 (if (@keystates Keyboard/KEY_D) -0.0001 0))
             ts        (if (@keystates Keyboard/KEY_W) 0.001 (if (@keystates Keyboard/KEY_S) -0.001 0))
             ra        (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
             rb        (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             rc        (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
             v         (if (@keystates Keyboard/KEY_PRIOR) 5 (if (@keystates Keyboard/KEY_NEXT) -5 0))
             l         (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! threshold + (* dt tr))
         (swap! multiplier + (* dt tm))
         (swap! anisotropic + (* dt ts))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! light + (* l 0.1 dt))
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 1 0]))
                          (use-program program)
                          (use-textures W B P T S M)
                          (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @position))
                          (uniform-vector3 program :origin @position)
                          (uniform-vector3 program :light_direction (matrix [0 (cos @light) (sin @light)]))
                          (uniform-float program :threshold @threshold)
                          (uniform-float program :cloud_multiplier @multiplier)
                          (uniform-float program :anisotropic @anisotropic)
                          (render-quads vao))
         (print "\rheight" (format "%.3f" (- (norm @position) radius))
                "threshold q/a" (format "%.3f" @threshold)
                "anisotropic w/s" (format "%.3f" @anisotropic)
                "multiplier e/d" (format "%.3f" @multiplier)
                "dt" (format "%.3f" (* dt 0.001))
                "      ")
         (flush)
         (swap! t0 + dt)))

(destroy-texture W)
(destroy-texture B)
(destroy-texture P)
(destroy-texture T)
(destroy-texture S)
(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
