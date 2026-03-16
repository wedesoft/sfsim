;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-render
  (:require
    [clojure.math :refer (to-radians asin sin sqrt PI)]
    [comb.template :as template]
    [fastmath.matrix :refer (eye) :as m]
    [fastmath.vector :refer (vec3 vec4 normalize)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (is-image roughly-vector roughly-matrix)]
    [sfsim.image :refer :all]
    [sfsim.matrix :refer :all :as matrix]
    [sfsim.quaternion :as q]
    [sfsim.render :refer :all :as render]
    [sfsim.shaders :as s]
    [sfsim.texture :refer :all]
    [sfsim.util :refer :all])
  (:import
    (org.lwjgl
      BufferUtils)
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.opengl
      GL11
      GL12)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(facts "Byte size of OpenGL types"
       (opengl-type-size GL11/GL_UNSIGNED_BYTE) => 1
       (opengl-type-size GL11/GL_UNSIGNED_SHORT) => 2
       (opengl-type-size GL11/GL_UNSIGNED_INT) => 4
       (opengl-type-size GL11/GL_FLOAT) => 4
       (opengl-type-size GL11/GL_DOUBLE) => 8)


(fact "Render background color"
      (offscreen-render 160 120 (clear (vec3 1.0 0.0 0.0)))
      => (is-image "test/clj/sfsim/fixtures/render/red.png" 0.0))


(def vertex-passthrough
"#version 450 core
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")


(def fragment-blue
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quad.png" 0.0))


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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quad.png" 0.0))


(def vertex-color
  "#version 450 core
in vec3 point;
in vec2 uv;
out vec3 color;
void main()
{
  gl_Position = vec4(point, 1);
  color = vec3(uv.x, 0.5, uv.y);
}")


(def fragment-color
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/colors.png" 0.0))


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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quads.png" 0.0))


(def fragment-red
  "#version 450 core
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
                          (destroy-program program1))) => (is-image "test/clj/sfsim/fixtures/render/objects.png" 0.0))


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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/lines.png" 4.3))


(def fragment-uniform-floats
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/uniform-floats.png" 0.0))


(def fragment-uniform-ints
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/uniform-ints.png" 0.0))


(def fragment-uniform-vector3
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/uniform-floats.png" 0.0))


(def vertex-transform3
  "#version 450 core
in vec3 point;
uniform mat3 world_to_camera;
void main()
{
  gl_Position = vec4(world_to_camera * point, 1);
}")


(fact "Set uniform 3x3 matrix"
      (offscreen-render 160 120
                        (let [indices  [0 1 3 2]
                              vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
                              program  (make-program :sfsim.render/vertex [vertex-transform3] :sfsim.render/fragment [fragment-blue])
                              vao      (make-vertex-array-object program indices vertices ["point" 3])]
                          (clear (vec3 0.0 0.0 0.0))
                          (use-program program)
                          (uniform-matrix3 program "world_to_camera" (eye 3))
                          (render-quads vao)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quad.png" 0.0))


(def vertex-transform4
  "#version 450 core
in vec3 point;
uniform mat4 world_to_camera;
void main()
{
  gl_Position = world_to_camera * vec4(point, 1);
}")


(fact "Set uniform 4x4 matrix"
      (offscreen-render 160 120
                        (let [indices  [0 1 3 2]
                              vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
                              program  (make-program :sfsim.render/vertex [vertex-transform4] :sfsim.render/fragment [fragment-blue])
                              vao      (make-vertex-array-object program indices vertices ["point" 3])]
                          (clear (vec3 0.0 0.0 0.0))
                          (use-program program)
                          (uniform-matrix4 program "world_to_camera" (eye 4))
                          (render-quads vao)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quad.png" 0.0))


(def vertex-texture
  "#version 450 core
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
  "#version 450 core
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
           => (is-image (str "test/clj/sfsim/fixtures/render/" ?result ".png") 0.0))
         ?interpolation ?boundary ?result
         :sfsim.texture/nearest       :sfsim.texture/clamp    "floats-1d-nearest-clamp"
         :sfsim.texture/linear        :sfsim.texture/clamp    "floats-1d-linear-clamp"
         :sfsim.texture/nearest       :sfsim.texture/repeat   "floats-1d-nearest-repeat"
         :sfsim.texture/linear        :sfsim.texture/repeat   "floats-1d-linear-repeat")


(def fragment-texture-2d
  "#version 450 core
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
                              tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp (slurp-image "test/clj/sfsim/fixtures/render/pattern.png"))]
                          (clear (vec3 0.0 0.0 0.0))
                          (use-program program)
                          (uniform-sampler program "tex" 0)
                          (use-textures {0 tex})
                          (render-quads vao)
                          (destroy-texture tex)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/texture.png" 0.07))


(def fragment-texture-array
  "#version 450 core
in vec2 uv_fragment;
out vec3 fragColor;
uniform sampler2DArray tex;
void main()
{
  fragColor = texture(tex, uv_fragment.sts).rgb;
}")


(fact "Render 2D RGB texture array"
      (offscreen-render 64 64
                        (let [indices  [0 1 3 2]
                              vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
                              program  (make-program :sfsim.render/vertex [vertex-texture] :sfsim.render/fragment [fragment-texture-array])
                              vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])
                              tex      (make-rgb-texture-array :sfsim.texture/linear :sfsim.texture/clamp
                                                               [(slurp-image "test/clj/sfsim/fixtures/render/red.png")
                                                                (slurp-image "test/clj/sfsim/fixtures/render/green.png")])]
                          (clear (vec3 0.0 0.0 0.0))
                          (use-program program)
                          (uniform-sampler program "tex" 0)
                          (use-textures {0 tex})
                          (render-quads vao)
                          (destroy-texture tex)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/texture-array.png" 0.0))


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
           => (is-image (str "test/clj/sfsim/fixtures/render/" ?result ".png") 0.05))
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/ubytes.png" 0.02))


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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/vectors.png" 0.01))


(def vertex-interpolate
  "#version 450 core
in vec3 point;
out vec3 pos;
void main()
{
  gl_Position = vec4(point, 1);
  pos = vec3(0.5 + 0.5 * point.xy, point.z);
}")


(def fragment-sample-shadow
  "#version 450 core
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
      => (is-image "test/clj/sfsim/fixtures/render/shadow-sample.png" 0.0))


(def fragment-texture-3d
  "#version 450 core
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
           => (is-image (str "test/clj/sfsim/fixtures/render/" ?result ".png") 0.0))
         ?interpolation ?boundary ?result
         :sfsim.texture/nearest       :sfsim.texture/clamp    "floats-3d-nearest-clamp"
         :sfsim.texture/linear        :sfsim.texture/clamp    "floats-3d-linear-clamp"
         :sfsim.texture/nearest       :sfsim.texture/repeat   "floats-3d-nearest-repeat"
         :sfsim.texture/linear        :sfsim.texture/repeat   "floats-3d-linear-repeat")


(def fragment-two-textures
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/two-textures.png" 0.0))


(def control-uniform
  "#version 450 core
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
  "#version 450 core
layout(quads, equal_spacing, ccw) in;
void main()
{
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  gl_Position = mix(a, b, gl_TessCoord.y);
}")


(def geometry-triangle
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/tessellation.png" 28.9))


(def fragment-part1
  "#version 450 core
vec3 fun()
{
  return vec3(0.0, 0.0, 1.0);
}
")


(def fragment-part2
  "#version 450 core
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
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/quad.png" 0.0))


(fact "Render to floating-point texture (needs active OpenGL context)"
      (with-invisible-window
        (let [tex (texture-render-color 8 8 true (clear (vec3 1.0 2.0 3.0)))]
          (get-vector3 (rgb-texture->vectors3 tex) 0 0) => (vec3 1.0 2.0 3.0)
          (destroy-texture tex))))


(fact "Render to image texture (needs active OpenGL context)"
      (with-invisible-window
        (let [tex (texture-render-color 160 120 false (clear (vec3 1.0 0.0 0.0)))
              img (texture->image tex)]
          img => (is-image "test/clj/sfsim/fixtures/render/red.png" 0.0)
          (destroy-texture tex))))


(def fragment-two-attachments
  "#version 450 core
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
  "#version 450 core
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
  "#version 450 core
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
                               (destroy-program program))) => (is-image (str "test/clj/sfsim/fixtures/render/" ?result ".png") 0.0))
         ?lod ?result
         0.0  "lod-1d-0"
         1.0  "lod-1d-1"
         2.0  "lod-1d-2")


(def alpha-probe
  (template/fn [alpha]
    "#version 450 core
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
  "#version 450 core
uniform mat4 world_to_shadow_ndc;
in vec3 point;
vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);
void main(void)
{
  gl_Position = shrink_shadow_index(world_to_shadow_ndc * vec4(point, 1), 128, 128);
}")


(def fragment-shadow
  "#version 450 core
void main(void)
{
}")


(def vertex-scene
  "#version 450 core
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
  "#version 450 core
uniform sampler2DShadow shadow_map;
uniform mat4 world_to_shadow_map;
in vec4 pos;
in float ambient;
out vec4 fragColor;
float shadow_lookup(sampler2DShadow shadow_map, vec4 shadow_pos);
void main(void)
{
  vec4 shadow_pos = world_to_shadow_map * pos;
  float shade = shadow_lookup(shadow_map, shadow_pos);
  float brightness = 0.7 * shade + 0.1 * ambient + 0.1;
  fragColor = vec4(brightness, brightness, brightness, 1.0);
}")


(fact "Shadow mapping integration test"
      (with-invisible-window
        (let [projection      (projection-matrix 320 240 2.0 5.0 (to-radians 90))
              camera-to-world (eye 4)
              light-direction (normalize (vec3 1 1 2))
              shadow-mat      (shadow-matrices projection camera-to-world light-direction 1.0)
              indices         [0 1 3 2 6 7 5 4 8 9 11 10]
              vertices        [-2.0 -2.0 -4.0, 2.0 -2.0 -4.0, -2.0 2.0 -4.0, 2.0 2.0 -4.0,
                               -1.0 -1.0 -3.0, 1.0 -1.0 -3.0, -1.0 1.0 -3.0, 1.0 1.0 -3.0
                               -1.0 -1.0 -2.9, 1.0 -1.0 -2.9, -1.0 1.0 -2.9, 1.0 1.0 -2.9]
              program-shadow  (make-program :sfsim.render/vertex [vertex-shadow s/shrink-shadow-index]
                                            :sfsim.render/fragment [fragment-shadow])
              program-main    (make-program :sfsim.render/vertex [vertex-scene]
                                            :sfsim.render/fragment [fragment-scene
                                                                    (s/shadow-lookup "shadow_lookup" "shadow_size")])
              vao             (make-vertex-array-object program-main indices vertices ["point" 3])
              shadow-map      (texture-render-depth
                                128 128
                                (clear)
                                (use-program program-shadow)
                                (uniform-matrix4 program-shadow "world_to_shadow_ndc" (:sfsim.matrix/world-to-shadow-ndc shadow-mat))
                                (render-quads vao))
              depth           (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 320 240)
              tex             (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGBA8 320 240)]
          (framebuffer-render 320 240 :sfsim.render/cullback depth [tex]
                              (clear (vec3 0 0 0))
                              (use-program program-main)
                              (uniform-sampler program-main "shadow_map" 0)
                              (uniform-int program-main "shadow_size" 128)
                              (uniform-matrix4 program-main "projection" projection)
                              (uniform-matrix4 program-main "world_to_shadow_map" (:sfsim.matrix/world-to-shadow-map shadow-mat))
                              (use-textures {0 shadow-map})
                              (render-quads vao))
          (let [img (texture->image tex)]
            (destroy-texture shadow-map)
            (destroy-vertex-array-object vao)
            (destroy-program program-main)
            (destroy-program program-shadow)
            img)))
      => (is-image "test/clj/sfsim/fixtures/render/shadow.png" 0.20))


(def fragment-scene-cascade
  "#version 450 core
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
        (let [projection      (projection-matrix 320 240 2.0 5.0 (to-radians 90))
              camera-to-world (eye 4)
              num-steps       1
              light-direction (normalize (vec3 1 1 2))
              shadow-data     #:sfsim.opacity{:num-steps num-steps :mix 0.5 :depth 1.0}
              render-vars     #:sfsim.render{:projection projection :camera-to-world camera-to-world
                                             :light-direction light-direction :z-near 2.0 :z-far 5.0}
              shadow-mats     (shadow-matrix-cascade shadow-data render-vars)
              indices         [0 1 3 2 6 7 5 4 8 9 11 10]
              vertices        [-2.0 -2.0 -4.0, 2.0 -2.0 -4.0, -2.0 2.0 -4.0, 2.0 2.0 -4.0,
                               -1.0 -1.0 -3.0, 1.0 -1.0 -3.0, -1.0 1.0 -3.0, 1.0 1.0 -3.0
                               -1.0 -1.0 -2.9, 1.0 -1.0 -2.9, -1.0 1.0 -2.9, 1.0 1.0 -2.9]
              program-shadow  (make-program :sfsim.render/vertex [vertex-shadow s/shrink-shadow-index]
                                            :sfsim.render/fragment [fragment-shadow])
              program-main    (make-program :sfsim.render/vertex [vertex-scene]
                                            :sfsim.render/fragment [fragment-scene-cascade
                                                                    (s/shadow-cascade-lookup num-steps "shadow_lookup")
                                                                    (s/shadow-lookup "shadow_lookup" "shadow_size")])
              vao             (make-vertex-array-object program-main indices vertices ["point" 3])
              shadow-maps     (shadow-cascade 128 shadow-mats program-shadow
                                              (fn [world-to-shadow-ndc]
                                                  (uniform-matrix4 program-shadow "world_to_shadow_ndc" world-to-shadow-ndc)
                                                  (render-quads vao)))
              depth           (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 320 240)
              tex             (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGBA8 320 240)]
          (framebuffer-render 320 240 :sfsim.render/cullback depth [tex]
                              (clear (vec3 0 0 0))
                              (use-program program-main)
                              (uniform-sampler program-main "shadow_map0" 0)
                              (uniform-float program-main "split0" 0.0)
                              (uniform-float program-main "split1" 50.0)
                              (uniform-int program-main "shadow_size" 128)
                              (uniform-matrix4 program-main "projection" projection)
                              (uniform-matrix4 program-main "world_to_camera" (eye 4))
                              (uniform-matrix4 program-main "world_to_shadow_map0"
                                               (:sfsim.matrix/world-to-shadow-map (shadow-mats 0)))
                              (use-textures (zipmap (range) shadow-maps))
                              (render-quads vao))
          (let [img (texture->image tex)]
            (doseq [shadow-map shadow-maps]
                   (destroy-texture shadow-map))
            (destroy-vertex-array-object vao)
            (destroy-program program-main)
            (destroy-program program-shadow)
            img)))
      => (is-image "test/clj/sfsim/fixtures/render/shadow.png" 0.20))


(def fragment-cubemap-attachment
  "#version 450 core
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


(tabular "Render two quads with positive or negative stencil test"
         (fact
           (offscreen-render
             160 120
             (let [indices1  [0 1 3 2]
                   vertices1 [-1.0 -1.0 0.2 1.0 0.0, 0.5 -1.0 0.2 1.0 0.0, -1.0 0.5 0.2 1.0 0.0, 0.5 0.5 0.2 1.0 0.0]
                   indices2  [0 1 3 2]
                   vertices2 [-0.5 -0.5 0.3 0.0 1.0, 1.0 -0.5 0.3 0.0 1.0, -0.5 1.0 0.3 0.0 1.0, 1.0 1.0 0.3 0.0 1.0]
                   program   (make-program :sfsim.render/vertex [vertex-color] :sfsim.render/fragment [fragment-color])
                   vao1      (make-vertex-array-object program indices1 vertices1 ["point" 3 "uv" 2])
                   vao2      (make-vertex-array-object program indices2 vertices2 ["point" 3 "uv" 2])]
               (with-stencils
                 (clear (vec3 0.0 0.0 0.0) 1.0 0x1)
                 (use-program program)
                 (with-stencil-op-ref-and-mask GL11/GL_ALWAYS 0x4 0x4
                   (render-quads vao1))  ; render red quad
                 (clear)
                 (with-stencil-op-ref-and-mask ?operation ?reference ?mask
                   (render-quads vao2))  ; render blue quad
                 (destroy-vertex-array-object vao2)
                 (destroy-vertex-array-object vao1)
                 (destroy-program program)))) => (is-image (str "test/clj/sfsim/fixtures/render/" ?image) 0.0))
         ?reference ?operation       ?mask ?image
         0x4        GL11/GL_EQUAL    0x4   "render-inside-red-quad.png"
         0x2        GL11/GL_GREATER  0x6   "render-outside-red-quad.png")


(fact "Render a quad with scissor test"
      (offscreen-render 160 120
                        (let [indices  [0 1 3 2]
                              vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                              program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-blue])
                              vao      (make-vertex-array-object program indices vertices ["point" 3])]
                          (clear (vec3 0.0 0.0 0.0))
                          (with-scissor
                            (use-program program)
                            (set-scissor 20 10 100 90)
                            (render-quads vao))
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/render/scissor.png" 0.0))


(facts "Diagonal field of view"
       (diagonal-field-of-view 320   0 (* 0.25 PI)) => (roughly (* 0.25 PI) 1e-6)
       (diagonal-field-of-view 320 320 (* 0.25 PI)) => (roughly (* 2 (asin (* (sqrt 2.0) (sin (* 0.125 PI))))) 1e-6)
       (diagonal-field-of-view 320 240 (* 0.25 PI)) => (roughly 0.997559 1e-6))


(facts "Join z-ranges and projection matrices of two render variable hashmaps"
       (let [render-config #:sfsim.render{:fov (to-radians 60.0) :cloud-subsampling 2}
             origin        (vec3 1 2 3)
             object-origin (vec3 4 0 0)
             orientation   (q/->Quaternion 1 0 0 0)
             light-dir     (vec3 0 0 1)
             planet-vars   (make-render-vars render-config 320 240 origin orientation light-dir object-origin orientation
                                             1000.0 10000.0 0.0 1.0)
             scene-vars    (make-render-vars render-config 320 240 origin orientation light-dir object-origin orientation
                                             10.0 100.0 0.0 1.0)
             joined-vars   (joined-render-vars planet-vars scene-vars)]
         (:sfsim.render/origin joined-vars) => origin
         (:sfsim.render/object-origin planet-vars) => (vec3 -3 2 3)
         (:sfsim.render/window-width joined-vars) => 320
         (:sfsim.render/window-height joined-vars) => 240
         (:sfsim.render/overlay-width joined-vars) => 160
         (:sfsim.render/overlay-height joined-vars) => 120
         (:sfsim.render/projection joined-vars) => (roughly-matrix (projection-matrix 320 240 10.0 10001.0 (to-radians 60)) 1e-6)
         (:sfsim.render/overlay-projection joined-vars) => (roughly-matrix (projection-matrix 160 120 10.0 10001.0 (to-radians 60)) 1e-6)
         (:sfsim.render/origin joined-vars) => origin
         (:sfsim.render/z-near joined-vars) => 10.0
         (:sfsim.render/z-far joined-vars) => 10000.0
         (:sfsim.render/light-direction joined-vars) => light-dir
         (:sfsim.render/camera-to-world joined-vars) => (:sfsim.render/camera-to-world planet-vars)))


(fact "Function to determine quad tessellation orientation of diagonal split"
      (with-invisible-window
        (let [orientations (quad-splits-orientations 65 8)]
          (count orientations) => 64
          (count (first orientations)) => 64)))


(def fragment-uniform-color
"#version 450 core
uniform vec4 color;
out vec4 fragColor;
void main()
{
  fragColor = color;
}")


(tabular "Overlay blending test"
         (fact
           (with-invisible-window
             (let [indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                   program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                          :sfsim.render/fragment [fragment-uniform-color])
                   vao      (make-vertex-array-object program indices vertices ["point" 3])
                   output   (texture-render-color 1 1 true
                                                  (clear (vec3 0.0 0.0 0.0))
                                                  (use-program program)
                                                  (uniform-vector4 program "color" ?background)
                                                  (render-quads vao)
                                                  (with-overlay-blending
                                                    (uniform-vector4 program "color" ?overlay)
                                                    (render-quads vao)))
                   result   (get-vector4 (rgba-texture->vectors4 output) 0 0)]
               (destroy-vertex-array-object vao)
               (destroy-program program)
               result)) => (roughly-vector ?result 1e-3))
         ?background              ?overlay                  ?result
         (vec4 0.25 0.5 0.75 0.5) (vec4 1.0  1.0 1.0  0.0 ) (vec4 0.25 0.5  0.75 0.5 )
         (vec4 1.0  1.0 1.0  0.5) (vec4 0.25 0.5 0.75 1.0 ) (vec4 0.25 0.5  0.75 1.0 )
         (vec4 1.0  0.0 0.0  1.0) (vec4 0.0  1.0 0.0  0.25) (vec4 0.75 0.25 0.0  1.0 )
         (vec4 1.0  0.0 0.0  1.0) (vec4 0.0  1.0 0.0  0.25) (vec4 0.75 0.25 0.0  1.0 )
         (vec4 1.0  0.0 0.0  0.2) (vec4 0.0  1.0 0.0  0.4 ) (vec4 0.6  0.4  0.0  0.52))


(tabular "Underlay blending test"
         (fact
           (with-invisible-window
             (let [indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                   program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                          :sfsim.render/fragment [fragment-uniform-color])
                   vao      (make-vertex-array-object program indices vertices ["point" 3])
                   output   (texture-render-color 1 1 true
                                                  (clear (vec3 0.0 0.0 0.0))
                                                  (use-program program)
                                                  (uniform-vector4 program "color" ?foreground)
                                                  (render-quads vao)
                                                  (with-underlay-blending
                                                    (uniform-vector4 program "color" ?underlay)
                                                    (render-quads vao)))
                   result   (get-vector4 (rgba-texture->vectors4 output) 0 0)]
               (destroy-vertex-array-object vao)
               (destroy-program program)
               result)) => (roughly-vector ?result 1e-3))
         ?foreground               ?underlay                ?result
         (vec4 0.25 0.5 0.75 1.0 ) (vec4 1.0  1.0 1.0  1.0) (vec4 0.25 0.5  0.75 1.0 )
         (vec4 0.0  0.0 0.0  0.0 ) (vec4 0.25 0.5 0.75 1.0) (vec4 0.25 0.5  0.75 1.0 )
         (vec4 1.0  0.0 0.0  0.75) (vec4 0.0  1.0 0.0  1.0) (vec4 1.0  0.25 0.0  1.0 )
         (vec4 1.0  0.0 0.0  0.25) (vec4 0.0  1.0 0.0  1.0) (vec4 1.0  0.75 0.0  1.0 )
         (vec4 1.0  0.0 0.0  0.4 ) (vec4 0.0  1.0 0.0  0.2) (vec4 1.0  0.6  0.0  0.52))


(GLFW/glfwTerminate)
