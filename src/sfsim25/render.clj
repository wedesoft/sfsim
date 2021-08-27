(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:require [clojure.core.matrix :refer (mget eseq)]
            [sfsim25.util :refer (def-context-macro def-context-create-macro)])
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40]
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

(defmacro onscreen-render
  "Macro to use the current display for rendering"
  [width height & body]
  `(do
     (Display/makeCurrent)
     (GL11/glViewport 0 0 ~width ~height)
     ~@body
     (Display/update)))

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
  [& {:keys [vertex tess-control tess-evaluation geometry fragment]}]
  (let [vertex-shader          (make-shader vertex GL20/GL_VERTEX_SHADER)
        tess-control-shader    (if tess-control (make-shader tess-control GL40/GL_TESS_CONTROL_SHADER))
        tess-evaluation-shader (if tess-evaluation (make-shader tess-evaluation GL40/GL_TESS_EVALUATION_SHADER))
        geometry-shader        (if geometry (make-shader geometry GL32/GL_GEOMETRY_SHADER))
        fragment-shader        (make-shader fragment GL20/GL_FRAGMENT_SHADER)]
    (let [program (GL20/glCreateProgram)
          shaders (remove nil? [vertex-shader tess-control-shader tess-evaluation-shader geometry-shader fragment-shader])]
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

(defn- setup-vertex-array-object
  "Initialise rendering of a vertex array object"
  [vertex-array-object]
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)
  (GL30/glBindVertexArray (:vertex-array-object vertex-array-object)))

(defn render-quads
  "Render one or more quads"
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_QUADS (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-patches
  "Render one or more tessellated quads"
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
  (GL11/glDrawElements GL40/GL_PATCHES (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defmacro raster-lines
  "Macro for temporarily switching polygon rasterization to line mode"
  [& body]
  `(do
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
     ~@body
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)))

(defn- texture-wrap-clamp-2d
  "Set wrapping mode of active 2D texture"
  []
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE))

(defn- texture-interpolate-linear-2d
  "Set interpolation of active 2D texture to linear interpolation"
  []
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR))

(def-context-macro with-1d-texture (fn [texture] (GL11/glBindTexture GL11/GL_TEXTURE_1D texture)) (fn [texture] (GL11/glBindTexture GL11/GL_TEXTURE_1D 0)))

(def-context-macro with-2d-texture (fn [texture] (GL11/glBindTexture GL11/GL_TEXTURE_2D texture)) (fn [texture] (GL11/glBindTexture GL11/GL_TEXTURE_2D 0)))

(def-context-create-macro create-1d-texture (fn [] (GL11/glGenTextures)) 'with-1d-texture)

(def-context-create-macro create-2d-texture (fn [] (GL11/glGenTextures)) 'with-2d-texture)

(defn make-rgb-texture
  "Load RGB image into an OpenGL texture"
  [image]
  (let [buffer (make-int-buffer (:data image))]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-2d-texture texture
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RGB (:width image) (:height image) 0 GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE buffer)
      (texture-wrap-clamp-2d)
      (texture-interpolate-linear-2d)
      {:texture texture :target GL11/GL_TEXTURE_2D})))

(defn make-float-texture-1d
  "Load floating-point 1D data into red-channel of an OpenGL texture"
  [data]
  (let [buffer  (make-float-buffer data)]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-1d-texture texture
      (GL11/glTexImage1D GL11/GL_TEXTURE_1D 0 GL30/GL_R32F (count data) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
      {:texture texture :target GL11/GL_TEXTURE_1D})))

(defn make-float-texture-2d
  "Load floating-point 2D data into red-channel of an OpenGL texture"
  [image]
  (let [buffer (make-float-buffer (:data image))]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-2d-texture texture
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_R32F (:width image) (:height image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
      (texture-wrap-clamp-2d)
      (texture-interpolate-linear-2d)
      {:texture texture :target GL11/GL_TEXTURE_2D})))

(defn make-ubyte-texture-2d
  "Load unsigned-byte 2D data into red-channel of an OpenGL texture (data needs to be 32-bit aligned!)"
  [image]
  (let [buffer (make-byte-buffer (:data image))]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-2d-texture texture
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL11/GL_RED (:width image) (:height image) 0 GL11/GL_RED GL11/GL_UNSIGNED_BYTE buffer)
      (texture-wrap-clamp-2d)
      (texture-interpolate-linear-2d)
      {:texture texture :target GL11/GL_TEXTURE_2D})))

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
