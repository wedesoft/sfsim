(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:import [org.lwjgl.opengl GL11 GL30]
           [org.lwjgl BufferUtils]))

(defn clear [color]
  (GL11/glClearColor (:r color) (:g color) (:b color) 0.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))

(defmacro offscreen-render [width height & body]
  `(let [fbo#     (GL30/glGenFramebuffers)
         texture# (GL11/glGenTextures)
         buffer   (BufferUtils/createIntBuffer (* ~width ~height))]
     (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
     (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGBA ~width ~height 0 GL11/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer)
     (GL30/glFramebufferTexture2D GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 GL11/GL_TEXTURE_2D texture# 0)
     glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);  <Paste>
     ~@body
     (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
     (GL30/glDeleteFramebuffers fbo#)))


