(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL20]
           [org.lwjgl BufferUtils]))

(defmacro offscreen-render [width height & body]
  `(let [pixels#  (BufferUtils/createIntBuffer (* ~width ~height))
         pbuffer# (Pbuffer. ~width ~height (PixelFormat. 24 8 0 0 0) nil nil)
         data#    (int-array (* ~width ~height))]
     (.makeCurrent pbuffer#)
     (GL11/glViewport 0 0 ~width ~height)
     (try
       ~@body
       (GL11/glReadPixels 0 0 ~width ~height GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE pixels#)
       (.get pixels# data#)
       {:width ~width :height ~height :data data#}
       (finally
         (.releaseContext pbuffer#)
         (.destroy pbuffer#)))))

(defn clear [color]
  (GL11/glClearColor (:r color) (:g color) (:b color) 1.0)
  (GL11/glClear GL11/GL_COLOR_BUFFER_BIT))

(defn make-shader [source shader-type]
  "Compile a GLSL shader"
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (if (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (GL20/glGetShaderInfoLog shader 1024))))
    shader))

(defn destroy-shader
  "Delete a shader"
  [shader]
  (GL20/glDeleteShader shader))

(defn make-program
  "Compile and link a shader program"
  [& {:keys [vertex fragment]}]
  (let [vertex-shader   (make-shader vertex GL20/GL_VERTEX_SHADER)
        fragment-shader (make-shader fragment GL20/GL_FRAGMENT_SHADER)]
    (let [program (GL20/glCreateProgram)
          shaders [vertex-shader fragment-shader]]
      (doseq [shader shaders] (GL20/glAttachShader program shader))
      (GL20/glLinkProgram program)
      (if (zero? (GL20/glGetShaderi program GL20/GL_LINK_STATUS))
        (throw (Exception. (GL20/glGetShaderInfoLog program 1024))))
      {:program program :shaders shaders})))

(defn destroy-program
  "Delete a program and associated shaders"
  [{:keys [program shaders]}]
  (doseq [shader shaders]
    (GL20/glDetachShader program shader)
    (GL20/glDeleteShader shader))
  (GL20/glDeleteProgram program))
