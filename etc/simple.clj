(require '[clojure.math :refer (cos sin sqrt pow to-radians)]
         '[clojure.core.matrix :refer (matrix add mul mmul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.quaternion :as q]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1920 4) (/ 1080 4)))
(Display/create)

(Keyboard/create)

(def radius 6378000.0)
(def max-height 35000.0)
(def light (atom 1.559))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 500)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def threshold (atom 0.48))
(def multiplier (atom 10.0))
(def anisotropic (atom 0.3))
(def z-near 1000)
(def z-far (* 2.0 radius))
(def worley-size 128)
(def keystates (atom {}))
(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))

(def data (slurp-floats "values.raw"))
(def W (make-float-texture-3d {:width worley-size :height worley-size :depth worley-size :data data}))
(generate-mipmap W)

(def fragment
"#version 410 core

uniform mat4 projection;
uniform mat4 transform;
uniform vec3 origin;
uniform vec3 light;
uniform float radius;
uniform float max_height;
uniform float cloud_step;
uniform float cloud_step2;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float cloud_scale;
uniform float cloud_multiplier;
uniform float threshold;
uniform float anisotropic;
uniform float specular;
uniform float cloud_scatter_amount;
uniform sampler3D worley;
in highp vec3 point;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, float limit);
float phase(float g, float mu);

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
    float cos_incidence = dot(normal, light);
    background = vec3(max(cos_incidence, 0), 0, 0);
  } else {
    float glare = pow(max(0, dot(direction, light)), specular);
    background = vec3(glare, glare, 1);
  };
  int steps = int(ceil(atmosphere.y / cloud_step));
  float step = atmosphere.y / steps;
  vec3 point = origin + direction * (atmosphere.x + atmosphere.y - step * 0.5);
  float scatter_amount = (anisotropic * phase(0.76, -1) + 1 - anisotropic) * cloud_scatter_amount;
  for (int i=0; i<steps; i++) {
    vec3 pos = point - i * step * direction;
    float r = length(pos);
    if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
      float noise = (texture(worley, pos / cloud_scale).r - threshold) * cloud_multiplier;
      if (noise > 0) {
        vec2 planet = ray_sphere(vec3(0, 0, 0), radius, pos, light);
        float t = exp(-step * 0.0001 * noise);
        float scatter;
        float intensity;
        if (planet.y == 0) {
          vec2 atmosphere2 = ray_sphere(vec3(0, 0, 0), radius + max_height, pos, light);
          int steps2 = int(ceil(atmosphere2.y / cloud_step2));
          float step2 = atmosphere2.y / steps2;
          intensity = 1;
          for (int j=0; j<10; j++) {
            vec3 pos2 = pos + (j + 0.5) * step2 * light;
            float noise2 = (texture(worley, pos2 / cloud_scale).r - threshold) * cloud_multiplier;
            if (noise2 > 0) {
              float t2 = exp((scatter_amount - 1) * step2 * 0.0001 * noise);
              intensity = intensity * t2;
            };
          };
          scatter = anisotropic * phase(0.76, dot(direction, light)) + 1 - anisotropic;
        } else {
          scatter = 0;
          intensity = 0;
        };
        background = background * t + intensity * scatter * (1 - t);
      };
    };
  };
  fragColor = background;
}")

(def program
  (make-program :vertex [vertex-atmosphere]
                :fragment [fragment shaders/ray-sphere shaders/ray-shell shaders/clip-shell-intersections phase-function]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)
(uniform-matrix4 program :projection projection)
(uniform-float program :radius radius)
(uniform-float program :max_height max-height)
(uniform-float program :cloud_step 100)
(uniform-float program :cloud_step2 1000)
(uniform-float program :cloud_bottom 1000)
(uniform-float program :cloud_top 6000)
(uniform-float program :cloud_scale 15000)
(uniform-float program :cloud_scatter_amount 1.0)
(uniform-float program :specular 200)
(uniform-sampler program :worley 0)

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (while (Keyboard/next)
              (let [state     (Keyboard/getEventKeyState)
                    event-key (Keyboard/getEventKey)]
                (swap! keystates assoc event-key state)))
       (let [t1        (System/currentTimeMillis)
             dt        (- t1 @t0)
             tr        (if (@keystates Keyboard/KEY_Q) 0.001 (if (@keystates Keyboard/KEY_A) -0.001 0))
             tm        (if (@keystates Keyboard/KEY_E) 0.01 (if (@keystates Keyboard/KEY_D) -0.01 0))
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
                          (use-textures W)
                          (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @position))
                          (uniform-vector3 program :origin @position)
                          (uniform-vector3 program :light (matrix [0 (cos @light) (sin @light)]))
                          (uniform-float program :threshold @threshold)
                          (uniform-float program :cloud_multiplier @multiplier)
                          (uniform-float program :anisotropic @anisotropic)
                          (render-quads vao))
         (print "\rheight" (format "%.3f" (- (norm @position) radius))
                "threshold" (format "%.3f" @threshold)
                "multiplier" (format "%.3f" @multiplier)
                "anisotropic" (format "%.3f" @anisotropic)
                "      ")
         (flush)
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
