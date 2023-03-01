(require '[sfsim25.render :refer :all])
(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL30])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 400))
(Display/create)

; https://www.khronos.org/opengl/wiki/Cubemap_Texture

(def image {:width 2 :height 2 :data (float-array [0.0 1.0 1.0 0.0])})
(def tex {:texture (GL11/glGenTextures) :target GL13/GL_TEXTURE_CUBE_MAP :width 2 :height 2 :depth 6})
(GL11/glBindTexture GL13/GL_TEXTURE_CUBE_MAP (:texture tex))
(def buffer (make-float-buffer (:data image)))
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_X 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_Y 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_Y 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_Z 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
(GL11/glTexImage2D GL13/GL_TEXTURE_CUBE_MAP_NEGATIVE_Z 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)

(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
(GL11/glTexParameteri GL13/GL_TEXTURE_CUBE_MAP GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE)


(destroy-texture tex)
(Display/destroy)
