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
;(Display/setDisplayMode (DisplayMode. 1920 1080))
;(Display/setFullscreen true)
(Display/create)

(Keyboard/create)

(def radius 6378000.0)
(def light (atom 1.559))
(def position (atom (matrix [0 (* -0 radius) (+ (* 1 radius) 1500)])))
(def orientation (atom (q/rotation (to-radians 90) (matrix [1 0 0]))))
(def z-near 1000)
(def z-far (* 2.0 radius))

(def projection (projection-matrix (Display/getWidth) (Display/getHeight) z-near z-far (to-radians 60)))

(def fragment
"#version 410 core

uniform mat4 projection;
uniform mat4 transform;
in highp vec3 point;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

void main()
{
  fragColor = vec3(1, 0, 0);
}
")

(def program
  (make-program :vertex [vertex-atmosphere] :fragment [fragment]))

(def indices [0 1 3 2])
(def vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(use-program program)
(uniform-matrix4 program :projection projection)

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (let [transform (transformation-matrix (quaternion->matrix @orientation) @position)]
         (onscreen-render (Display/getWidth) (Display/getHeight)
                          (clear (matrix [0 1 0]))
                          (uniform-matrix4 program :transform transform)
                          (render-quads vao))))

(destroy-vertex-array-object vao)
(destroy-program program)

(Display/destroy)
