(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:require [clojure.core.matrix :refer (mget eseq)])
  (:import [org.lwjgl.opengl Pbuffer PixelFormat GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL45]
           [org.lwjgl BufferUtils]
           [mikera.vectorz Vector]
           [mikera.matrixx Matrix]))

(defn setup-rendering
  "Common code for setting up rendering"
  [width height]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace GL11/GL_BACK)
  (GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
  (GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE))

(defmacro offscreen-render
  "Macro to use a pbuffer for offscreen rendering"
  [width height & body]
  `(let [pixels#  (BufferUtils/createIntBuffer (* ~width ~height))
         pbuffer# (Pbuffer. ~width ~height (PixelFormat. 24 8 24 0 0) nil nil)
         data#    (int-array (* ~width ~height))]
     (.makeCurrent pbuffer#)
     (setup-rendering ~width ~height)
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
     (setup-rendering ~width ~height)
     ~@body
     (Display/update)))

(defn clear
  "Set clear color and clear color buffer as well as depth buffer"
  ([]
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear GL11/GL_DEPTH_BUFFER_BIT))
  ([color]
   (GL11/glClearColor (mget color 0) (mget color 1) (mget color 2) 1.0)
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))))

(defn make-shader
  "Compile a GLSL shader"
  [^String context ^String source ^long shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (if (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (str context " shader: " (GL20/glGetShaderInfoLog shader 1024)))))
    shader))

(defn destroy-shader
  "Delete a shader"
  [shader]
  (GL20/glDeleteShader shader))

(defn make-program
  "Compile and link a shader program"
  [& {:keys [vertex tess-control tess-evaluation geometry fragment]
      :or {vertex [] tess-control [] tess-evaluation [] geometry [] fragment []}}]
  (let [vertex-shaders          (map #(make-shader "vertex" % GL20/GL_VERTEX_SHADER) vertex)
        tess-control-shaders    (map #(make-shader "tessellation control" % GL40/GL_TESS_CONTROL_SHADER) tess-control)
        tess-evaluation-shaders (map #(make-shader "tessellation evaluation" % GL40/GL_TESS_EVALUATION_SHADER) tess-evaluation)
        geometry-shaders        (map #(make-shader "geometry" % GL32/GL_GEOMETRY_SHADER) geometry)
        fragment-shaders        (map #(make-shader "fragment" % GL20/GL_FRAGMENT_SHADER) fragment)]
    (let [program (GL20/glCreateProgram)
          shaders (concat vertex-shaders tess-control-shaders tess-evaluation-shaders geometry-shaders fragment-shaders)]
      (doseq [shader shaders] (GL20/glAttachShader program shader))
      (GL20/glLinkProgram program)
      (if (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
        (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
      (doseq [shader shaders] (destroy-shader shader))
      program)))

(defn destroy-program
  "Delete a program and associated shaders"
  [program]
  (GL20/glDeleteProgram program))

(defmacro def-make-buffer [method create-buffer]
  "Create a buffer object for binary input/output"
  `(defn ~method [data#]
     (let [buffer# (~create-buffer (count data#))]
       (.put buffer# data#)
       (.flip buffer#)
       buffer#)))

(defn make-float-buffer
  "Create a floating-point buffer object"
  ^java.nio.DirectFloatBufferU [^floats data]
  (doto (BufferUtils/createFloatBuffer (count data))
    (.put data)
    (.flip)))

(defn make-int-buffer
  "Create a integer buffer object"
  ^java.nio.DirectIntBufferU [^ints data]
  (doto (BufferUtils/createIntBuffer (count data))
    (.put data)
    (.flip)))

(defn make-byte-buffer
  "Create a byte buffer object"
  ^java.nio.DirectByteBuffer [^bytes data]
  (doto (BufferUtils/createByteBuffer (count data))
    (.put data)
    (.flip)))

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
          (GL20/glVertexAttribPointer (GL20/glGetAttribLocation ^int program (name attribute)) ^int size
                                      GL11/GL_FLOAT false ^int (* stride Float/BYTES) ^int (* offset Float/BYTES))
          (GL20/glEnableVertexAttribArray i))
        {:vertex-array-object vertex-array-object
         :array-buffer        array-buffer
         :index-buffer        index-buffer
         :nrows               (count indices)
         :ncols               (count attribute-pairs)}))))

(defn destroy-vertex-array-object
  "Destroy vertex array object and vertex buffer objects"
  [{:keys [^int vertex-array-object ^int array-buffer ^int index-buffer ^int ncols]}]
  (GL30/glBindVertexArray vertex-array-object)
  (doseq [i (range ncols)] (GL20/glDisableVertexAttribArray i))
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers index-buffer)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers array-buffer)
  (GL30/glBindVertexArray 0)
  (GL30/glDeleteVertexArrays vertex-array-object))

(defn use-program
  "Use specified shader program"
  [program]
  (GL20/glUseProgram ^int program))

(defn uniform-float
  "Set uniform float variable in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^double value]
  (GL20/glUniform1f (GL20/glGetUniformLocation ^int program (name k)) value))

(defn uniform-int
  "Set uniform integer variable in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^long value]
  (GL20/glUniform1i (GL20/glGetUniformLocation ^int program (name k)) value))

(defn uniform-vector3
  "Set uniform 3D vector in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^Vector value]
  (GL20/glUniform3f (GL20/glGetUniformLocation ^int program (name k)) (mget value 0) (mget value 1) (mget value 2)))

(defn uniform-matrix4
  "Set uniform 4x4 matrix in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^Matrix value]
  (GL20/glUniformMatrix4 (GL20/glGetUniformLocation ^int program (name k)) true (make-float-buffer (float-array (eseq value)))))

(defn uniform-sampler
  "Set index of uniform sampler in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^clojure.lang.Keyword k ^long value]
  (GL20/glUniform1i (GL20/glGetUniformLocation ^int program (name k)) value))

(defn- setup-vertex-array-object
  "Initialise rendering of a vertex array object"
  [vertex-array-object]
  (GL30/glBindVertexArray ^int (:vertex-array-object vertex-array-object)))

(defn render-quads
  "Render one or more quads"
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_QUADS ^int (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-patches
  "Render one or more tessellated quads"
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
  (GL11/glDrawElements GL40/GL_PATCHES ^int (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

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

(defmacro with-texture
  "Macro to bind a texture and open a context with it"
  [target texture & body]
  `(do
     (GL11/glBindTexture ~target ~texture)
     (let [result# (do ~@body)]
       (GL11/glBindTexture ~target 0)
       result#)))

(defmacro create-texture
  "Macro to create a texture and open a context with it"
  [target texture & body]
  `(let [~texture (GL11/glGenTextures)]
     (with-texture ~target ~texture ~@body)))

(defn generate-mipmap [texture]
  "Generate mipmap for texture and set texture min filter to linear mipmap mode"
  (let [target (:target texture)]
    (with-texture target (:texture texture)
      (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
      (GL30/glGenerateMipmap target))))

(defn make-float-texture-1d
  "Load floating-point 1D data into red channel of an OpenGL texture"
  [data]
  (let [buffer  (make-float-buffer data)]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-texture GL11/GL_TEXTURE_1D texture
      (GL11/glTexImage1D GL11/GL_TEXTURE_1D 0 GL30/GL_R32F (count data) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
      (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
      {:texture texture :target GL11/GL_TEXTURE_1D})))

(defn- make-texture-2d
  [image make-buffer internalformat format_ type_]
  "Initialise a 2D texture"
  (let [buffer (make-buffer (:data image))]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-texture GL11/GL_TEXTURE_2D texture
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 internalformat (:width image) (:height image) 0 format_ type_ buffer)
      (texture-wrap-clamp-2d)
      (texture-interpolate-linear-2d)
      {:texture texture :target GL11/GL_TEXTURE_2D})))

(defn make-rgb-texture
  "Load RGB image into an OpenGL texture"
  [image]
  (make-texture-2d image make-int-buffer GL11/GL_RGB GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE))

(defn make-float-texture-2d
  "Load floating-point 2D data into red channel of an OpenGL texture"
  [image]
  (make-texture-2d image make-float-buffer GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))

(defn make-ubyte-texture-2d
  "Load unsigned-byte 2D data into red channel of an OpenGL texture (data needs to be 32-bit aligned!)"
  [image]
  (make-texture-2d image make-byte-buffer GL11/GL_RED GL11/GL_RED GL11/GL_UNSIGNED_BYTE))

(defn make-vector-texture-2d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  [image]
  (make-texture-2d image make-float-buffer GL30/GL_RGB32F GL12/GL_BGR GL11/GL_FLOAT))

(defn make-float-texture-3d
  "Load floating-point 3D data into red channel of an OpenGL texture"
  [image]
  (let [buffer (make-float-buffer (:data image))]
    (GL13/glActiveTexture GL13/GL_TEXTURE0)
    (create-texture GL12/GL_TEXTURE_3D texture
      (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_R32F (:width image) (:height image) (:depth image) 0 GL11/GL_RED GL11/GL_FLOAT buffer)
      (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
      (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
      (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL12/GL_TEXTURE_WRAP_R GL11/GL_REPEAT)
      (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
      (GL11/glTexParameteri GL12/GL_TEXTURE_3D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
      {:texture texture :target GL12/GL_TEXTURE_3D})))

(defn destroy-texture
  "Delete an OpenGL texture"
  [texture]
  (GL11/glDeleteTextures ^int (:texture texture)))

(defn use-textures
  "Specify textures to be used in the next rendering operation"
  [& textures]
  (doseq [[i texture] (map list (range) textures)]
    (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 i))
    (GL11/glBindTexture (:target texture) (:texture texture))))

(defmacro texture-render-color
  "Macro to render color image to a texture"
  [width height floating-point & body]
  `(let [fbo# (GL45/glCreateFramebuffers) ; TODO: use GL30/glGenFramebuffers?
         tex# (GL11/glGenTextures)]
     (try
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
       (GL11/glBindTexture GL11/GL_TEXTURE_2D tex#)
       (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 (if ~floating-point GL30/GL_RGBA32F GL11/GL_RGBA8) ~width ~height)
       (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_COLOR_ATTACHMENT0 tex# 0)
       (GL20/glDrawBuffers (make-int-buffer (int-array [GL30/GL_COLOR_ATTACHMENT0])))
       (GL11/glViewport 0 0 ~width ~height)
       ~@body
       {:texture tex# :target GL11/GL_TEXTURE_2D}
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))

(defmacro texture-render-depth
  "Macro to render depth map to a texture"
  [width height & body]
  `(let [fbo# (GL30/glGenFramebuffers)
         tex# (GL11/glGenTextures)]
     (try
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
       (GL11/glBindTexture GL11/GL_TEXTURE_2D tex#)
       (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH_COMPONENT32F ~width ~height)
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR)
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE); TODO: set border color?
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_MODE GL14/GL_COMPARE_R_TO_TEXTURE); TODO: test this
       (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_FUNC GL11/GL_GEQUAL); TODO: test this
       (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_DEPTH_ATTACHMENT tex# 0)
       (GL11/glViewport 0 0 ~width ~height)
       ~@body
       {:texture tex# :target GL11/GL_TEXTURE_2D}
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))

(defn texture->floats
  "Extract floating-point depth map from texture"
  [texture width height]
  (with-texture (:target texture) (:texture texture)
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn texture->vectors3
  "Extract floating-point BGR vectors from texture"
  [texture width height]
  (with-texture (:target texture) (:texture texture)
    (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
          data (float-array (* width height 3))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_BGR GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn texture->vectors4
  "Extract floating-point BGRA vectors from texture"
  [texture width height]
  (with-texture (:target texture) (:texture texture)
    (let [buf  (BufferUtils/createFloatBuffer (* width height 4))
          data (float-array (* width height 4))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_BGRA GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn texture->image
  "Convert texture to RGB image"
  [texture width height]
  (with-texture (:target texture) (:texture texture)
    (let [buf  (BufferUtils/createIntBuffer (* width height))
          data (int-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_BGRA GL11/GL_UNSIGNED_BYTE buf)
      (.get buf data)
      {:width width :height height :data data})))
