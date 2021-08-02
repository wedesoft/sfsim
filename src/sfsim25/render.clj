(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL15 GL20 GL30]
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

(defmacro def-make-buffer [method create-buffer]
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer BufferUtils/createIntBuffer)
(def-make-buffer make-byte-buffer BufferUtils/createByteBuffer)

(defn make-vao
  "Create vertex array object and vertex buffer objects"
  [program indices vertices attributes]
  (let [vao (GL30/glGenVertexArrays)]
    (GL30/glBindVertexArray vao)
    (let [vbo  (GL15/glGenBuffers)
          idx  (GL15/glGenBuffers)]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER vbo)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER (make-float-buffer (float-array vertices)) GL15/GL_STATIC_DRAW)
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER idx)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER (make-int-buffer (int-array indices)) GL15/GL_STATIC_DRAW)
      (let [attribute-pairs (partition 2 attributes)
            sizes           (map second attribute-pairs)
            stride          (apply + sizes)
            offsets         (reductions + (cons 0 (butlast sizes)))]
        (doseq [[i [attribute size] offset] (map list (range) attribute-pairs offsets)]
          (GL20/glVertexAttribPointer (GL20/glGetAttribLocation (:program program) (name attribute)) size GL11/GL_FLOAT false
                                      (* stride Float/BYTES) (* offset Float/BYTES))
          (GL20/glEnableVertexAttribArray i))
        {:vao vao :vbo vbo :idx idx}))))

(defn destroy-vao
  "Destroy vertex array object and vertex buffer objects"
  [{:keys [vao vbo idx]}]
  (GL30/glBindVertexArray vao)
  (GL20/glDisableVertexAttribArray 0)
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers idx)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers vbo)
  (GL30/glBindVertexArray 0)
  (GL30/glDeleteVertexArrays vao))

(defn render-quads
  "Render one or more quads"
  [program vao]
  (GL20/glUseProgram (:program program))
  (GL30/glBindVertexArray (:vao vao))
  (GL11/glDrawElements GL11/GL_QUADS 4 GL11/GL_UNSIGNED_INT 0))
