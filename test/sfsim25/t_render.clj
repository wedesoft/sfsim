(ns sfsim25.t-render
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix identity-matrix)]
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
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/quad.png"))

(def vertex-color "#version 410 core
in highp vec3 point;
in mediump vec2 uv;
out lowp vec3 color;
void main()
{
  gl_Position = vec4(point, 1);
  color = vec3(uv.x, 0.5, uv.y);
}")

(def fragment-color "#version 410 core
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
          program  (make-program :vertex vertex-color :fragment fragment-color)
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/colors.png"))

(fact "Render two quads with depth testing"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2, 4 5 7 6]
          vertices [-1.0 -1.0 -0.1 1.0 0.0, 0.5 -1.0 -0.1 1.0 0.0, -1.0 0.5 -0.1 1.0 0.0, 0.5 0.5 -0.1 1.0 0.0,
                    -0.5 -0.5  0.1 0.0 1.0, 1.0 -0.5  0.1 0.0 1.0, -0.5 1.0  0.1 0.0 1.0, 1.0 1.0  0.1 0.0 1.0]
          program  (make-program :vertex vertex-color :fragment fragment-color)
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
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
          vertices1 [-1.0 -1.0 0.1, 0.5 -1.0 0.1, -1.0 0.5 0.1, 0.5 0.5 0.1]
          vertices2 [-0.5 -0.5 0.0, 1.0 -0.5 0.0, -0.5 1.0 0.0, 1.0 1.0 0.0]
          program1  (make-program :vertex vertex-passthrough :fragment fragment-red)
          program2  (make-program :vertex vertex-passthrough :fragment fragment-blue)
          vao1      (make-vertex-array-object program1 indices vertices1 [:point 3])
          vao2      (make-vertex-array-object program2 indices vertices2 [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program1)
      (render-quads vao1)
      (use-program program2)
      (render-quads vao2)
      (destroy-vertex-array-object vao2)
      (destroy-vertex-array-object vao1)
      (destroy-program program2)
      (destroy-program program1))) => (is-image "test/sfsim25/fixtures/objects.png"))

(fact "Render lines only"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5 0.5 0.0, 0.5 0.5 0.0]
          program  (make-program :vertex vertex-passthrough :fragment fragment-blue)
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program)
      (raster-lines (render-quads vao))
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/lines.png"))

(def fragment-uniform-floats "#version 410 core
out lowp vec3 fragColor;
uniform float red;
uniform float green;
uniform float blue;
void main()
{
  fragColor = vec3(red, green, blue);
}")

(fact "Set uniform floating point numbers"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5 0.5 0.0, 0.5 0.5 0.0]
          program  (make-program :vertex vertex-passthrough :fragment fragment-uniform-floats)
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program (uniform-float :red 1.0) (uniform-float :green 0.5) (uniform-float :blue 0.0))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/uniform.png"))

(def fragment-uniform-vector3 "#version 410 core
out lowp vec3 fragColor;
uniform vec3 color;
void main()
{
  fragColor = color;
}")

(fact "Set uniform 3D vector"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5 0.5 0.0, 0.5 0.5 0.0]
          program  (make-program :vertex vertex-passthrough :fragment fragment-uniform-vector3)
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program (uniform-vector3 :color (matrix [1.0 0.5 0.0])))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/uniform.png"))

(def vertex-transform "#version 410 core
in highp vec3 point;
uniform mat4 transform;
void main()
{
  gl_Position = transform * vec4(point, 1);
}")

(fact "Set uniform 4x4 matrix"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.0, 0.5 -0.5 0.0, -0.5 0.5 0.0, 0.5 0.5 0.0]
          program  (make-program :vertex vertex-transform :fragment fragment-blue)
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program (uniform-matrix4 :transform (identity-matrix 4)))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/quad.png"))

(def vertex-texture "#version 410 core
in highp vec3 point;
in mediump vec2 uv;
out lowp vec3 color;
out mediump vec2 uv_fragment;
void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}")

(def fragment-texture "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
uniform sampler2D tex;
void main()
{
  fragColor = texture(tex, uv_fragment).rgb;
}")

(fact "Render 2D texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.0 0.0 0.0, 1.0 -1.0 0.0 1.0 0.0, -1.0 1.0 0.0 0.0 1.0, 1.0 1.0 0.0 1.0 1.0]
          program  (make-program :vertex vertex-texture :fragment fragment-texture)
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-rgb-texture "test/sfsim25/pattern.png")]
      (clear (->RGB 0.0 0.0 0.0))
      (use-program program (uniform-sampler :tex 0))
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/texture.png"))
