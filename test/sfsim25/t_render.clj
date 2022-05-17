(ns sfsim25.t-render
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix identity-matrix)]
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

; Use this test function to record the image the first time.
(defn record-image [filename]
  (fn [other]
    (spit-image filename other)))

(fact "Render background color"
  (offscreen-render 160 120 (clear (matrix [1.0 0.0 0.0]))) => (is-image "test/sfsim25/fixtures/render/red.png"))

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
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

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

(def fragment-uniform-ints "#version 410 core
out lowp vec3 fragColor;
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
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :vertex [vertex-passthrough] :fragment [fragment-uniform-vector3])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-vector3 program :color (matrix [1.0 0.5 0.0]))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/uniform-floats.png"))

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
          vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5]
          program  (make-program :vertex [vertex-transform] :fragment [fragment-blue])
          vao      (make-vertex-array-object program indices vertices [:point 3])]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-matrix4 program :transform (identity-matrix 4))
      (render-quads vao)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/quad.png"))

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

(def fragment-texture-1d "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
uniform sampler1D tex;
void main()
{
  fragColor = texture(tex, uv_fragment.x).rgb;
}")

(fact "Render 1D floating-point texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-1d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-float-texture-1d (float-array [0.0 1.0]))]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/floats-1d.png"))

(def fragment-texture-2d "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
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
          tex      (make-rgb-texture (slurp-image "test/sfsim25/fixtures/render/pattern.png"))]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/texture.png"))

(fact "Render 2D floating-point texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-float-texture-2d {:width 2 :height 2 :data (float-array [0.0 0.25 0.5 1.0])})]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/floats.png"))

(fact "Render 2D unsigned-byte texture"
  (offscreen-render 64 64
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
          program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-2d])
          vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
          tex      (make-ubyte-texture-2d {:width 2 :height 2 :data (byte-array [0 64 0 0 127 255 0 0])})]
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
          tex      (make-vector-texture-2d {:width 2 :height 2 :data (float-array [0 0 0 0 0 1 0 1 0 1 1 1])})]
      (clear (matrix [0.0 0.0 0.0]))
      (use-program program)
      (uniform-sampler program :tex 0)
      (use-textures tex)
      (render-quads vao)
      (destroy-texture tex)
      (destroy-vertex-array-object vao)
      (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/vectors.png"))

(def fragment-texture-3d "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
uniform sampler3D tex;
void main()
{
  fragColor = texture(tex, vec3(uv_fragment, 1.0)).rgb;
}")

(fact "Render 3D floating-point texture"
      (offscreen-render 64 64
        (let [indices  [0 1 3 2]
              vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
              program  (make-program :vertex [vertex-texture] :fragment [fragment-texture-3d])
              vao      (make-vertex-array-object program indices vertices [:point 3 :uv 2])
              data     [0 0.125 0.25 0.375 0.5 0.625 0.75 0.875]
              tex      (make-float-texture-3d {:width 2 :height 2 :depth 2 :data (float-array data)})]
          (clear (matrix [0.0 0.0 0.0]))
          (use-program program)
          (uniform-sampler program :tex 0)
          (use-textures tex)
          (render-quads vao)
          (destroy-texture tex)
          (destroy-vertex-array-object vao)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/render/slice.png"))

(def fragment-two-textures "#version 410 core
in mediump vec2 uv_fragment;
out lowp vec3 fragColor;
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
          tex1     (make-vector-texture-2d {:width 2 :height 2 :data (float-array [0 0 0 0 0 0 0 0 0 0 0 0])})
          tex2     (make-vector-texture-2d {:width 2 :height 2 :data (float-array [1 1 1 1 1 1 1 1 1 1 1 1])})]
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

(def control-uniform "#version 410 core
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

(def evaluation-mix "#version 410 core
layout(quads, equal_spacing, ccw) in;
void main()
{
  vec4 a = mix(gl_in[0].gl_Position, gl_in[1].gl_Position, gl_TessCoord.x);
  vec4 b = mix(gl_in[3].gl_Position, gl_in[2].gl_Position, gl_TessCoord.x);
  gl_Position = mix(a, b, gl_TessCoord.y);
}")

(def geometry-triangle "#version 410 core
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

(def fragment-part1 "#version 410 core
vec3 fun()
{
  return vec3(0.0, 0.0, 1.0);
}
")

(def fragment-part2 "#version 410 core
out lowp vec3 fragColor;
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
        (let [tex (texture-render 8 8 true (clear (matrix [1.0 2.0 3.0])))]
          (get-vector (texture->vectors tex 32 32) 0 0) => (matrix [1.0 2.0 3.0])
          (destroy-texture tex))))

(fact "Render to image texture (needs active OpenGL context)"
      (offscreen-render 32 32
        (let [tex (texture-render 160 120 false (clear (matrix [1.0 0.0 0.0])))
              img (texture->image tex 160 120)]
          img => (is-image "test/sfsim25/fixtures/render/red.png")
          (destroy-texture tex))))
