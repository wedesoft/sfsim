(import '[org.lwjgl BufferUtils]
        '[org.lwjgl.glfw GLFW]
        '[org.lwjgl.opengl GL GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL45])

(defn setup-rendering
  "Common code for setting up rendering"
  [width height culling]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace ({:cullfront GL11/GL_FRONT :cullback GL11/GL_BACK} culling))
  (GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
  (GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE))

(defmacro onscreen-render
  "Macro to use the specified window for rendering"
  [window & body]
  `(let [width#  (BufferUtils/createIntBuffer 1)
         height# (BufferUtils/createIntBuffer 1)]
     (GLFW/glfwGetWindowSize ~window width# height#)
     (GLFW/glfwMakeContextCurrent ~window)
     (GL/createCapabilities)
     (setup-rendering (.get width# 0) (.get height# 0) :cullback)
     ~@body
     (GLFW/glfwSwapBuffers window)))

(GLFW/glfwInit)
(GLFW/glfwDefaultWindowHints)
(def window (GLFW/glfwCreateWindow 320 240 "onscreen" 0 0))

(onscreen-render window
                 (GL11/glClearColor 1.0 0.0 0.0 0.0)
                 (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))

(GLFW/glfwDestroyWindow window)
(GLFW/glfwTerminate)

; (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
; (GLFW/glfwMakeContextCurrent window)
; (GL/createCapabilities)
; (def fbo (GL30/glGenFramebuffers))
; (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo)
; (def tex (GL11/glGenTextures))
; (GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
; (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_RGBA8 320 240 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE 0)
; (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 tex 0)
; (GL11/glViewport 0 0 320 240)
; (GL11/glClearColor 1.0 0.0 0.0 0.0)
; (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
; (GL11/glFlush)
; (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
; (def buf (BufferUtils/createIntBuffer (* 320 240)))
; (GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
; (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE buf)
; (def data (int-array (* 320 240)))
; (.get buf data)
; (println (take 10 data))
