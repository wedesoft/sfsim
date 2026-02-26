;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.render
  "Functions for doing OpenGL rendering"
  (:require
    [clojure.math :refer (sin asin hypot)]
    [fastmath.matrix :refer (mulm mulv inverse mat->float-array)]
    [fastmath.vector :refer (vec3 vec4 mag)]
    [malli.core :as m]
    [sfsim.image :refer (get-pixel)]
    [sfsim.matrix :refer (fvec2 fvec3 fvec4 fmat3 fmat4 shadow-box transformation-matrix quaternion->matrix projection-matrix
                          vec4->vec3)]
    [sfsim.quaternion :refer (quaternion)]
    [sfsim.shaders :refer (vertex-passthrough)]
    [sfsim.texture :refer (make-int-buffer make-float-buffer make-empty-texture-2d make-empty-depth-texture-2d
                                           make-empty-depth-stencil-texture-2d texture->image destroy-texture texture texture-2d)]
    [sfsim.util :refer (N)])
  (:import
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.opengl
      GL
      GL11
      GL13
      GL14
      GL15
      GL20
      GL30
      GL32
      GL40
      GL45)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


;; Malli schema for recursive vector of strings
(def shaders
  (m/schema [:schema {:registry {::node [:sequential [:or :string [:ref ::node]]]}}
             [:ref ::node]]))


(defmacro with-culling
  "Use specified culling mode"
  [culling & body]
  `(let [mode# ~culling]
     (if (= mode# ::noculling)
       (GL11/glDisable GL11/GL_CULL_FACE)
       (do
         (GL11/glEnable GL11/GL_CULL_FACE)
         (GL11/glCullFace ({::cullfront GL11/GL_FRONT ::cullback GL11/GL_BACK} mode#))))
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_CULL_FACE)
       result#)))


(defmacro with-depth-test
  "Enable depth test if boolean is true"
  [enable & body]
  `(let [on# ~enable]
     (when on#
       (GL11/glEnable GL11/GL_DEPTH_TEST)
       (GL11/glDepthFunc GL11/GL_GEQUAL); Reversed-z rendering requires greater (or greater-equal) comparison function
       (GL45/glClipControl GL20/GL_LOWER_LEFT GL45/GL_ZERO_TO_ONE))
     (let [result# (do ~@body)]
       (when on#
         (GL11/glDisable GL11/GL_DEPTH_TEST))
       result#)))


(defmacro without-depth-test
  "Disable depth test temporarily"
  [& body]
  `(do
     (GL11/glDisable GL11/GL_DEPTH_TEST)
     (let [result# (do ~@body)]
       (GL11/glEnable GL11/GL_DEPTH_TEST)
       result#)))


(defmacro with-stencils
  "Enable stencil buffer for the specified body of code"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_STENCIL_TEST)
     (GL11/glStencilMask 0xff)
     (GL11/glStencilFunc GL11/GL_ALWAYS 0 0xff)
     (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_KEEP)
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_STENCIL_TEST)
       result#)))


(defmacro with-stencil-op-ref-and-mask
  "Write bits to stencil buffer if they are not all set"
  [op reference mask & body]
  `(do
     (GL11/glStencilFunc ~op ~reference ~mask)
     (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_REPLACE)
     (let [result# (do ~@body)]
       (GL11/glStencilFunc GL11/GL_ALWAYS 0 0xff)
       (GL11/glStencilOp GL11/GL_KEEP GL11/GL_KEEP GL11/GL_KEEP)
       result#)))


(defmacro with-overlay-blending
  "Render using transparency over existing colour buffer"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_BLEND)
     (GL14/glBlendEquation GL14/GL_FUNC_ADD)
     ; void glBlendFuncSeparate(GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha);
     (GL14/glBlendFuncSeparate GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA GL14/GL_ONE_MINUS_DST_ALPHA GL14/GL_ONE)
     (let [result# (do ~@body)]
       (GL11/glDisable GL11/GL_BLEND)
       result#)))


(defmacro with-underlay-blending
  "Render behind existing transparent colour buffer"
  [& body]
  `(do
     (GL11/glEnable GL11/GL_BLEND)
     (GL14/glBlendEquation GL14/GL_FUNC_ADD)
     ; void glBlendFuncSeparate(GLenum srcRGB, GLenum dstRGB, GLenum srcAlpha, GLenum dstAlpha);
     (GL14/glBlendFuncSeparate GL14/GL_ONE_MINUS_DST_ALPHA GL14/GL_ONE GL14/GL_ONE_MINUS_DST_ALPHA GL14/GL_ONE)
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


(definline set-scissor
  {:malli/schema [:=> [:cat number? number? number? number?] :nil]}
  [x y w h]
  `(GL11/glScissor (int ~x) (int ~y) (int ~w) (int ~h)))


(definline setup-window-hints
  "Set GLFW window hints"
  [visible border]
  `(do
     (GLFW/glfwDefaultWindowHints)
     (GLFW/glfwWindowHint GLFW/GLFW_DECORATED (if ~border GLFW/GLFW_TRUE GLFW/GLFW_FALSE))
     (GLFW/glfwWindowHint GLFW/GLFW_DEPTH_BITS 24)
     (GLFW/glfwWindowHint GLFW/GLFW_STENCIL_BITS 8)
     (GLFW/glfwWindowHint GLFW/GLFW_VISIBLE (if ~visible GLFW/GLFW_TRUE GLFW/GLFW_FALSE))))


(defmacro with-invisible-window
  "Macro to create temporary invisible window to provide context"
  [& body]
  `(do
     (setup-window-hints false true)
     (let [window# (GLFW/glfwCreateWindow 8 2 "sfsim" 0 0)]
       (GLFW/glfwMakeContextCurrent window#)
       (GL/createCapabilities)
       (let [result# (do ~@body)]
         (GLFW/glfwDestroyWindow window#)
         result#))))


(defn make-window
  "Method to create a window and make the context current and create the capabilities"
  {:malli/schema [:=> [:cat :string N N :boolean] :int]}
  [title width height border]
  (setup-window-hints true border)
  (let [window (GLFW/glfwCreateWindow ^long width ^long height ^String title 0 0)]
    (GLFW/glfwMakeContextCurrent window)
    (GLFW/glfwShowWindow window)
    (GL/createCapabilities)
    (GLFW/glfwSwapInterval 1)
    window))


(definline destroy-window
  "Destroy the window"
  {:malli/schema [:=> [:cat :int] :nil]}
  [window]
  `(GLFW/glfwDestroyWindow ~window))


(defmacro onscreen-render
  "Macro to use the specified window for rendering"
  [window & body]
  `(let [width#  (int-array 1)
         height# (int-array 1)]
     (GLFW/glfwGetWindowSize ~(vary-meta window assoc :tag 'long) width# height#)
     (GL11/glViewport 0 0 (aget width# 0) (aget height# 0))
     (with-culling ::cullback
       ~@body)
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
      (throw (Exception. (str context " shader: " (GL20/glGetShaderInfoLog shader 1024) "\n" source))))
    shader))


(definline destroy-shader
  "Delete a shader"
  {:malli/schema [:=> [:cat :int] :nil]}
  [shader]
  `(GL20/glDeleteShader ~shader))


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
  (let [shaders (concat (mapv (partial make-shader "vertex" GL20/GL_VERTEX_SHADER)
                              (distinct (flatten vertex)))
                        (mapv (partial make-shader "tessellation control" GL40/GL_TESS_CONTROL_SHADER)
                              (distinct (flatten tess-control)))
                        (mapv (partial make-shader "tessellation evaluation" GL40/GL_TESS_EVALUATION_SHADER)
                              (distinct (flatten tess-evaluation)))
                        (mapv (partial make-shader "geometry" GL32/GL_GEOMETRY_SHADER)
                              (distinct (flatten geometry)))
                        (mapv (partial make-shader "fragment" GL20/GL_FRAGMENT_SHADER)
                              (distinct (flatten fragment))))
        program (GL20/glCreateProgram)]
    (doseq [shader shaders] (GL20/glAttachShader program shader))
    (GL20/glLinkProgram program)
    (when (zero? (GL20/glGetProgrami program GL20/GL_LINK_STATUS))
      (throw (Exception. (GL20/glGetProgramInfoLog program 1024))))
    (doseq [shader shaders] (destroy-shader shader))
    program))


(definline destroy-program
  "Delete a program and associated shaders"
  {:malli/schema [:=> [:cat :int] :nil]}
  [program]
  `(GL20/glDeleteProgram ~program))


(def vertex-array-object
  (m/schema [:map [::vertex-array-object :int] [::array-buffer :int] [::index-buffer :int]
             [::attribute-locations [:vector :int]]]))


(definline set-static-float-buffer-data
  "Use glBufferData to set up static buffer data"
  {:malli/schema [:=> [:cat :int :some] :nil]}
  [target data]
  `(GL15/glBufferData ~(vary-meta target assoc :tag 'int)
                      ~(vary-meta data assoc :tag 'java.nio.DirectFloatBufferU)
                      GL15/GL_STATIC_DRAW))


(definline set-static-int-buffer-data
  "Use glBufferData to set up static buffer data"
  {:malli/schema [:=> [:cat :int :some] :nil]}
  [target data]
  `(GL15/glBufferData ~(vary-meta target assoc :tag 'int)
                      ~(vary-meta data assoc :tag 'java.nio.DirectIntBufferU)
                      GL15/GL_STATIC_DRAW))


(defn opengl-type-size
  "Get byte size of OpenGL type"
  ^long [^long opengl-type]
  (condp = opengl-type
    GL11/GL_UNSIGNED_BYTE  Byte/BYTES
    GL11/GL_UNSIGNED_SHORT Short/BYTES
    GL11/GL_UNSIGNED_INT   Integer/BYTES
    GL11/GL_FLOAT          Float/BYTES
    GL11/GL_DOUBLE         Double/BYTES))


(defn setup-vertex-attrib-pointers
  "Set up mappings from vertex array buffer row to vertex shader attributes"
  {:malli/schema [:=> [:cat :int [:and sequential? [:repeat [:cat :int :string N]]]] [:vector :int]]}
  [program attributes]
  (let [attribute-pairs (partition 3 attributes)
        sizes           (mapv (fn [[^long opengl-type _name ^long size]] (* (opengl-type-size opengl-type) size)) attribute-pairs)
        stride          (apply + sizes)
        offsets         (reductions + 0 (butlast sizes))
        attr-locations  (for [[[opengl-type attribute size] offset] (mapv list attribute-pairs offsets)]
                          (let [location (GL20/glGetAttribLocation ^long program ^String attribute)]
                            (GL20/glVertexAttribPointer location ^long size ^long opengl-type true ^long stride ^long offset)
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


(defn make-vertex-array-stream
  "Create vertex array object using stream draw mode for integer index buffer and mixed type array buffer"
  {:malli/schema [:=> [:cat :int :int :int] vertex-array-object]}
  [_program max-index-buffer max-vertex-buffer]
  (let [vertex-array-object (GL30/glGenVertexArrays)
        array-buffer        (GL15/glGenBuffers)
        index-buffer        (GL15/glGenBuffers)]
    (GL30/glBindVertexArray vertex-array-object)
    (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer)
    (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER index-buffer)
    (GL15/glBufferData GL15/GL_ARRAY_BUFFER ^long max-vertex-buffer GL15/GL_STREAM_DRAW)
    (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER ^long max-index-buffer GL15/GL_STREAM_DRAW)
    {::vertex-array-object vertex-array-object
     ::array-buffer        array-buffer
     ::index-buffer        index-buffer
     ::attribute-locations []
     ::max-vertex-buffer   max-vertex-buffer
     ::max-index-buffer    max-index-buffer}))


(defmacro with-mapped-vertex-arrays
  "Perform memory mapping for specified block of code"
  [vao vertices indices & body]
  `(do
     (setup-vertex-array-object ~vao)
     (let [~vertices (GL15/glMapBuffer GL15/GL_ARRAY_BUFFER GL15/GL_WRITE_ONLY (::max-vertex-buffer ~vao) nil)
           ~indices  (GL15/glMapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER GL15/GL_WRITE_ONLY (::max-index-buffer ~vao) nil)]
       ~@body
       (GL15/glUnmapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER)
       (GL15/glUnmapBuffer GL15/GL_ARRAY_BUFFER))))


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


(definline use-program
  "Use specified shader program"
  {:malli/schema [:=> [:cat :int] :nil]}
  [program]
  `(GL20/glUseProgram ~(vary-meta program assoc :tag 'long)))


(definline uniform-location
  "Get index of uniform variable"
  {:malli/schema [:=> [:cat :int :string] :int]}
  [program k]
  `(GL20/glGetUniformLocation ~(vary-meta program assoc :tag 'long)
                              ~(vary-meta k assoc :tag 'String)))


(defn uniform-float
  "Set uniform float variable in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :double] :nil]}
  [program k value]
  (GL20/glUniform1f (uniform-location program k) value))


(defn uniform-int
  "Set uniform integer variable in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :int] :nil]}
  [program k value]
  (GL20/glUniform1i (uniform-location program k) value))


(defn uniform-vector2
  "Set uniform 2D vector in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fvec2] :nil]}
  [program k value]
  (GL20/glUniform2f (uniform-location program k) (value 0) (value 1)))


(defn uniform-vector3
  "Set uniform 3D vector in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fvec3] :nil]}
  [program k value]
  (GL20/glUniform3f (uniform-location program k) (value 0) (value 1) (value 2)))


(defn uniform-vector4
  "Set uniform 3D vector in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fvec4] :nil]}
  [program k value]
  (GL20/glUniform4f (uniform-location program k) (value 0) (value 1) (value 2) (value 3)))


(defn uniform-matrix3
  "Set uniform 3x3 matrix in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fmat3] :nil]}
  [program k value]
  (GL20/glUniformMatrix3fv (uniform-location program k) true
                           ^java.nio.DirectFloatBufferU (make-float-buffer (mat->float-array value))))


(defn uniform-matrix4
  "Set uniform 4x4 matrix in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string fmat4] :nil]}
  [program k value]
  (GL20/glUniformMatrix4fv (uniform-location program k) true
                           ^java.nio.DirectFloatBufferU (make-float-buffer (mat->float-array value))))


(defn uniform-sampler
  "Set index of uniform sampler in current shader program (don't forget to set the program using use-program first)"
  {:malli/schema [:=> [:cat :int :string :int] :nil]}
  [program k value]
  (GL20/glUniform1i (uniform-location program k) value))


(definline use-texture
  "Set texture with specified index"
  {:malli/schema [:=> [:cat [:int {:min 0 :max 15}] texture] :nil]}
  [index texture]
  `(do
     (GL13/glActiveTexture (+ GL13/GL_TEXTURE0 (long ~index)))
     (GL11/glBindTexture (:sfsim.texture/target ~texture) (:sfsim.texture/texture ~texture))))


(definline use-textures
  "Specify textures to be used in the next rendering operation"
  {:malli/schema [:=> [:cat [:map-of :int texture]] :nil]}
  [textures]
  `(doseq [[index# texture#] ~textures] (use-texture index# texture#)))


(definline setup-vertex-array-object
  "Initialise rendering of a vertex array object"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  `(do
     (GL30/glBindVertexArray (::vertex-array-object ~vertex-array-object))
     (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER (::array-buffer ~vertex-array-object))
     (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER (::index-buffer ~vertex-array-object))))


(definline render-quads
  "Render one or more quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  `(do
     (setup-vertex-array-object ~vertex-array-object)
     (GL11/glDrawElements GL11/GL_QUADS ^long (::nrows ~vertex-array-object) GL11/GL_UNSIGNED_INT 0)))


(definline render-triangles
  "Render one or more triangles"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  `(do
     (setup-vertex-array-object ~vertex-array-object)
     (GL11/glDrawElements GL11/GL_TRIANGLES (::nrows ~vertex-array-object) GL11/GL_UNSIGNED_INT 0)))


(definline render-patches
  "Render one or more tessellated quads"
  {:malli/schema [:=> [:cat vertex-array-object] :nil]}
  [vertex-array-object]
  `(do
     (setup-vertex-array-object ~vertex-array-object)
     (GL40/glPatchParameteri GL40/GL_PATCH_VERTICES 4)
     (GL11/glDrawElements GL40/GL_PATCHES (::nrows ~vertex-array-object) GL11/GL_UNSIGNED_INT 0)))


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
    (mapv (fn layers-of-texture
            [texture]
            (if (:sfsim.texture/depth texture)
              (mapv (fn layer-of-3d-texture [layer] (assoc texture :sfsim.texture/layer layer))
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
          (fn setup-color-attachment
            [^long index texture]
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
       (GL11/glViewport 0 0 ~width ~height)
       (with-culling ~culling
         (with-depth-test ~depth-texture
           ~@body))
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
         texture#        (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp internalformat# ~width ~height)
         depth#          (make-empty-depth-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp ~width ~height)]
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
    (fn render-shadow-segment
      [shadow-level]
      (texture-render-depth size size
                            (clear)
                            (use-program program)
                            (fun (:sfsim.matrix/world-to-shadow-ndc shadow-level))))
    matrix-cascade))


(defmacro offscreen-render
  "Macro to render to a texture using depth and stencil buffer and convert it to an image"
  [width height & body]
  `(with-invisible-window
     (let [depth# (make-empty-depth-stencil-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp ~width ~height)
           tex#   (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL11/GL_RGB8 ~width ~height)]
       (framebuffer-render ~width ~height ::cullback depth# [tex#] ~@body)
       (let [img# (texture->image tex#)]
         (destroy-texture tex#)
         (destroy-texture depth#)
         img#))))


(defn diagonal-field-of-view
  "Compute diagonal field of view angle"
  ^double [^long width ^long height ^double fov]
  (let [dx  (sin (* 0.5 fov))
        dy  (/ (* height dx) width)
        dxy (hypot dx dy)]
    (* 2.0 (asin dxy))))


(def render-config (m/schema [:map [::amplification :double] [::specular :double] [::fov :double] [::min-z-near :double]
                                   [::cloud-subsampling :int]]))


(def render-vars
  (m/schema [:map [::origin fvec3] [::z-near :double] [::z-far :double] [::window-width N]
             [::window-height N] [::light-direction fvec3] [::camera-to-world fmat4] [::projection fmat4]
             [::object-origin fvec3] [::camera-to-object fmat4] [::object-distance :double]
             [::time :double] [::pressure :double] [::overlay-width N] [::overlay-height N] [::overlay-projection fmat4]]))


(defn make-render-vars
  "Create hash map with render variables for rendering current frame with specified depth range"
  {:malli/schema [:=> [:cat [:map [::fov :double] [::cloud-subsampling :int]] N N fvec3 quaternion fvec3 fvec3 quaternion
                            :double :double :double :double]
                      render-vars]}
  [render-config window-width window-height camera-position camera-orientation light-direction object-position object-orientation
   z-near z-far time_ pressure]
  (let [fov                (::fov render-config)
        cloud-subsampling  (::cloud-subsampling render-config)
        overlay-width      (quot ^long window-width ^long cloud-subsampling)
        overlay-height     (quot ^long window-height ^long cloud-subsampling)
        camera-to-world    (transformation-matrix (quaternion->matrix camera-orientation) camera-position)
        world-to-object    (inverse (transformation-matrix (quaternion->matrix object-orientation) object-position))
        camera-to-object   (mulm world-to-object camera-to-world)
        object-origin      (vec4->vec3 (mulv camera-to-object (vec4 0 0 0 1)))
        z-offset           1.0
        projection         (projection-matrix window-width window-height z-near (+ ^double z-far ^double z-offset) fov)
        overlay-projection (projection-matrix overlay-width overlay-height z-near (+ ^double z-far ^double z-offset) fov)]
    {::origin camera-position
     ::z-near z-near
     ::z-far z-far
     ::fov fov
     ::window-width window-width
     ::window-height window-height
     ::overlay-width overlay-width
     ::overlay-height overlay-height
     ::light-direction light-direction
     ::object-origin object-origin
     ::camera-to-object camera-to-object
     ::object-distance (mag object-origin)
     ::camera-to-world camera-to-world
     ::projection projection
     ::overlay-projection overlay-projection
     ::time time_
     ::pressure pressure}))


(defn joined-render-vars
  "Create hash map with render variables using joined z-range of input render variables"
  {:malli/schema [:=> [:cat render-vars render-vars] render-vars]}
  [vars-first vars-second]
  (let [fov                (::fov vars-first)
        window-width       (::window-width vars-first)
        window-height      (::window-height vars-first)
        overlay-width      (::overlay-width vars-first)
        overlay-height     (::overlay-height vars-first)
        object-origin      (::object-origin vars-first)
        object-distance    (::object-distance vars-first)
        camera-to-object   (::camera-to-object vars-first)
        position           (::origin vars-first)
        z-near             (min ^double (::z-near vars-first) ^double (::z-near vars-second))
        z-far              (max ^double (::z-far vars-first) ^double (::z-far vars-second))
        light-direction    (::light-direction vars-first)
        camera-to-world    (::camera-to-world vars-first)
        z-offset           1.0
        projection         (projection-matrix window-width window-height z-near (+ z-far z-offset) fov)
        overlay-projection (projection-matrix overlay-width overlay-height z-near (+ z-far z-offset) fov)
        time_              (::time vars-first)
        pressure           (::pressure vars-first)]
    {::origin position
     ::z-near z-near
     ::z-far z-far
     ::fov fov
     ::window-width window-width
     ::window-height window-height
     ::overlay-width overlay-width
     ::overlay-height overlay-height
     ::object-origin object-origin
     ::object-distance object-distance
     ::camera-to-object camera-to-object
     ::light-direction light-direction
     ::camera-to-world camera-to-world
     ::projection projection
     ::overlay-projection overlay-projection
     ::time time_
     ::pressure pressure}))


(defn setup-shadow-and-opacity-maps
  "Set up cascade of deep opacity maps and cascade of shadow maps"
  [^long program shadow-data ^long sampler-offset]
  (doseq [i (range (:sfsim.opacity/num-steps shadow-data))]
    (uniform-sampler program (str "shadow_map" i) (+ ^long i sampler-offset)))
  (doseq [i (range (:sfsim.opacity/num-steps shadow-data))]
    (uniform-sampler program (str "opacity" i) (+ ^long i sampler-offset ^long (:sfsim.opacity/num-steps shadow-data))))
  (uniform-int program "num_opacity_layers" (:sfsim.opacity/num-opacity-layers shadow-data))
  (uniform-int program "shadow_size" (:sfsim.opacity/shadow-size shadow-data))
  (uniform-float program "depth" (:sfsim.opacity/depth shadow-data))
  (uniform-float program "shadow_bias" (:sfsim.opacity/shadow-bias shadow-data)))


(def shadow-matrix-vars
  (m/schema [:map [:sfsim.opacity/splits [:vector :double]]
             [:sfsim.opacity/matrix-cascade [:vector [:map [:sfsim.matrix/world-to-shadow-map fmat4]
                                                      [:sfsim.matrix/depth :double]]]]]))


(defn setup-shadow-matrices
  "Set up cascade of shadow matrices for rendering"
  {:malli/schema [:=> [:cat :int shadow-matrix-vars] :nil]}
  [program shadow-vars]
  (doseq [[idx item] (map-indexed vector (:sfsim.opacity/splits shadow-vars))]
    (uniform-float program (str "split" idx) item))
  (doseq [[idx item] (map-indexed vector (:sfsim.opacity/biases shadow-vars))]
    (uniform-float program (str "bias" idx) item))
  (doseq [[idx item] (map-indexed vector (:sfsim.opacity/matrix-cascade shadow-vars))]
    (uniform-matrix4 program (str "world_to_shadow_map" idx) (:sfsim.matrix/world-to-shadow-map item))
    (uniform-float program (str "depth" idx) (:sfsim.matrix/depth item))))


(def tessellation-uniform (slurp "resources/shaders/render/tessellation-uniform.glsl"))
(def tessellation-chequer (slurp "resources/shaders/render/tessellation-chequer.glsl"))
(def geometry-point-color (slurp "resources/shaders/render/geometry-point-color.glsl"))
(def fragment-tessellation (slurp "resources/shaders/render/fragment-tessellation.glsl"))


(defn quad-splits-orientations
  "Function to determine quad tessellation orientation of diagonal split (true: 00->11, false: 10->01)"
  [^long tilesize ^long zoom]
  (let [indices  [0 1 3 2]
        vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        detail   (dec tilesize)
        program  (make-program :sfsim.render/vertex [vertex-passthrough]
                               :sfsim.render/tess-control [tessellation-uniform]
                               :sfsim.render/tess-evaluation [tessellation-chequer]
                               :sfsim.render/geometry [geometry-point-color]
                               :sfsim.render/fragment [fragment-tessellation])
        vao      (make-vertex-array-object program indices vertices ["point" 3])
        tex      (texture-render-color (* zoom detail) (* zoom detail) false
                                       (clear (vec3 0 0 0))
                                       (use-program program)
                                       (uniform-int program "detail" detail)
                                       (render-patches vao))
        img      (texture->image tex)
        result   (mapv (fn [^long y]
                         (mapv (fn [^long x]
                                 (let [value ((get-pixel img
                                                         (+ (quot zoom 2) (* y zoom))
                                                         (+ (quot zoom 2) (* x zoom))) 1)
                                       odd   (= (mod (+ x y) 2) 1)]
                                   (= (>= ^long value 127) odd)))
                               (range detail)))
                       (range detail))]
    (destroy-texture tex)
    (destroy-vertex-array-object vao)
    (destroy-program program)
    result))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
