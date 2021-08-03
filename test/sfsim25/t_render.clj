(ns sfsim25.t-render
  (:require [midje.sweet :refer :all]
            [sfsim25.rgb :refer (->RGB)]
            [sfsim25.util :refer :all]
            [sfsim25.render :refer :all])
  (:import [org.lwjgl.opengl Display DisplayMode]))

; Compare RGB components of image and ignore alpha values.
(defn is-image [filename]
  (fn [other]
    (let [img (slurp-image filename)]
      (and (= (:width img) (:width other))
           (= (:height img) (:height other))
           (= (map #(bit-and % 0x00ffffff) (:data img)) (map #(bit-and % 0x00ffffff) (:data other)))))))

(fact "Render background color"
  (offscreen-render 160 120 (clear (->RGB 1.0 0.0 0.0))) => (is-image "test/sfsim25/fixtures/red.png"))

(def vertex-passthrough "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-blue "#version 410 core
out lowp vec3 fragColor;
void main()
{
  fragColor = vec3(0.0, 0.0, 1.0);
}")

(fact "Render a quad"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5 0.5 0.0, 0.5 0.5 0.0]
          program  (make-program :vertex vertex-passthrough :fragment fragment-blue)
          vao      (make-vao program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (render-quads program vao)
      (destroy-vao vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/quad.png"))

(def vertex-color-source "#version 410 core
in highp vec3 point;
in mediump vec2 uv;
out lowp vec3 color;
void main()
{
  gl_Position = vec4(point, 1);
  color = vec3(uv.x, 0.5, uv.y);
}")

(def fragment-color-source "#version 410 core
in mediump vec3 color;
out lowp vec3 fragColor;
void main()
{
  fragColor = color;
}")

(fact "Shader with two vertex attributes"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.0 0.0 0.0, 1.0 -1.0 0.0 1.0 0.0, -1.0 1.0 0.0 0.0 1.0, 1.0 1.0 0.0 1.0 1.0]
          program  (make-program :vertex vertex-color-source :fragment fragment-color-source)
          vao      (make-vao program indices vertices [:point 3 :uv 2])]
      (clear (->RGB 0.0 0.0 0.0))
      (render-quads program vao)
      (destroy-vao vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/colors.png"))

(fact "Render two quads"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2, 4 5 7 6]
          vertices [-1.0 -1.0 0.0 1.0 0.0, 0.5 -1.0 0.0 1.0 0.0, -1.0 0.5 0.0 1.0 0.0, 0.5 0.5 0.0 1.0 0.0,
                    -0.5 -0.5 0.0 0.0 1.0, 1.0 -0.5 0.0 0.0 1.0, -0.5 1.0 0.0 0.0 1.0, 1.0 1.0 0.0 0.0 1.0]
          program  (make-program :vertex vertex-color-source :fragment fragment-color-source)
          vao      (make-vao program indices vertices [:point 3 :uv 2])]
      (clear (->RGB 0.0 0.0 0.0))
      (render-quads program vao)
      (destroy-vao vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/quads.png"))

(def fragment-red "#version 410 core
out lowp vec3 fragColor;
void main()
{
  fragColor = vec3(1.0, 0.0, 0.0);
}")

(fact "Correct switching between two vertex array objects and shader programs"
  (offscreen-render 160 120
    (let [indices   [0 1 3 2]
          vertices1 [-1.0 -1.0 0.0, 0.5 -1.0 0.0, -1.0 0.5 0.0, 0.5 0.5 0.0]
          vertices2 [-0.5 -0.5 0.0, 1.0 -0.5 0.0, -0.5 1.0 0.0, 1.0 1.0 0.0]
          program1  (make-program :vertex vertex-passthrough :fragment fragment-red)
          program2  (make-program :vertex vertex-passthrough :fragment fragment-blue)
          vao1      (make-vao program1 indices vertices1 [:point 3])
          vao2      (make-vao program2 indices vertices2 [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (render-quads program1 vao1)
      (render-quads program2 vao2)
      (destroy-vao vao2)
      (destroy-vao vao1)
      (destroy-program program2)
      (destroy-program program1))) => (is-image "test/sfsim25/fixtures/objects.png"))
