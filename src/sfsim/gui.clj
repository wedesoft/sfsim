(ns sfsim.gui
    (:require [fastmath.matrix :as fm]
              [sfsim.matrix :refer (fmat4)]
              [sfsim.util :refer (N slurp-byte-buffer)]
              [sfsim.render :refer (make-program use-program uniform-matrix4 with-mapped-vertex-arrays with-blending
                                    with-scissor set-scissor destroy-program setup-vertex-attrib-pointers make-vertex-array-stream
                                    destroy-vertex-array-object)]
              [sfsim.image :refer (white-image-with-alpha)]
              [sfsim.texture :refer (make-rgba-texture byte-buffer->array destroy-texture texture-2d)])
    (:import [org.lwjgl BufferUtils]
             [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.opengl GL11]
             [org.lwjgl.nuklear Nuklear NkDrawNullTexture NkAllocator NkPluginAllocI NkPluginFreeI NkDrawVertexLayoutElement
              NkDrawVertexLayoutElement$Buffer NkConvertConfig NkContext NkBuffer NkUserFont NkHandle NkTextWidthCallbackI
              NkQueryFontGlyphCallbackI NkUserFontGlyph]
             [org.lwjgl.stb STBTTFontinfo STBTruetype STBTTPackedchar STBTTPackContext STBTTAlignedQuad STBTTPackedchar$Buffer]))

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
  "Destroy Nuklear command buffer"
  {:malli/schema [:=> [:cat :some] :nil]}
  [buffer]
  (Nuklear/nk_buffer_free buffer))

(defn make-nuklear-gui
  "Create a hashmap with required GUI objects"
  {:malli/schema [:=> [:cat :some :int] :some]}
  [font buffer-initial-size]
  (let [max-vertex-buffer (* 512 1024)
        max-index-buffer  (* 128 1024)
        allocator         (make-allocator)
        program           (make-gui-program)
        vao               (make-vertex-array-stream program max-index-buffer max-vertex-buffer)
        vertex-layout     (make-vertex-layout)
        null-texture      (make-null-texture)
        config            (make-gui-config null-texture vertex-layout)
        context           (make-gui-context allocator font)
        cmds              (make-gui-buffer allocator buffer-initial-size)]
    (setup-vertex-attrib-pointers program [GL11/GL_FLOAT "position" 2 GL11/GL_FLOAT "texcoord" 2 GL11/GL_UNSIGNED_BYTE "color" 4])
    {::allocator     allocator
     ::context       context
     ::program       program
     ::vao           vao
     ::vertex-layout vertex-layout
     ::null-tex      null-texture
     ::config        config
     ::cmds          cmds}))

(defn render-nuklear-gui
  "Display the graphical user interface"
  {:malli/schema [:=> [:cat :some :int :int] :nil]}
  [{::keys [context config program vao cmds]} width height]
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
                    (set-scissor (.x clip-rect) (- height (int (+ (.y clip-rect) (.h clip-rect)))) (.w clip-rect) (.h clip-rect)))
                  (GL11/glDrawElements GL11/GL_TRIANGLES (.elem_count cmd) GL11/GL_UNSIGNED_SHORT offset))
                (recur (Nuklear/nk__draw_next cmd cmds context) (+ offset (* 2 (.elem_count cmd))))))))
    (Nuklear/nk_clear context)
    (Nuklear/nk_buffer_clear cmds)
    (MemoryStack/stackPop)
    nil))

(defn destroy-nuklear-gui
  "Destruct GUI objects"
  {:malli/schema [:=> [:cat :some] :nil]}
  [gui]
  (destroy-gui-buffer (::cmds gui))
  (destroy-null-texture (::null-tex gui))
  (destroy-vertex-array-object (::vao gui))
  (destroy-program (::program gui))
  (destroy-gui-context (::context gui))
  (destroy-allocator (::allocator gui)))

(defmacro nuklear-window [gui title x y width height & body]
  `(let [stack#   (MemoryStack/stackPush)
         rect#    (NkRect/malloc stack#)
         context# (:sfsim.gui/context ~gui)]
     (when (Nuklear/nk_begin context# ~title (Nuklear/nk_rect ~x ~y ~width ~height rect#) Nuklear/NK_WINDOW_NO_SCROLLBAR)
       ~@body
       (Nuklear/nk_end context#))
     (MemoryStack/stackPop)))

(defn layout-row-dynamic
  "Create dynamic layout with specified height and number of columns"
  {:malli/schema [:=> [:cat :some :int :int] :nil]}
  [gui height cols]
  (Nuklear/nk_layout_row_dynamic (::context gui) height cols))

(defn make-bitmap-font
  "Create a bitmap font with character packing data"
  {:malli/schema [:=> [:cat :string :int :int :int] :some]}
  [ttf-filename bitmap-width bitmap-height font-height]
  (let [font         (NkUserFont/create)
        fontinfo     (STBTTFontinfo/create)
        ttf          (slurp-byte-buffer ttf-filename)
        orig-descent (int-array 1)
        cdata        (STBTTPackedchar/calloc 95)
        pc           (STBTTPackContext/calloc)
        bitmap       (MemoryUtil/memAlloc (* bitmap-width bitmap-height))]
    (STBTruetype/stbtt_InitFont fontinfo ttf)
    (STBTruetype/stbtt_GetFontVMetrics fontinfo nil orig-descent nil)
    (STBTruetype/stbtt_PackBegin pc bitmap bitmap-width bitmap-height 0 1 0)
    (STBTruetype/stbtt_PackSetOversampling pc 4 4)
    (STBTruetype/stbtt_PackFontRange pc ttf 0 font-height 32 cdata)
    (STBTruetype/stbtt_PackEnd pc)
    (let [scale (STBTruetype/stbtt_ScaleForPixelHeight fontinfo font-height)
          data  (byte-buffer->array bitmap)
          alpha #:sfsim.image{:width bitmap-width :height bitmap-height :data data :channels 1}
          image (white-image-with-alpha alpha)]
      {::font font
       ::fontinfo fontinfo
       ::ttf ttf
       ::font-height font-height
       ::scale scale
       ::descent (* (aget orig-descent 0) scale)
       ::cdata cdata
       ::image image})))

(defn set-font-texture-id
  "Configure font texture"
  {:malli/schema [:=> [:cat :some texture-2d] :any]}
  [font texture]
  (let [handle (NkHandle/create)]
    (.id ^NkHandle handle (:sfsim.texture/texture texture))
    (.texture ^NkUserFont font handle)))

(defn set-width-callback
  "Set callback function for computing width of text"
  {:malli/schema [:=> [:cat :some :some] :any]}
  [{::keys [fontinfo scale]} font]
  (.width ^NkUserFont font
          (reify NkTextWidthCallbackI
                 (invoke [_this _handle _h text len]
                   (let [stack     (MemoryStack/stackPush)
                         unicode   (.mallocInt stack 1)
                         advance   (.mallocInt stack 1)
                         glyph-len (Nuklear/nnk_utf_decode text (MemoryUtil/memAddress unicode) len)
                         result
                         (loop [text-len glyph-len glyph-len glyph-len text-width 0.0]
                               (if (or (> text-len len)
                                       (zero? glyph-len)
                                       (= (.get unicode 0) Nuklear/NK_UTF_INVALID))
                                 text-width
                                 (do
                                   (STBTruetype/stbtt_GetCodepointHMetrics ^STBTTFontinfo fontinfo (.get unicode 0) advance nil)
                                   (let [text-width (+ text-width (* (.get advance 0) scale))
                                         glyph-len  (Nuklear/nnk_utf_decode (+ text text-len)
                                                                            (MemoryUtil/memAddress unicode) (- len text-len))]
                                     (recur (+ text-len glyph-len) glyph-len text-width)))))]
                     (MemoryStack/stackPop)
                     result)))))

(defn set-height-callback
  "Set callback function for returning height of text"
  {:malli/schema [:=> [:cat :some :some] :any]}
  [{::keys [font-height]} font]
  (.height ^NkUserFont font font-height))

(defn set-glyph-callback
  "Set callback function for getting rectangle of glyph"
  {:malli/schema [:=> [:cat :some :some] :any]}
  [{::keys [fontinfo image cdata scale descent]} font]
  (let [bitmap-width  (:sfsim.image/width image)
        bitmap-height (:sfsim.image/height image)]
    (.query ^NkUserFont font
            (reify NkQueryFontGlyphCallbackI
                   (invoke [_this _handle font-height glyph codepoint _next-codepoint]
                     (let [stack   (MemoryStack/stackPush)
                           x       (.floats stack 0.0)
                           y       (.floats stack 0.0)
                           q       (STBTTAlignedQuad/malloc stack)
                           advance (.mallocInt stack 1)]
                       (STBTruetype/stbtt_GetPackedQuad ^STBTTPackedchar$Buffer cdata ^long bitmap-width ^long bitmap-height
                                                        (- codepoint 32) x y q false)
                       (STBTruetype/stbtt_GetCodepointHMetrics ^STBTTFontinfo fontinfo codepoint advance nil)
                       (let [ufg (NkUserFontGlyph/create glyph)]
                         (.width ufg (- (.x1 q) (.x0 q)))
                         (.height ufg (- (.y1 q) (.y0 q)))
                         (.set (.offset ufg) (.x0 q) (+ (.y0 q) font-height descent))
                         (.xadvance ufg (* (.get advance 0) scale))
                         (.set (.uv ufg 0) (.s0 q) (.t0 q))
                         (.set (.uv ufg 1) (.s1 q) (.t1 q)))
                       (MemoryStack/stackPop)))))))

(defn setup-font-texture
  "Create font texture and callbacks for text size and glyph information"
  {:malli/schema [:=> [:cat :some] :some]}
  [{::keys [image font] :as bitmap-font}]
  (let [font-texture  (make-rgba-texture :sfsim.texture/linear :sfsim.texture/clamp image)]
    (set-font-texture-id font font-texture)
    (set-width-callback bitmap-font font)
    (set-height-callback bitmap-font font)
    (set-glyph-callback bitmap-font font)
    (assoc bitmap-font ::texture font-texture)))

(defn destroy-font-texture
  [bitmap-font]
  (destroy-texture (::texture bitmap-font)))

(defn slider-int
  "Create a slider with integer value"
  {:malli/schema [:=> [:cat :some :int :int :int :int] :int]}
  [gui minimum value maximum step]
  (let [buffer (BufferUtils/createIntBuffer 1)]
    (.put buffer 0 ^int value)
    (Nuklear/nk_slider_int ^NkContext (::context gui) ^int minimum buffer ^int maximum ^int step)
    (.get buffer 0)))

(defn button-label
  "Create a button with text label"
  [gui label]
  (Nuklear/nk_button_label ^NkContext (::context gui) ^String label))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
