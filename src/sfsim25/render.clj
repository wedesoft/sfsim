(ns sfsim25.render
  "Functions for doing OpenGL rendering"
  (:require [fastmath.matrix :refer (mat->float-array)]
            [malli.core :as m]
            [sfsim25.matrix :refer (fvec3 fmat3 fmat4 shadow-box)]
            [sfsim25.util :refer (N image)])
  (:import [org.lwjgl.opengl GL GL11 GL12 GL13 GL14 GL15 GL20 GL30 GL32 GL40 GL42 GL45]
           [org.lwjgl BufferUtils]
           [org.lwjgl.glfw GLFW]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; Malli schema for recursive vector of strings
(def shaders (m/schema [:schema {:registry {::node [:sequential [:or :string [:ref ::node]]]}}
                        [:ref ::node]]))

(defn setup-rendering
  "Common code for setting up rendering"
  {:malli/schema [:=> [:cat N N [:or [:= :cullfront] [:= :cullback]]] :nil]}
  [width height culling]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace ({:cullfront GL11/GL_FRONT :cullback GL11/GL_BACK} culling))
  (GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
  (GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE))

(defmacro with-stencils
  "Enable stencil buffer for the specified body of code"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_STENCIL_TEST)
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_STENCIL_TEST)
       result#)))

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

(defn make-window
  "Method to create a window and make the context current and create the capabilities"
  {:malli/schema [:=> [:cat :string N N] :int]}
  [title width height]
  (GLFW/glfwDefaultWindowHints)
  (let [window (GLFW/glfwCreateWindow ^long width ^long height ^String title 0 0)]
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwShowWindow window)
    (GL/createCapabilities)
    window))

(defn destroy-window
  "Destroy the window"
  {:malli/schema [:=> [:cat :int] :nil]}
  [window]
  (GLFW/glfwDestroyWindow window))

(defmacro onscreen-render
  "Macro to use the specified window for rendering"
  [window & body]
  `(let [width#  (int-array 1)
         height# (int-array 1)]
     (GLFW/glfwGetWindowSize ~(vary-meta window assoc :tag 'long) width# height#)
     (setup-rendering (aget width# 0) (aget height# 0) :cullback)
     ~@body
     (GLFW/glfwSwapBuffers ~window)))

(defn clear
  "Set clear color and clear color buffer as well as depth buffer"
  {:malli/schema [:=> [:cat [:? [:cat fvec3 [:? [:cat :double [:? :int]]]]]] :nil]}
  ([]
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear GL11/GL_DEPTH_BUFFER_BIT))
  ([color]
   (clear color 1.0))
  ([color alpha]
   (GL11/glClearColor (color 0) (color 1) (color 2) alpha)
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT)))
  ([color alpha stencil]
   (GL11/glClearColor (color 0) (color 1) (color 2) alpha)
   (GL11/glClearDepth 0.0) ; Reversed-z rendering requires initial depth to be zero.
   (GL11/glClearStencil stencil)
   (GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT GL11/GL_STENCIL_BUFFER_BIT))))

(defn make-shader
  "Compile a GLSL shader"
  {:malli/schema [:=> [:cat :string :int :string] :int]}
  [context shader-type source]
  (let [shader (GL20/glCreateShader shader-type)]
    (GL20/glShaderSource shader ^String source)
    (GL20/glCompileShader shader)
    (when (zero? (GL20/glGetShaderi shader GL20/GL_COMPILE_STATUS))
      (throw (Exception. (str context " shader: " (GL20/glGetShaderInfoLog shader 1024)))))
    shader))

(defn destroy-shader
  "Delete a shader"
  {:malli/schema [:=> [:cat :int] :nil]}
  [shader]
  (GL20/glDeleteShader shader))

(defn make-program
  "Compile and link a shader program"
  {:malli/schema [:=> [:cat
                       [:cat [:= :vertex] shaders]
                       [:? [:cat [:= :tess-control] shaders]]
                       [:? [:cat [:= :tess-evaluation] shaders]]
                       [:? [:cat [:= :geometry] shaders]]
                       [:cat [:= :fragment] shaders]]
                  :int]}
  [& {:keys [vertex tess-control tess-evaluation geometry fragment]
      :or {vertex [] tess-control [] tess-evaluation [] geometry [] fragment []}}]
  (let [shaders (concat (map (partial make-shader "vertex" GL20/GL_VERTEX_SHADER)
                             (distinct (flatten vertex)))
                        (map (partial make-shader "tessellation control" GL40/GL_TESS_CONTROL_SHADER)
                             (distinct (flatten tess-control)))
                        (map (partial make-shader "tessellation evaluation" GL40/GL_TESS_EVALUATION_SHADER)
                             (distinct (flatten tess-evaluation)))
                        (map (partial make-shader "geometry" GL32/GL_GEOMETRY_SHADER)
                             (distinct (flatten geometry)))
                        (map (partial make-shader "fragment" GL20/GL_FRAGMENT_SHADER)
                             (distinct (flatten fragment))))
        program (GL20/glCreateProgram)]
    (doseq [shader shaders] (GL20/glAttachShader program shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    (doseq [shader shaders] (destroy-shader shader))
    program))

(defn destroy-program
  "Delete a program and associated shaders"
  {:malli/schema [:=> [:cat :int] :nil]}
  [program]
  (GL20/glDeleteProgram program))

(defn make-float-buffer
  "Create a floating-point buffer object"
  {:malli/schema [:=> [:cat seqable?] :some]}
  [data]
  (doto (BufferUtils/createFloatBuffer (count data))
    (.put ^floats data)
    (.flip)))

(defn make-int-buffer
  "Create a integer buffer object"
  {:malli/schema [:=> [:cat seqable?] :some]}
  [data]
  (doto (BufferUtils/createIntBuffer (count data))
    (.put ^ints data)
    (.flip)))

(defn make-byte-buffer
  "Create a byte buffer object"
  {:malli/schema [:=> [:cat bytes?] :some]}
  [data]
  (doto (BufferUtils/createByteBuffer (count data))
    (.put ^bytes data)
    (.flip)))

(def vertex-array-object
  (m/schema [:map [:vertex-array-object :int] [:array-buffer :int] [:index-buffer :int] [:nrows N] [:ncols N]]))

(defn make-vertex-array-object
  "Create vertex array object and vertex buffer objects"
  {:malli/schema [:=> [:cat :int [:vector :int] [:vector number?] [:vector [:or :string N]]] vertex-array-object]}
  [program indices vertices attributes]
  (let [vertex-array-object (GL30/glGenVertexArrays)]
    (GL30/glBindVertexArray vertex-array-object)
    (let [array-buffer (GL15/glGenBuffers)
          index-buffer (GL15/glGenBuffers)]
      (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer)
      (GL15/glBufferData GL15/GL_ARRAY_BUFFER ^java.nio.DirectFloatBufferU (make-float-buffer (float-array vertices))
                         GL15/GL_STATIC_DRAW)
      (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER index-buffer)
      (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER ^java.nio.DirectIntBufferU (make-int-buffer (int-array indices))
                         GL15/GL_STATIC_DRAW)
      (let [attribute-pairs (partition 2 attributes)
            sizes           (map second attribute-pairs)
            stride          (apply + sizes)
            offsets         (reductions + (cons 0 (butlast sizes)))]
        (doseq [[i [attribute size] offset] (map list (range) attribute-pairs offsets)]
          (GL20/glVertexAttribPointer (GL20/glGetAttribLocation ^long program ^String attribute) ^long size
                                      GL11/GL_FLOAT false ^long (* stride Float/BYTES) ^long (* offset Float/BYTES))
          (GL20/glEnableVertexAttribArray i))
        {:vertex-array-object vertex-array-object
         :array-buffer        array-buffer
         :index-buffer        index-buffer
         :nrows               (count indices)
         :ncols               (count attribute-pairs)}))))

(defn destroy-vertex-array-object
  "Destroy vertex array object and vertex buffer objects"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [{:keys [vertex-array-object array-buffer index-buffer ncols]}]
  (GL30/glBindVertexArray vertex-array-object)
  (doseq [i (range ncols)] (GL20/glDisableVertexAttribArray i))
  (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers ^long index-buffer)
  (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
  (GL15/glDeleteBuffers ^long array-buffer)
  (GL30/glBindVertexArray 0)
  (GL30/glDeleteVertexArrays ^long vertex-array-object))

(defn use-program
  "Use specified shader program"
  {:malli/schema [:=> [:cat :int] :nil]}
  [program]
  (GL20/glUseProgram ^long program))

(defn- uniform-location
  "Get index of uniform variable"
  {:malli/schema [:=> [:cat :int :string] :int]}
  [program k]
  (GL20/glGetUniformLocation ^long program ^String k))

(defn uniform-float
  "Set uniform float variable in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :double] :nil]}
  [program k value]
  (GL20/glUniform1f ^long (uniform-location program k) value))

(defn uniform-int
  "Set uniform integer variable in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :int] :nil]}
  [program k value]
  (GL20/glUniform1i ^long (uniform-location program k) value))

(defn uniform-vector3
  "Set uniform 3D vector in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fvec3] :nil]}
  [program k value]
  (GL20/glUniform3f ^long (uniform-location program k) (value 0) (value 1) (value 2)))

(defn uniform-matrix3
  "Set uniform 3x3 matrix in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fmat3] :nil]}
  [program k value]
  (GL20/glUniformMatrix3fv ^long (uniform-location program k) true
                           ^java.nio.DirectFloatBufferU (make-float-buffer (mat->float-array value))))

(defn uniform-matrix4
  "Set uniform 4x4 matrix in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fmat4] :nil]}
  [program k value]
  (GL20/glUniformMatrix4fv ^long (uniform-location program k) true
                           ^java.nio.DirectFloatBufferU (make-float-buffer (mat->float-array value))))

(defn uniform-sampler
  "Set index of uniform sampler in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :int] :nil]}
  [program k value]
  (GL20/glUniform1i ^long (uniform-location program k) value))

(defn- setup-vertex-array-object
  "Initialise rendering of a vertex array object"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (GL30/glBindVertexArray ^long (:vertex-array-object vertex-array-object)))

(defn render-quads
  "Render one or more quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_QUADS ^long (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-triangles
  "Render one or more triangles"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_TRIANGLES ^long (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-patches
  "Render one or more tessellated quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
  (GL11/glDrawElements GL40/GL_PATCHES ^long (:nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defmacro raster-lines
  "Macro for temporarily switching polygon rasterization to line mode"
  [& body]
  `(do
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
     ~@body
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)))

(def texture (m/schema [:map [:target :int] [:texture :int]]))
(def texture-1d (m/schema [:map [:width N] [:target :int] [:texture :int]]))
(def texture-2d (m/schema [:map [:width N] [:height N] [:target :int] [:texture :int]]))
(def texture-3d (m/schema [:map [:width N] [:height N] [:depth N] [:target :int] [:texture :int]]))
(def texture-4d (m/schema [:map [:width N] [:height N] [:depth N] [:hyperdepth N] [:target :int] [:texture :int]]))

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
  {:malli/schema [:=> [:cat texture] :nil]}
  [texture]
  (let [target (:target texture)]
    (with-texture target (:texture texture)
      (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR_MIPMAP_LINEAR)
      (GL30/glGenerateMipmap target))))

(def interpolation (m/schema [:or [:= :nearest] [:= :linear]]))

(defmulti setup-interpolation (comp second vector))
(m/=> setup-interpolation [:=> [:cat :int interpolation] :nil])

(defmethod setup-interpolation :nearest
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_NEAREST)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_NEAREST))

(defmethod setup-interpolation :linear
  [target _interpolation]
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MIN_FILTER GL11/GL_LINEAR)
  (GL11/glTexParameteri target GL11/GL_TEXTURE_MAG_FILTER GL11/GL_LINEAR))

(def boundary (m/schema [:or [:= :clamp] [:= :repeat]]))

(defmulti setup-boundary-1d identity)
(m/=> setup-boundary-1d [:=> [:cat boundary] :nil])

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
(m/=> setup-boundary-2d [:=> [:cat boundary] :nil])

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
(m/=> setup-boundary-3d [:=> [:cat :int boundary] :nil])

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
  {:malli/schema [:=> [:cat interpolation boundary seqable?] texture-1d]}
  [interpolation boundary data]
  (let [buffer (make-float-buffer data)
        width  (count data) ]
    (create-texture-1d interpolation boundary width
                       (GL11/glTexImage1D GL11/GL_TEXTURE_1D 0 GL30/GL_R32F width 0 GL11/GL_RED GL11/GL_FLOAT
                                          ^java.nio.DirectFloatBufferU buffer))))

(defn- make-byte-texture-2d-base
  "Initialise a 2D byte texture"
  {:malli/schema [:=> [:cat image interpolation boundary :int :int :int] texture-2d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer (make-byte-buffer (:data image))]
    (create-texture-2d interpolation boundary (:width image) (:height image)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long (:width image) ^long (:height image) 0
                         ^long format_ ^long type_ ^java.nio.DirectByteBuffer buffer))))

(defn- make-float-texture-2d-base
  "Initialise a 2D texture"
  {:malli/schema [:=> [:cat image interpolation boundary :int :int :int] texture-2d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer (make-float-buffer (:data image))]
    (create-texture-2d interpolation boundary (:width image) (:height image)
      (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long (:width image) ^long (:height image) 0
                         ^long format_ ^long type_ ^java.nio.DirectFloatBufferU buffer))))


(defn make-rgb-texture
  "Load image into an RGB OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RGB GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))

(defn make-rgba-texture
  "Load image into an RGBA OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RGBA GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE))

(def byte-image (m/schema [:map [:width N] [:height N] [:data bytes?]]))
(def float-image-2d (m/schema [:map [:width N] [:height N] [:data seqable?]]))
(def float-image-3d (m/schema [:map [:width N] [:height N] [:depth N] [:data seqable?]]))
(def float-image-4d (m/schema [:map [:width N] [:height N] [:depth N] [:hyperdepth N] [:data seqable?]]))

(defn make-depth-texture
  "Load floating-point values into a shadow map"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (assoc (create-depth-texture interpolation boundary (:width image) (:height image)
                               (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 GL30/GL_DEPTH_COMPONENT32F
                                                  ^long (:width image) ^long (:height image) 0
                                                  GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT
                                                  ^java.nio.DirectFloatBufferU (make-float-buffer (:data image))))
         :stencil false))

(defn make-empty-texture-2d
  "Create 2D texture with specified format and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary :int N N] texture-2d]}
  [interpolation boundary internalformat width height]
  (create-texture-2d interpolation boundary width height
                     (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 internalformat width height)))

(defn make-empty-float-texture-2d
  "Create 2D floating-point texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (make-empty-texture-2d interpolation boundary GL30/GL_R32F width height))

(defn make-empty-depth-texture-2d
  "Create 2D depth texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (assoc (create-depth-texture interpolation boundary width height
                               (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH_COMPONENT32F width height))
         :stencil false))

(defn make-empty-depth-stencil-texture-2d
  "Create 2D depth texture and allocate storage"
  {:malli/schema [:=> [:cat interpolation boundary N N] texture-2d]}
  [interpolation boundary width height]
  (assoc (create-depth-texture interpolation boundary width height
                               (GL42/glTexStorage2D GL11/GL_TEXTURE_2D 1 GL30/GL_DEPTH32F_STENCIL8 width height))
         :stencil true))

(defn make-float-texture-2d
  "Load floating-point 2D data into red channel of an OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (make-float-texture-2d-base image interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))

(defn make-ubyte-texture-2d
  "Load unsigned-byte 2D data into red channel of an OpenGL texture (data needs to be 32-bit aligned!)"
  {:malli/schema [:=> [:cat interpolation boundary byte-image] texture-2d]}
  [interpolation boundary image]
  (make-byte-texture-2d-base image interpolation boundary GL11/GL_RED GL11/GL_RED GL11/GL_UNSIGNED_BYTE))

(defn make-vector-texture-2d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-2d] texture-2d]}
  [interpolation boundary image]
  (make-float-texture-2d-base image interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))

(defn make-float-texture-3d
  "Load floating-point 3D data into red channel of an OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-3d] texture-3d]}
  [interpolation boundary image]
  (let [buffer (make-float-buffer (:data image))]
    (create-texture-3d interpolation boundary (:width image) (:height image) (:depth image)
      (GL12/glTexImage3D GL12/GL_TEXTURE_3D 0 GL30/GL_R32F ^long (:width image) ^long (:height image) ^long (:depth image) 0
                         GL11/GL_RED GL11/GL_FLOAT ^java.nio.DirectFloatBufferU buffer))))

(defn make-empty-float-texture-3d
  "Create empty 3D floating-point texture"
  {:malli/schema [:=> [:cat interpolation boundary N N N] texture-3d]}
  [interpolation boundary width height depth]
  (create-texture-3d interpolation boundary width height depth
                     (GL42/glTexStorage3D GL12/GL_TEXTURE_3D 1 GL30/GL_R32F width height depth)))

(defn- make-float-texture-4d
  "Initialise a 2D texture"
  {:malli/schema [:=> [:cat float-image-4d interpolation boundary :int :int :int] texture-4d]}
  [image interpolation boundary internalformat format_ type_]
  (let [buffer     (make-float-buffer (:data image))
        width      ^long (:width image)
        height     ^long (:height image)
        depth      ^long (:depth image)
        hyperdepth ^long (:hyperdepth image)]
    (assoc (create-texture-2d interpolation boundary (* width depth) (* height hyperdepth)
             (GL11/glTexImage2D GL11/GL_TEXTURE_2D 0 ^long internalformat ^long (* width depth) ^long (* height hyperdepth) 0
                                ^long format_ ^long type_ ^java.nio.DirectFloatBufferU buffer))
           :width width
           :height height
           :depth depth
           :hyperdepth hyperdepth)))

(defn make-vector-texture-4d
  "Load floating point 2D array of 3D vectors into OpenGL texture"
  {:malli/schema [:=> [:cat interpolation boundary float-image-4d] texture-4d]}
  [interpolation boundary image]
  (make-float-texture-4d image interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))

(defn destroy-texture
  "Delete an OpenGL texture"
  {:malli/schema [:=> [:cat texture] :nil]}
  [texture]
  (GL11/glDeleteTextures ^long (:texture texture)))

(defn use-texture
  "Set texture with specified index"
  {:malli/schema [:=> [:cat :int texture] :nil]}
  [index texture]
  (GL13/glActiveTexture ^long (+ GL13/GL_TEXTURE0 index))
  (GL11/glBindTexture (:target texture) (:texture texture)))

(defn use-textures
  "Specify textures to be used in the next rendering operation"
  {:malli/schema [:=> [:cat [:map-of :int texture]] :nil]}
  [textures]
  (doseq [[index texture] textures] (use-texture index texture)))

(defn- list-texture-layers
  "Return 2D textures and each layer of 3D textures"
  {:malli/schema [:=> [:cat [:sequential texture]] [:sequential texture]]}
  [textures]
  (flatten
    (map (fn [texture]
             (if (:depth texture)
               (map (fn [layer] (assoc texture :layer layer)) (range (:depth texture)))
               texture))
         textures)))

(defn setup-color-attachments
  "Setup color attachments with 2D and 3D textures"
  {:malli/schema [:=> [:cat [:sequential texture]] :nil]}
  [textures]
  (GL20/glDrawBuffers
    ^java.nio.DirectIntBufferU
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
         (let [attachment-type# (if (:stencil ~depth-texture) GL30/GL_DEPTH_STENCIL_ATTACHMENT GL30/GL_DEPTH_ATTACHMENT)]
           (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER attachment-type# (:texture ~depth-texture) 0)))
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

(defmacro texture-render-color-depth
  "Macro to render to a 2D color texture using ad depth buffer"
  [width height floating-point & body]
  `(let [internalformat# (if ~floating-point GL30/GL_RGBA32F GL11/GL_RGBA8)
         texture#        (make-empty-texture-2d :linear :clamp internalformat# ~width ~height)
         depth#          (make-empty-depth-texture-2d :linear :clamp ~width ~height)]
     (framebuffer-render ~width ~height :cullback depth# [texture#] ~@body)
     (destroy-texture depth#)
     texture#))

(defmacro texture-render-depth
  "Macro to create shadow map"
  [width height & body]
  `(let [tex# (make-empty-depth-texture-2d :linear :clamp ~width ~height)]
     (framebuffer-render ~width ~height :cullfront tex# [] ~@body tex#)))

(defn shadow-cascade
  "Render cascaded shadow map"
  {:malli/schema [:=> [:cat :int [:vector shadow-box] :int fn?] [:vector texture-2d]]}
  [size matrix-cascade program fun]
  (mapv
    (fn [shadow-level]
        (texture-render-depth size size
                              (clear)
                              (use-program program)
                              (fun (:shadow-ndc-matrix shadow-level))))
    matrix-cascade))

(defn depth-texture->floats
  "Extract floating-point depth map from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_DEPTH_COMPONENT GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn float-texture-2d->floats
  "Extract floating-point floating-point data from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn float-texture-3d->floats
  "Extract floating-point floating-point data from texture"
  {:malli/schema [:=> [:cat texture-3d] float-image-3d]}
  [{:keys [target texture width height depth]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height depth))
          data (float-array (* width height depth))]
      (GL11/glGetTexImage GL12/GL_TEXTURE_3D 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :depth depth :data data})))

(defn rgb-texture->vectors3
  "Extract floating-point RGB vectors from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
          data (float-array (* width height 3))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn rgba-texture->vectors4
  "Extract floating-point RGBA vectors from texture"
  {:malli/schema [:=> [:cat texture-2d] float-image-2d]}
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 4))
          data (float-array (* width height 4))]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn texture->image
  "Convert texture to RGB image"
  {:malli/schema [:=> [:cat texture-2d] byte-image]}
  [{:keys [target texture width height]}]
  (with-texture target texture
    (let [size (* 4 width height)
          buf  (BufferUtils/createByteBuffer size)
          data (byte-array size)]
      (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE buf)
      (.get buf data)
      {:width width :height height :data data})))

(defmacro offscreen-render
  "Macro to render to a texture using depth and stencil buffer and convert it to an image"
  [width height & body]
  `(with-invisible-window
     (let [depth# (make-empty-depth-stencil-texture-2d :linear :clamp ~width ~height)
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
  {:malli/schema [:=> [:cat [:vector float-image-2d] interpolation boundary :int :int :int] texture-3d]}
  [images interpolation boundary internalformat format_ type_]
  (let [size (:width (first images))]
    (create-cubemap interpolation boundary size
      (doseq [[face image] (map-indexed vector images)]
             (GL11/glTexImage2D ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 ^long internalformat ^long size ^long size
                                0 ^long format_ ^long type_ ^java.nio.DirectFloatBufferU (make-float-buffer (:data image)))))))

(defn make-float-cubemap
  "Load floating-point 2D textures into red channel of an OpenGL cubemap"
  {:malli/schema [:=> [:cat interpolation boundary [:sequential float-image-2d]] texture-3d]}
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_R32F GL11/GL_RED GL11/GL_FLOAT))

(defn make-empty-float-cubemap
  "Create empty cubemap with faces of specified size"
  {:malli/schema [:=> [:cat interpolation boundary :int] texture-3d]}
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_R32F size size)))

(defn float-cubemap->floats
  "Extract floating-point data from cubemap face"
  {:malli/schema [:=> [:cat texture-3d :int] float-image-2d]}
  [{:keys [target texture width height]} face]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height))
          data (float-array (* width height))]
      (GL11/glGetTexImage ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL11/GL_RED GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(defn make-vector-cubemap
  "Load vector 2D textures into an OpenGL cubemap"
  {:malli/schema [:=> [:cat interpolation boundary [:vector float-image-2d]] texture-3d]}
  [interpolation boundary images]
  (make-cubemap images interpolation boundary GL30/GL_RGB32F GL12/GL_RGB GL11/GL_FLOAT))

(defn make-empty-vector-cubemap
  "Create empty cubemap with faces of specified size"
  {:malli/schema [:=> [:cat interpolation boundary :int] texture-3d]}
  [interpolation boundary size]
  (create-cubemap interpolation boundary size
                  (GL42/glTexStorage2D GL13/GL_TEXTURE_CUBE_MAP 1 GL30/GL_RGB32F size size)))

(defn vector-cubemap->vectors3
  "Extract floating-point vector data from cubemap face"
  {:malli/schema [:=> [:cat texture-3d :int] float-image-2d]}
  [{:keys [target texture width height]} face]
  (with-texture target texture
    (let [buf  (BufferUtils/createFloatBuffer (* width height 3))
          data (float-array (* width height 3))]
      (GL11/glGetTexImage ^long (+ GL13/GL_TEXTURE_CUBE_MAP_POSITIVE_X face) 0 GL12/GL_RGB GL11/GL_FLOAT buf)
      (.get buf data)
      {:width width :height height :data data})))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
