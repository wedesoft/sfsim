(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [comb.template :as template]
            [sfsim25.shaders :refer :all]
            [sfsim25.render :refer :all]
            [sfsim25.util :refer :all]
            [sfsim25.rgb :refer (->RGB)]))

; Compare RGB components of image and ignore alpha values.
(defn is-image [filename]
  (fn [other]
    (let [img (slurp-image filename)]
      (and (= (:width img) (:width other))
           (= (:height img) (:height other))
           (= (map #(bit-and % 0x00ffffff) (:data img)) (map #(bit-and % 0x00ffffff) (:data other)))))))

; Use this test function to record the image the first time.
(defn record-image [filename]
  (fn [other]
    (spit-image filename other)))

(def vertex-passthrough "#version 410 core
in highp vec3 point;
out highp vec3 pos;
void main()
{
  pos = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-sphere-test
  (template/eval "#version 410 core
in highp vec3 pos;
out lowp vec3 fragColor;
<%= ray-sphere %>
void main()
{
  vec2 solution = 0.5 * ray_sphere(vec3(0, 0, 0), 1, vec3(pos.x, pos.y, -1), vec3(0, 0, 1));
  fragColor = vec3(solution.x, solution.y, 0);
}"))

(fact "Intersection with sphere"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program  (make-program :vertex vertex-passthrough :fragment fragment-sphere-test)
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/sphere.png"))
