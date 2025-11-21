(require '[sfsim.render :refer :all])
(require '[sfsim.texture :refer :all])
(require '[sfsim.image :refer :all])
(import '[org.lwjgl.glfw GLFW])
(import '[org.lwjgl.opengl GL GL30])

; render depth map, coordinates, colors and try second pass  with blending

(GLFW/glfwInit)

(setup-window-hints false false)
(def window (GLFW/glfwCreateWindow 1 1 "sfsim" 0 0))
(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)

(def vertex-source
"#version 410 core
in vec3 point;
out vec3 fs_point;
void main()
{
  fs_point = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-source
"#version 410 core
layout (location = 0) out vec4 color;
layout (location = 1) out vec4 vertex;
in vec3 fs_point;
void main()
{
  color = vec4(0.0, 0.0, 1.0, 0.5);
  vertex = vec4(fs_point, 1.0);
}")

(def program (make-program :sfsim.render/vertex [vertex-source] :sfsim.render/fragment [fragment-source]))

(def indices [0 1 3 2])
(def vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5])
(def vao (make-vertex-array-object program indices vertices ["point" 3]))

(def depth (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp 1 1))
(def color (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL30/GL_RGBA32F 1 1))
(def vertex (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL30/GL_RGBA32F 1 1))

(framebuffer-render 1 1 :sfsim.render/noculling depth [color vertex]
                    (use-program program)
                    (render-quads vao))

(println "rendered color:" (get-vector4 (rgba-texture->vectors4 color) 0 0))
(println "stored point:" (get-vector4 (rgba-texture->vectors4 vertex) 0 0))

(def vertex-source-2
"#version 410 core
in vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(def fragment-source-2
"#version 410 core
layout (location = 0) out vec4 color2;
uniform sampler2D color;
void main()
{
  vec4 foreground = texture(color, vec2(0.5, 0.5));
  color2 = vec4(vec3(1.0, 0.0, 0.0) * foreground.a + foreground.rgb, 1.0);
}")

(def program2 (make-program :sfsim.render/vertex [vertex-source-2] :sfsim.render/fragment [fragment-source-2]))

(def vertices2 [-1.0 -1.0 0.0, 1.0 -1.0 0.0, -1.0 1.0 0.0, 1.0 1.0 0.0])
(def vao2 (make-vertex-array-object program2 indices vertices2 ["point" 3]))

(def color2 (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL30/GL_RGBA32F 1 1))

(framebuffer-render 1 1 :sfsim.render/noculling nil [color2]
                    (use-program program2)
                    (uniform-sampler program "color" 0)
                    (use-textures {0 color})
                    (render-quads vao2))

(println "color after second pass:" (get-vector4 (rgba-texture->vectors4 color2) 0 0))

(destroy-texture color2)
(destroy-texture vertex)
(destroy-texture color)
(destroy-texture depth)

(destroy-vertex-array-object vao2)
(destroy-vertex-array-object vao)

(destroy-program program2)
(destroy-program program)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
