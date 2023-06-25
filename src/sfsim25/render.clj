(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:require [fastmath.matrix :refer (mat->float-array)])
  (:import [org.lwjgl.opengl GL GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL45]
           [org.lwjgl BufferUtils]
           [org.lwjgl.glfw GLFW]
           [fastmath.matrix Mat3x3 Mat4x4]
           [fastmath.vector Vec3]))

(defn setup-rendering
  "Common code for setting up rendering"
  [width height culling]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace ({:cullfront GL11/GL_FRONT :cullback GL11/GL_BACK} culling))
  (GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
  (GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE))

(defmacro with-invisible-window
  "Macro to create temporary invisible window to provide context"
  [& body]
  `(do
     (GLFW/glfwDefaultWindowHints)
     (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE GLFW/GLFW_FALSE)
     (let [window# (GLFW/glfwCreateWindow 8 2 "sfsim25" 0 0)]
       (GLFW/glfwMakeContextCurrent window#)
       (GL/createCapabilities)
       (let [result# (do ~@body)]
         (GLFW/glfwDestroyWindow window#)
         result#))))

(defmacro onscreen-render
  "Macro to use the specified window for rendering"
  [window & body]
  `(let [width#  (int-array 1)
         height# (int-array 1)]
     (GLFW/glfwGetWindowSize ~window width# height#)
     (GLFW/glfwMakeContextCurrent ~window)
     (GL/createCapabilities)
     (setup-rendering (aget width# 0) (aget height# 0) :cullback)
     ~@body
     (GLFW/glfwSwapBuffers ~window)))

(defn clear
  "Set clear color and clear color buffer as well as depth buffer"
  ([]
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear GL11/GL_DEPTH_BUFFER_BIT))
  ([color]
   (clear color 1.0))
  ([color alpha]
   (GL11/glClearColor (color 0) (color 1) (color 2) alpha)
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))))

(defn make-shader
  "Compile a GLSL shader"
  [^String context ^String source ^long shader-type]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
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
        fragment-shaders        (map #(make-shader "fragment" % GL20/GL_FRAGMENT_SHADER) fragment)
        program (GL20/glCreateProgram)
        shaders (concat vertex-shaders tess-control-shaders tess-evaluation-shaders geometry-shaders fragment-shaders)]
    (doseq [shader shaders] (GL20/glAttachShader program shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    (doseq [shader shaders] (destroy-shader shader))
    program))

(defn destroy-program
  "Delete a program and associated shaders"
  [program]
  (GL20/glDeleteProgram program))

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
  [^clojure.lang.IPersistentMap program ^String k ^double value]
  (GL20/glUniform1f (GL20/glGetUniformLocation ^int program ^String k) value))

(defn uniform-int
  "Set uniform integer variable in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^String k ^long value]
  (GL20/glUniform1i (GL20/glGetUniformLocation ^int program ^String k) value))

(defn uniform-vector3
  "Set uniform 3D vector in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^String k ^Vec3 value]
  (GL20/glUniform3f (GL20/glGetUniformLocation ^int program ^String k) (value 0) (value 1) (value 2)))

(defn uniform-matrix3
  "Set uniform 3x3 matrix in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^String k ^Mat3x3 value]
  (GL20/glUniformMatrix3fv (GL20/glGetUniformLocation ^int program ^String k) true (make-float-buffer (mat->float-array value))))

(defn uniform-matrix4
  "Set uniform 4x4 matrix in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^String k ^Mat4x4 value]
  (GL20/glUniformMatrix4fv (GL20/glGetUniformLocation ^int program ^String k) true (make-float-buffer (mat->float-array value))))

(defn uniform-sampler
  "Set index of uniform sampler in current shader program (don't forget to set the program using use-program first)"
  [^clojure.lang.IPersistentMap program ^String k ^long value]
  (GL20/glUniform1i (GL20/glGetUniformLocation ^int program ^String k) value))

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

(defn generate-mipmap
  "Generate mipmap for texture and set texture min filter to linear mipmap mode"
  [texture]
  (let [target (:target texture)]
    (with-texture target (:texture texture)
      (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
      (GL30/glGenerateMipmap target))))

(defmulti setup-interpolation (comp second vector))

(defmethod setup-interpolation :nearest
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST))

(defmethod setup-interpolation :linear
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR))

(defmulti setup-boundary-1d identity)

(defmethod setup-boundary-1d :clamp
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE))

(defmethod setup-boundary-1d :repeat
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_1D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT))

(defmacro create-texture-1d
  "Macro to initialise 1D texture"
  [interpolation boundary width & body]
  `(create-texture GL11/GL_TEXTURE_1D texture#
                   (setup-interpolation GL11/GL_TEXTURE_1D ~interpolation)
                   (setup-boundary-1d ~boundary)
                   ~@body
                   {:texture texture# :target GL11/GL_TEXTURE_1D :width ~width}))

(defmulti setup-boundary-2d identity)

(defmethod setup-boundary-2d :clamp
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE))

(defmethod setup-boundary-2d :repeat
  [_boundary]
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT))

(defmacro create-texture-2d
  "Macro to initialise 2D texture"
  [interpolation boundary width height & body]
  `(create-texture GL11/GL_TEXTURE_2D texture#
                   (setup-interpolation GL11/GL_TEXTURE_2D ~interpolation)
                   (setup-boundary-2d ~boundary)
                   ~@body
                   {:texture texture# :target GL11/GL_TEXTURE_2D :width ~width :height ~height}))

(defmacro create-depth-texture
  "Macro to initialise shadow map"
  [interpolation boundary width height & body]
  `(create-texture-2d ~interpolation ~boundary ~width ~height
                      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_MODE GL14/GL_COMPARE_R_TO_TEXTURE)
                      (GL11/glTexParameteri GL11/GL_TEXTURE_2D GL14/GL_TEXTURE_COMPARE_FUNC GL11/GL_GEQUAL)
                      ~@body))

(defmulti setup-boundary-3d (comp second vector))

(defmethod setup-boundary-3d :clamp
  [target _boundary]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL12/GL_CLAMP_TO_EDGE)
  (GL11/glTexParameteri target GL12/GL_TEXTURE_WRAP_R GL12/GL_CLAMP_TO_EDGE))

(defmethod setup-boundary-3d :repeat
  [target _boundary]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_S GL11/GL_REPEAT)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_WRAP_T GL11/GL_REPEAT)
  (GL11/glTexParameteri target GL12/GL_TEXTURE_WRAP_R GL11/GL_REPEAT))

(defmacro create-texture-3d
  "Macro to initialise 3D texture"
  [interpolation boundary width height depth & body]
  `(create-texture GL12/GL_TEXTURE_3D texture#
                   (setup-interpolation GL12/GL_TEXTURE_3D ~interpolation)
                   (setup-boundary-3d GL12/GL_TEXTURE_3D ~boundary)
                   ~@body
                   {:texture texture# :target GL12/GL_TEXTURE_3D :width ~width :height ~height :depth ~depth}))

(defn make-float-texture-1d
  "Load floating-point 1D data into red channel of an OpenGL texture"
  [interpolation boundary data]
  (let [buffer (make-float-buffer data)
        width  (count data) ]
    (create-texture-1d interpolation boundary width
      (GL11/glTexImage1D GL11/GL_TEXTURE_1D 0 GL30/GL_R32F width 0 GL11/GL_RED GL11/GL_FLOAT buffer))))

(defn- make-texture-2d
  "Initialise a 2D texture"
  [image make-buffer interpolation boundary internalformat format_ type_]
  (let [buffer (make-buffer (:data image))]
    (create-texture-2d interpolation boundary (:width image) (:height image)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 internalformat (:width image) (:height image) 0 format_ type_ buffer))))

(defn make-rgb-texture
  "Load RGB image into an OpenGL texture"
  [interpolation boundary image]
  (make-texture-2d image make-byte-buffer interpolation boundary GL11/GL_RGB GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))

(defn make-depth-texture
  "Load floating-point values into a shadow map"
  [interpolation boundary image]
  (create-depth-texture interpolation boundary (:width image) (:height image)
                        (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_DEPTH_COMPONENT32F (:width image) (:height image) 0
                                           GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT (make-float-buffer (:data image)))))

(defn make-empty-texture-2d
  "Create 2D texture with specified format and allocate storage"
  [interpolation boundary internalformat width height]
  (create-texture-2d interpolation boundary width height
                     (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 internalformat width height)))

(defn make-empty-float-texture-2d
  "Create 2D floating-point texture and allocate storage"
  [interpolation boundary width height]
  (make-empty-texture-2d interpolation boundary GL30/GL_R32F width height))

(defn make-empty-depth-texture-2d
  "Create 2D depth texture and allocate storage"
  [interpolation boundary width height]
  (create-depth-texture interpolation boundary width height
                        (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH_COMPONENT32F width height)))

(defn make-float-texture-2d
  "Load floating-point 2D data into red channel of an OpenGL texture"
  [interpolation boundary image]
  (make-texture-2d image make-float-buffer interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))

(defn make-ubyte-texture-2d
  "Load unsigned-byte 2D data into red channel of an OpenGL texture (data needs to be 32-bit aligned!)"
  [interpolation boundary image]
  (make-texture-2d image make-byte-buffer interpolation boundary GL11/GL_RED GL11/GL_RED GL11/GL_UNSIGNED_BYTE))

(defn make-vector-texture-2d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  [interpolation boundary image]
  (make-texture-2d image make-float-buffer interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))

(defn make-float-texture-3d
  "Load floating-point 3D data into red channel of an OpenGL texture"
  [interpolation boundary image]
  (let [buffer (make-float-buffer (:data image))]
    (create-texture-3d interpolation boundary (:width image) (:height image) (:depth image)
      (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_R32F (:width image) (:height image) (:depth image) 0 GL11/GL_RED GL11/GL_FLOAT buffer))))

(defn make-empty-float-texture-3d
  "Create empty 3D floating-point texture"
  [interpolation boundary width height depth]
  (create-texture-3d interpolation boundary width height depth
                     (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F width height depth)))

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

(defn- list-texture-layers
  "Return 2D textures and each layer of 3D textures"
  [textures]
  (flatten
    (map (fn [texture]
             (if (:depth texture)
               (map (fn [layer] (assoc texture :layer layer)) (range (:depth texture)))
               texture))
         textures)))

(defn setup-color-attachments
  "Setup color attachments with 2D and 3D textures"
  [textures]
  (GL20/glDrawBuffers
    (make-int-buffer
      (int-array
        (map-indexed
          (fn [index texture]
              (let [color-attachment (+ GL30/GL_COLOR_ATTACHMENT0 index)]
                (if (:layer texture)
                  (GL30/glFramebufferTextureLayer GL30/GL_FRAMEBUFFER color-attachment (:texture texture) 0 (:layer texture))
                  (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER color-attachment (:texture texture) 0))
                color-attachment))
          (list-texture-layers textures))))))

(defmacro framebuffer-render
  "Macro to render to depth and color texture attachments"
  [width height culling depth-texture color-textures & body]
  `(let [fbo# (GL30/glGenFramebuffers)]
     (try
       (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER fbo#)
       (when ~depth-texture
         (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER GL30/GL_DEPTH_ATTACHMENT (:texture ~depth-texture) 0))
       (setup-color-attachments ~color-textures)
       (setup-rendering ~width ~height ~culling)
       ~@body
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))

(defmacro texture-render-color
  "Macro to render to a 2D color texture"
  [width height floating-point & body]
  `(let [internalformat# (if ~floating-point GL30/GL_RGBA32F GL11/GL_RGBA8)
         texture#        (make-empty-texture-2d :linear :clamp internalformat# ~width ~height)]
     (framebuffer-render ~width ~height :cullback nil [texture#] ~@body)
     texture#))

(defmacro texture-render-depth
  "Macro to create shadow map"
  [width height & body]
  `(let [tex# (make-empty-depth-texture-2d :linear :clamp ~width ~height)]
     (framebuffer-render ~width ~height :cullfront tex# [] ~@body tex#)))

(defn depth-texture->floats
  "Extract floating-point depth map from texture"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn float-texture-2d->floats
  "Extract floating-point floating-point data from texture"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn float-texture-3d->floats
  "Extract floating-point floating-point data from texture"
  [{:keys [target texture width height depth]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height depth))
          data (float-array (* width height depth))]
      (GL11/glGetTexImage GL12/GL_TEXTURE_3D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :depth depth :data data})))

(defn rgb-texture->vectors3
  "Extract floating-point RGB vectors from texture"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
          data (float-array (* width height 3))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn rgba-texture->vectors4
  "Extract floating-point RGBA vectors from texture"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 4))
          data (float-array (* width height 4))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn texture->image
  "Convert texture to RGB image"
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [size (* 4 width height)
          buf  (BufferUtils/createByteBuffer size)
          data (byte-array size)]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE buf)
      (.get buf data)
      {:width width :height height :data data})))

(defmacro offscreen-render
  "Macro to render to a texture and convert it to an image"
  [width height & body]
  `(with-invisible-window
     (let [depth# (make-empty-depth-texture-2d :linear :clamp ~width ~height)
           tex#   (make-empty-texture-2d :linear :clamp GL11/GL_RGB8 ~width ~height)]
       (framebuffer-render ~width ~height :cullback depth# [tex#] ~@body)
       (let [img# (texture->image tex#)]
         (destroy-texture tex#)
         (destroy-texture depth#)
         img#))))

(defmacro create-cubemap
  "Macro to initialise cubemap"
  [interpolation boundary size & body]
  `(create-texture GL13/GL_TEXTURE_CUBE_MAP texture#
                   (setup-interpolation GL13/GL_TEXTURE_CUBE_MAP ~interpolation)
                   (setup-boundary-3d GL13/GL_TEXTURE_CUBE_MAP ~boundary)
                   ~@body
                   {:width ~size :height ~size :depth 6 :target GL13/GL_TEXTURE_CUBE_MAP :texture texture#}))


(defn- make-cubemap
  "Initialise a cubemap"
  [images interpolation boundary internalformat format_ type_]
  (let [size (:width (first images))]
    (create-cubemap interpolation boundary size
      (doseq [[face image] (map-indexed vector images)]
             (GL11/glTexImage2D (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 internalformat size size 0 format_
                                type_ (make-float-buffer (:data image)))))))
(defn make-float-cubemap
  "Load floating-point 2D textures into red channel of an OpenGL cubemap"
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))

(defn make-empty-float-cubemap
  "Create empty cubemap with faces of specified size"
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_R32F size size)))

(defn float-cubemap->floats
  "Extract floating-point data from cubemap face"
  [{:keys [target texture width height]} face]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn make-vector-cubemap
  "Load vector 2D textures into an OpenGL cubemap"
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))

(defn make-empty-vector-cubemap
  "Create empty cubemap with faces of specified size"
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_RGB32F size size)))

(defn vector-cubemap->vectors3
  "Extract floating-point vector data from cubemap face"
  [{:keys [target texture width height]} face]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
          data (float-array (* width height 3))]
      (GL11/glGetTexImage (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))
