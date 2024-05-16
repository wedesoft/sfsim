(ns sfsim.render
  "Functions for doing OpenGL rendering"
  (:require [clojure.math :refer (sin asin hypot)]
            [fastmath.matrix :refer (mat->float-array)]
            [malli.core :as m]
            [sfsim.matrix :refer (fvec3 fmat3 fmat4 shadow-box transformation-matrix quaternion->matrix projection-matrix)]
            [sfsim.quaternion :refer (quaternion)]
            [sfsim.util :refer (N)]
            [sfsim.texture :refer (make-int-buffer make-float-buffer make-empty-texture-2d make-empty-depth-texture-2d
                                   make-empty-depth-stencil-texture-2d texture->image destroy-texture texture texture-2d)])
  (:import [org.lwjgl.opengl GL GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40 GL45]
           [org.lwjgl.glfw GLFW]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; Malli schema for recursive vector of strings
(def shaders (m/schema [:schema {:registry {::node [:sequential [:or :string [:ref ::node]]]}}
                        [:ref ::node]]))

(defn setup-rendering
  "Common code for setting up rendering"
  {:malli/schema [:=> [:cat N N [:enum ::cullfront ::cullback ::noculling] :boolean] :nil]}
  [width height culling depth-test]
  (GL11/glViewport 0 0 width height)
  (if (= culling ::noculling)
    (GL11/glDisable GL11/GL_CULL_FACE)
    (do
      (GL11/glEnable GL11/GL_CULL_FACE)
      (GL11/glCullFace ({::cullfront GL11/GL_FRONT ::cullback GL11/GL_BACK} culling))))
  (if depth-test
    (GL11/glEnable GL11/GL_DEPTH_TEST)
    (GL11/glDisable GL11/GL_DEPTH_TEST))
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

(defn write-to-stencil-buffer
  "Write to stencil buffer when rendering"
  []
  (GL11/glStencilFunc GL11/GL_ALWAYS 1 0xff)
  (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_REPLACE)
  (GL11/glStencilMask 0xff))

(defn mask-with-stencil-buffer
  "Only render where stencil buffer is not set"
  []
  (GL11/glStencilFunc GL12/GL_NOTEQUAL 1 0xff)
  (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_REPLACE)
  (GL11/glStencilMask 0))

(defmacro with-blending
  "Enable alpha blending for the specified body of code"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_BLEND)
     (GL14/glBlendEquation GL14/GL_FUNC_ADD)
     (GL14/glBlendFunc GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA)
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_BLEND)
       result#)))

(defmacro with-scissor
  "Enable scissor test for the specified body of code"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_SCISSOR_TEST)
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_SCISSOR_TEST)
       result#)))

(defn set-scissor
  {:malli/schema [:=> [:cat number? number? number? number?] :nil]}
  [x y w h]
  (GL11/glScissor (int x) (int y) (int w) (int h)))

(defn setup-window-hints
  "Set GLFW window hints"
  [visible]
  (GLFW/glfwDefaultWindowHints)
  (GLFW/glfwWindowHint GLFW/GLFW_DEPTH_BITS 24)
  (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)
  (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE (if visible GLFW/GLFW_TRUE GLFW/GLFW_FALSE)))

(defmacro with-invisible-window
  "Macro to create temporary invisible window to provide context"
  [& body]
  `(do
     (setup-window-hints false)
     (let [window# (GLFW/glfwCreateWindow 8 2 "sfsim" 0 0)]
       (GLFW/glfwMakeContextCurrent window#)
       (GL/createCapabilities)
       (let [result# (do ~@body)]
         (GLFW/glfwDestroyWindow window#)
         result#))))

(defn make-window
  "Method to create a window and make the context current and create the capabilities"
  {:malli/schema [:=> [:cat :string N N] :int]}
  [title width height]
  (setup-window-hints true)
  (let [window (GLFW/glfwCreateWindow ^long width ^long height ^String title 0 0)]
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwShowWindow window)
    (GL/createCapabilities)
    (GLFW/glfwSwapInterval 1)
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
     (setup-rendering (aget width# 0) (aget height# 0) ::cullback true)
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
   (GL11/glStencilMask 0xff)
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
                       [:cat [:= ::vertex] shaders]
                       [:? [:cat [:= ::tess-control] shaders]]
                       [:? [:cat [:= ::tess-evaluation] shaders]]
                       [:? [:cat [:= ::geometry] shaders]]
                       [:cat [:= ::fragment] shaders]]
                  :int]}
  [& {::keys [vertex tess-control tess-evaluation geometry fragment]
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

(def vertex-array-object
  (m/schema [:map [::vertex-array-object :int] [::array-buffer :int] [::index-buffer :int] [::nrows N]
                  [::attribute-locations [:vector :int]]]))

(defn set-static-float-buffer-data
  "Use glBufferData to set up static buffer data"
  {:malli/schema [:=> [:cat :int :some] :nil]}
  [target data]
  (GL15/glBufferData ^int target ^java.nio.DirectFloatBufferU data GL15/GL_STATIC_DRAW))

(defn set-static-int-buffer-data
  "Use glBufferData to set up static buffer data"
  {:malli/schema [:=> [:cat :int :some] :nil]}
  [target data]
  (GL15/glBufferData ^int target ^java.nio.DirectIntBufferU data GL15/GL_STATIC_DRAW))

(defn opengl-type-size
  "Get byte size of OpenGL type"
  {:malli/schema [:=> [:cat :int] :int]}
  [opengl-type]
  (cond
        (= opengl-type GL11/GL_UNSIGNED_BYTE)  Byte/BYTES
        (= opengl-type GL11/GL_UNSIGNED_SHORT) Short/BYTES
        (= opengl-type GL11/GL_UNSIGNED_INT)   Integer/BYTES
        (= opengl-type GL11/GL_FLOAT)          Float/BYTES
        (= opengl-type GL11/GL_DOUBLE)         Double/BYTES))


(defn setup-vertex-attrib-pointers
  "Set up mappings from vertex array buffer row to vertex shader attributes"
  {:malli/schema [:=> [:cat :int [:and sequential? [:repeat [:cat :int :string N]]]] [:vector :int]]}
  [program attributes]
  (let [attribute-pairs (partition 3 attributes)
        sizes           (map (fn [[opengl-type _name size]] (* (opengl-type-size opengl-type) size) ) attribute-pairs)
        stride          (apply + sizes)
        offsets         (reductions + 0 (butlast sizes))
        attr-locations  (for [[[opengl-type attribute size] offset] (map list attribute-pairs offsets)]
                             (let [location (GL20/glGetAttribLocation ^long program ^String attribute)]
                               (GL20/glVertexAttribPointer location ^long size ^long opengl-type false ^long stride ^long offset)
                               (GL20/glEnableVertexAttribArray location)
                               location))]
    (vec attr-locations)))

(defn make-vertex-array-object
  "Create vertex array object and vertex buffer objects using integers for indices and floating point numbers for vertex data"
  {:malli/schema [:=> [:cat :int [:vector :int] [:vector number?] [:and sequential? [:repeat [:cat :string N]]]]
                      vertex-array-object]}
  [program indices vertices attributes]
  (let [float-attributes    (mapcat #(conj % GL11/GL_FLOAT) (partition 2 attributes))
        vertex-array-object (GL30/glGenVertexArrays)
        array-buffer        (GL15/glGenBuffers)
        index-buffer        (GL15/glGenBuffers)]
    (GL30/glBindVertexArray vertex-array-object)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer)
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER index-buffer)
    (set-static-float-buffer-data GL15/GL_ARRAY_BUFFER (make-float-buffer (float-array vertices)))
    (set-static-int-buffer-data GL15/GL_ELEMENT_ARRAY_BUFFER (make-int-buffer (int-array indices)))
    {::vertex-array-object vertex-array-object
     ::array-buffer        array-buffer
     ::index-buffer        index-buffer
     ::attribute-locations (setup-vertex-attrib-pointers program float-attributes)
     ::nrows               (count indices)}))

(defn destroy-vertex-array-object
  "Destroy vertex array object and vertex buffer objects"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [{::keys [vertex-array-object array-buffer index-buffer attribute-locations]}]
  (GL30/glBindVertexArray vertex-array-object)
  (doseq [location attribute-locations] (GL20/glDisableVertexAttribArray location))
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

(defn use-texture
  "Set texture with specified index"
  {:malli/schema [:=> [:cat :int texture] :nil]}
  [index texture]
  (GL13/glActiveTexture ^long (+ GL13/GL_TEXTURE0 index))
  (GL11/glBindTexture (:sfsim.texture/target texture) (:sfsim.texture/texture texture)))

(defn use-textures
  "Specify textures to be used in the next rendering operation"
  {:malli/schema [:=> [:cat [:map-of :int texture]] :nil]}
  [textures]
  (doseq [[index texture] textures] (use-texture index texture)))

(defn- setup-vertex-array-object
  "Initialise rendering of a vertex array object"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (GL30/glBindVertexArray ^long (::vertex-array-object vertex-array-object)))

(defn render-quads
  "Render one or more quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_QUADS ^long (::nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-triangles
  "Render one or more triangles"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL11/glDrawElements GL11/GL_TRIANGLES ^long (::nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defn render-patches
  "Render one or more tessellated quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  (setup-vertex-array-object vertex-array-object)
  (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
  (GL11/glDrawElements GL40/GL_PATCHES ^long (::nrows vertex-array-object) GL11/GL_UNSIGNED_INT 0))

(defmacro raster-lines
  "Macro for temporarily switching polygon rasterization to line mode"
  [& body]
  `(do
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_LINE)
     ~@body
     (GL11/glPolygonMode GL11/GL_FRONT_AND_BACK GL11/GL_FILL)))

(defn- list-texture-layers
  "Return 2D textures and each layer of 3D textures"
  {:malli/schema [:=> [:cat [:sequential texture]] [:sequential texture]]}
  [textures]
  (flatten
    (map (fn layers-of-texture [texture]
             (if (:sfsim.texture/depth texture)
               (map (fn layer-of-3d-texture [layer] (assoc texture :sfsim.texture/layer layer))
                    (range (:sfsim.texture/depth texture)))
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
          (fn setup-color-attachment [index texture]
              (let [color-attachment (+ GL30/GL_COLOR_ATTACHMENT0 index)]
                (if (:sfsim.texture/layer texture)
                  (GL30/glFramebufferTextureLayer GL30/GL_FRAMEBUFFER color-attachment (:sfsim.texture/texture texture) 0
                                                  (:sfsim.texture/layer texture))
                  (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER color-attachment (:sfsim.texture/texture texture) 0))
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
           (GL32/glFramebufferTexture GL30/GL_FRAMEBUFFER attachment-type# (:sfsim.texture/texture ~depth-texture) 0)))
       (setup-color-attachments ~color-textures)
       (setup-rendering ~width ~height ~culling (boolean ~depth-texture))
       ~@body
       (finally
         (GL30/glBindFramebuffer GL30/GL_FRAMEBUFFER 0)
         (GL30/glDeleteFramebuffers fbo#)))))

(defmacro texture-render-color
  "Macro to render to a 2D color texture"
  [width height floating-point & body]
  `(let [internalformat# (if ~floating-point GL30/GL_RGBA32F GL11/GL_RGBA8)
         texture#        (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp internalformat# ~width ~height)]
     (framebuffer-render ~width ~height ::cullback nil [texture#] ~@body)
     texture#))

(defmacro texture-render-color-depth
  "Macro to render to a 2D color texture using ad depth buffer"
  [width height floating-point & body]
  `(let [internalformat# (if ~floating-point GL30/GL_RGBA32F GL11/GL_RGBA8)
         texture#        (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp internalformat# ~width ~height)
         depth#          (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp ~width ~height)]
     (framebuffer-render ~width ~height ::cullback depth# [texture#] ~@body)
     (destroy-texture depth#)
     texture#))

(defmacro texture-render-depth
  "Macro to create shadow map"
  [width height & body]
  `(let [tex# (make-empty-depth-texture-2d :sfsim.texture/linear :sfsim.texture/clamp ~width ~height)]
     (framebuffer-render ~width ~height ::cullfront tex# [] ~@body tex#)))

(defn shadow-cascade
  "Render cascaded shadow map"
  {:malli/schema [:=> [:cat :int [:vector shadow-box] :int fn?] [:vector texture-2d]]}
  [size matrix-cascade program fun]
  (mapv
    (fn render-shadow-segment [shadow-level]
        (texture-render-depth size size
                              (clear)
                              (use-program program)
                              (fun (:sfsim.matrix/shadow-ndc-matrix shadow-level))))
    matrix-cascade))

(defmacro offscreen-render
  "Macro to render to a texture using depth and stencil buffer and convert it to an image"
  [width height & body]
  `(with-invisible-window
     (let [depth# (make-empty-depth-stencil-texture-2d :sfsim.texture/linear :sfsim.texture/clamp ~width ~height)
           tex#   (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGB8 ~width ~height)]
       (framebuffer-render ~width ~height ::cullback depth# [tex#] ~@body)
       (let [img# (texture->image tex#)]
         (destroy-texture tex#)
         (destroy-texture depth#)
         img#))))

(defn diagonal-field-of-view
  "Compute diagonal field of view angle"
  {:malli/schema [:=> [:cat :int :int :double] :double]}
  [width height fov]
  (let [dx  (sin (* 0.5 fov))
        dy  (/ (* height dx) width)
        dxy (hypot dx dy)]
    (* 2.0 (asin dxy))))

(def render-config (m/schema [:map [::amplification :double] [::specular :double] [::fov :double] [::min-z-near :double]]))

(def render-vars (m/schema [:map [::origin fvec3] [::z-near :double] [::z-far :double] [::window-width N]
                                 [::window-height N] [::light-direction fvec3] [::camera-to-world fmat4] [::projection fmat4]]))

(defn make-render-vars
  "Create hash map with render variables for rendering current frame with specified depth range"
  {:malli/schema [:=> [:cat [:map [::fov :double]] N N fvec3 quaternion fvec3 :double :double] render-vars]}
  [render-config window-width window-height position orientation light-direction z-near z-far]
  (let [fov             (::fov render-config)
        rotation        (quaternion->matrix orientation)
        camera-to-world (transformation-matrix rotation position)
        z-offset        1.0
        projection      (projection-matrix window-width window-height z-near (+ z-far z-offset) fov)]
    {::origin position
     ::z-near z-near
     ::z-far z-far
     ::fov fov
     ::window-width window-width
     ::window-height window-height
     ::light-direction light-direction
     ::camera-to-world camera-to-world
     ::projection projection}))

(defn joined-render-vars
  "Create hash map with render variables using joined z-range of input render variables"
  {:malli/schema [:=> [:cat render-vars render-vars] render-vars]}
  [vars-first vars-second]
  (let [fov             (::fov vars-first)
        window-width    (::window-width vars-first)
        window-height   (::window-height vars-first)
        position        (::origin vars-first)
        z-near          (min (::z-near vars-first) (::z-near vars-second))
        z-far           (max (::z-far vars-first) (::z-far vars-second))
        light-direction (::light-direction vars-first)
        camera-to-world (::camera-to-world vars-first)
        z-offset        1.0
        projection      (projection-matrix window-width window-height z-near (+ z-far z-offset) fov)]
    {::origin position
     ::z-near z-near
     ::z-far z-far
     ::fov fov
     ::window-width window-width
     ::window-height window-height
     ::light-direction light-direction
     ::camera-to-world camera-to-world
     ::projection projection}))

(defn setup-shadow-and-opacity-maps
  "Set up cascade of deep opacity maps and cascade of shadow maps"
  {:malli/schema [:=> [:cat :int :map :int] :nil]}
  [program shadow-data sampler-offset]
  (doseq [i (range (:sfsim.opacity/num-steps shadow-data))]
         (uniform-sampler program (str "shadow_map" i) (+ i sampler-offset)))
  (doseq [i (range (:sfsim.opacity/num-steps shadow-data))]
         (uniform-sampler program (str "opacity" i) (+ i sampler-offset (:sfsim.opacity/num-steps shadow-data))))
  (uniform-int program "num_opacity_layers" (:sfsim.opacity/num-opacity-layers shadow-data))
  (uniform-int program "shadow_size" (:sfsim.opacity/shadow-size shadow-data))
  (uniform-float program "depth" (:sfsim.opacity/depth shadow-data))
  (uniform-float program "shadow_bias" (:sfsim.opacity/shadow-bias shadow-data)))

(def shadow-matrix-vars (m/schema [:map [:sfsim.opacity/splits [:vector :double]]
                                        [:sfsim.opacity/matrix-cascade [:vector [:map [:sfsim.matrix/world-to-shadow-map fmat4]
                                                                                      [:sfsim.matrix/depth :double]]]]]))

(defn setup-shadow-matrices
  "Set up cascade of shadow matrices for rendering"
  {:malli/schema [:=> [:cat :int shadow-matrix-vars] :nil]}
  [program shadow-vars]
  (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
         (uniform-float program (str "split" idx) item))
  (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
         (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
         (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
