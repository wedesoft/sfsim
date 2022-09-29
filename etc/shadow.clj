(require '[sfsim25.render :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Pbuffer Display DisplayMode PixelFormat GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL43 GL45]
        '[org.lwjgl BufferUtils])

(def size 256)

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. size size))
(Display/create)

(def shadow (GL30/glGenFramebuffers))
(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER shadow)
(def tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
(GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT size size 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT nil)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_MODE GL14/GL_COMPARE_R_TO_TEXTURE)
(GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_FUNC GL11/GL_GEQUAL)
(GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_DEPTH_ATTACHMENT tex 0)
;(GL11/glDrawBuffer GL11/GL_NONE)
;(GL11/glReadBuffer GL11/GL_NONE)
(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)

(def vert
"#version 410 core
in vec3 point;
void main(void)
{
  gl_Position = vec4(point, 1);
}")

(def frag
"#version 410 core
void main(void)
{
}")

(def indices [0 1 3 2])
(def vertices [-0.5 -0.5 0.1, 0.5 -0.5 0.1, -0.5 0.5 0.7, 0.5 0.5 0.7])
(def program (make-program :vertex [vert] :fragment [frag]))
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(GL11/glViewport 0 0 size size)
(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER shadow)
(GL11/glEnable GL11/GL_DEPTH_TEST)
(GL11/glClearDepth 0.0)
(GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
(GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE)
(GL11/glClear GL11/GL_DEPTH_BUFFER_BIT)
(GL11/glDisable GL11/GL_CULL_FACE)
(use-program program)
(render-quads vao)
(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)

;(show-floats
;  (with-texture GL11/GL_TEXTURE_2D tex
;    (let [buf  (BufferUtils/createFloatBuffer (* size size))
;          data (float-array (* size size))]
;      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
;      (.get buf data)
;      {:width size :height size :data data})))

(def vert2
"#version 410 core
in vec3 point;
out vec4 pos;
void main(void)
{
  pos = vec4(point * vec3(0.5, 0.5, 1.0) + vec3(0.5, 0.5, 0.0), 1);
  gl_Position = vec4(point, 1);
}")

(def frag2
"#version 410 core
uniform sampler2DShadow tex;
in vec4 pos;
out vec3 fragColor;
void main(void)
{
  float shade = textureProj(tex, pos);
  fragColor = vec3(shade, shade, shade);
}")

(def indices [0 1 3 2])
(def vertices [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5 0.5 0.5, 0.5 0.5 0.5])
(def program2 (make-program :vertex [vert2] :fragment [frag2]))
(def vao2 (make-vertex-array-object program2 indices vertices [:point 3]))

(GL11/glViewport 0 0 size size)
(GL11/glEnable GL11/GL_DEPTH_TEST)
(GL11/glClearDepth 0.0)
(GL11/glDepthFunc GL11/GL_GEQUAL)
(GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE)
(GL11/glClearColor 0.1 0.1 0.1 1.0)
(GL11/glClear (bit-or GL11/GL_DEPTH_BUFFER_BIT GL11/GL_COLOR_BUFFER_BIT))
(use-program program2)
(uniform-sampler program2 :tex 0)
(use-textures {:target GL11/GL_TEXTURE_2D :texture tex})
(render-quads vao2)
(Display/update)

(GL11/glDeleteTextures tex)
(destroy-vertex-array-object vao2)
(destroy-program program2)
(destroy-vertex-array-object vao)
(destroy-program program)
(GL30/glDeleteFramebuffers shadow)

(Display/destroy)
