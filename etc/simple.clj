(require '[clojure.math :refer (cos sin sqrt pow to-radians)]
         '[clojure.core.matrix :refer (matrix add mul mmul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.quaternion :as q]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.shaders :as shaders])

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
(def z-near 1000)
(def z-far (* 2.0 radius))

(def keystates (atom {}))

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))

(def fragment
"#version 410 core

uniform mat4 projection;
uniform mat4 transform;
uniform vec3 origin;
uniform float radius;
uniform float max_height;
uniform float cloud_step;
uniform float cloud_bottom;
uniform float cloud_top;
in highp vec3 point;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, float limit);

void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 planet = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec3 background;
  if (planet.y > 0) {
    atmosphere.y = planet.x - atmosphere.x;
    background = vec3(1, 0, 0);
  } else
    background = vec3(0, 0, 1);
  int steps = int(ceil(atmosphere.y / cloud_step));
  float step = atmosphere.y / steps;
  vec3 point = origin + direction * (atmosphere.x + step * 0.5);
  if (direction.x >= 0) {
    for (int i=0; i<steps; i++) {
      float r = length(point);
      if (r >= radius + cloud_bottom && r <= radius + cloud_top) {
        float t = exp(-step * 0.0001);
        background = background * t + vec3(1, 1, 1) * (1 - t);
      };
      point = point + direction * step;
    };
  } else {
    vec4 intersection = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    if (planet.y > 0)
      intersection = clip_shell_intersections(intersection, planet.x);
    float t = exp(-(intersection.y + intersection.w) * 0.0001);
    background = background * t + vec3(1, 1, 1) * (1 - t);
  };
  fragColor = background;
}")

(def program
  (make-program :vertex [vertex-atmosphere] :fragment [fragment shaders/ray-sphere shaders/ray-shell shaders/clip-shell-intersections]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)
(uniform-matrix4 program :projection projection)
(uniform-float program :radius radius)
(uniform-float program :max_height max-height)
(uniform-float program :cloud_step 50)
(uniform-float program :cloud_bottom 2000)
(uniform-float program :cloud_top 5000)

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
             v         (if (@keystates Keyboard/KEY_PRIOR) 5 (if (@keystates Keyboard/KEY_NEXT) -5 0))]
         (swap! orientation q/* (q/rotation (* dt ra) (matrix [1 0 0])))
         (swap! orientation q/* (q/rotation (* dt rb) (matrix [0 1 0])))
         (swap! orientation q/* (q/rotation (* dt rc) (matrix [0 0 1])))
         (swap! position add (mul dt v (q/rotate-vector @orientation (matrix [0 0 -1]))))
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 1 0]))
                          (uniform-matrix4 program :transform (transformation-matrix (quaternion->matrix @orientation) @position))
                          (uniform-vector3 program :origin @position)
                          (render-quads vao))
         (print "\rheight" (format "%.3f    " (- (norm @position) radius)))
         (flush)
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
