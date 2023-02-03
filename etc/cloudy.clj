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

(Keyboard/create)

(def radius 6378000.0)
(def max-height 35000.0)
(def cloud-bottom 1500)
(def cloud-top 4000)
(def cloud-scale 2500)
(def cloud-scatter-amount 0.2)
(def worley-size 64)
(def octaves [0.7 0.7 -0.4])
(def noise-size 64)
(def depth 120000.0)
(def fov 45.0)
(def height-size 32)
(def elevation-size 127)
(def light-elevation-size 32)
(def heading-size 8)
(def transmittance-height-size 64)
(def transmittance-elevation-size 255)
(def shadow-size 128)
; check use-textures below; at least 16 textures are supported.
(def num-steps 5)
; at least 8 color attachments supported minus one offset layer.
(def num-opacity-layers 7)

(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 5000)])))
(def light (atom (* 0.03 PI)))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def cloud-multiplier (atom 0.008))
(def threshold (atom -0.45))
(def mix (atom 0.8))
(def opacity-step (atom 400.0))
(def cms (atom 1.05))
(def anisotropic (atom 0.3))
(def keystates (atom {}))

(def data (slurp-floats "data/worley.raw"))
(def W (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def data (float-array [0.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 0.7 0.5 0.3 0.0]))
(def P (atom (make-float-texture-1d :linear :clamp data)))

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

(def program-atmosphere
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment shaders/ray-sphere shaders/ray-shell
                           sky-outer attenuation-outer ray-scatter-outer
                           shaders/ray-scatter-forward shaders/height-to-index shaders/interpolate-4d transmittance-outer
                           shaders/horizon-distance shaders/elevation-to-index shaders/make-2d-index-from-4d
                           shaders/limit-quot shaders/sun-elevation-to-index phase-function shaders/sun-angle-to-index
                           shaders/transmittance-forward cloud-track shaders/interpolate-2d shaders/convert-2d-index
                           ray-scatter-track shaders/is-above-horizon transmittance-track exponential-sampling
                           (cloud-density octaves) cloud-shadow attenuation-track sky-track
                           shaders/clip-shell-intersections (opacity-cascade-lookup num-steps) opacity-lookup
                           shaders/convert-3d-index bluenoise/sampling-offset]))

(use-program program-atmosphere)
(uniform-sampler program-atmosphere :transmittance 0)
(uniform-sampler program-atmosphere :ray_scatter 1)
(uniform-sampler program-atmosphere :mie_strength 2)
(uniform-sampler program-atmosphere :worley 3)
(uniform-sampler program-atmosphere :cloud_profile 4)
(uniform-sampler program-atmosphere :bluenoise 5)
(doseq [i (range num-steps)]
       (uniform-sampler program-atmosphere (keyword (str "offset" i)) (+ (* 2 i) 6))
       (uniform-sampler program-atmosphere (keyword (str "opacity" i)) (+ (* 2 i) 7)))
(uniform-float program-atmosphere :radius radius)
(uniform-float program-atmosphere :max_height max-height)
(uniform-int program-atmosphere :num_opacity_layers num-opacity-layers)
(uniform-int program-atmosphere :height_size height-size)
(uniform-int program-atmosphere :elevation_size elevation-size)
(uniform-int program-atmosphere :light_elevation_size light-elevation-size)
(uniform-int program-atmosphere :heading_size heading-size)
(uniform-int program-atmosphere :transmittance_height_size transmittance-height-size)
(uniform-int program-atmosphere :transmittance_elevation_size transmittance-elevation-size)
(uniform-float program-atmosphere :cloud_bottom cloud-bottom)
(uniform-float program-atmosphere :cloud_top cloud-top)
(uniform-int program-atmosphere :cloud_samples 128)
(uniform-float program-atmosphere :cloud_scatter_amount cloud-scatter-amount)
(uniform-float program-atmosphere :transparency_cutoff 0.05)
(uniform-float program-atmosphere :cloud_scale cloud-scale)
(uniform-int program-atmosphere :cloud_size worley-size)
(uniform-int program-atmosphere :shadow_size shadow-size)
(uniform-int program-atmosphere :noise_size noise-size)
(uniform-float program-atmosphere :amplification 6.0)

(def program-shadow
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) shaders/ray-shell (cloud-density octaves) shaders/ray-sphere
                           bluenoise/sampling-offset]))

(def indices [0 1 3 2])
(def shadow-vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0])
(def shadow-vao (make-vertex-array-object program-shadow indices shadow-vertices [:point 2]))

(use-program program-shadow)
(uniform-sampler program-shadow :worley 0)
(uniform-sampler program-shadow :cloud_profile 1)
(uniform-sampler program-shadow :bluenoise 2)
(uniform-int program-shadow :noise_size noise-size)

(defn shadow-cascade [matrix-cascade light-direction scatter-amount]
  (mapv
    (fn [{:keys [shadow-ndc-matrix depth]}]
        (let [opacity-offsets (make-empty-float-texture-2d :linear :clamp shadow-size shadow-size)
              opacity-layers  (make-empty-float-texture-3d :linear :clamp shadow-size shadow-size num-opacity-layers)]
          (framebuffer-render shadow-size shadow-size :cullback nil [opacity-offsets opacity-layers]
                              (use-program program-shadow)
                              (uniform-int program-shadow :shadow_size shadow-size)
                              (uniform-float program-shadow :radius radius)
                              (uniform-float program-shadow :cloud_bottom cloud-bottom)
                              (uniform-float program-shadow :cloud_top cloud-top)
                              (uniform-float program-shadow :cloud_scale cloud-scale)
                              (uniform-matrix4 program-shadow :ndc_to_shadow (inverse shadow-ndc-matrix))
                              (uniform-vector3 program-shadow :light_direction light-direction)
                              (uniform-float program-shadow :cloud_multiplier @cloud-multiplier)
                              (uniform-float program-shadow :scatter_amount scatter-amount)
                              (uniform-float program-shadow :depth depth)
                              (uniform-float program-shadow :opacity_step @opacity-step)
                              (uniform-float program-shadow :cloud_max_step 50) ; TODO: rename or use sampling shader functions
                              (use-textures W @P B)
                              (render-quads shadow-vao))
          {:offset opacity-offsets :layer opacity-layers}))
    matrix-cascade))

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
             l         (if (@keystates Keyboard/KEY_ADD) 0.005 (if (@keystates Keyboard/KEY_SUBTRACT) -0.005 0))
             tr        (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             tm        (if (@keystates Keyboard/KEY_E) 0.00001 (if (@keystates Keyboard/KEY_D) -0.00001 0))
             to        (if (@keystates Keyboard/KEY_T) 0.05 (if (@keystates Keyboard/KEY_G) -0.05 0))
             tx        (if (@keystates Keyboard/KEY_R) 0.0001 (if (@keystates Keyboard/KEY_F) -0.0001 0))
             ts        (if (@keystates Keyboard/KEY_Y) 0.0001 (if (@keystates Keyboard/KEY_H) -0.0001 0))
             ta        (if (@keystates Keyboard/KEY_U) 0.0001 (if (@keystates Keyboard/KEY_J) -0.0001 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (swap! light + (* l 0.1 dt))
         (swap! threshold + (* dt tr))
         (swap! cloud-multiplier + (* dt tm))
         (swap! opacity-step + (* dt to))
         (swap! anisotropic + (* dt ta))
         (swap! mix #(min 1.0 (max 0.0 (+ %1 %2))) (* dt tx))
         (swap! cms #(min 2.0 (max 1.001 (+ %1 %2))) (* dt ts))
         (let [data (float-array (map #(+ @threshold %) [0.0 1.0 1.0 1.0 1.0 1.0 1.0 1.0 0.7 0.5 0.3 0.0]))]
           (destroy-texture @P)
           (reset! P (make-float-texture-1d :linear :clamp data)))
         (let [norm-position       (norm @position)
               dist                (- norm-position radius cloud-top)
               z-near              (max 10.0 dist)
               z-far               (+ (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))
                                      (sqrt (- (sqr norm-position) (sqr radius))))
               projection          (projection-matrix (Display/getWidth) (Display/getHeight) z-near (+ z-far 10) (to-radians fov))
               indices             [0 1 3 2]
               atmosphere-vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
               atmosphere-vao      (make-vertex-array-object program-atmosphere indices atmosphere-vertices [:point 3])
               transform           (transformation-matrix (quaternion->matrix @orientation) @position)
               light-direction     (matrix [0 (cos @light) (sin @light)])
               matrix-cascade      (shadow-matrix-cascade projection transform light-direction depth @mix z-near z-far
                                                          num-steps)
               splits              (map #(split-mixed @mix z-near z-far num-steps %) (range (inc num-steps)))
               scatter-amount      (* (+ (* @anisotropic (phase 0.76 -1)) (- 1 @anisotropic)) cloud-scatter-amount)
               tex-cascade         (shadow-cascade matrix-cascade light-direction scatter-amount)]
           (onscreen-render (Display/getWidth) (Display/getHeight)
                            (clear (matrix [0 1 0]))
                            (use-program program-atmosphere)
                            (uniform-matrix4 program-atmosphere :projection projection)
                            (uniform-float program-atmosphere :cloud_multiplier @cloud-multiplier)
                            (uniform-float program-atmosphere :anisotropic @anisotropic)
                            (apply use-textures
                                   T S M W @P B
                                   (mapcat (fn [{:keys [offset layer]}] [offset layer]) tex-cascade))
                            (uniform-matrix4 program-atmosphere :transform transform)
                            (uniform-matrix4 program-atmosphere :inverse_transform (inverse transform))
                            (uniform-vector3 program-atmosphere :origin @position)
                            (uniform-vector3 program-atmosphere :light_direction light-direction)
                            (uniform-float program-atmosphere :opacity_step @opacity-step)
                            (uniform-float program-atmosphere :cloud_max_step @cms)
                            (doseq [[idx item] (map-indexed vector splits)]
                                   (uniform-float program-atmosphere (keyword (str "split" idx)) item))
                            (doseq [[idx item] (map-indexed vector matrix-cascade)]
                                   (uniform-matrix4 program-atmosphere
                                                    (keyword (str "shadow_map_matrix" idx))
                                                    (:shadow-map-matrix item))
                                   (uniform-float program-atmosphere
                                                  (keyword (str "depth" idx))
                                                  (:depth item)))
                            (uniform-float program-atmosphere :depth (:depth (first matrix-cascade)))
                            (GL11/glDepthFunc GL11/GL_ALWAYS)
                            (render-quads atmosphere-vao))
           (doseq [{:keys [offset layer]} tex-cascade]
                  (destroy-texture offset)
                  (destroy-texture layer))
           (destroy-vertex-array-object atmosphere-vao)
           (print "\rh" (format "%.3f" (- (norm @position) radius))
                  "thres (q/a)" (format "%.3f" @threshold)
                  "mul (e/d)" (format "%.3f" @cloud-multiplier)
                  "mix (r/f)" (format "%.3f" @mix)
                  "o.-step (t/g)" (format "%.3f" @opacity-step)
                  "cms (y/h)" (format "%.3f" @cms)
                  "aniso (u/j)" (format "%.3f" @anisotropic)
                  "dt" (format "%.3f" (* dt 0.001))
                  "      ")
           (flush)
           (swap! t0 + dt))))

(destroy-texture W)
(destroy-texture @P)
(destroy-texture T)
(destroy-texture S)
(destroy-texture M)
(destroy-program program-atmosphere)
(destroy-vertex-array-object shadow-vao)
(destroy-program program-shadow)

(Display/destroy)
