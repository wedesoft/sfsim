(require '[clojure.math :refer (to-radians cos sin)]
         '[clojure.core.matrix :refer (matrix add mul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all]
         '[sfsim25.shaders :as shaders])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
;(Display/setDisplayMode (DisplayMode. 1280 720))
(Display/create)

(Keyboard/create)

(def radius 6378000.0)
(def max-height 35000.0)
(def cloud-bottom 1500)
(def cloud-top 3000)
(def worley-size 128)
(def z-near 1000.0)
(def z-far 120000.0)
(def fov 45.0)
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def shadow-size 256)
(def num-opacity-layers 7)

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) (to-radians fov)))

(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 1000)])))
(def light (atom 0.041))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def keystates (atom {}))

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

(def fragment
"#version 410 core

uniform mat4 projection;
uniform mat4 transform;
uniform vec3 origin;
uniform vec3 light_direction;
uniform float radius;
uniform float max_height;
uniform float amplification;

in VS_OUT
{
  vec3 direction;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 sky_outer(vec3 light_direction, vec3 origin, vec3 direction, vec3 incoming);
vec3 sky_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);

void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  if (atmosphere.y > 0) {
    vec2 planet = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
    if (planet.y > 0) {
      vec3 pos = origin + direction * planet.x;
      vec3 normal = normalize(pos);
      float cos_incidence = dot(normal, light_direction);
      float bright = max(cos_incidence, 0.1);
      vec3 background = vec3(bright, bright * 0.5, bright * 0.5);
      fragColor = sky_track(light_direction, origin, direction, atmosphere.x, planet.x, background / amplification) * amplification;
    } else {
      fragColor = sky_outer(light_direction, origin, direction, vec3(0, 0, 0)) * amplification;
    };
  } else {
    fragColor = vec3(0, 0, 0);
  };
}
")

(def opacity-cascade-mock
"#version 410 core
float opacity_cascade_lookup(vec4 point)
{
  return 1.0;
}")

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment shaders/ray-sphere shaders/ray-shell
                           sky-outer attenuation-outer ray-scatter-outer
                           shaders/ray-scatter-forward shaders/height-to-index shaders/interpolate-4d transmittance-outer
                           shaders/horizon-distance shaders/elevation-to-index shaders/make-2d-index-from-4d
                           shaders/limit-quot shaders/sun-elevation-to-index phase-function shaders/sun-angle-to-index
                           shaders/transmittance-forward cloud-track shaders/interpolate-2d shaders/convert-2d-index
                           ray-scatter-track shaders/is-above-horizon transmittance-track exponential-sampling
                           cloud-density cloud-shadow attenuation-track sky-track shaders/clip-shell-intersections
                           opacity-cascade-mock]))

(def indices [0 1 3 2])
(def atmosphere-vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def atmosphere-vao (make-vertex-array-object program-atmosphere indices atmosphere-vertices [:point 3]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere :transmittance 0)
(uniform-sampler program-atmosphere :ray_scatter 1)
(uniform-sampler program-atmosphere :mie_strength 2)
(uniform-sampler program-atmosphere :worley 3)
(uniform-sampler program-atmosphere :cloud_profile 4)
(uniform-matrix4 program-atmosphere :projection projection)
(uniform-float program-atmosphere :radius radius)
(uniform-float program-atmosphere :max_height max-height)
(uniform-int program-atmosphere :height_size height-size)
(uniform-int program-atmosphere :elevation_size elevation-size)
(uniform-int program-atmosphere :light_elevation_size light-elevation-size)
(uniform-int program-atmosphere :heading_size heading-size)
(uniform-int program-atmosphere :transmittance_height_size transmittance-height-size)
(uniform-int program-atmosphere :transmittance_elevation_size transmittance-elevation-size)
(uniform-float program-atmosphere :cloud_bottom cloud-bottom)
(uniform-float program-atmosphere :cloud_top cloud-top)
(uniform-float program-atmosphere :anisotropic 0.15)
(uniform-int program-atmosphere :cloud_min_samples 5)
(uniform-int program-atmosphere :cloud_max_samples 64)
(uniform-float program-atmosphere :cloud_scatter_amount 0.2)
(uniform-float program-atmosphere :cloud_max_step 1.04)
(uniform-float program-atmosphere :transparency_cutoff 0.05)
(uniform-float program-atmosphere :cloud_scale 5000)
(uniform-int program-atmosphere :cloud_size worley-size)
(uniform-float program-atmosphere :cloud_multiplier (* 0.01 1.2))
(uniform-float program-atmosphere :amplification 6.0)

(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell cloud-density shaders/ray-sphere]))

(def indices [0 1 3 2])
(def shadow-vertices (map #(* % z-far) [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]))
(def shadow-vao (make-vertex-array-object program-shadow indices shadow-vertices [:point 3]))

(map
  (fn [{:keys [shadow-ndc-matrix depth]}]
      (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
            opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)]
        (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                            (use-program program-shadow)
                            (uniform-int program-shadow :shadow_size shadow-size)
                            (uniform-float program-shadow :radius radius)
                            (uniform-float program-shadow :cloud_bottom cloud-bottom)
                            (uniform-float program-shadow :cloud_top cloud-top)
                            (uniform-matrix4 program-shadow :ndc_to_shadow (inverse shadow-ndc-matrix))
                            (uniform-vector3 program-shadow :light_direction light-direction)
                            ; TODO: num-shell-intersections?
                            (uniform-float program-shadow :cloud_multiplier (* 0.01 1.2))
                            ; TODO: scatter-amount to be computed?
                            (uniform-float program-shadow :depth depth)
                            (uniform-float program-shadow :cloud_max_step 200) ; TODO: rename or use sampling shader functions
                            (uniform-float program-shadow :opacity_step 500)
                            (render-quads shadow-vao))
        {:offset opacity-offsets :layer opacity-layers}))
  (shadow-matrix-cascade projection transform light-direction (/ z-far 2) 0.5 z-near z-far num-opacity-layers))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1        (System/currentTimeMillis)
             dt        (- t1 @t0)
             ra        (if (@keystates Keyboard/KEY_NUMPAD2) 0.001 (if (@keystates Keyboard/KEY_NUMPAD8) -0.001 0))
             rb        (if (@keystates Keyboard/KEY_NUMPAD4) 0.001 (if (@keystates Keyboard/KEY_NUMPAD6) -0.001 0))
             rc        (if (@keystates Keyboard/KEY_NUMPAD1) 0.001 (if (@keystates Keyboard/KEY_NUMPAD3) -0.001 0))
             v         (if (@keystates Keyboard/KEY_PRIOR) 5 (if (@keystates Keyboard/KEY_NEXT) -5 0))
             l         (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! light + (* l 0.1 dt))
         (let [transform       (transformation-matrix (quaternion->matrix @orientation) @position)
               light-direction (matrix [0 (cos @light) (sin @light)])]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 1 0]))
                            (use-program program-atmosphere)
                            (GL11/glDepthFunc GL11/GL_ALWAYS)
                            (use-textures T S M W P)
                            (uniform-matrix4 program-atmosphere :transform transform)
                            (uniform-vector3 program-atmosphere :origin @position)
                            (uniform-vector3 program-atmosphere :light_direction light-direction)
                            (render-quads atmosphere-vao)))
         (print "\rheight" (format "%.3f" (- (norm @position) radius))
                "dt" (format "%.3f" (* dt 0.001))
                "      ")
         (flush)
         (swap! t0 + dt)))

(destroy-texture W)
(destroy-texture P)
(destroy-texture T)
(destroy-texture S)
(destroy-vertex-array-object vao)
(destroy-program program-atmosphere)

(Display/destroy)
