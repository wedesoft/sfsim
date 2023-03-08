(ns sfsim25.t-render
  (:require [midje.sweet :refer :all]
            [sfsim25.conftest :refer (is-image record-image roughly-matrix)]
            [clojure.core.matrix :refer (matrix identity-matrix)]
            [clojure.math :refer (to-radians)]
            [comb.template :as template]
            [sfsim25.util :refer :all]
            [sfsim25.matrix :refer :all]
            [sfsim25.shaders :as s]
            [sfsim25.render :refer :all])
  (:import [org.lwjgl.opengl Display DisplayMode GL11 GL12 GL30 GL42]
           [org.lwjgl BufferUtils]))

(fact "Render background color"
  (offscreen-render 160 120 (clear (matrix [1.0 0.0 0.0]))) => (is-image "test/sfsim25/fixtures/render/red.png"))

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
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

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
          program  (make-program :vertex [vertex-color] :fragment [fragment-color])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/colors.png"))

(fact "Render two quads with depth testing"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2, 4 5 7 6]
          vertices [-1.0 -1.0 0.2 1.0 0.0, 0.5 -1.0 0.2 1.0 0.0, -1.0 0.5 0.2 1.0 0.0, 0.5 0.5 0.2 1.0 0.0,
                    -0.5 -0.5 0.1 0.0 1.0, 1.0 -0.5 0.1 0.0 1.0, -0.5 1.0 0.1 0.0 1.0, 1.0 1.0 0.1 0.0 1.0]
          program  (make-program :vertex [vertex-color] :fragment [fragment-color])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quads.png"))

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
          program1  (make-program :vertex [vertex-passthrough] :fragment [fragment-red])
          program2  (make-program :vertex [vertex-passthrough] :fragment [fragment-blue])
          vao1      (make-vertex-array-object program1 indices vertices1 [:point 3])
          vao2      (make-vertex-array-object program2 indices vertices2 [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program1)
      (render-quads vao1)
      (use-program program2)
      (render-quads vao2)
      (destroy-vertex-array-object vao2)
      (destroy-vertex-array-object vao1)
      (destroy-program program2)
      (destroy-program program1))) => (is-image "test/sfsim25/fixtures/render/objects.png"))

(fact "Render lines only"
  (offscreen-render 160 120
    (let [indices  [0 1 3 2]
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (raster-lines (render-quads vao))
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/lines.png"))

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
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-uniform-floats])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-float program :red 1.0)
      (uniform-float program :green 0.5)
      (uniform-float program :blue 0.0)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/uniform-floats.png"))

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
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-uniform-ints])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-int program :red 255)
      (uniform-int program :green 128)
      (uniform-int program :blue 0)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/uniform-ints.png"))

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
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-uniform-vector3])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-vector3 program :color (matrix [1.0 0.5 0.0]))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/uniform-floats.png"))

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
          program  (make-program :vertex [vertex-transform3] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-matrix3 program :transform (identity-matrix 3))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

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
          program  (make-program :vertex [vertex-transform4] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-matrix4 program :transform (identity-matrix 4))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

(fact "Size of 1D texture"
      (offscreen-render 64 64
                        (let [tex (make-float-texture-1d :linear :clamp (float-array [0 1 0 1]))]
                          (:width tex) => 4
                          (destroy-texture tex))))

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
                            program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-1d])
                            vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
                            tex      (make-float-texture-1d ?interpolation ?boundary (float-array [0.0 1.0]))]
                        (clear (matrix [0.0 0.0 0.0]))
                        (use-program program)
                        (uniform-sampler program :tex 0)
                        (use-textures tex)
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/render/" ?result ".png")))
  ?interpolation ?boundary ?result
  :nearest       :clamp    "floats-1d-nearest-clamp"
  :linear        :clamp    "floats-1d-linear-clamp"
  :nearest       :repeat   "floats-1d-nearest-repeat"
  :linear        :repeat   "floats-1d-linear-repeat")

(fact "Size of 2D RGB texture"
      (offscreen-render 64 64
                        (let [tex (make-rgb-texture :linear :clamp (slurp-image "test/sfsim25/fixtures/render/pattern.png"))]
                          (:width tex) => 2
                          (:height tex) => 2
                          (destroy-texture tex))))

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
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-rgb-texture :linear :clamp (slurp-image "test/sfsim25/fixtures/render/pattern.png"))]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/texture.png"))

(tabular "Render 2D floating-point texture"
  (fact
    (offscreen-render 64 64
                      (let [indices  [0 1 3 2]
                            vertices [-1.0 -1.0 0.5 -1.0 -1.0, 1.0 -1.0 0.5 2.0 -1.0, -1.0 1.0 0.5 -1.0 2.0, 1.0 1.0 0.5 2.0 2.0]
                            program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
                            vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
                            img      {:width 2 :height 2 :data (float-array [0.0 0.25 0.5 1.0])}
                            tex      (make-float-texture-2d ?interpolation ?boundary img)]
                        (clear (matrix [0.0 0.0 0.0]))
                        (use-program program)
                        (uniform-sampler program :tex 0)
                        (use-textures tex)
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/render/" ?result ".png")))
  ?interpolation ?boundary ?result
  :nearest       :clamp    "floats-2d-nearest-clamp"
  :linear        :clamp    "floats-2d-linear-clamp"
  :nearest       :repeat   "floats-2d-nearest-repeat"
  :linear        :repeat   "floats-2d-linear-repeat")

(fact "Render 2D unsigned-byte texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-ubyte-texture-2d :linear :clamp {:width 2 :height 2 :data (byte-array [0 64 0 0 127 255 0 0])})]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/ubytes.png"))

(fact "Render 2D vector texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-vector-texture-2d :linear :clamp {:width 2 :height 2 :data (float-array [0 0 0 0 0 1 0 1 0 1 1 1])})]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/vectors.png"))

(fact "Size of 2D depth texture"
      (offscreen-render 64 64
                        (let [tex (make-depth-texture :linear :clamp {:width 2 :height 1 :data (float-array [0 0])})]
                          (:width tex) => 2
                          (:height tex) => 1
                          (destroy-texture tex))))

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
                          program  (make-program :vertex [vertex-interpolate] :fragment [fragment-sample-shadow])
                          vao      (make-vertex-array-object program indices vertices [:point 3])
                          data     [0.4 0.4 0.4 0.4, 0.4 0.6 0.6 0.4, 0.4 0.6 0.6 0.4, 0.4 0.4 0.4 0.4]
                          depth    (make-depth-texture :linear :clamp {:width 4 :height 4 :data (float-array data)})]
                      (clear (matrix [1.0 0.0 0.0]))
                      (use-program program)
                      (uniform-sampler program :shadow_map 0)
                      (use-textures depth)
                      (render-quads vao)
                      (destroy-texture depth)
                      (destroy-vertex-array-object vao)
                      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/shadow-sample.png"))

(fact "Size of 3D texture"
      (offscreen-render 64 64
                        (let [tex (make-float-texture-3d :linear :clamp
                                                         {:width 3 :height 2 :depth 1 :data (float-array (repeat 6 0))})]
                          (:width tex) => 3
                          (:height tex) => 2
                          (:depth tex) => 1
                          (destroy-texture tex))))

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
                            program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-3d])
                            vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
                            data     [0 0.125 0.25 0.375 0.5 0.625 0.75 0.875]
                            tex      (make-float-texture-3d ?interpolation ?boundary
                                                            {:width 2 :height 2 :depth 2 :data (float-array data)})]
                        (clear (matrix [0.0 0.0 0.0]))
                        (use-program program)
                        (uniform-sampler program :tex 0)
                        (use-textures tex)
                        (render-quads vao)
                        (destroy-texture tex)
                        (destroy-vertex-array-object vao)
                        (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/render/" ?result ".png")))
  ?interpolation ?boundary ?result
  :nearest       :clamp    "floats-3d-nearest-clamp"
  :linear        :clamp    "floats-3d-linear-clamp"
  :nearest       :repeat   "floats-3d-nearest-repeat"
  :linear        :repeat   "floats-3d-linear-repeat")

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

(fact "Test use of two textures"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-two-textures])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex1     (make-vector-texture-2d :linear :clamp {:width 2 :height 2 :data (float-array [0 0 0 0 0 0 0 0 0 0 0 0])})
          tex2     (make-vector-texture-2d :linear :clamp {:width 2 :height 2 :data (float-array [1 1 1 1 1 1 1 1 1 1 1 1])})]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex1 0)
      (uniform-sampler program :tex2 1)
      (use-textures tex1 tex2)
      (render-quads vao)
      (destroy-texture tex2)
      (destroy-texture tex1)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/two-textures.png"))

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
          program  (make-program :vertex [vertex-passthrough] :tess-control [control-uniform] :tess-evaluation [evaluation-mix]
                                 :geometry [geometry-triangle] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (raster-lines (render-patches vao))
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/tessellation.png"))

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
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-part1 fragment-part2])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

(fact "Render to floating-point texture (needs active OpenGL context)"
      (offscreen-render 32 32
        (let [tex (texture-render-color 8 8 true (clear (matrix [1.0 2.0 3.0])))]
          (get-vector3 (rgb-texture->vectors3 tex) 0 0) => (matrix [1.0 2.0 3.0])
          (destroy-texture tex))))

(fact "Render to image texture (needs active OpenGL context)"
      (offscreen-render 32 32
        (let [tex (texture-render-color 160 120 false (clear (matrix [1.0 0.0 0.0])))
              img (texture->image tex)]
          img => (is-image "test/sfsim25/fixtures/render/red.png")
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
       (offscreen-render 32 32
         (let [tex1     (make-empty-float-texture-2d :linear :clamp 1 1)
               tex2     (make-empty-float-texture-2d :linear :clamp 1 1)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :vertex [vertex-passthrough] :fragment [fragment-two-attachments])
               vao      (make-vertex-array-object program indices vertices [:point 3])]
           (framebuffer-render 1 1 :cullback nil [tex1 tex2]
                               (use-program program)
                               (render-quads vao))
           (get-float (float-texture-2d->floats tex1) 0 0) => 0.25
           (get-float (float-texture-2d->floats tex2) 0 0) => 0.75
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex1)
           (destroy-texture tex2))))

(facts "Using framebuffer to render to layers of 3D texture"
       (offscreen-render 32 32
         (let [tex      (make-empty-float-texture-3d :linear :clamp 1 1 2)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :vertex [vertex-passthrough] :fragment [fragment-two-attachments])
               vao      (make-vertex-array-object program indices vertices [:point 3])]
           (framebuffer-render 1 1 :cullback nil [tex]
                               (use-program program)
                               (render-quads vao))
           (with-texture (:target tex) (:texture tex)
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
       (offscreen-render 32 32
         (let [depth    (make-empty-depth-texture-2d :linear :clamp 1 1)
               indices  [2 3 1 0]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :vertex [vertex-passthrough] :fragment [fragment-noop])
               vao      (make-vertex-array-object program indices vertices [:point 3])]
           (framebuffer-render 1 1 :cullfront depth []
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
                   program  (make-program :vertex [vertex-texture] :fragment [lod-texture-1d])
                   vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
                   data     (flatten (repeat 8 [0 0 1 1]))
                   tex      (make-float-texture-1d :linear :clamp (float-array data))]
               (generate-mipmap tex)
               (clear (matrix [0.0 0.0 0.0]))
               (use-program program)
               (uniform-sampler program :tex 0)
               (uniform-float program :lod ?lod)
               (use-textures tex)
               (render-quads vao)
               (destroy-texture tex)
               (destroy-vertex-array-object vao)
               (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/render/" ?result ".png")))
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
           (let [result (promise)]
             (offscreen-render 1 1
               (let [indices  [0 1 3 2]
                     vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                     program  (make-program :vertex [vertex-passthrough] :fragment [(alpha-probe ?alpha)])
                     vao      (make-vertex-array-object program indices vertices [:point 3])
                     tex      (texture-render-color 1 1 true (use-program program) (render-quads vao))
                     img      (rgba-texture->vectors4 tex)]
                 (deliver result (get-vector4 img 0 0))
                 (destroy-texture tex)
                 (destroy-vertex-array-object vao)
                 (destroy-program program)))
             @result) => (roughly-matrix (matrix [?alpha 0.25 0.5 0.75]) 1e-6))
         ?alpha
         1.0
         0.0)

(fact "Render empty depth map"
      (offscreen-render 32 32
        (let [tex (texture-render-depth 10 10 (clear))]
          (get-float (depth-texture->floats tex) 0 0) => 0.0
          (destroy-texture tex))))

(tabular "Render back face of quad into shadow map"
         (offscreen-render 32 32
           (let [indices  (reverse [0 1 3 2])
                 vertices [-1.0 -1.0 ?z, 1.0 -1.0 ?z, -1.0 1.0 ?z, 1.0 1.0 ?z]
                 program  (make-program :vertex [vertex-passthrough] :fragment [fragment-noop])
                 vao      (make-vertex-array-object program indices vertices [:point 3])
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
uniform mat4 shadow_ndc_matrix;
in vec3 point;
vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);
void main(void)
{
  gl_Position = shrink_shadow_index(shadow_ndc_matrix * vec4(point, 1), 256, 256);
}
")

(def fragment-shadow
"#version 410 core
void main(void)
{
}")

(def vertex-scene
"#version 410 core
uniform mat4 projection;
uniform mat4 shadow_map_matrix;
in vec3 point;
out vec4 shadow_pos;
out float ambient;
void main(void)
{
  gl_Position = projection * vec4(point, 1);
  shadow_pos = shadow_map_matrix * vec4(point, 1);
  ambient = -point.z - 3;
}")

(def fragment-scene
"#version 410 core
uniform sampler2DShadow shadow_map;
in vec4 shadow_pos;
in float ambient;
out vec3 fragColor;
vec4 convert_shadow_index(vec4 idx, int size_y, int size_x);
void main(void)
{
  float shade = textureProj(shadow_map, convert_shadow_index(shadow_pos, 256, 256));
  float brightness = 0.7 * shade + 0.1 * ambient + 0.1;
  fragColor = vec3(brightness, brightness, brightness);
}")

(fact "Shadow mapping integration test"
      (offscreen-render 320 240
        (let [projection (projection-matrix 320 240 2 5 (to-radians 90))
              transform (identity-matrix 4)
              light-vector (normalize (matrix [1 1 2]))
              shadow (shadow-matrices projection transform light-vector 1)
              indices [0 1 3 2 6 7 5 4 8 9 11 10]
              vertices [-2 -2 -4  , 2 -2 -4  , -2 2 -4  , 2 2 -4,
                        -1 -1 -3  , 1 -1 -3  , -1 1 -3  , 1 1 -3
                        -1 -1 -2.9, 1 -1 -2.9, -1 1 -2.9, 1 1 -2.9]
              program-shadow (make-program :vertex [vertex-shadow s/shrink-shadow-index] :fragment [fragment-shadow])
              program-main (make-program :vertex [vertex-scene] :fragment [fragment-scene s/convert-shadow-index])
              vao (make-vertex-array-object program-main indices vertices [:point 3])
              shadow-map (texture-render-depth
                           256 256
                           (clear)
                           (use-program program-shadow)
                           (uniform-matrix4 program-shadow :shadow_ndc_matrix (:shadow-ndc-matrix shadow))
                           (render-quads vao))]
          (setup-rendering 320 240 :cullback); Need to set it up again because texture-render-depth has overriden the settings
          (clear (matrix [0 0 0]))
          (use-program program-main)
          (uniform-sampler program-main :shadow_map 0)
          (uniform-matrix4 program-main :projection projection)
          (uniform-matrix4 program-main :shadow_map_matrix (:shadow-map-matrix shadow))
          (use-textures shadow-map)
          (render-quads vao)
          (destroy-texture shadow-map)
          (destroy-vertex-array-object vao)
          (destroy-program program-main)
          (destroy-program program-shadow)))
      => (is-image "test/sfsim25/fixtures/render/shadow.png"))

(fact "Create floating-point cube map and read them out"
      (offscreen-render 16 16
        (let [cubemap (make-float-cubemap :linear :clamp
                                          (mapv (fn [i] {:width 1 :height 1 :data (float-array [(inc i)])}) (range 6)))]
          (doseq [i (range 6)]
                 (get-float (float-cubemap->floats cubemap i) 0 0) => (float (inc i)))
          (destroy-texture cubemap))))

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
       (offscreen-render 32 32
         (let [tex      (make-empty-float-cubemap :linear :clamp 1)
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
               program  (make-program :vertex [vertex-passthrough] :fragment [fragment-cubemap-attachment])
               vao      (make-vertex-array-object program indices vertices [:point 3])]
           (framebuffer-render 1 1 :cullback nil [tex]
                               (use-program program)
                               (render-quads vao))
           (doseq [i (range 6)]
                 (get-float (float-cubemap->floats tex i) 0 0) => (float (inc i)))
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex))))

(fact "Create cube map of 3D vectors and read them out"
      (offscreen-render 16 16
        (let [gen-vector (fn [i] (let [i3 (* i 3)] [i3 (inc i3) (inc (inc i3))]))
              cubemap (make-vector-cubemap :linear :clamp
                                           (mapv (fn [i] {:width 1 :height 1 :data (float-array (reverse (gen-vector i)))})
                                                 (range 6)))]
          (doseq [i (range 6)]
                 (get-vector3 (vector-cubemap->vectors3 cubemap i) 0 0) => (matrix (gen-vector i)))
          (destroy-texture cubemap))))
