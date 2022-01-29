(import '[org.lwjgl.opengl Pbuffer PixelFormat GL11 GL20 GL30 GL32 GL42 GL45])
(import '[org.lwjgl BufferUtils])
(require '[sfsim25.render :refer :all])

(GL11/glGetError)

(def pbuffer (Pbuffer. 320 240 (PixelFormat. 24 8 24 0 0) nil nil))
(.makeCurrent pbuffer)

(def fbo (GL45/glCreateFramebuffers))
(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo); p. 394

(def tex (GL11/glGenTextures))
(GL11/glBindTexture GL11/GL_TEXTURE_2D tex)
(GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_RGB32F 32 32)

(GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 tex 0)
(GL20/glDrawBuffers (make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))

(GL11/glViewport 0 0 32 32)

(GL11/glClearColor 1.0 0.0 0.0 1.0)
(GL11/glClear GL11/GL_COLOR_BUFFER_BIT)

(def buf (BufferUtils/createFloatBuffer (* 32 32 3)))
(GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_RGB GL11/GL_FLOAT buf); p. 465


(GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)

(.releaseContext pbuffer)
(.destroy pbuffer)
