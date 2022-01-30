(import '[org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL20 GL30 GL32 GL42 GL45])
(import '[org.lwjgl BufferUtils])
(require '[sfsim25.render :refer :all])
(require '[sfsim25.util :refer :all])

; TODO: depth buffer attachment; test rendering of quads with depth

(defmacro texture-render
  "Macro to render to a texture"
  [width height & body]
  `(let [fbo# (GL45/glCreateFramebuffers)
         tex# (GL11/glGenTextures)]
     (try
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
       (GL11/glBindTexture GL11/GL_TEXTURE_2D tex#)
       (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_RGB32F ~width ~height)
       (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 tex# 0)
       (GL20/glDrawBuffers (make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))
       (GL11/glViewport 0 0 ~width ~height)
       ~@body
       {:texture tex# :target GL11/GL_TEXTURE_2D}
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))

(defn texture->vectors
  "Extract floating-point vectors from texture"
  [texture width height]
  (with-2d-texture (:texture texture)
      (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
            data (float-array (* width height 3))]
        (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_BGR GL11/GL_FLOAT buf)
        (.get buf data)
        {:width width :height height :data data})))

(offscreen-render
  320 240
  (let [tex (texture-render 32 32
              (GL11/glClearColor 1.0 0.0 0.0 1.0)
              (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))]
    (println (get-vector (texture->vectors tex 32 32) 0 0))
    (destroy-texture tex)))
