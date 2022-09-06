(require '[clojure.math :refer (cos sin sqrt pow to-radians)]
         '[clojure.core.matrix :refer (matrix add mul mmul inverse)]
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
(def light (atom 1.559))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 1500)])))
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
in highp vec3 point;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);

void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec2 intersection = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  if (intersection.y > 0)
    fragColor = vec3(1, 0, 0);
  else
    fragColor = vec3(0, 0, 1);
}
")

(def program
  (make-program :vertex [vertex-atmosphere] :fragment [fragment shaders/ray-sphere]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)
(uniform-matrix4 program :projection projection)
(uniform-float program :radius radius)

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
         (swap! t0 + dt)))

(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
