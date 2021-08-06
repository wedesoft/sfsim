(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:require [clojure.core.matrix :refer (mget eseq)]
            [sfsim25.util :refer (slurp-image)])
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL13 GL15 GL20 GL30]
           [org.lwjgl BufferUtils]))

(defmacro offscreen-render
  "Macro to use a pbuffer for offscreen rendering"
  [width height & body]
  `(let [pixels#  (BufferUtils/createIntBuffer (* ~width ~height))
         pbuffer# (Pbuffer. ~width ~height (PixelFormat. 24 8 24 0 0) nil nil)
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
  "Set clear color and clear color buffer as well as depth buffer"
  (GL11/glClearColor (:r color) (:g color) (:b color) 1.0)
  (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))

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
  "Create a buffer object for binary input/output"
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(def-make-buffer make-float-buffer BufferUtils/createFloatBuffer)
(def-make-buffer make-int-buffer   BufferUtils/createIntBuffer  )
(def-make-buffer make-byte-buffer  BufferUtils/createByteBuffer )

(defn make-vertex-array-object
  "Create vertex array object and vertex buffer objects"
  [program indices vertices attributes]
  (let [vertex-array-object (GL30/glGenVertexArrays)]
    (GL30/glBindVertexArray vertex-array-object)
    (let [array-buffer (GL15/glGenBuffers)
          index-buffer (GL15/glGenBuffers)]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER (make-float-buffer (float-array vertices)) GL15/GL_STATIC_DRAW)
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER index-buffer)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER (make-int-buffer (int-array indices)) GL15/GL_STATIC_DRAW)
      (let [attribute-pairs (partition 2 attributes)
            sizes           (map second attribute-pairs)
            stride          (apply + sizes)
            offsets         (reductions + (cons 0 (butlast sizes)))]
        (doseq [[i [attribute size] offset] (map list (range) attribute-pairs offsets)]
          (GL20/glVertexAttribPointer (GL20/glGetAttribLocation (:program program) (name attribute)) size GL11/GL_FLOAT false
                                      (* stride Float/BYTES) (* offset Float/BYTES))
          (GL20/glEnableVertexAttribArray i))
        {:vertex-array-object vertex-array-object
         :array-buffer        array-buffer
         :index-buffer        index-buffer
         :nrows               (count indices)
         :ncols               (count attribute-pairs)}))))

(defn destroy-vertex-array-object
  "Destroy vertex array object and vertex buffer objects"
  [{:keys [vertex-array-object array-buffer index-buffer ncols]}]
  (GL30/glBindVertexArray vertex-array-object)
  (doseq [i (range ncols)] (GL20/glDisableVertexAttribArray i))
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers index-buffer)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers array-buffer)
  (GL30/glBindVertexArray 0)
  (GL30/glDeleteVertexArrays vertex-array-object))

(defn uniform-float
  "Set uniform float variable in current shader program"
  [program k value]
  (GL20/glUniform1f (GL20/glGetUniformLocation (:program program) (name k)) value))

(defn uniform-vector3
  "Set uniform 3D vector in current shader program"
  [program k value]
  (GL20/glUniform3f (GL20/glGetUniformLocation (:program program) (name k)) (mget value 0) (mget value 1) (mget value 2)))

(defn uniform-matrix4
  "Set uniform 4x4 matrix in current shader program"
  [program k value]
  (GL20/glUniformMatrix4 (GL20/glGetUniformLocation (:program program) (name k)) true (make-float-buffer (float-array (eseq value)))))

(defn uniform-sampler
  "Set index of uniform sampler in current shader program"
  [program k value]
  (GL20/glUniform1i (GL20/glGetUniformLocation (:program program) (name k)) value))

(defmacro use-program
  "Use program and set uniform variables"
  [program & uniforms]
  `(do
     (GL20/glUseProgram (:program ~program))
     ~@(map (fn [[method & args]] `(~method ~program ~@args)) uniforms)))

(defn render-quads
  "Render one or more quads"
  [vertex-array-object]
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)
  (GL30/glBindVertexArray (:vertex-array-object vertex-array-object))
  (GL11/glDrawElements GL11/GL_QUADS (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defmacro raster-lines
  "Macro for temporarily switching polygon rasterization to line mode"
  [& body]
  `(do
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
     ~@body
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)))

(defn make-rgb-texture
  "Load RGB image from file and load it into an OpenGL texture"
  [filename]
  (let [image   (slurp-image filename)
        texture (GL11/glGenTextures)
        buffer  (make-int-buffer (:data image))]
    (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)
    (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB (:width image) (:height image) 0 GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE buffer)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
    (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
    {:texture texture :target GL11/GL_TEXTURE_2D}))

(defn destroy-texture
  "Delete an OpenGL texture"
  [texture]
  (GL11/glDeleteTextures (:texture texture)))

(defn use-textures
  "Specify textures to be used in the next rendering operation"
  [& textures]
  (doseq [[i texture] (map list (range) textures)]
    (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
    (GL11/glBindTexture (:target texture) (:texture texture))))
