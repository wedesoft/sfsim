(ns sfsim25.t-render
  (:require [midje.sweet :refer :all]
            [sfsim25.rgb :refer (->RGB)]
            [sfsim25.util :refer (slurp-image)]
            [sfsim25.render :refer :all])
  (:import [org.lwjgl.opengl Display DisplayMode]))

(defn is-image [filename]
  (fn [other]
    (let [img (slurp-image filename)]
      (and (= (:width img) (:width other))
           (= (:height img) (:height other))
           (= (seq (:data img)) (seq (:data other)))))))

(fact "Render background color"
  (offscreen-render 160 120 (clear (->RGB 1.0 0.0 0.0))) => (is-image "test/sfsim25/fixtures/red.png"))

(def vertex-minimal-source "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-minimal-source "#version 410 core
out lowp vec3 fragColor;
void main()
{
  fragColor = vec3(0.0, 0.0, 1.0);
}")

(fact "Render a quad"
  (offscreen-render 160 120
    (let [prog (make-program :vertex vertex-minimal-source :fragment fragment-minimal-source)]
      (clear (->RGB 0.0 0.0 0.0))
      (destroy-program prog))) => (is-image "test/sfsim25/fixtures/red.png"))
