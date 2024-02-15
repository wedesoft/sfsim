(ns sfsim.render
  "Functions for doing OpenGL rendering"
  (:require [clojure.math :refer (sqrt cos sin asin hypot)]
            [fastmath.vector :refer (mag)]
            [fastmath.matrix :refer (mat->float-array)]
            [malli.core :as m]
            [sfsim.matrix :refer (fvec3 fmat3 fmat4 shadow-box quaternion->matrix transformation-matrix projection-matrix)]
            [sfsim.quaternion :refer (quaternion) :as q]
            [sfsim.util :refer (sqr N)]
            [sfsim.texture :refer (make-int-buffer make-float-buffer make-empty-texture-2d make-empty-depth-texture-2d
                                   make-empty-depth-stencil-texture-2d texture->image destroy-texture texture texture-2d)])
  (:import [org.lwjgl.opengl GL GL11 GL13 GL15 GL20 GL30 GL32 GL40 GL45]
           [org.lwjgl.glfw GLFW]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

; Malli schema for recursive vector of strings
(def shaders (m/schema [:schema {:registry {::node [:sequential [:or :string [:ref ::node]]]}}
                        [:ref ::node]]))

(defn setup-rendering
  "Common code for setting up rendering"
  {:malli/schema [:=> [:cat N N [:or [:= ::cullfront] [:= ::cullback]]] :nil]}
  [width height culling]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_DEPTH_TEST)
  (GL11/glEnable GL11/GL_CULL_FACE)
  (GL11/glCullFace ({::cullfront GL11/GL_FRONT ::cullback GL11/GL_BACK} culling))
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
     (setup-rendering (aget width# 0) (aget height# 0) ::cullback)
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
            offsets         (reductions + (cons 0 (butlast sizes)))
            attr-locations  (for [[[attribute size] offset] (map list attribute-pairs offsets)]
                                 (let [location (GL20/glGetAttribLocation ^long program ^String attribute)]
                                   (GL20/glVertexAttribPointer location ^long size GL11/GL_FLOAT false
                                                               ^long (* stride Float/BYTES) ^long (* offset Float/BYTES))
                                   (GL20/glEnableVertexAttribArray location)
                                   location))]
        {::vertex-array-object vertex-array-object
         ::array-buffer        array-buffer
         ::index-buffer        index-buffer
         ::attribute-locations (vec attr-locations)
         ::nrows               (count indices)}))))

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
       (setup-rendering ~width ~height ~culling)
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

(defn render-depth
  "Determine maximum shadow depth for cloud shadows"
  {:malli/schema [:=> [:cat :double :double :double] :double]}
  [radius max-height cloud-top]
  (+ (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
     (sqrt (- (sqr (+ radius cloud-top)) (sqr radius)))))

(defn diagonal-field-of-view
  "Compute diagonal field of view angle"
  {:malli/schema [:=> [:cat :int :int :double] :double]}
  [width height fov]
  (let [dx  (sin (* 0.5 fov))
        dy  (/ (* height dx) width)
        dxy (hypot dx dy)]
    (* 2.0 (asin dxy))))

(defn make-render-vars
  "Create hash map with render variables for rendering current frame"
  {:malli/schema [:=> [:cat :map :map :map N N fvec3 quaternion fvec3 :double] :map]}
  [planet-config cloud-data render-config window-width window-height position orientation light-direction min-z-near]
  (let [distance     (mag position)
        radius       (:sfsim.planet/radius planet-config)
        cloud-top    (:sfsim.clouds/cloud-top cloud-data)
        fov          (::fov render-config)
        height       (- distance radius)
        diagonal-fov (diagonal-field-of-view window-width window-height fov)
        z-near       (* (max (- height cloud-top) min-z-near) (cos (* 0.5 diagonal-fov)))
        z-far        (render-depth radius height cloud-top)
        rotation     (quaternion->matrix orientation)
        extrinsics   (transformation-matrix rotation position)
        z-offset     1.0
        projection   (projection-matrix window-width window-height z-near (+ z-far z-offset) fov)]
    {::origin position
     ::height height
     ::z-near z-near
     ::z-far z-far
     ::window-width window-width
     ::window-height window-height
     ::light-direction light-direction
     ::extrinsics extrinsics
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

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
