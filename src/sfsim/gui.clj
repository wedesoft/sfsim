(ns sfsim.gui
    (:require [fastmath.matrix :as fm]
              [sfsim.matrix :refer (fmat4)]
              [sfsim.util :refer (N)]
              [sfsim.render :refer (make-program use-program uniform-matrix4 with-mapped-vertex-arrays with-blending
                                    with-scissor set-scissor vertex-array-object destroy-program setup-vertex-attrib-pointers)]
              [sfsim.texture :refer (make-rgba-texture)])
    (:import [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.opengl GL11 GL14]
             [org.lwjgl.nuklear Nuklear NkDrawNullTexture NkAllocator NkPluginAllocI NkPluginFreeI NkDrawVertexLayoutElement
              NkDrawVertexLayoutElement$Buffer NkConvertConfig NkContext NkBuffer]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def vertex-gui
  "Vertex shader for rendering graphical user interfaces"
  (slurp "resources/shaders/gui/vertex.glsl"))

(def fragment-gui
  "Fragment shader for rendering graphical user interfaces"
  (slurp "resources/shaders/gui/fragment.glsl"))

(defn make-gui-program
  "Create shader program for GUI rendering"
  {:malli/schema [:=> :cat :int]}
  []
  (make-program :sfsim.render/vertex [vertex-gui] :sfsim.render/fragment [fragment-gui]))

(defn gui-matrix
  "Projection matrix for rendering 2D GUI"
  {:malli/schema [:=> [:cat N N] fmat4]}
  [width height]
  (let [w2 (/  2.0 width)
        h2 (/ -2.0 height)]
    (fm/mat4x4  w2 0.0  0.0 -1.0
               0.0  h2  0.0  1.0
               0.0 0.0 -1.0  0.0
               0.0 0.0  0.0  1.0)))

(defn make-null-texture
  "Make texture with single white pixel"
  {:malli/schema [:=> :cat :some]}
  []
  (let [image   #:sfsim.image {:width 1 :height 1 :data (byte-array [-1 -1 -1 -1]) :channels 4}
        texture (make-rgba-texture :sfsim.texture/nearest :sfsim.texture/clamp image)
        result  (NkDrawNullTexture/create)]
    (.id (.texture result) (:sfsim.texture/texture texture))
    (.set (.uv result) 0.5 0.5)
    result))

(defn destroy-null-texture
  "Destroy single pixel texture"
  [null-texture]
  (GL11/glDeleteTextures (.id (.texture ^NkDrawNullTexture null-texture))))

(defn make-allocator
  "Create Nuklear allocation object"
  []
  (let [result (NkAllocator/create)]
    (.alloc result (reify NkPluginAllocI (invoke [_this _handle _old size] (MemoryUtil/nmemAllocChecked size))))
    (.mfree result (reify NkPluginFreeI (invoke [_this _handle ptr] (MemoryUtil/nmemFree ptr))))
    result))

(defn destroy-allocator
  "Destruct Nuklear allocation object"
  [allocator]
  (.free (.alloc ^NkAllocator allocator))
  (.free (.mfree ^NkAllocator allocator)))

(set! *warn-on-reflection* false)

(defn make-vertex-layout
  "Create Nuklear vertex layout object"
  {:malli/schema [:=> :cat :some]}
  []
  (let [result (NkDrawVertexLayoutElement/malloc 4)]
    (-> result (.position 0) (.attribute Nuklear/NK_VERTEX_POSITION) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 0))
    (-> result (.position 1) (.attribute Nuklear/NK_VERTEX_TEXCOORD) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 8))
    (-> result (.position 2) (.attribute Nuklear/NK_VERTEX_COLOR) (.format Nuklear/NK_FORMAT_R8G8B8A8) (.offset 16))
    (-> result (.position 3) (.attribute Nuklear/NK_VERTEX_ATTRIBUTE_COUNT) (.format Nuklear/NK_FORMAT_COUNT) (.offset 0))
    (.flip result)))

(set! *warn-on-reflection* true)

(defn destroy-vertex-layout
  "Destructor for vertex layout object"
  {:malli/schema [:=> [:cat :some] :nil]}
  [vertex-layout])

(defn make-gui-config
  "Create and initialise Nuklear configuration"
  {:malli/schema [:=> [:cat :some :some] :some]}
  [null-texture vertex-layout]
  (let [result (NkConvertConfig/calloc)]
    (doto result
          (.vertex_layout ^NkDrawVertexLayoutElement$Buffer vertex-layout)
          (.vertex_size 20)
          (.vertex_alignment 4)
          (.tex_null ^NkDrawNullTexture null-texture)
          (.circle_segment_count 22)
          (.curve_segment_count 22)
          (.arc_segment_count 22)
          (.global_alpha 1.0)
          (.shape_AA Nuklear/NK_ANTI_ALIASING_ON)
          (.line_AA Nuklear/NK_ANTI_ALIASING_ON))
    result))

(defn destroy-gui-config
  "Destroy GUI configuration"
  {:malli/schema [:=> [:cat :some] :nil]}
  [config])

(defn make-gui-context
  "Create Nuklear context object"
  {:malli/schema [:=> [:cat :some :some] :some]}
  [allocator font]
  (let [result (NkContext/create)]
    (Nuklear/nk_init result allocator font)
    result))

(defn destroy-gui-context
  "Destroy Nuklear GUI context object"
  {:malli/schema [:=> [:cat :some] :nil]}
  [context]
  (Nuklear/nk_free context))

(defn make-gui-buffer
  "Create Nuklear render command buffer"
  {:malli/schema [:=> [:cat :some :int] :some]}
  [allocator initial-size]
  (let [result (NkBuffer/create)]
    (Nuklear/nk_buffer_init result allocator initial-size)
    result))

(defn destroy-gui-buffer
  "Destroy Nuklear render command buffer"
  {:malli/schema [:=> [:cat :some] :nil]}
  [buffer]
  (.free ^NkBuffer buffer))

(defn render-gui
  "Display the graphical user interface"
  {:malli/schema [:=> [:cat :some :some :some :int vertex-array-object :int :int] :nil]}
  [context config cmds program vao width height]
  (let [stack (MemoryStack/stackPush)]
    (GL11/glViewport 0 0 width height)
    (use-program program)
    (uniform-matrix4 program "projection" (gui-matrix width height))
    (with-mapped-vertex-arrays vao vertices elements
      (let [vbuf (NkBuffer/malloc stack)
            ebuf (NkBuffer/malloc stack)]
        (Nuklear/nk_buffer_init_fixed vbuf vertices)
        (Nuklear/nk_buffer_init_fixed ebuf elements)
        (Nuklear/nk_convert context cmds vbuf ebuf config)))
    (with-blending
      (with-scissor
        (loop [cmd (Nuklear/nk__draw_begin context cmds) offset 0]
              (when cmd
                (when (not (zero? (.elem_count cmd)))
                  (GL11/glBindTexture GL11/GL_TEXTURE_2D (.id (.texture cmd)))
                  (let [clip-rect (.clip_rect cmd)]
                    (set-scissor (.x clip-rect) (- 32 (int (+ (.y clip-rect) (.h clip-rect)))) (.w clip-rect) (.h clip-rect)))
                  (GL11/glDrawElements GL11/GL_TRIANGLES (.elem_count cmd) GL11/GL_UNSIGNED_SHORT offset))
                (recur (Nuklear/nk__draw_next cmd cmds context) (+ offset (* 2 (.elem_count cmd))))))))
    (Nuklear/nk_clear context)
    (Nuklear/nk_buffer_clear cmds)
    (MemoryStack/stackPop)
    nil))

(defn make-nuklear-gui
  "Create a hashmap with required GUI objects"
  []
  (let [allocator    (make-allocator)
        program      (make-gui-program)
        null-texture (make-null-texture)]
    {::allocator allocator
     ::program   program
     ::null-tex  null-texture}))

(defn destroy-nuklear-gui
  "Destruct GUI objects"
  [gui]
  (destroy-null-texture (::null-tex gui))
  (destroy-program (::program gui))
  (destroy-allocator (::allocator gui)))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
