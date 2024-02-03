(ns sfsim.t-render
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim.conftest :refer (is-image roughly-vector)]
            [fastmath.vector :refer (vec3 vec4 normalize)]
            [fastmath.matrix :refer (eye diagonal) :as m]
            [clojure.math :refer (to-radians)]
            [comb.template :as template]
            [sfsim.util :refer :all]
            [sfsim.image :refer :all]
            [sfsim.matrix :refer :all :as matrix]
            [sfsim.shaders :as s]
            [sfsim.quaternion :as q]
            [sfsim.texture :refer :all]
            [sfsim.render :refer :all :as render])
  (:import [org.lwjgl.opengl GL11 GL12 GL30 GL42]
           [org.lwjgl.glfw GLFW]
           [org.lwjgl BufferUtils]))

(mi/collect! {:ns ['sfsim.render]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(fact "Render background color"
  (offscreen-render 160 120 (clear (vec3 1.0 0.0 0.0)))
  => (is-image "test/sfsim/fixtures/render/red.png" 0.0))

(def vertex-passthrough
"#version 410 core
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-blue
"#version 410 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(0.0, 0.0, 1.0);
}")

(fact "Render a quad"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quad.png" 0.0))

(fact "Group shaders in lists"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [[vertex-passthrough]] :sfsim.render/fragment [[fragment-blue]])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quad.png" 0.0))

(def vertex-color
"#version 410 core
in vec3 point;
in vec2 uv;
out vec3 color;
void main()
{
  gl_Position = vec4(point, 1);
  color = vec3(uv.x, 0.5, uv.y);
}")

(def fragment-color
"#version 410 core
in vec3 color;
out vec3 fragColor;
void main()
{
  fragColor = color;
}")

(fact "Shader with two vertex attributes"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-color] :sfsim.render/fragment [fragment-color])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/colors.png" 0.0))

(fact "Render two quads with depth testing"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2, 4 5 7 6]
          vertices [-1.0 -1.0 0.2 1.0 0.0, 0.5 -1.0 0.2 1.0 0.0, -1.0 0.5 0.2 1.0 0.0, 0.5 0.5 0.2 1.0 0.0,
                    -0.5 -0.5 0.1 0.0 1.0, 1.0 -0.5 0.1 0.0 1.0, -0.5 1.0 0.1 0.0 1.0, 1.0 1.0 0.1 0.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-color] :sfsim.render/fragment [fragment-color])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quads.png" 0.0))

(def fragment-red
"#version 410 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(1.0, 0.0, 0.0);
}")

(fact "Correct switching between two vertex array objects and shader programs"
  (offscreen-render 160 120
    (let [indices   [0 1 3 2]
          vertices1 [-1.0 -1.0 0.1, 0.5 -1.0 0.1, -1.0 0.5 0.1, 0.5 0.5 0.1]
          vertices2 [-0.5 -0.5 0.2, 1.0 -0.5 0.2, -0.5 1.0 0.2, 1.0 1.0 0.2]
          program1  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-red])
          program2  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-blue])
          vao1      (make-vertex-array-object program1 indices vertices1 ["point" 3])
          vao2      (make-vertex-array-object program2 indices vertices2 ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program1)
      (render-quads vao1)
      (use-program program2)
      (render-quads vao2)
      (destroy-vertex-array-object vao2)
      (destroy-vertex-array-object vao1)
      (destroy-program program2)
      (destroy-program program1))) => (is-image "test/sfsim/fixtures/render/objects.png" 0.0))

(fact "Render lines only"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (raster-lines (render-quads vao))
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/lines.png" 4.3))

(def fragment-uniform-floats
"#version 410 core
out vec3 fragColor;
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
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-uniform-floats])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-float program "red" 1.0)
      (uniform-float program "green" 0.5)
      (uniform-float program "blue" 0.0)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/uniform-floats.png" 0.0))

(def fragment-uniform-ints
"#version 410 core
out vec3 fragColor;
uniform int red;
uniform int green;
uniform int blue;
void main()
{
  fragColor = vec3(red / 255.0, green / 255.0, blue / 255.0);
}")

(fact "Set uniform integer numbers"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-uniform-ints])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-int program "red" 255)
      (uniform-int program "green" 128)
      (uniform-int program "blue" 0)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/uniform-ints.png" 0.0))

(def fragment-uniform-vector3
"#version 410 core
out vec3 fragColor;
uniform vec3 color;
void main()
{
  fragColor = color;
}")

(fact "Set uniform 3D vector"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-uniform-vector3])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-vector3 program "color" (vec3 1.0 0.5 0.0))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/uniform-floats.png" 0.0))

(def vertex-transform3
"#version 410 core
in vec3 point;
uniform mat3 transform;
void main()
{
  gl_Position = vec4(transform * point, 1);
}")

(fact "Set uniform 3x3 matrix"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-transform3] :sfsim.render/fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-matrix3 program "transform" (eye 3))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quad.png" 0.0))

(def vertex-transform4
"#version 410 core
in vec3 point;
uniform mat4 transform;
void main()
{
  gl_Position = transform * vec4(point, 1);
}")

(fact "Set uniform 4x4 matrix"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-transform4] :sfsim.render/fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-matrix4 program "transform" (eye 4))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quad.png" 0.0))

(def vertex-texture
"#version 410 core
in vec3 point;
in vec2 uv;
out vec3 color;
out vec2 uv_fragment;
void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}")

(def fragment-texture-1d
"#version 410 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler1D tex;
void main()
{
  fragColor = texture(tex, uv_fragment.x).rgb;
}")

(tabular "Render 1D floating-point texture"
  (fact
    (offscreen-render 64 64
                      (let [indices  [0 1 3 2]
                            vertices [-1.0 -1.0 0.5 -1.0 -1.0, 1.0 -1.0 0.5 2.0 -1.0, -1.0 1.0 0.5 -1.0 2.0, 1.0 1.0 0.5 2.0 2.0]
                            program  (make-program :sfsim.render/vertex [vertex-texture]
                                                   :sfsim.render/fragment [fragment-texture-1d])
                            vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
                            tex      (make-float-texture-1d ?interpolation ?boundary (float-array [0.0 1.0]))]
                        (clear (vec3 0.0 0.0 0.0))
                        (use-program program)
                        (uniform-sampler program "tex" 0)
                        (use-textures {0 tex})
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program)))
    => (is-image (str "test/sfsim/fixtures/render/" ?result ".png") 0.0))
  ?interpolation ?boundary ?result
  :sfsim.texture/nearest       :sfsim.texture/clamp    "floats-1d-nearest-clamp"
  :sfsim.texture/linear        :sfsim.texture/clamp    "floats-1d-linear-clamp"
  :sfsim.texture/nearest       :sfsim.texture/repeat   "floats-1d-nearest-repeat"
  :sfsim.texture/linear        :sfsim.texture/repeat   "floats-1d-linear-repeat")

(def fragment-texture-2d
"#version 410 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler2D tex;
void main()
{
  fragColor = texture(tex, uv_fragment).rgb;
}")

(fact "Render 2D RGB texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
          tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp (slurp-image "test/sfsim/fixtures/render/pattern.png"))]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-sampler program "tex" 0)
      (use-textures {0 tex})
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/texture.png" 0.0))

(tabular "Render 2D floating-point texture"
  (fact
    (offscreen-render 64 64
                      (let [indices  [0 1 3 2]
                            vertices [-1.0 -1.0 0.5 -1.0 -1.0, 1.0 -1.0 0.5 2.0 -1.0, -1.0 1.0 0.5 -1.0 2.0, 1.0 1.0 0.5 2.0 2.0]
                            program  (make-program :sfsim.render/vertex [vertex-texture]
                                                   :sfsim.render/fragment [fragment-texture-2d])
                            vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
                            img      #:sfsim.image{:width 2 :height 2 :data (float-array [0.0 0.25 0.5 1.0])}
                            tex      (make-float-texture-2d ?interpolation ?boundary img)]
                        (clear (vec3 0.0 0.0 0.0))
                        (use-program program)
                        (uniform-sampler program "tex" 0)
                        (use-textures {0 tex})
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program)))
    => (is-image (str "test/sfsim/fixtures/render/" ?result ".png") 0.0))
  ?interpolation ?boundary ?result
  :sfsim.texture/nearest       :sfsim.texture/clamp    "floats-2d-nearest-clamp"
  :sfsim.texture/linear        :sfsim.texture/clamp    "floats-2d-linear-clamp"
  :sfsim.texture/nearest       :sfsim.texture/repeat   "floats-2d-nearest-repeat"
  :sfsim.texture/linear        :sfsim.texture/repeat   "floats-2d-linear-repeat")

(fact "Render 2D unsigned-byte texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
          tex      (make-ubyte-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                          #:sfsim.image{:width 2 :height 2 :data (byte-array [0 64 0 0 127 255 0 0])})]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-sampler program "tex" 0)
      (use-textures {0 tex})
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/ubytes.png" 0.0))

(fact "Render 2D vector texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
          tex      (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                           #:sfsim.image{:width 2 :height 2 :data (float-array [0 0 0 1 0 0 0 1 0 1 1 1])})]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-sampler program "tex" 0)
      (use-textures {0 tex})
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/vectors.png" 0.0))

(def vertex-interpolate
"#version 410 core
in vec3 point;
out vec3 pos;
void main()
{
  gl_Position = vec4(point, 1);
  pos = vec3(0.5 + 0.5 * point.xy, point.z);
}")

(def fragment-sample-shadow
"#version 410 core
uniform sampler2DShadow shadow_map;
in vec3 pos;
out vec3 fragColor;
void main(void)
{
  float result = texture(shadow_map, pos);
  fragColor = vec3(result, result, result);
}")

(fact "Perform shadow sampling using 2D depth texture"
  (offscreen-render 64 64
                    (let [indices  [0 1 3 2]
                          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                          program  (make-program :sfsim.render/vertex [vertex-interpolate]
                                                 :sfsim.render/fragment [fragment-sample-shadow])
                          vao      (make-vertex-array-object program indices vertices ["point" 3])
                          data     [0.4 0.4 0.4 0.4, 0.4 0.6 0.6 0.4, 0.4 0.6 0.6 0.4, 0.4 0.4 0.4 0.4]
                          depth    (make-depth-texture :sfsim.texture/linear :sfsim.texture/clamp #:sfsim.image{:width 4 :height 4 :data (float-array data)})]
                      (clear (vec3 1.0 0.0 0.0))
                      (use-program program)
                      (uniform-sampler program "shadow_map" 0)
                      (use-textures {0 depth})
                      (render-quads vao)
                      (destroy-texture depth)
                      (destroy-vertex-array-object vao)
                      (destroy-program program)))
  => (is-image "test/sfsim/fixtures/render/shadow-sample.png" 0.0))

(def fragment-texture-3d
"#version 410 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler3D tex;
void main()
{
  fragColor = texture(tex, vec3(uv_fragment, 1.0)).rgb;
}")

(tabular "Render 3D floating-point texture"
  (fact
    (offscreen-render 64 64
                      (let [indices  [0 1 3 2]
                            vertices [-1.0 -1.0 0.5 -1.0 -1.0, 1.0 -1.0 0.5 2.0 -1.0, -1.0 1.0 0.5 -1.0 2.0, 1.0 1.0 0.5 2.0 2.0]
                            program  (make-program :sfsim.render/vertex [vertex-texture]
                                                   :sfsim.render/fragment [fragment-texture-3d])
                            vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
                            data     [0 0.125 0.25 0.375 0.5 0.625 0.75 0.875]
                            tex      (make-float-texture-3d ?interpolation ?boundary
                                                            #:sfsim.image{:width 2 :height 2 :depth 2 :data (float-array data)})]
                        (clear (vec3 0.0 0.0 0.0))
                        (use-program program)
                        (uniform-sampler program "tex" 0)
                        (use-textures {0 tex})
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program)))
    => (is-image (str "test/sfsim/fixtures/render/" ?result ".png") 0.0))
  ?interpolation ?boundary ?result
  :sfsim.texture/nearest       :sfsim.texture/clamp    "floats-3d-nearest-clamp"
  :sfsim.texture/linear        :sfsim.texture/clamp    "floats-3d-linear-clamp"
  :sfsim.texture/nearest       :sfsim.texture/repeat   "floats-3d-nearest-repeat"
  :sfsim.texture/linear        :sfsim.texture/repeat   "floats-3d-linear-repeat")

(def fragment-two-textures
"#version 410 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler2D tex1;
uniform sampler2D tex2;
void main()
{
  if (uv_fragment.x < 0.5)
    fragColor = texture(tex1, uv_fragment).rgb;
  else
    fragColor = texture(tex2, uv_fragment).rgb;
}")

(fact "Test use of two textures using different indices"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-two-textures])
          vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
          tex1     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                           #:sfsim.image{:width 2 :height 2 :data (float-array [0 0 0 0 0 0 0 0 0 0 0 0])})
          tex2     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                           #:sfsim.image{:width 2 :height 2 :data (float-array [1 1 1 1 1 1 1 1 1 1 1 1])})]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (uniform-sampler program "tex1" 0)
      (uniform-sampler program "tex2" 1)
      (use-textures {1 tex2})
      (use-textures {0 tex1})
      (render-quads vao)
      (destroy-texture tex2)
      (destroy-texture tex1)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/two-textures.png" 0.0))

(def control-uniform
"#version 410 core
layout(vertices = 4) out;
void main(void)
{
  if (gl_InvocationID == 0) {
    gl_TessLevelOuter[0] = 4.0;
    gl_TessLevelOuter[1] = 4.0;
    gl_TessLevelOuter[2] = 4.0;
    gl_TessLevelOuter[3] = 4.0;
    gl_TessLevelInner[0] = 4.0;
    gl_TessLevelInner[1] = 4.0;
  }
  gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
}")

(def evaluation-mix
"#version 410 core
layout(quads, equal_spacing, ccw) in;
void main()
{
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  gl_Position = mix(a, b, gl_TessCoord.y);
}")

(def geometry-triangle
"#version 410 core
layout(triangles) in;
layout(triangle_strip, max_vertices = 3) out;
void main(void)
{
	gl_Position = gl_in[0].gl_Position;
	EmitVertex();
	gl_Position = gl_in[1].gl_Position;
	EmitVertex();
	gl_Position = gl_in[2].gl_Position;
	EmitVertex();
	EndPrimitive();
}")

(fact "Subdivide quad using a tessellation shader"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/tess-control [control-uniform]
                                 :sfsim.render/tess-evaluation [evaluation-mix] :sfsim.render/geometry [geometry-triangle]
                                 :sfsim.render/fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (raster-lines (render-patches vao))
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/tessellation.png" 28.9))

(def fragment-part1
"#version 410 core
vec3 fun()
{
  return vec3(0.0, 0.0, 1.0);
}
")

(def fragment-part2
"#version 410 core
out vec3 fragColor;
vec3 fun();
void main()
{
  fragColor = fun();
}")

(fact "Link two shader parts"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                 :sfsim.render/fragment [fragment-part1 fragment-part2])
          vao      (make-vertex-array-object program indices vertices ["point" 3])]
      (clear (vec3 0.0 0.0 0.0))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim/fixtures/render/quad.png" 0.0))

(fact "Render to floating-point texture (needs active OpenGL context)"
      (with-invisible-window
        (let [tex (texture-render-color 8 8 true (clear (vec3 1.0 2.0 3.0)))]
          (get-vector3 (rgb-texture->vectors3 tex) 0 0) => (vec3 1.0 2.0 3.0)
          (destroy-texture tex))))

(fact "Render to image texture (needs active OpenGL context)"
      (with-invisible-window
        (let [tex (texture-render-color 160 120 false (clear (vec3 1.0 0.0 0.0)))
              img (texture->image tex)]
          img => (is-image "test/sfsim/fixtures/render/red.png" 0.0)
          (destroy-texture tex))))

(def fragment-two-attachments
"#version 410 core
layout (location = 0) out float output1;
layout (location = 1) out float output2;
void main()
{
  output1 = 0.25;
  output2 = 0.75;
}")

(facts "Using framebuffer to render to two color textures"
       (with-invisible-window
         (let [tex1     (make-empty-float-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 1 1)
               tex2     (make-empty-float-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 1 1)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                      :sfsim.render/fragment [fragment-two-attachments])
               vao      (make-vertex-array-object program indices vertices ["point" 3])]
           (framebuffer-render 1 1 :sfsim.render/cullback nil [tex1 tex2]
                               (use-program program)
                               (render-quads vao))
           (get-float (float-texture-2d->floats tex1) 0 0) => 0.25
           (get-float (float-texture-2d->floats tex2) 0 0) => 0.75
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex1)
           (destroy-texture tex2))))

(facts "Using framebuffer to render to layers of 3D texture"
       (with-invisible-window
         (let [tex      (make-empty-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp 1 1 2)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                      :sfsim.render/fragment [fragment-two-attachments])
               vao      (make-vertex-array-object program indices vertices ["point" 3])]
           (framebuffer-render 1 1 :sfsim.render/cullback nil [tex]
                               (use-program program)
                               (render-quads vao))
           (with-texture (:sfsim.texture/target tex) (:sfsim.texture/texture tex)
             (let [buf  (BufferUtils/createFloatBuffer 2)
                   data (float-array 2)]
               (GL11/glGetTexImage GL12/GL_TEXTURE_3D 0 GL11/GL_RED GL11/GL_FLOAT buf)
               (.get buf data)
               (seq data) => [0.25 0.75]))
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex))))

(def fragment-noop
"#version 410 core
void main(void)
{
}")

(facts "Using framebuffer to render to depth texture"
       (with-invisible-window
         (let [depth    (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 1 1)
               indices  [2 3 1 0]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-noop])
               vao      (make-vertex-array-object program indices vertices ["point" 3])]
           (framebuffer-render 1 1 :sfsim.render/cullfront depth []
                               (use-program program)
                               (clear)
                               (render-quads vao))
           (get-float (depth-texture->floats depth) 0 0) => 0.5
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture depth))))

(def lod-texture-1d
"#version 410 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler1D tex;
uniform float lod;
void main()
{
  float value = textureLod(tex, uv_fragment.x, lod).r;
  fragColor = vec3(value, value, value);
}")

(tabular "1D mipmaps"
         (fact
           (offscreen-render 64 64
             (let [indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
                   program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [lod-texture-1d])
                   vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
                   data     (flatten (repeat 8 [0 0 1 1]))
                   tex      (make-float-texture-1d :sfsim.texture/linear :sfsim.texture/clamp (float-array data))]
               (generate-mipmap tex)
               (clear (vec3 0.0 0.0 0.0))
               (use-program program)
               (uniform-sampler program "tex" 0)
               (uniform-float program "lod" ?lod)
               (use-textures {0 tex})
               (render-quads vao)
               (destroy-texture tex)
               (destroy-vertex-array-object vao)
               (destroy-program program))) => (is-image (str "test/sfsim/fixtures/render/" ?result ".png") 0.0))
         ?lod ?result
         0.0  "lod-1d-0"
         1.0  "lod-1d-1"
         2.0  "lod-1d-2")

(def alpha-probe
  (template/fn [alpha]
"#version 410 core
out vec4 fragColor;
void main()
{
  fragColor = vec4(0.25, 0.5, 0.75, <%= alpha %>);
}"))

(tabular "render alpha"
         (fact
           (with-invisible-window
             (let [indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                   program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                          :sfsim.render/fragment [(alpha-probe ?alpha)])
                   vao      (make-vertex-array-object program indices vertices ["point" 3])
                   tex      (texture-render-color 1 1 true (use-program program) (render-quads vao))
                   img      (rgba-texture->vectors4 tex)]
               (destroy-texture tex)
               (destroy-vertex-array-object vao)
               (destroy-program program)
               (get-vector4 img 0 0))) => (roughly-vector (vec4 0.25 0.5 0.75 ?alpha) 1e-6))
         ?alpha
         1.0
         0.0)

(fact "Render empty depth map"
      (with-invisible-window
        (let [tex (texture-render-depth 10 10 (clear))]
          (get-float (depth-texture->floats tex) 0 0) => 0.0
          (destroy-texture tex))))

(tabular "Render back face of quad into shadow map"
         (with-invisible-window
           (let [indices  (vec (reverse [0 1 3 2]))
                 vertices [-1.0 -1.0 ?z, 1.0 -1.0 ?z, -1.0 1.0 ?z, 1.0 1.0 ?z]
                 program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-noop])
                 vao      (make-vertex-array-object program indices vertices ["point" 3])
                 tex      (texture-render-depth 10 10 (clear) (use-program program) (render-quads vao))]
             (get-float (depth-texture->floats tex) 5 5) => ?z
             (destroy-texture tex)
             (destroy-vertex-array-object vao)
             (destroy-program program)))
         ?z
         0.5
         0.75
         0.25)

(def vertex-shadow
"#version 410 core
uniform mat4 transform;
in vec3 point;
vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);
void main(void)
{
  gl_Position = shrink_shadow_index(transform * vec4(point, 1), 128, 128);
}")

(def fragment-shadow
"#version 410 core
void main(void)
{
}")

(def vertex-scene
"#version 410 core
uniform mat4 projection;
in vec3 point;
out vec4 pos;
out float ambient;
void main(void)
{
  gl_Position = projection * vec4(point, 1);
  pos = vec4(point, 1);
  ambient = -point.z - 3;
}")

(def fragment-scene
"#version 410 core
uniform sampler2DShadow shadow_map;
uniform mat4 shadow_map_matrix;
in vec4 pos;
in float ambient;
out vec4 fragColor;
float shadow_lookup(sampler2DShadow shadow_map, vec4 shadow_pos);
void main(void)
{
  vec4 shadow_pos = shadow_map_matrix * pos;
  float shade = shadow_lookup(shadow_map, shadow_pos);
  float brightness = 0.7 * shade + 0.1 * ambient + 0.1;
  fragColor = vec4(brightness, brightness, brightness, 1.0);
}")

(fact "Shadow mapping integration test"
      (with-invisible-window
        (let [projection     (projection-matrix 320 240 2.0 5.0 (to-radians 90))
              transform      (eye 4)
              light-vector   (normalize (vec3 1 1 2))
              shadow-mat     (shadow-matrices projection transform light-vector 1.0)
              indices        [0 1 3 2 6 7 5 4 8 9 11 10]
              vertices       [-2.0 -2.0 -4.0, 2.0 -2.0 -4.0, -2.0 2.0 -4.0, 2.0 2.0 -4.0,
                              -1.0 -1.0 -3.0, 1.0 -1.0 -3.0, -1.0 1.0 -3.0, 1.0 1.0 -3.0
                              -1.0 -1.0 -2.9, 1.0 -1.0 -2.9, -1.0 1.0 -2.9, 1.0 1.0 -2.9]
              program-shadow (make-program :sfsim.render/vertex [vertex-shadow s/shrink-shadow-index]
                                           :sfsim.render/fragment [fragment-shadow])
              program-main   (make-program :sfsim.render/vertex [vertex-scene]
                                           :sfsim.render/fragment [fragment-scene s/shadow-lookup])
              vao            (make-vertex-array-object program-main indices vertices ["point" 3])
              shadow-map     (texture-render-depth
                               128 128
                               (clear)
                               (use-program program-shadow)
                               (uniform-matrix4 program-shadow "transform" (:shadow-ndc-matrix shadow-mat))
                               (render-quads vao))]
          (let [depth (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 320 240)
                tex   (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGBA8 320 240)]
            (framebuffer-render 320 240 :sfsim.render/cullback depth [tex]
                                (clear (vec3 0 0 0))
                                (use-program program-main)
                                (uniform-sampler program-main "shadow_map" 0)
                                (uniform-int program-main "shadow_size" 128)
                                (uniform-matrix4 program-main "projection" projection)
                                (uniform-matrix4 program-main "shadow_map_matrix" (:shadow-map-matrix shadow-mat))
                                (use-textures {0 shadow-map})
                                (render-quads vao))
            (let [img (texture->image tex)]
              (destroy-texture shadow-map)
              (destroy-vertex-array-object vao)
              (destroy-program program-main)
              (destroy-program program-shadow)
              img))))
      => (is-image "test/sfsim/fixtures/render/shadow.png" 0.04))

(def fragment-scene-cascade
"#version 410 core
in vec4 pos;
in float ambient;
out vec4 fragColor;
float shadow_cascade_lookup(vec4 point);
void main(void)
{
  float shade = shadow_cascade_lookup(pos);
  float brightness = 0.7 * shade + 0.1 * ambient + 0.1;
  fragColor = vec4(brightness, brightness, brightness, 1.0);
}")

(fact "Shadow cascade integration test"
      (with-invisible-window
        (let [projection     (projection-matrix 320 240 2.0 5.0 (to-radians 90))
              transform      (eye 4)
              num-steps      1
              light-vector   (normalize (vec3 1 1 2))
              shadow-data    #:sfsim.opacity{:num-steps num-steps :mix 0.5 :depth 1.0}
              render-vars    #:sfsim.render{:projection projection :extrinsics transform :light-direction light-vector
                                            :z-near 2.0 :z-far 5.0}
              shadow-mats    (shadow-matrix-cascade shadow-data render-vars)
              indices        [0 1 3 2 6 7 5 4 8 9 11 10]
              vertices       [-2.0 -2.0 -4.0, 2.0 -2.0 -4.0, -2.0 2.0 -4.0, 2.0 2.0 -4.0,
                              -1.0 -1.0 -3.0, 1.0 -1.0 -3.0, -1.0 1.0 -3.0, 1.0 1.0 -3.0
                              -1.0 -1.0 -2.9, 1.0 -1.0 -2.9, -1.0 1.0 -2.9, 1.0 1.0 -2.9]
              program-shadow (make-program :sfsim.render/vertex [vertex-shadow s/shrink-shadow-index]
                                           :sfsim.render/fragment [fragment-shadow])
              program-main   (make-program :sfsim.render/vertex [vertex-scene]
                                           :sfsim.render/fragment [fragment-scene-cascade
                                                                   (s/shadow-cascade-lookup num-steps "shadow_lookup")
                                                                   s/shadow-lookup])
              vao            (make-vertex-array-object program-main indices vertices ["point" 3])
              shadow-maps    (shadow-cascade 128 shadow-mats program-shadow
                                             (fn [shadow-ndc-matrix]
                                                 (uniform-matrix4 program-shadow "transform" shadow-ndc-matrix)
                                                 (render-quads vao)))]
          (let [depth (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 320 240)
                tex   (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGBA8 320 240)]
            (framebuffer-render 320 240 :sfsim.render/cullback depth [tex]
                                (clear (vec3 0 0 0))
                                (use-program program-main)
                                (uniform-sampler program-main "shadow_map0" 0)
                                (uniform-float program-main "split0" 0.0)
                                (uniform-float program-main "split1" 50.0)
                                (uniform-int program-main "shadow_size" 128)
                                (uniform-matrix4 program-main "projection" projection)
                                (uniform-matrix4 program-main "transform" (eye 4))
                                (uniform-matrix4 program-main "shadow_map_matrix0" (:shadow-map-matrix (shadow-mats 0)))
                                (use-textures (zipmap (range) shadow-maps))
                                (render-quads vao))
            (let [img (texture->image tex)]
              (doseq [shadow-map shadow-maps]
                     (destroy-texture shadow-map))
              (destroy-vertex-array-object vao)
              (destroy-program program-main)
              (destroy-program program-shadow)
              img))))
      => (is-image "test/sfsim/fixtures/render/shadow.png" 0.04))

(def fragment-cubemap-attachment
"#version 410 core
layout (location = 0) out float output1;
layout (location = 1) out float output2;
layout (location = 2) out float output3;
layout (location = 3) out float output4;
layout (location = 4) out float output5;
layout (location = 5) out float output6;
void main()
{
  output1 = 1;
  output2 = 2;
  output3 = 3;
  output4 = 4;
  output5 = 5;
  output6 = 6;
}")

(facts "Using framebuffer to render to faces of cubemap"
       (with-invisible-window
         (let [tex      (make-empty-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp 1)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                      :sfsim.render/fragment [fragment-cubemap-attachment])
               vao      (make-vertex-array-object program indices vertices ["point" 3])]
           (framebuffer-render 1 1 :sfsim.render/cullback nil [tex]
                               (use-program program)
                               (render-quads vao))
           (doseq [i (range 6)]
                 (get-float (float-cubemap->floats tex i) 0 0) => (float (inc i)))
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex))))

(fact "Create cube map of 3D vectors and read them out"
      (with-invisible-window
        (let [gen-vector (fn [i] (let [i3 (* i 3)] [i3 (inc i3) (inc (inc i3))]))
              cubemap (make-vector-cubemap :sfsim.texture/linear :sfsim.texture/clamp
                                           (mapv (fn [i] #:sfsim.image{:width 1 :height 1 :data (float-array (gen-vector i))})
                                                 (range 6)))]
          (doseq [i (range 6)]
                 (get-vector3 (vector-cubemap->vectors3 cubemap i) 0 0) => (apply vec3 (gen-vector i)))
          (destroy-texture cubemap))))

(fact "Render two quads with depth testing"
  (offscreen-render 160 120
    (let [indices1  [0 1 3 2]
          vertices1 [-1.0 -1.0 0.2 1.0 0.0, 0.5 -1.0 0.2 1.0 0.0, -1.0 0.5 0.2 1.0 0.0, 0.5 0.5 0.2 1.0 0.0]
          indices2  [0 1 3 2]
          vertices2 [-0.5 -0.5 0.3 0.0 1.0, 1.0 -0.5 0.3 0.0 1.0, -0.5 1.0 0.3 0.0 1.0, 1.0 1.0 0.3 0.0 1.0]
          program   (make-program :sfsim.render/vertex [vertex-color] :sfsim.render/fragment [fragment-color])
          vao1      (make-vertex-array-object program indices1 vertices1 ["point" 3 "uv" 2])
          vao2      (make-vertex-array-object program indices2 vertices2 ["point" 3 "uv" 2])]
      (with-stencils
        (let [stb (GL30/glGenFramebuffers)]
          (clear (vec3 0.0 0.0 0.0) 1.0 0)
          (GL11/glStencilFunc GL11/GL_ALWAYS 1 0xff)
          (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_REPLACE)
          (GL11/glStencilMask 0xff)
          (use-program program)
          (render-quads vao1)
          (clear)
          (GL11/glStencilFunc GL11/GL_EQUAL 0 0xff)
          (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_REPLACE)
          (GL11/glStencilMask 0)
          (render-quads vao2)
          (destroy-vertex-array-object vao2)
          (destroy-vertex-array-object vao1)
          (destroy-program program)
          (GL30/glDeleteFramebuffers stb))))) => (is-image "test/sfsim/fixtures/render/stencil.png" 0.0))

(facts "Maximum shadow depth for cloud shadows"
       (render-depth 4.0 1.0 0.0) => 3.0
       (render-depth 3.0 2.0 0.0) => 4.0
       (render-depth 4.0 0.0 1.0) => 3.0
       (render-depth 4.0 1.0 1.0) => 6.0)

(facts "Create hashmap with render variables for rendering current frame"
       (let [planet {:sfsim.planet/radius 1000.0}
             cloud  {:sfsim.clouds/cloud-top 100.0}
             render {:sfsim.render/fov 0.5}
             pos1   (vec3 (+ 1000 150) 0 0)
             pos2   (vec3 (+ 1000 75) 0 0)
             o      (q/rotation 0.0 (vec3 0 0 1))
             light  (vec3 1 0 0)]
         (with-redefs [render/render-depth (fn [radius height cloud-top] (fact [radius cloud-top] => [1000.0 100.0]) 300.0)
                       matrix/quaternion->matrix (fn [orientation] (fact [orientation] orientation => o) :rotation-matrix)
                       matrix/transformation-matrix (fn [rot pos] (fact rot => :rotation-matrix) (eye 4))
                       matrix/projection-matrix (fn [w h near far fov] (fact [w h fov] => [640 480 0.5]) (diagonal 1 2 3 4))]
           (:sfsim.render/origin (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => pos1
           (:sfsim.render/height (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => 150.0
           (:sfsim.render/z-near (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => 50.0
           (:sfsim.render/z-near (make-render-vars planet cloud render 640 480 pos2 o light 1.0)) => 1.0
           (:sfsim.render/z-far (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => 300.0
           (:sfsim.render/extrinsics (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => (eye 4)
           (:sfsim.render/projection (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => (diagonal 1 2 3 4)
           (:sfsim.render/light-direction (make-render-vars planet cloud render 640 480 pos1 o light 1.0)) => light)))

(GLFW/glfwTerminate)
