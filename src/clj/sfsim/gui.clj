;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.gui
  (:require
    [fastmath.matrix :as fm]
    [sfsim.image :refer (white-image-with-alpha)]
    [sfsim.render :refer (make-program use-program uniform-matrix4 with-mapped-vertex-arrays with-overlay-blending
                                       with-scissor set-scissor destroy-program setup-vertex-attrib-pointers make-vertex-array-stream
                                       destroy-vertex-array-object)]
    [sfsim.texture :refer (make-rgba-texture byte-buffer->array destroy-texture texture-2d)]
    [sfsim.util :refer (slurp-byte-buffer)])
  (:import
    (java.nio
      DirectByteBuffer)
    (java.nio.charset
      StandardCharsets)
    (fastmath.matrix
      Mat4x4)
    (org.lwjgl
      BufferUtils)
    (org.lwjgl.nuklear
      NkAllocator
      NkBuffer
      NkColor
      NkColor$Buffer
      NkContext
      NkConvertConfig
      NkDrawNullTexture
      NkDrawVertexLayoutElement
      NkDrawVertexLayoutElement$Buffer
      NkHandle
      NkPluginAllocI
      NkPluginFilterI
      NkPluginFreeI
      NkQueryFontGlyphCallbackI
      NkRect
      NkTextWidthCallbackI
      NkUserFont
      NkUserFontGlyph
      Nuklear)
    (org.lwjgl.opengl
      GL11
      GL13)
    (org.lwjgl.stb
      STBTTAlignedQuad
      STBTTFontinfo
      STBTTPackContext
      STBTTPackedchar
      STBTTPackedchar$Buffer
      STBTruetype)
    (org.lwjgl.system
      MemoryStack
      MemoryUtil)))


(set! *unchecked-math* :warn-on-boxed)
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
  ^Mat4x4 [^long width ^long height]
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
    (.alloc result (reify NkPluginAllocI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
                     (invoke [_this _handle _old size] (MemoryUtil/nmemAllocChecked size))))
    (.mfree result (reify NkPluginFreeI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
                     (invoke [_this _handle ptr] (MemoryUtil/nmemFree ptr))))
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
  [{::keys [context config program vao cmds]} ^long width ^long height]
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
    (with-overlay-blending
      (with-scissor
        (loop [cmd (Nuklear/nk__draw_begin context cmds) offset 0]
          (when cmd
            (when (not (zero? (.elem_count cmd)))
              (GL13/glActiveTexture GL13/GL_TEXTURE0)
              (GL11/glBindTexture GL11/GL_TEXTURE_2D (.id (.texture cmd)))
              (let [clip-rect (.clip_rect cmd)]
                (set-scissor (.x clip-rect) (- height (long (+ (.y clip-rect) (.h clip-rect)))) (.w clip-rect) (.h clip-rect)))
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


(defmacro nuklear-window
  [gui title x y width height border & body]
  `(let [stack#   (MemoryStack/stackPush)
         rect#    (NkRect/malloc stack#)
         context# (:sfsim.gui/context ~gui)]
     (try
       (when (Nuklear/nk_begin ^NkContext context# ~title (Nuklear/nk_rect ~x ~y ~width ~height rect#)
                               ~(if border
                                  `(bit-or Nuklear/NK_WINDOW_BORDER Nuklear/NK_WINDOW_TITLE Nuklear/NK_WINDOW_NO_SCROLLBAR)
                                  `Nuklear/NK_WINDOW_NO_SCROLLBAR))
         (let [result# (do ~@body)]
           (Nuklear/nk_end context#)
           result#))
       (finally
         (MemoryStack/stackPop)))))


(defn layout-row-dynamic
  "Create dynamic layout with specified height and number of columns"
  {:malli/schema [:=> [:cat :some :int :int] :nil]}
  [gui height cols]
  (Nuklear/nk_layout_row_dynamic (::context gui) height cols))


(defn make-bitmap-font
  "Create a bitmap font with character packing data"
  [^String ttf-filename ^long bitmap-width ^long bitmap-height ^long font-height]
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
       ::ttf ttf  ; keep alive after passing buffer to stbtt_InitFont
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
          (reify NkTextWidthCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
            (invoke
              [_this _handle _h text len]
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
                          (let [text-width (+ text-width (* (.get advance 0) ^double scale))
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
            (reify NkQueryFontGlyphCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
              (invoke
                [_this _handle font-height glyph codepoint _next-codepoint]
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
                    (.set (.offset ufg) (.x0 q) (+ (.y0 q) font-height ^double descent))
                    (.xadvance ufg (* (.get advance 0) ^double scale))
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


(defn nuklear-dark-style
  [gui]
  (let [stack       (MemoryStack/stackPush)
        rgb         (NkColor/malloc ^MemoryStack stack)
        style-table (NkColor/malloc Nuklear/NK_COLOR_COUNT ^MemoryStack stack)]
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TEXT (Nuklear/nk_rgb 210 210 210 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_WINDOW (Nuklear/nk_rgb 57 67 71 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_HEADER (Nuklear/nk_rgb 51 51 56 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BORDER (Nuklear/nk_rgb 46 46 46 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON_HOVER (Nuklear/nk_rgb 58 93 121 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_BUTTON_ACTIVE (Nuklear/nk_rgb 63 98 126 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE_HOVER (Nuklear/nk_rgb 45 53 56 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TOGGLE_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SELECT (Nuklear/nk_rgb 57 67 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SELECT_ACTIVE (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR_HOVER (Nuklear/nk_rgb 53 88 116 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SLIDER_CURSOR_ACTIVE (Nuklear/nk_rgb 58 93 121 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_PROPERTY (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_EDIT (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_EDIT_CURSOR (Nuklear/nk_rgb 210 210 210 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_COMBO (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART_COLOR (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_CHART_COLOR_HIGHLIGHT (Nuklear/nk_rgb 255 0 0 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR (Nuklear/nk_rgb 50 58 61 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR (Nuklear/nk_rgb 48 83 111 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR_HOVER (Nuklear/nk_rgb 53 88 116 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_SCROLLBAR_CURSOR_ACTIVE (Nuklear/nk_rgb 58 93 121 rgb))
    (.put ^NkColor$Buffer style-table Nuklear/NK_COLOR_TAB_HEADER (Nuklear/nk_rgb 48 83 111 rgb))
    (Nuklear/nk_style_from_table (::context gui) style-table)
    (MemoryStack/stackPop)))


(defn slider-int
  "Create a slider with integer value"
  {:malli/schema [:=> [:cat :some :int :int :int :int] :int]}
  [gui minimum value maximum step]
  (let [buffer (int-array 1)]
    (aset-int buffer 0 value)
    (Nuklear/nk_slider_int ^NkContext (::context gui) ^int minimum buffer ^int maximum ^int step)
    (aget buffer 0)))


(defn slider-float
  "Create a slider with float value"
  {:malli/schema [:=> [:cat :some :double :double :double :double] :double]}
  [gui minimum value maximum step]
  (let [buffer (float-array 1)]
    (aset-float buffer 0 value)
    (Nuklear/nk_slider_float ^NkContext (::context gui) ^float minimum buffer ^float maximum ^float step)
    (double (aget buffer 0))))


(defn button-label
  "Create a button with text label"
  [gui label]
  (Nuklear/nk_button_label ^NkContext (::context gui) ^String label))


(defn text-label
  "Create a text label"
  ([gui label]
   (text-label gui label (bit-or Nuklear/NK_TEXT_ALIGN_LEFT Nuklear/NK_TEXT_ALIGN_MIDDLE)))
  ([gui label alignment]
   (Nuklear/nk_label ^NkContext (::context gui) ^String label ^long alignment)))


(defn edit-set
  "Set string with text for edit field"
  [data text]
  (let [buffer   (::buffer data)
        text-len (::text-len data)]
    (.put ^DirectByteBuffer buffer (.getBytes ^String text StandardCharsets/UTF_8))
    (.flip ^DirectByteBuffer buffer)
    (.limit ^DirectByteBuffer buffer (.capacity ^DirectByteBuffer buffer))
    (aset-int text-len 0 (count text))))


(defn edit-data
  "Return map with text, limit, and filter for edit field"
  [text max-size text-filter-type]
  (let [text-filter
        (case text-filter-type
          ::filter-ascii   (reify NkPluginFilterI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
                             (invoke [_this edit unicode] (Nuklear/nnk_filter_ascii edit unicode)))
          ::filter-float   (reify NkPluginFilterI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
                             (invoke [_this edit unicode] (Nuklear/nnk_filter_float edit unicode)))
          ::filter-decimal (reify NkPluginFilterI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
                             (invoke [_this edit unicode] (Nuklear/nnk_filter_decimal edit unicode))))
        text-len    (int-array 1)
        buffer      (BufferUtils/createByteBuffer max-size)]
    (edit-set {::buffer buffer ::text-len text-len} text)
    {::buffer      buffer
     ::text-len    text-len
     ::max-size    max-size
     ::text-filter text-filter}))


(defn edit-get
  "Get string with text from edit field"
  [data]
  (let [buffer   (::buffer data)
        text-len (aget ^ints (::text-len data) 0)
        arr      (byte-array (inc text-len))]
    (.get ^DirectByteBuffer buffer arr 0 text-len)
    (.flip ^DirectByteBuffer buffer)
    (.limit ^DirectByteBuffer buffer (.capacity ^DirectByteBuffer buffer))
    (aset-byte arr text-len 0)
    (String. arr 0 text-len StandardCharsets/UTF_8)))


(defn edit-field
  "Create edit field"
  [gui data]
  (Nuklear/nk_edit_string ^NkContext (::context gui) Nuklear/NK_EDIT_FIELD ^DirectByteBuffer (::buffer data)
                          ^ints (::text-len data) ^int (::max-size data) ^NkPluginFilterI (::text-filter data)))


(defmacro layout-row
  [gui height cnt & body]
  `(do
     (Nuklear/nk_layout_row_begin (:sfsim.gui/context ~gui) Nuklear/NK_DYNAMIC ~height ~cnt)
     (let [result# (do ~@body)]
       (Nuklear/nk_layout_row_end (:sfsim.gui/context ~gui))
       result#)))


(defn layout-row-push
  [gui frac]
  (Nuklear/nk_layout_row_push (:sfsim.gui/context gui) frac))


(defn flight-controls-display
  [input-controls gui]
  (let [stack    (MemoryStack/stackPush)
        rect     (NkRect/malloc stack)
        rgb      (NkColor/malloc stack)
        throttle (:sfsim.input/throttle input-controls)
        aileron  (:sfsim.input/aileron input-controls)
        elevator (:sfsim.input/elevator input-controls)
        rudder   (:sfsim.input/rudder input-controls)]
    (nuklear-window gui "Yoke" 10 10 80 80 false
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui 80 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect (- 45 (* ^double aileron 30)) (- 45 (* ^double elevator 30)) 10 10 rect)
                                              (Nuklear/nk_rgb 255 0 0 rgb))))
    (nuklear-window gui "Rudder" 10 95 80 20 false
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui 20 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect (- 45 (* ^double rudder 30)) 100 10 10 rect)
                                              (Nuklear/nk_rgb 255 0 255 rgb))))
    (nuklear-window gui "Throttle" 95 10 20 80 false
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui 80 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect 100 (- 75 (* 60 ^double throttle)) 10 10 rect)
                                              (Nuklear/nk_rgb 255 255 255 rgb)))))
  (MemoryStack/stackPop))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
