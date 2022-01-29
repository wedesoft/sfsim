(import '[org.lwjgl.opengl Pbuffer PixelFormat GL11 GL20 GL30 GL32 GL42 GL45])
(import '[org.lwjgl BufferUtils])
(require '[sfsim25.render :refer :all])

(offscreen-render
  320 240
  (let [fbo (GL45/glCreateFramebuffers)
        tex (GL11/glGenTextures)
        buf (BufferUtils/createFloatBuffer (* 32 32 3))]
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo)
    (GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
    (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_RGB32F 32 32)
    (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 tex 0)
    (GL20/glDrawBuffers (make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))
    (GL11/glViewport 0 0 32 32)
    (GL11/glClearColor 1.0 0.0 0.0 1.0)
    (GL11/glClear GL11/GL_COLOR_BUFFER_BIT)
    (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_RGB GL11/GL_FLOAT buf)
    (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
    (println (.get buf) (.get buf) (.get buf))))
