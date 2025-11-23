(require '[sfsim.render :refer :all])
(require '[sfsim.texture :refer :all])
(require '[sfsim.image :refer :all])
(require '[fastmath.vector :refer (vec3)])
(import '[org.lwjgl.glfw GLFW])
(import '[org.lwjgl.opengl GL GL30])

(GLFW/glfwInit)

(def big 256)
(def small 32)

(setup-window-hints true true)
(def window (GLFW/glfwCreateWindow big big "sfsim" 0 0))
(GLFW/glfwMakeContextCurrent window)
(GL/createCapabilities)

; Test program: render triangle and merge low-resolution fog image using depth-aware upsampling.

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
  color = vec4(1.0, 0.0, 0.0, 1.0);
  vertex = vec4(fs_point, 1.0);
}")

(def program (make-program :sfsim.render/vertex [vertex-source] :sfsim.render/fragment [fragment-source]))

(def indices-triangle [0 1 2])
(def vertices-triangle [0.5 0.5 0.5, -0.5 0.5 0.5, -0.5 -0.5 0.5])
(def vao (make-vertex-array-object program indices-triangle vertices-triangle ["point" 3]))

(def depth (make-empty-depth-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp big big))
(def color (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F big big))
(def vertex (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F big big))

(framebuffer-render big big :sfsim.render/noculling depth [color vertex]
                    (use-program program)
                    (clear (vec3 0 0 0))
                    (render-triangles vao))

; write out red triangle
(spit-png "/tmp/triangle.png" (texture->image color))

(def vertex-depth-source vertex-source)
(def fragment-depth-source
"#version 410 core
layout (location = 0) out vec4 vertex;
in vec3 fs_point;
void main()
{
  vertex = vec4(fs_point, 1.0);
}")

(def program-depth (make-program :sfsim.render/vertex [vertex-depth-source] :sfsim.render/fragment [fragment-depth-source]))
(def depth-small (make-empty-depth-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp small small))
(def vertex-small (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F small small)) ; TODO: use nearest
(framebuffer-render small small :sfsim.render/noculling depth-small [vertex-small]
                    (use-program program-depth)
                    (clear)
                    (render-triangles vao))

(def indices-quad [0 1 3 2])
(def vertices-quad [-1.0 -1.0 0.0, 1.0 -1.0 0.0, -1.0 1.0 0.0, 1.0 1.0 0.0])
(def vao-quad (make-vertex-array-object program indices-quad vertices-quad ["point" 3]))

(framebuffer-render small small :sfsim.render/noculling depth-small [vertex-small]
                    (use-program program-depth)
                    (render-quads vao-quad))

; display vertex from small vertex texture
; (println "triangle vertex:" (get-vector4 (rgba-texture->vectors4 vertex-small) (- 128 48) 48))
; (println "background vertex:" (get-vector4 (rgba-texture->vectors4 vertex-small) 48 (- 128 48)))

(def vertex-fog-source
"#version 410 core
in vec3 point;
in vec2 uv;
out vec2 fs_uv;
void main()
{
  fs_uv = uv;
  gl_Position = vec4(point, 1);
}")

(def fragment-fog-source
"#version 410 core
layout (location = 0) out vec4 color;
uniform sampler2D vertex;
in vec2 fs_uv;
void main()
{
  vec4 point = texture(vertex, fs_uv);
  float z = 1.0 - point.z;
  float transparency = pow(0.25, z);
  color = vec4(0.5, 0.5, 0.5, 1.0 - transparency);
}")

(def program-fog (make-program :sfsim.render/vertex [vertex-fog-source] :sfsim.render/fragment [fragment-fog-source]))

(def indices-full [0 1 3 2])
(def vertices-full [-1.0 -1.0 0.0 0.0 0.0, 1.0 -1.0 0.0 1.0 0.0, -1.0 1.0 0.0 0.0 1.0, 1.0 1.0 0.0 1.0 1.0])
(def vao-full (make-vertex-array-object program-fog indices-full vertices-full ["point" 3 "uv" 2]))

(def fog-small (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F small small))
(framebuffer-render small small :sfsim.render/noculling nil [fog-small]
                    (use-program program-fog)
                    (uniform-sampler program-fog "vertex" 0)
                    (use-textures {0 vertex-small})
                    (render-quads vao-full))

(spit-png "/tmp/fog.png" (texture->image fog-small))

(def vertex-final-source vertex-fog-source)

(def fragment-final-source
"#version 410 core
uniform sampler2D color;
uniform sampler2D fog;
in vec2 fs_uv;
out vec4 fragColor;
void main()
{
  vec4 color_ = texture(color, fs_uv);
  vec4 fog_ = texture(fog, fs_uv);
  fragColor = vec4(color_.rgb * (1.0 - fog_.a) + fog_.rgb * fog_.a, 1.0);
}")

(def program-final (make-program :sfsim.render/vertex [vertex-final-source] :sfsim.render/fragment [fragment-final-source]))

(onscreen-render window
                 (use-program program-final)
                 (uniform-sampler program-final "color" 0)
                 (uniform-sampler program-final "fog" 1)
                 (use-textures {0 color 1 fog-small})
                 (render-quads vao-full))


(while (not (GLFW/glfwWindowShouldClose window))
       (GLFW/glfwPollEvents))


(destroy-texture depth)
(destroy-texture depth-small)

(destroy-vertex-array-object vao)
(destroy-vertex-array-object vao-quad)
(destroy-vertex-array-object vao-full)

(destroy-texture color)
(destroy-texture vertex)
(destroy-texture vertex-small)
(destroy-texture fog-small)

(destroy-program program)
(destroy-program program-depth)
(destroy-program program-fog)
(destroy-program program-final)

(GLFW/glfwDestroyWindow window)

(GLFW/glfwTerminate)
