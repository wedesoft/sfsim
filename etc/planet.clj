(require '[clojure.core.matrix :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.planet :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode PixelFormat]
        '[org.lwjgl.input Keyboard])

(set! *unchecked-math* true)

(def vertex-source-planet "#version 410 core
in highp vec3 point;
in mediump vec2 heightcoord;
in mediump vec2 colorcoord;
uniform mat4 projection;
void main()
{
  gl_Position = projection * vec4(point, 1);
}")

(def fragment-source-planet "#version 410 core
out lowp vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(def program-planet (make-program :vertex [vertex-source-planet] :fragment [fragment-source-planet]))

(defn make-face [face]
  (make-vertex-array-object program-planet
                            [0 2 3 1]
                            (make-cube-map-tile-vertices face 0 0 0 33 129)
                            [:point 3 :heightcoord 2 :colorcoord 2]))
(def faces (map make-face (range 6)))

(use-program program-planet)
(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) 0.1 100 (/ (* 60 Math/PI) 180)))

(def position (atom (matrix [0 0 5])))
(def orientation (atom (q/rotation 0 (matrix [0 1 0]))))
(def angle (atom 0))

(while (not (Display/isCloseRequested))
       (swap! angle + 0.0001)
       (reset! orientation (q/rotation @angle (matrix [1 1 0])))
       (onscreen-render (.getWidth desktop) (.getHeight desktop)
                        (clear (matrix [0 0 0]))
                        (use-program program-planet)
                        (uniform-matrix4 program-planet
                                         :projection (mmul projection
                                                           (inverse (transformation-matrix (identity-matrix 3) @position))
                                                           (transformation-matrix (quaternion->matrix @orientation) (zero-vector 3))))
                        (doseq [face faces] (raster-lines (render-quads face)))))

(doseq [face faces] (destroy-vertex-array-object face))
(destroy-program program-planet)

(Display/destroy)

(set! *unchecked-math* false)
