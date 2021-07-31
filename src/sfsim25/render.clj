(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12]
           [org.lwjgl BufferUtils]))

(defn clear [color]
  (GL11/glClearColor (:r color) (:g color) (:b color) 1.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))

(defmacro offscreen-render [width height & body]
  `(let [pixels#  (BufferUtils/createIntBuffer (* ~width ~height))
         pbuffer# (Pbuffer. ~width ~height (PixelFormat. 24 8 0 0 0) nil nil)
         data#    (int-array (* ~width ~height))]
     (.makeCurrent pbuffer#)
     ~@body
     (GL11/glReadPixels 0 0 ~width ~height GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE pixels#)
     (.releaseContext pbuffer#)
     (.destroy pbuffer#)
     (.get pixels# data#)
     {:width ~width :height ~height :data data#}))


