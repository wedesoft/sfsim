;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obteclipse/solar/2026-august-12ain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.gui
  (:require
    [clojure.math :refer (PI to-radians to-degrees cos sin ceil)]
    [clojure.java.io :as io]
    [clojure.string :refer (trim)]
    [fastmath.vector :refer (vec2 vec3)]
    [fastmath.matrix :as fm]
    [sfsim.image :refer (white-image-with-alpha slurp-image)]
    [sfsim.config :as config]
    [sfsim.version :refer (version)]
    [sfsim.physics :as physics]
    [sfsim.matrix :refer (quaternion->matrix)]
    [sfsim.astro :as astro]
    [sfsim.util :refer (slurp-byte-buffer dissoc-in ignore-nil-> invert-map sqr)]
    [sfsim.render :refer (make-program use-program uniform-matrix4 with-mapped-vertex-arrays with-overlay-blending
                          with-scissor set-scissor destroy-program setup-vertex-attrib-pointers make-vertex-array-stream
                          destroy-vertex-array-object with-invisible-window framebuffer-render make-vertex-array-object
                          destroy-vertex-array-object uniform-sampler uniform-matrix3 use-textures clear render-quads
                          uniform-vector2)]
    [sfsim.texture :refer (make-rgba-texture make-rgb-texture byte-buffer->array destroy-texture texture-2d make-empty-texture-2d
                           texture->image generate-mipmap)]
    [sfsim.input :refer (get-joystick-sensor-for-mapping get-key-name)])
  (:import
    (java.nio
      DirectByteBuffer)
    (java.nio.charset
      StandardCharsets)
    (fastmath.matrix
      Mat4x4)
    (org.lwjgl
      BufferUtils)
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.nuklear
      NkAllocator
      NkBuffer
      NkColor
      NkColor$Buffer
      NkCommandBuffer
      NkContext
      NkConvertConfig
      NkDrawNullTexture
      NkDrawVertexLayoutElement
      NkDrawVertexLayoutElement$Buffer
      NkHandle
      NkImage
      NkPluginAllocI
      NkPluginFilterI
      NkPluginFreeI
      NkQueryFontGlyphCallbackI
      NkRect
      NkStyleButton
      NkStyleScrollbar
      NkTextWidthCallbackI
      NkUserFont
      NkUserFontGlyph
      NkVec2
      Nuklear)
    (org.lwjgl.opengl
      GL11
      GL13
      GL30)
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


(def title-height 42)
(def widget-height 38)
(def padding 4)
(def row-height (- ^long widget-height ^long padding))
(def text-height 24)
(def text-row-height (- ^long text-height ^long padding))


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
      (.circle_segment_count 44)
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
  {:malli/schema [:=> [:cat :some :double] :some]}
  [font scale]
  (let [max-vertex-buffer   (* 512 1024)
        max-index-buffer    (* 128 1024)
        buffer-initial-size (* 4 1024)
        allocator           (make-allocator)
        program             (make-gui-program)
        vao                 (make-vertex-array-stream program max-index-buffer max-vertex-buffer)
        vertex-layout       (make-vertex-layout)
        null-texture        (make-null-texture)
        config              (make-gui-config null-texture vertex-layout)
        context             (make-gui-context allocator font)
        cmds                (make-gui-buffer allocator buffer-initial-size)]
    (setup-vertex-attrib-pointers program [GL11/GL_FLOAT "position" 2 GL11/GL_FLOAT "texcoord" 2 GL11/GL_UNSIGNED_BYTE "color" 4])
    {::allocator     allocator
     ::context       context
     ::scale         scale
     ::program       program
     ::vao           vao
     ::vertex-layout vertex-layout
     ::null-tex      null-texture
     ::config        config
     ::cmds          cmds}))


(defn scale
  "Scale a value"
  ^double [{::keys [scale]} ^long value]
  (* value ^double scale))


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
  "Create window using specified title, size, and set of instructions"
  [gui title x y width height decoration & body]
  `(let [stack#   (MemoryStack/stackPush)
         rect#    (NkRect/malloc stack#)
         context# (::context ~gui)]
     (try
       (when (Nuklear/nk_begin ^NkContext context# ~title (Nuklear/nk_rect ~x ~y ~width ~height rect#)
                               ~(case decoration
                                  :dialog `(bit-or Nuklear/NK_WINDOW_BORDER Nuklear/NK_WINDOW_TITLE
                                                   Nuklear/NK_WINDOW_NO_SCROLLBAR)
                                  :window `(bit-or Nuklear/NK_WINDOW_BORDER Nuklear/NK_WINDOW_TITLE)
                                  :widget `Nuklear/NK_WINDOW_NO_SCROLLBAR))
         (let [result# (do ~@body)]
           (Nuklear/nk_end context#)
           result#))
       (finally
         (MemoryStack/stackPop)))))


(defmacro without-window-padding
  "Temporarily disable window padding"
  [gui & body]
  `(let [stack#   (MemoryStack/stackPush)
         nk-vec2# (NkVec2/malloc stack#)
         win#     (.window (.style ^NkContext (::context ~gui)))]
     (.x nk-vec2# 0)
     (.y nk-vec2# 0)
     (.padding win# nk-vec2#)
     ~@body
      (.x nk-vec2# (scale ~gui 4))
      (.y nk-vec2# (scale ~gui 4))
     (.padding win# nk-vec2#)
     (MemoryStack/stackPop)))


(defn layout-row-dynamic
  "Create dynamic layout with specified height and number of columns"
  {:malli/schema [:=> [:cat :some :double :int] :nil]}
  [gui height cols]
  (Nuklear/nk_layout_row_dynamic (::context gui) height cols))


(defn make-bitmap-font
  "Create a bitmap font with character packing data"
  {:malli/schema [:=> [:cat :string :int :int :int [:? [:cat :int :int]]] :some]}
  ([ttf-filename bitmap-width bitmap-height font-height]
   (make-bitmap-font ttf-filename bitmap-width bitmap-height font-height 4 4))
  ([ttf-filename bitmap-width bitmap-height font-height h-oversample v-oversample]
   (let [num-chars    145
         fontinfo     (STBTTFontinfo/create)
         ttf          (slurp-byte-buffer ttf-filename)
         orig-descent (int-array 1)
         cdata        (STBTTPackedchar/calloc num-chars)
         pc           (STBTTPackContext/calloc)
         bitmap       (MemoryUtil/memAlloc (* ^long bitmap-width ^long bitmap-height))]
     (STBTruetype/stbtt_InitFont fontinfo ttf)
     (STBTruetype/stbtt_GetFontVMetrics fontinfo nil orig-descent nil)
     (STBTruetype/stbtt_PackBegin pc bitmap bitmap-width bitmap-height 0 1 0)
     (STBTruetype/stbtt_PackSetOversampling pc h-oversample v-oversample)
     (STBTruetype/stbtt_PackFontRange pc ttf 0 font-height 32 cdata)
     (STBTruetype/stbtt_PackEnd pc)
     (.free pc)
     (let [scale (STBTruetype/stbtt_ScaleForPixelHeight fontinfo font-height)
           data  (byte-buffer->array bitmap)
           alpha #:sfsim.image{:width bitmap-width :height bitmap-height :data data :channels 1}
           image (white-image-with-alpha alpha)
           texture (make-rgba-texture :sfsim.texture/linear :sfsim.texture/clamp image)]
       (MemoryUtil/memFree bitmap)
       {::fontinfo fontinfo
        ::ttf ttf  ; keep alive after passing buffer to stbtt_InitFont
        ::font-height font-height
        ::scale scale
        ::descent (* (aget orig-descent 0) scale)
        ::cdata cdata
        ::image image
        ::texture texture}))))


(defn set-font-texture-id
  "Configure font texture"
  {:malli/schema [:=> [:cat :some texture-2d] :any]}
  [font texture]
  (let [handle (NkHandle/create)]
    (.id ^NkHandle handle (:sfsim.texture/texture texture))
    (.texture ^NkUserFont font handle)))


(defn text-width-callback
  "Determine width of text in pixels"
  [fontinfo scale text len scale-x]
  (let [stack     (MemoryStack/stackPush)
        unicode   (.mallocInt stack 1)
        advance   (.mallocInt stack 1)
        glyph-len (Nuklear/nnk_utf_decode ^long text (MemoryUtil/memAddress unicode) ^long len)
        result
        (loop [text-len glyph-len glyph-len glyph-len text-width 0.0]
              (if (or (> ^long text-len ^long len)
                      (zero? glyph-len)
                      (= (.get unicode 0) Nuklear/NK_UTF_INVALID))
                text-width
                (do
                  (STBTruetype/stbtt_GetCodepointHMetrics ^STBTTFontinfo fontinfo (.get unicode 0) advance nil)
                  (let [text-width (+ text-width (* (.get advance 0) ^double scale ^double scale-x))
                        glyph-len  (Nuklear/nnk_utf_decode (+ ^long text text-len)
                                                           (MemoryUtil/memAddress unicode) (- ^long len text-len))]
                    (recur (+ text-len glyph-len) glyph-len text-width)))))]
    (MemoryStack/stackPop)
    result))


(defn text-width
  "Determine width of text in pixels"
  [text {::keys [fontinfo scale scale-x]}]
  (let [buffer   (MemoryUtil/memUTF8 ^String text)
        address  (MemoryUtil/memAddress ^DirectByteBuffer buffer)
        size     (.remaining buffer)
        result   (text-width-callback fontinfo scale address size scale-x)]
    (MemoryUtil/memFree buffer)
    result))


(defn set-width-callback
  "Set callback function for computing width of text"
  {:malli/schema [:=> [:cat :some :some :double] :any]}
  [{::keys [fontinfo scale]} font scale-x]
  (.width ^NkUserFont font
          (reify NkTextWidthCallbackI  ; do not simplify using a Clojure fn, because otherwise the uber jar build breaks
            (invoke
              [_this _handle _h text len]
              (text-width-callback fontinfo scale text len scale-x)))))


(defn set-height-callback
  "Set callback function for returning height of text"
  {:malli/schema [:=> [:cat :some :some :double] :any]}
  [{::keys [font-height]} font scale-y]
  (.height ^NkUserFont font (* ^double scale-y ^long font-height)))


(defn set-glyph-callback
  "Set callback function for getting rectangle of glyph"
  {:malli/schema [:=> [:cat :some :some :double :double] :any]}
  [{::keys [fontinfo image cdata scale descent]} font scale-x scale-y]
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
                  (let [ufg (NkUserFontGlyph/create glyph)
                        w   (- (.x1 q) (.x0 q))
                        h   (- (.y1 q) (.y0 q))
                        ox  (* ^double scale-x (.x0 q))
                        oy  (+ (* ^double scale-y (.y0 q)) font-height (* ^double scale-y ^double descent))]
                    (.width ufg (* ^double scale-x w))
                    (.height ufg (* ^double scale-y h))
                    (.set (.offset ufg) ox oy)
                    (.xadvance ufg (* (.get advance 0) ^double scale ^double scale-x))
                    (.set (.uv ufg 0) (.s0 q) (.t0 q))
                    (.set (.uv ufg 1) (.s1 q) (.t1 q)))
                  (MemoryStack/stackPop)))))))


(defn make-font
  "Set font texture, callbacks for text size, and glyph information"
  {:malli/schema [:=> [:cat :some :double :double] :some]}
  [{::keys [texture] :as bitmap-font} scale-x scale-y]
  (let [font (NkUserFont/create)]
    (set-font-texture-id font texture)
    (set-width-callback bitmap-font font scale-x)
    (set-height-callback bitmap-font font scale-y)
    (set-glyph-callback bitmap-font font scale-x scale-y)
    (assoc bitmap-font ::font font ::scale-x scale-x ::scale-y scale-y)))


(defn destroy-bitmap-font
  [bitmap-font]
  (destroy-texture (::texture bitmap-font))
  (.free ^STBTTPackedchar$Buffer (::cdata bitmap-font))
  (.free ^STBTTFontinfo (::fontinfo bitmap-font)))


(defn make-nuklear-gui-with-font
  "Render glyphs to texture and initialise GUI"
  {:malli/schema [:=> [:cat :double] :some]}
  [scale]
  (let [bitmap-font (make-font
                      (make-bitmap-font "resources/fonts/b612.ttf"
                                        (long (* 512 ^double scale)) (long (* 512 ^double scale))
                                        (long (* 18 ^double scale)))
                      1.0 1.0)]
    (assoc (make-nuklear-gui (::font bitmap-font) scale) ::bitmap-font bitmap-font)))


(defn destroy-nuklear-gui-with-font
  "Destroy Nuklear GUI and bitmap font"
  {:malli/schema [:=> [:cat :some] :nil]}
  [{::keys [bitmap-font] :as gui} ]
  (destroy-nuklear-gui gui)
  (destroy-bitmap-font bitmap-font))


(defn nuklear-global-scale
  [gui]
  (let [stack       (MemoryStack/stackPush)
        nk-vec2     (NkVec2/malloc ^MemoryStack stack)
        context     (::context gui)
        style       (.style ^NkContext context)]
    ;; Scale widget dimensions
    ;;
    ;; see https://github.com/Immediate-Mode-UI/Nuklear/blob/master/src/nuklear_style.c
    ;;
    ;; default buttons
    (let [button (.button style)]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding button nk-vec2)
      (.border button (scale gui 1))
      (.rounding button (scale gui 4)))
    ;; contextual button
    (let [button (.contextual_button style)]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding button nk-vec2))
    ;; menu button
    (let [button (.menu_button style)]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding button nk-vec2)
      (.rounding button(scale gui 1)))
    ;; checkbox toggle
    (let [toggle (.checkbox style)]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding toggle nk-vec2)
      (.spacing toggle (scale gui 4)))
    ;; option toggle
    (let [toggle (.option style)]
      (.x nk-vec2 (scale gui 3))
      (.y nk-vec2 (scale gui 3))
      (.padding toggle nk-vec2)
      (.spacing toggle (scale gui 4)))
    ;; selectable
    (let [select (.selectable style)]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding select nk-vec2)
      (.image_padding select nk-vec2))
    ;; slider
    (let [slider (.slider style)]
      (.x nk-vec2 (scale gui 16))
      (.y nk-vec2 (scale gui 16))
      (.cursor_size slider nk-vec2)
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding slider nk-vec2)
      (.spacing slider nk-vec2)
      (.bar_height slider (scale gui 4)))
    ;; slider buttons
    (doseq [button [(.inc_button (.slider style))
                    (.dec_button (.slider style))]]
           (.border ^NkStyleButton button(scale gui 1)))
    ;; knob
    (let [knob (.knob style)]
      (.knob_border knob(scale gui 1))
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding knob nk-vec2)
      (.cursor_width knob (scale gui 2)))
    ;; progress
    (let [prog (.progress style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding prog nk-vec2))
    ;; scrollbars
    (doseq [_scroll [(.scrollh style) (.scrollv style)]])
    ;; scrollbars buttons
    (doseq [scroll [(.scrollh style) (.scrollv style)]]
           (doseq [button [(.inc_button ^NkStyleScrollbar scroll)
                           (.dec_button ^NkStyleScrollbar scroll)]]
                  (.border ^NkStyleButton button(scale gui 1))))
    ;; edit
    (let [edit (.edit style)]
      (.x nk-vec2 (scale gui 10))
      (.y nk-vec2 (scale gui 10))
      (.scrollbar_size edit nk-vec2)
      (let [_scroll (.scrollbar edit)])
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding edit nk-vec2)
      (.row_padding edit (scale gui 2))
      (.cursor_size edit (scale gui 4))
      (.border edit(scale gui 1)))
    ;; property
    (let [property (.property style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding property nk-vec2)
      (.border property(scale gui 1))
      (.rounding property (scale gui 10)))
    ;; property buttons
    (doseq [_button [(.inc_button (.property style))
                     (.dec_button (.property style))]])
    ;; property edit
    (let [edit (.edit (.property style))]
      (.cursor_size edit (scale gui 8)))
    ;; chart
    (let [chart (.chart style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding chart nk-vec2))
    ;; combo
    (let [combo (.combo style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.content_padding combo nk-vec2)
      (.x nk-vec2 (scale gui 0))
      (.y nk-vec2 (scale gui 4))
      (.button_padding combo nk-vec2)
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 0))
      (.spacing combo nk-vec2)
      (.border combo(scale gui 1)))
    ;; combo button
    (let [button (.button (.combo style))]
      (.x nk-vec2 (scale gui 2))
      (.y nk-vec2 (scale gui 2))
      (.padding button nk-vec2))
    ;; tab
    (let [tab (.tab style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding tab nk-vec2)
      (.spacing tab nk-vec2)
      (.indent tab (scale gui 10))
      (.border tab(scale gui 1)))
    ;; tab button
    (doseq [button [(.tab_minimize_button (.tab style))
                    (.tab_maximize_button (.tab style))]]
           (.x nk-vec2 (scale gui 2))
           (.y nk-vec2 (scale gui 2))
           (.padding ^NkStyleButton button nk-vec2))
    ;; node button
    (doseq [button [(.node_minimize_button (.tab style))
                    (.node_maximize_button (.tab style))]]
           (.x nk-vec2 (scale gui 2))
           (.y nk-vec2 (scale gui 2))
           (.padding ^NkStyleButton button nk-vec2))
    ;; window header
    (let [header (.header (.window style))]
           (.x nk-vec2 (scale gui 4))
           (.y nk-vec2 (scale gui 4))
           (.label_padding header nk-vec2)
           (.padding header nk-vec2))
    ;; window header close button
    (let [_button (.close_button (.header (.window style)))])
    ;; window header minimize button
    (let [_button (.minimize_button (.header (.window style)))])
    ;; window
    (let [win (.window style)]
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.spacing win nk-vec2)
      (.x nk-vec2 (scale gui 10))
      (.y nk-vec2 (scale gui 10))
      (.scrollbar_size win nk-vec2)
      (.x nk-vec2 (scale gui 64))
      (.y nk-vec2 (scale gui 64))
      (.min_size win nk-vec2)
      (.combo_border win(scale gui 1))
      (.contextual_border win(scale gui 1))
      (.menu_border win(scale gui 1))
      (.group_border win(scale gui 1))
      (.tooltip_border win(scale gui 1))
      (.popup_border win(scale gui 1))
      (.border win (scale gui 2))
      (.min_row_height_padding win (scale gui 8))
      (.x nk-vec2 (scale gui 4))
      (.y nk-vec2 (scale gui 4))
      (.padding win nk-vec2)
      (.group_padding win nk-vec2)
      (.popup_padding win nk-vec2)
      (.combo_padding win nk-vec2)
      (.contextual_padding win nk-vec2)
      (.menu_padding win nk-vec2)
      (.tooltip_padding win nk-vec2))
    (MemoryStack/stackPop)))


(defn nuklear-dark-style
  [gui]
  (let [stack       (MemoryStack/stackPush)
        rgb         (NkColor/malloc ^MemoryStack stack)
        style-table (NkColor/malloc Nuklear/NK_COLOR_COUNT ^MemoryStack stack)
        context     (::context gui)]
    ;; Set color scheme
    ;;
    ;; see https://github.com/Immediate-Mode-UI/Nuklear/blob/master/src/nuklear_style.c
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
    (Nuklear/nk_style_from_table context style-table)
    ;; pop stack
    (MemoryStack/stackPop)
    (nuklear-global-scale gui)))


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


(defn property-int
  "Create a property slider with integer value"
  {:malli/schema [:=> [:cat :some :string :int :int :int :int :double] :double]}
  [gui text minimum value maximum step inc-per-pixel]
  (let [buffer (int-array 1)]
    (aset-int buffer 0 value)
    (Nuklear/nk_property_int ^NkContext (::context gui) ^String text ^int minimum buffer ^int maximum ^int step ^float inc-per-pixel)
    (aget buffer 0)))


(defn property-float
  "Create a property slider with float value"
  {:malli/schema [:=> [:cat :some :string :double :double :double :double :double] :double]}
  [gui text minimum value maximum step inc-per-pixel]
  (let [buffer (float-array 1)]
    (aset-float buffer 0 value)
    (Nuklear/nk_property_float ^NkContext (::context gui) ^String text ^float minimum buffer ^float maximum ^float step
                               ^float inc-per-pixel)
    (double (aget buffer 0))))


(defn button-label
  "Create a button with text label"
  [gui label]
  (Nuklear/nk_button_label ^NkContext (::context gui) ^String label))


(defn check-label
  "Create a check box with text"
  [gui text on]
  (Nuklear/nk_check_label ^NkContext (::context gui) ^String text ^boolean on))


(defn option-label
  "Create a radio button with text"
  [gui text on]
  (Nuklear/nk_option_label ^NkContext (::context gui) ^String text ^boolean on))


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
  "Layout widgets using specified height and specified number of widgets in a row"
  [gui height cnt & body]
  `(do
     (Nuklear/nk_layout_row_begin (::context ~gui) Nuklear/NK_DYNAMIC ~height ~cnt)
     (let [result# (do ~@body)]
       (Nuklear/nk_layout_row_end (::context ~gui))
       result#)))


(defn layout-row-push
  "Set fraction of space next widget takes up in this layout row"
  [gui frac]
  (Nuklear/nk_layout_row_push (::context gui) frac))


(defmacro tabbing
  "Use and update keyboard tab focus for specified edit field"
  [gui state edit idx cnt]
  `(ignore-nil->
     ~'state ~state
     (when (and (-> ~'state :input :sfsim.input/focus-new) (= (mod (-> ~'state :input :sfsim.input/focus-new) ~cnt) ~idx))
       (Nuklear/nk_edit_focus (::context ~gui) Nuklear/NK_EDIT_ACTIVE)
       (dissoc-in ~'state [:input :sfsim.input/focus-new]))
     (when (= Nuklear/NK_EDIT_ACTIVE ~edit)
       (assoc-in ~'state [:input :sfsim.input/focus] ~idx))))


(defmacro group
  "Macro to set up widget group, do not forget to set the layout as first command in body"
  [gui group-name group-title & body]
  `(try
     (Nuklear/nk_group_begin_titled ^NkContext (::context ~gui) ~group-name ~group-title Nuklear/NK_WINDOW_BORDER)
     ~@body
     (finally
       (Nuklear/nk_group_end ^NkContext (::context ~gui)))))


(defmacro widget
  "Macro to set up rendering of a custom widget"
  [gui canvas rect & body]
  `(let [context# (::context ~gui)
         ~canvas  (Nuklear/nk_window_get_canvas context#)
         stack#   (MemoryStack/stackPush)
         ~rect    (NkRect/malloc stack#)]
     (Nuklear/nk_widget ~rect context#)
     ~@body
     (MemoryStack/stackPop)))


(defmacro with-rect
  "Define a temporary rectangle"
  [rect x y w h & body]
  `(let [stack# (MemoryStack/stackPush)
         ~rect  (NkRect/malloc stack#)]
     (Nuklear/nk_rect ~x ~y ~w ~h ~rect)
     (try
       ~@body
       (finally
         (MemoryStack/stackPop)))))


(defmacro with-color
  "Define a colour"
  [color r g b & body]
  `(let [stack# (MemoryStack/stackPush)
         ~color (NkColor/malloc stack#)]
     (Nuklear/nk_rgb ~r ~g ~b ~color)
     (try
       ~@body
       (finally
         (MemoryStack/stackPop)))))


(defmacro with-colors
  "Define a few colours"
  [colors & body]
  (if (seq colors)
    `(with-color ~(nth colors 0) ~(nth colors 1) ~(nth colors 2) ~(nth colors 3)
       (with-colors ~(drop 4 colors) ~@body))
    `(do ~@body)))


(defn fill-rect
  "Draw filled rectangle"
  [canvas rect rounding color]
  (Nuklear/nk_fill_rect canvas rect rounding color))


(defn stroke-rect
  "Draw outlines of rectangle"
  [canvas rect rounding thickness color]
  (Nuklear/nk_stroke_rect canvas rect rounding thickness color))


(defn fill-circle
  "Draw filled circle"
  [canvas rect color]
  (Nuklear/nk_fill_circle canvas rect color))


(defn stroke-circle
  "Draw circle outline"
  [canvas rect thickness color]
  (Nuklear/nk_stroke_circle canvas rect thickness color))


(defn stroke-arc
  "Draw arc outline"
  [canvas cx cy radius a-min a-max thickness color]
  (Nuklear/nk_stroke_arc canvas cx cy radius a-min a-max thickness color))


(defn stroke-line
  "Draw a line"
  [canvas x0 y0 x1 y1 thickness color]
  (Nuklear/nk_stroke_line canvas x0 y0 x1 y1 thickness color))


(defn fill-polygon
  "Draw filled polygon"
  [canvas points color]
  (Nuklear/nk_fill_polygon ^NkCommandBuffer canvas (float-array (flatten points)) ^NkColor color))


(defn stroke-polygon
  "Draw polygon"
  [canvas points thickness color]
  (Nuklear/nk_stroke_polygon ^NkCommandBuffer canvas (float-array (flatten points)) ^double thickness ^NkColor color))


(defn stroke-polyline
  "Draw polygon"
  [canvas points thickness color]
  (Nuklear/nk_stroke_polyline ^NkCommandBuffer canvas (float-array (flatten points)) ^double thickness ^NkColor color))


(defn draw-text
  "Draw left-aligned text on a canvas"
  [canvas x y w h text font color]
  (with-rect rect x y w h
    (with-color bg 0 0 0
      (Nuklear/nk_draw_text ^NkCommandBuffer canvas rect ^String text ^NkUserFont (::font font) bg ^NkColor color))))


(defn draw-text-right
  "Draw right-aligned text on a canvas"
  [canvas x y w h text font color]
  (let [text-width (text-width text font)]
    (draw-text canvas (- (+ ^double x ^double w) ^double text-width) y text-width h text font color)))


(declare main-dialog)


(defn toggle-joystick-control
  [state control]
  (if (= (-> state :gui ::joystick-config) control)
    (dissoc-in state [:gui ::joystick-config])
    (assoc-in state [:gui ::joystick-config] control)))


(defn joystick-dialog-item
  [state gui sensor-type last-event text control sensor-name prompt]
  (let [[device sensor] (get-joystick-sensor-for-mapping (-> state :input :sfsim.input/mappings) sensor-type control)]
    (layout-row
      gui (scale gui row-height) 4
      (ignore-nil-> state state
                    (layout-row-push gui 0.2)
                    (text-label gui text)
                    (layout-row-push gui 0.1)
                    (when (and (button-label gui "Clear") device)
                      (dissoc-in state [:input :sfsim.input/mappings :sfsim.input/joysticks :sfsim.input/devices
                                        device sensor-type sensor]))
                    (layout-row-push gui 0.1)
                    (when (button-label gui "Set")
                      (-> state
                          (dissoc-in [:input last-event])
                          (toggle-joystick-control control)))
                    (when-let [[device-new sensor-new] (and (= (-> state :gui ::joystick-config) control)
                                                            (-> state :input last-event))]
                              (-> state
                                  (dissoc-in [:input :sfsim.input/mappings :sfsim.input/joysticks :sfsim.input/devices
                                              device sensor-type sensor])
                                  (assoc-in [:input :sfsim.input/mappings :sfsim.input/joysticks :sfsim.input/devices
                                             device-new sensor-type sensor-new] control)
                                  (dissoc-in [:gui ::joystick-config])))
                    (layout-row-push gui 0.57)
                    (text-label gui (if (= (-> state :input ::joystick-config) control)
                                      prompt
                                      (if device (format "%s %d of %s" sensor-name sensor device) "None")))))))


(defn joystick-dialog-axis-item
  [state gui text control]
  (joystick-dialog-item state gui :sfsim.input/axes :sfsim.input/last-joystick-axis text control
                        "Axis" "Move axis to set"))


(defn joystick-dialog-button-item
  [state gui text control]
  (joystick-dialog-item state gui :sfsim.input/buttons :sfsim.input/last-joystick-button text control
                        "Button" "Press button to set"))


(defn joystick-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui "Joystick" (quot (- window-width (scale gui 640)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long widget-height 11) ^long padding))) 2)
    (scale gui 640) (scale gui (+ ^long title-height (* ^long widget-height 11) ^long padding)) :dialog
    (ignore-nil-> state state
                  (layout-row-dynamic gui (scale gui (* ^long widget-height 10)) 1)
                  (group gui "joystick" "Joystick"
                         (ignore-nil->
                           state state
                           (joystick-dialog-axis-item state gui "Aileron" :sfsim.input/aileron)
                           (joystick-dialog-axis-item state gui "Elevator" :sfsim.input/elevator)
                           (joystick-dialog-axis-item state gui "Rudder" :sfsim.input/rudder)
                           (joystick-dialog-axis-item state gui "Throttle" :sfsim.input/throttle)
                           (joystick-dialog-axis-item state gui "Throttle increment" :sfsim.input/throttle-increment)
                           (layout-row gui (scale gui row-height) 2
                                       (ignore-nil-> state state
                                                     (layout-row-push gui 0.2)
                                                     (text-label gui "Dead zone")
                                                     (layout-row-push gui 0.67)
                                                     (update-in state
                                                                [:input :sfsim.input/mappings
                                                                 :sfsim.input/joysticks
                                                                 :sfsim.input/dead-zone]
                                                                (fn [dead-zone]
                                                                    (slider-float gui 0.0 dead-zone 1.0 (/ 1.0 1024.0))))
                                                     (layout-row-push gui 0.1)
                                                     (text-label gui (format "%5.3f" (get-in state
                                                                                             [:input :sfsim.input/mappings
                                                                                              :sfsim.input/joysticks
                                                                                              :sfsim.input/dead-zone])))))
                           (joystick-dialog-button-item state gui "Gear" :sfsim.input/gear)
                           (joystick-dialog-button-item state gui "Air brake" :sfsim.input/air-brake)
                           (joystick-dialog-button-item state gui "RCS/aerofoil" :sfsim.input/rcs)
                           (joystick-dialog-button-item state gui "Brake" :sfsim.input/brake)
                           (joystick-dialog-button-item state gui "Parking brake" :sfsim.input/parking-brake)
                           (joystick-dialog-button-item state gui "Camera down" :sfsim.input/camera-rotate-x-positive)
                           (joystick-dialog-button-item state gui "Camera up" :sfsim.input/camera-rotate-x-negative)
                           (joystick-dialog-button-item state gui "Camera left" :sfsim.input/camera-rotate-y-negative)
                           (joystick-dialog-button-item state gui "Camera right" :sfsim.input/camera-rotate-y-positive)
                           (joystick-dialog-button-item state gui "Camera roll left" :sfsim.input/camera-rotate-z-positive)
                           (joystick-dialog-button-item state gui "Camera roll right" :sfsim.input/camera-rotate-z-negative)
                           (joystick-dialog-button-item state gui "Camera reset" :sfsim.input/camera-reset)))
                  (layout-row-dynamic gui (scale gui row-height) 2)
                  (when (button-label gui "Save")
                    (config/write-user-config "joysticks.edn" (get-in state [:input :sfsim.input/mappings :sfsim.input/joysticks]))
                    (assoc-in state [:gui ::menu] main-dialog))
                  (when (button-label gui "Close")
                    (-> state
                        (assoc-in [:input :sfsim.input/mappings :sfsim.input/joysticks]
                                  (config/read-user-config "joysticks.edn" {:sfsim.input/dead-zone 0.1}))
                        (assoc-in [:gui ::menu] main-dialog))))))


(defn keyboard-dialog
  [state gui ^long window-width ^long window-height]
  (let [mappings (invert-map (get-in state [:input :sfsim.input/mappings :sfsim.input/keyboard]))]
    (nuklear-window
      gui "Keyboard"
      (quot (- window-width (scale gui 480)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long text-height 20) ^long widget-height ^long padding))) 2)
      (scale gui 480) (scale gui (+ ^long title-height (* ^long text-height 20) ^long widget-height ^long padding)) :dialog
      (ignore-nil-> state state
                    (layout-row-dynamic gui (scale gui (* ^long text-height 20)) 1)
                    (group gui "keyboard" "Keyboard"
                           (layout-row-dynamic gui (scale gui text-row-height) 1)
                           (text-label gui "Note that joystick overrides keyboard commands!")
                           (layout-row-dynamic gui (scale gui text-row-height) 2)
                           (doseq [[control-key control-name]
                                   [[:sfsim.input/menu                            "Toggle menu"             ]
                                    ["Alt-Return"                                 "Toggle fullscreen"       ]
                                    [:sfsim.input/pause                           "Pause/unpause"           ]
                                    [:sfsim.input/time-lapse-slow-down            "Time lapse slow down"    ]
                                    [:sfsim.input/time-lapse-reset                "Time lapse reset"        ]
                                    [:sfsim.input/time-lapse-speed-up             "Time lapse speed up"     ]
                                    [:sfsim.input/gear                            "Gear up/down"            ]
                                    [:sfsim.input/brake                           "Brake"                   ]
                                    ["Shift-B"                                    "Parking brake"           ]
                                    [:sfsim.input/throttle-decrease               "Throttle decrease"       ]
                                    [:sfsim.input/throttle-increase               "Throttle increase"       ]
                                    [:sfsim.input/air-brake                       "Air brake"               ]
                                    [:sfsim.input/rcs                             "Toggle RCS/aerofoil"     ]
                                    [:sfsim.input/aileron-left                    "Aileron left"            ]
                                    [:sfsim.input/aileron-right                   "Aileron right"           ]
                                    ["Ctrl-A"                                     "Aileron center"          ]
                                    [:sfsim.input/elevator-down                   "Elevator down"           ]
                                    [:sfsim.input/elevator-up                     "Elevator up"             ]
                                    [:sfsim.input/rudder-left                     "Rudder left"             ]
                                    [:sfsim.input/rudder-right                    "Rudder right"            ]
                                    ["Ctrl-Q"                                     "Center rudder"           ]
                                    [:sfsim.input/camera-rotate-x-positive        "Camera rotate X positive"]
                                    [:sfsim.input/camera-rotate-x-negative        "Camera rotate X negative"]
                                    [:sfsim.input/camera-rotate-y-positive        "Camera rotate Y positive"]
                                    [:sfsim.input/camera-rotate-y-negative        "Camera rotate Y negative"]
                                    [:sfsim.input/camera-rotate-z-positive        "Camera rotate Z positive"]
                                    [:sfsim.input/camera-rotate-z-negative        "Camera rotate Z negative"]
                                    [:sfsim.input/camera-distance-change-positive "Increase camera distance"]
                                    [:sfsim.input/camera-distance-change-negative "Decrease camera distance"]
                                    [:sfsim.input/camera-reset                    "Reset camera"]]]
                                  (text-label gui control-name)
                                  (text-label gui (if (keyword? control-key) (get-key-name (control-key mappings)) control-key))))
                    (layout-row-dynamic gui (scale gui row-height) 1)
                    (when (button-label gui "Close")
                      (assoc-in state [:gui ::menu] main-dialog))))))


(defn sound-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui "Sound" (quot (- window-width (scale gui 320)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long widget-height 3)))) 2)
    (scale gui 320) (scale gui (+ ^long title-height (* ^long widget-height 3))) :dialog
    (ignore-nil-> state state
                  (layout-row-dynamic gui (scale gui row-height) 2)
                  (text-label gui "Volume")
                  (update-in state [:audio :sfsim.audio/settings :sfsim.audio/volume] #(property-float gui "volume" 0.0 % 1.0 0.1 0.0025))
                  (text-label gui "Music")
                  (update-in state [:audio :sfsim.audio/settings :sfsim.audio/no-music]
                             (fn [off] (not (check-label gui "enable" (not off)))))
                  (when (button-label gui "Save")
                    (config/write-user-config "sound.edn" (get-in state [:audio :sfsim.audio/settings]))
                    (assoc-in state [:gui ::menu] main-dialog))
                  (when (button-label gui "Close")
                    (-> state
                        (assoc-in [:audio :sfsim.audio/settings]
                                  (config/read-user-config "sound.edn" {:sfsim.audio/volume 1.0 :sfsim.audio/no-music false}))
                        (assoc-in [:gui ::menu] main-dialog))))))


(defn license-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui "License"
    (quot (- window-width (scale gui 768)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long text-height 12) ^long padding ^long widget-height))) 2)
    (scale gui 768) (scale gui (+ ^long title-height (* ^long text-height 12) ^long padding ^long widget-height)) :dialog
    (ignore-nil-> state state
      (layout-row-dynamic gui (scale gui (* ^long text-height 12)) 1)
      (group gui "license" "License"
             (layout-row-dynamic gui (scale gui text-row-height) 1)
             (doseq [line (line-seq (io/reader "about.txt"))]
                    (text-label gui line)))
      (layout-row-dynamic gui (scale gui row-height) 1)
      (when (button-label gui "Close")
        (assoc-in state [:gui ::menu] main-dialog)))))


(def position-data
  {:longitude (edit-data "0.0" 32 ::filter-float)
   :latitude  (edit-data "0.0" 32 ::filter-float)
   :height    (edit-data "0.0" 32 ::filter-float)})


(defn location-dialog-get
  [position-data]
  (let [longitude   (to-radians (Double/parseDouble (edit-get (:longitude position-data))))
        latitude    (to-radians (Double/parseDouble (edit-get (:latitude position-data))))
        height      (Double/parseDouble (edit-get (:height position-data)))]
    {:longitude longitude :latitude latitude :height height}))


(defn location-dialog-set
  [position-data state]
  (let [geographic (physics/get-geographic (:physics state) config/planet-config)]
    (edit-set (:longitude position-data) (format "%.5f" (to-degrees (:longitude geographic))))
    (edit-set (:latitude position-data) (format "%.5f" (to-degrees (:latitude geographic))))
    (edit-set (:height position-data) (format "%.1f" (:height geographic)))))


(defn location-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui "Location" (quot (- window-width (scale gui 320)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long widget-height 4)))) 2)
    (scale gui 320) (scale gui (+ ^long title-height (* ^long widget-height 4))) :dialog
    (ignore-nil-> state state
                  (layout-row-dynamic gui (scale gui row-height) 2)
                  (text-label gui "Longitude (East)")
                  (tabbing gui state (edit-field gui (:longitude position-data)) 0 3)
                  (text-label gui "Latitude (North)")
                  (tabbing gui state (edit-field gui (:latitude position-data)) 1 3)
                  (text-label gui "Height")
                  (tabbing gui state (edit-field gui (:height position-data)) 2 3)
                  (when (button-label gui "Set")
                    (let [{:keys [longitude latitude height]} (location-dialog-get position-data)]
                      (-> state
                          (update :physics physics/destroy-vehicle-constraint)
                          (update :physics physics/set-geographic (:surface state) config/planet-config
                                  (:sfsim.model/elevation config/model-config) longitude latitude height))))
                  (when (button-label gui "Close")
                    (assoc-in state [:gui ::menu] main-dialog)))))


(def time-data
  {:day    (edit-data    "1" 3 ::filter-decimal)
   :month  (edit-data    "1" 3 ::filter-decimal)
   :year   (edit-data "2000" 5 ::filter-decimal)
   :hour   (edit-data   "12" 3 ::filter-decimal)
   :minute (edit-data    "0" 3 ::filter-decimal)
   :second (edit-data    "0" 3 ::filter-decimal)})


(defn datetime-dialog-get
  ^double [time-data]
  (let [day    (Integer/parseInt (trim (edit-get (:day time-data))))
        month  (Integer/parseInt (trim (edit-get (:month time-data))))
        year   (Integer/parseInt (trim (edit-get (:year time-data))))
        hour   (Integer/parseInt (trim (edit-get (:hour time-data))))
        minute (Integer/parseInt (trim (edit-get (:minute time-data))))
        sec    (Integer/parseInt (trim (edit-get (:second time-data))))
        jd     (astro/julian-date #:sfsim.astro{:year year :month month :day day})
        clock  (/ (+ (/ (+ (/ sec 60.0) minute) 60.0) hour) 24.0)]
    (+ (- jd 0.5) clock)))


(defn datetime-dialog-set
  [time-data state]
  (let [t     (+ ^double (physics/get-julian-date-ut (:physics state)) 0.5)
        date  (astro/calendar-date (int t))
        clock (astro/clock-time (- t (int t)))]
    (edit-set (:day time-data) (format "%2d" (:sfsim.astro/day date)))
    (edit-set (:month time-data) (format "%2d" (:sfsim.astro/month date)))
    (edit-set (:year time-data) (format "%4d" (:sfsim.astro/year date)))
    (edit-set (:hour time-data) (format "%2d" (:sfsim.astro/hour clock)))
    (edit-set (:minute time-data) (format "%2d" (:sfsim.astro/minute clock)))
    (edit-set (:second time-data) (format "%2d" (:sfsim.astro/second clock)))))


(defn datetime-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui "Date and Time"
    (quot (- window-width (scale gui 320)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long widget-height 3)))) 2)
    (scale gui 320) (scale gui (+ ^long title-height (* ^long widget-height 3))) :dialog
    (ignore-nil-> state state
                  (layout-row gui (scale gui row-height) 6
                              (layout-row-push gui 0.4)
                              (text-label gui "Date")
                              (layout-row-push gui 0.15)
                              (tabbing gui state (edit-field gui (:day time-data)) 0 6)
                              (layout-row-push gui 0.05)
                              (text-label gui "/")
                              (layout-row-push gui 0.15)
                              (tabbing gui state (edit-field gui (:month time-data)) 1 6)
                              (layout-row-push gui 0.05)
                              (text-label gui "/")
                              (layout-row-push gui 0.2)
                              (tabbing gui state (edit-field gui (:year time-data)) 2 6))
                  (layout-row gui (scale gui row-height) 6
                              (layout-row-push gui 0.45)
                              (text-label gui "Time")
                              (layout-row-push gui 0.15)
                              (tabbing gui state (edit-field gui (:hour time-data)) 3 6)
                              (layout-row-push gui 0.05)
                              (text-label gui ":")
                              (layout-row-push gui 0.15)
                              (tabbing gui state (edit-field gui (:minute time-data)) 4 6)
                              (layout-row-push gui 0.05)
                              (text-label gui ":")
                              (layout-row-push gui 0.14999)
                              (tabbing gui state (edit-field gui (:second time-data)) 5 6))
                  (layout-row-dynamic gui (scale gui row-height) 2)
                  (when (button-label gui "Set")
                    (update state :physics physics/set-julian-date-ut (datetime-dialog-get time-data)))
                  (when (button-label gui "Close")
                    (assoc-in state [:gui ::menu] main-dialog)))))


(defn main-dialog
  [state gui ^long window-width ^long window-height]
  (nuklear-window
    gui (format "sfsim %s" version)
    (quot (- window-width (scale gui 320)) 2) (quot (- window-height (scale gui (+ ^long title-height (* ^long widget-height 8)))) 2)
    (scale gui 320) (scale gui (+ ^long title-height (* ^long widget-height 8)))
    :dialog
    (ignore-nil-> state state
                  (layout-row-dynamic gui (scale gui row-height) 1)
                  (when (button-label gui "Location")
                    (location-dialog-set position-data state)
                    (assoc-in state [:gui ::menu] location-dialog))
                  (when (button-label gui "Date/Time")
                    (datetime-dialog-set time-data state)
                    (assoc-in state [:gui ::menu] datetime-dialog))
                  (when (button-label gui "Joystick")
                    (assoc-in state [:gui ::menu] joystick-dialog))
                  (when (button-label gui "Keyboard")
                    (assoc-in state [:gui ::menu] keyboard-dialog))
                  (when (button-label gui "Sound")
                    (assoc-in state [:gui ::menu] sound-dialog))
                  (when (button-label gui "Resume")
                    (assoc-in state [:input :sfsim.input/menu] nil))
                  (when (button-label gui "License")
                    (assoc-in state [:gui ::menu] license-dialog))
                  (when (button-label gui "Quit")
                    (GLFW/glfwSetWindowShouldClose (:window state) true)))))


(defn flight-controls-display
  [input-controls gui]
  (let [stack    (MemoryStack/stackPush)
        rect     (NkRect/malloc stack)
        rgb      (NkColor/malloc stack)
        throttle (:sfsim.input/throttle input-controls)
        aileron  (:sfsim.input/aileron input-controls)
        elevator (:sfsim.input/elevator input-controls)
        rudder   (:sfsim.input/rudder input-controls)]
    (nuklear-window gui "Yoke" (scale gui 10) (scale gui 10) (scale gui 80) (scale gui 80) :widget
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui (scale gui 80) 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect (scale gui (- 45 (* ^double aileron 30)))
                                                               (scale gui (- 45 (* ^double elevator 30)))
                                                               (scale gui 10) (scale gui 10)
                                                               rect)
                                              (Nuklear/nk_rgb 255 0 0 rgb))))
    (nuklear-window gui "Rudder" (scale gui 10) (scale gui 95) (scale gui 80) (scale gui 20) :widget
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui (scale gui 20) 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect (scale gui (- 45 (* ^double rudder 30)))
                                                               (scale gui 100)
                                                               (scale gui 10) (scale gui 10)
                                                               rect)
                                              (Nuklear/nk_rgb 255 0 255 rgb))))
    (nuklear-window gui "Throttle" (scale gui 95) (scale gui 10) (scale gui 20) (scale gui 80) :widget
                    (let [canvas (Nuklear/nk_window_get_canvas (::context gui))]
                      (layout-row-dynamic gui (scale gui 80) 1)
                      (Nuklear/nk_widget rect (::context gui))
                      (Nuklear/nk_fill_circle canvas
                                              (Nuklear/nk_rect (scale gui 100)
                                                               (scale gui (- 75 (* 60 ^double throttle)))
                                                               (scale gui 10) (scale gui 10)
                                                               rect)
                                              (Nuklear/nk_rgb 255 255 255 rgb)))))
  (MemoryStack/stackPop))


(defn float-str
  "Create formatted string from distance value"
  ^String [^double number]
  ;; Also see https://github.com/orbitersim/orbiter/blob/main/Src/Orbiter/Astro.cpp
  (cond
    (< (abs number) 1e+3 ) (format "%7.2f" number)
    (< (abs number) 1e+4 ) (format "%6.3fk" (* number 1e-3 ))
    (< (abs number) 1e+5 ) (format "%6.2fk" (* number 1e-3 ))
    (< (abs number) 1e+6 ) (format "%6.1fk" (* number 1e-3 ))
    (< (abs number) 1e+7 ) (format "%6.3fM" (* number 1e-6 ))
    (< (abs number) 1e+8 ) (format "%6.2fM" (* number 1e-6 ))
    (< (abs number) 1e+9 ) (format "%6.1fM" (* number 1e-6 ))
    (< (abs number) 1e+10) (format "%6.3fG" (* number 1e-9 ))
    (< (abs number) 1e+11) (format "%6.2fG" (* number 1e-9 ))
    (< (abs number) 1e+12) (format "%6.1fG" (* number 1e-9 ))
    (< (abs number) 1e+13) (format "%6.3fT" (* number 1e-12))
    (< (abs number) 1e+14) (format "%6.2fT" (* number 1e-12))
    (< (abs number) 1e+15) (format "%6.1fT" (* number 1e-12))
    :else (format "%7.0g" number)))


(defn distance-for-anomaly
  "Compute orbit distance to planet center for given true anomaly"
  ^double [{:sfsim.physics/keys [semi-major-axis eccentricity]} ^double anomaly]
  (/ (* ^double semi-major-axis (- 1.0 (sqr eccentricity))) (+ 1.0 (* ^double eccentricity (cos anomaly)))))


(defn orbit-point
  "Sample a point from the orbit displayed in the orbit MFD"
  [{:sfsim.physics/keys [argument-of-periapsis] :as orbit-params} center-x center-y scale anomaly]
  (let [dist (distance-for-anomaly orbit-params anomaly)
        ra   (* ^double scale dist (cos anomaly))
        rb   (* ^double scale dist (sin anomaly))]
    [(+ ^double center-x (- (* (cos argument-of-periapsis) ra) (* (sin argument-of-periapsis) rb)))
     (- ^double center-y (+ (* (sin argument-of-periapsis) ra) (* (cos argument-of-periapsis) rb)))]))


(defn orbit-mfd
  "Visualize orbit and display parameters"
  [gui {:sfsim.physics/keys [periapsis-altitude apoapsis-altitude altitude eccentricity orbital-period time-since-periapsis
                             time-since-apoapsis velocity inclination longitude-ascending-node argument-of-periapsis
                             true-anomaly radius] :as orbital-params}]
  (widget gui canvas canvas-rect
          (let
            [font  (::bitmap-font gui)
             x0    (.x canvas-rect)
             y0    (.y canvas-rect)
             w     (.w canvas-rect)
             h     (.h canvas-rect)
             cx    (+ x0 (/ w 2))
             cy    (+ y0 (/ h 2))
             s     (/ (* w 120) 256 (+ (max ^double apoapsis-altitude 0.0) ^double radius))
             earth (* s ^double radius)
             n     256]
            (with-colors
              [bg       0   0   0
               fg      64 211  71
               border  82 185 142
               bright 202 213 197
               title  129 226 207]
              (fill-rect canvas canvas-rect 0.0 bg)
              (with-rect rect (+ x0 (scale gui 1)) (+ y0 (scale gui 1)) (- w (scale gui 2)) (- h (scale gui 2))
                (stroke-rect canvas rect 0.0 (scale gui 3.0) border))
              (with-rect rect (- cx earth) (- cy earth) (* 2 earth) (* 2 earth)
                (stroke-circle canvas rect (scale gui 2.0) bright))
              (doseq [i (range n)]
                     (let [a (to-radians (/ (* 360 ^long i) n))
                           b (to-radians (/ (* 360 (inc ^long i)) n))
                           [x0 y0] (orbit-point orbital-params cx cy s a)
                           [x1 y1] (orbit-point orbital-params cx cy s b)]
                       (stroke-line canvas x0 y0 x1 y1 (scale gui 2.0) fg)))
              (let [[x y] (orbit-point orbital-params cx cy s true-anomaly)]
                (stroke-line canvas cx cy x y (scale gui 2.0) fg)
                (with-rect rect (- ^double x (scale gui 2)) (- ^double y (scale gui 2)) (scale gui 5) (scale gui 5)
                  (fill-circle canvas rect fg)))
              (let [[x y] (orbit-point orbital-params cx cy s (- ^double argument-of-periapsis))]
                (with-rect rect (- ^double x (scale gui 3)) (- ^double y (scale gui 3)) (scale gui 7) (scale gui 7)
                  (fill-rect canvas rect 0.0 fg)))
              (let [[x y] (orbit-point orbital-params cx cy s (- PI ^double argument-of-periapsis))]
                (with-rect rect (- ^double x (scale gui 2)) (- ^double y (scale gui 2)) (scale gui 5) (scale gui 5)
                  (fill-rect canvas rect 0.0 bg)
                  (stroke-rect canvas rect 0.0 (scale gui 2.0) fg)))
              (let [x1 (+ x0 (scale gui 5))
                    x2 (+ x0 (scale gui 45))
                    y1 (+ y0 (scale gui 5))
                    w1 (scale gui 40)
                    w2 (scale gui 55)
                    h (scale gui 20)]
                (draw-text canvas x1 (+ y1 (* h 0)) w1 h "Earth" font title)
                (draw-text canvas x1 (+ y1 (* h 1)) w1 h "PeA" font fg)
                (draw-text canvas x2 (+ y1 (* h 1)) w2 h (float-str periapsis-altitude) font fg)
                (draw-text canvas x1 (+ y1 (* h 2)) w1 h "ApA" font fg)
                (draw-text canvas x2 (+ y1 (* h 2)) w2 h (float-str apoapsis-altitude) font fg)
                (draw-text canvas x1 (+ y1 (* h 3)) w1 h "Alt" font fg)
                (draw-text canvas x2 (+ y1 (* h 3)) w2 h (float-str altitude) font fg)
                (draw-text canvas x1 (+ y1 (* h 4)) w1 h "Ecc" font fg)
                (draw-text canvas x2 (+ y1 (* h 4)) w2 h (format "%7.4f" eccentricity) font fg)
                (draw-text canvas x1 (+ y1 (* h 5)) w1 h "T" font fg)
                (draw-text canvas x2 (+ y1 (* h 5)) w2 h (float-str orbital-period) font fg)
                (draw-text canvas x1 (+ y1 (* h 6)) w1 h "PeT" font fg)
                (draw-text canvas x2 (+ y1 (* h 6)) w2 h (float-str (- ^double time-since-periapsis)) font fg)
                (draw-text canvas x1 (+ y1 (* h 7)) w1 h "ApT" font fg)
                (draw-text canvas x2 (+ y1 (* h 7)) w2 h (float-str (- ^double time-since-apoapsis)) font fg)
                (draw-text canvas x1 (+ y1 (* h 8)) w1 h "Vel" font fg)
                (draw-text canvas x2 (+ y1 (* h 8)) w2 h (float-str velocity) font fg)
                (draw-text canvas x1 (+ y1 (* h 9)) w1 h "Inc" font fg)
                (draw-text-right canvas x2 (+ y1 (* h 9)) w2 h (format "%6.2f°" (to-degrees inclination)) font fg)
                (draw-text canvas x1 (+ y1 (* h 10)) w1 h "LAN" font fg)
                (draw-text-right canvas x2 (+ y1 (* h 10)) w2 h (format "%6.2f°" (to-degrees longitude-ascending-node)) font fg))))))

(set! *unchecked-math* false)

(defn navball-orbit
  []
  (let [w 512 h 1024]
    (with-invisible-window
      (let [tex (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGB8 w h)]
        (framebuffer-render w h :sfsim.render/noculling nil [tex]
                            (let [gui   (make-nuklear-gui-with-font 1.0)
                                  yaw   [30 45 60 75 105 120 135 150]
                                  fonts (zipmap yaw (map (fn [x] (make-font (::bitmap-font gui) 1.0 (/ 1.0 (sin (to-radians x))))) yaw))]
                              (nuklear-dark-style gui)
                              (without-window-padding gui
                                (nuklear-window
                                  gui "navball rendering" 0 0 w h :widget
                                  (layout-row-dynamic gui (double w) 1)
                                  (widget
                                    gui canvas canvas-rect
                                    (with-colors
                                      [white 255 255 255
                                       black   0   0   0
                                       red   255   0   0
                                       green   0 255   0
                                       blue    0   0 255]
                                      (with-rect rect 0 0 w w (fill-rect canvas rect 0.0 white))
                                      (with-rect rect 0 w w w (fill-rect canvas rect 0.0 black))
                                      (with-rect rect 0 0 (/ w 12) h (fill-rect canvas rect 0.0 red))
                                      (with-rect rect (ceil (/ (* w 11) 12)) 0 (/ w 12) h (fill-rect canvas rect 0.0 red))
                                      (stroke-line canvas (/ (* w 5) 180) 0 (/ (* w 5) 180) h 1.0 black)
                                      (stroke-line canvas (/ (* w 175) 180) 0 (/ (* w 175) 180) h 1.0 black)
                                      (doseq [yaw [15 30 60 120 150 165]]
                                             (stroke-line canvas (/ (* w yaw) 180) 0 (/ (* w yaw) 180) w 2.0 black)
                                             (stroke-line canvas (/ (* w yaw) 180) w (/ (* w yaw) 180) h 2.0 white))
                                      (let [yaw      (vec (range 5 180 5))
                                            pitch    (vec (range 0 390 30))
                                            x-coords (mapv #(/ (* % 512) 180) yaw)
                                            y-coords (mapv #(/ (* % 1024) 360) pitch)
                                            dy       (mapv #(/ 1 (sin (to-radians %))) yaw)]
                                        (doseq [j (range (count y-coords))]
                                               (doseq [i (range (dec (count x-coords)))]
                                                      (let [x1 (x-coords i)
                                                            x2 (x-coords (inc i))
                                                            yc (y-coords j)
                                                            dy1 (dy i)
                                                            dy2 (dy (inc i))
                                                            color (if (or (<= (pitch j) 180) (>= (pitch j) 360)
                                                                          (<= (yaw (inc i)) 15) (>= (yaw i) 165)) black white)]
                                                        (fill-polygon canvas [[x1 (- yc dy1)] [x2 (- yc dy2)] [x2 (+ yc dy2)] [x1 (+ yc dy1)]] color)))))
                                      (doseq [[i x] (map-indexed vector (range -5 5 2))]
                                             (with-rect rect (+ (/ w 2) x) 0 2 h (fill-rect canvas rect 0.0 (if (even? i) black white))))
                                      (doseq [yaw   [45 75 105 135]
                                              pitch (range 0 365 5)]
                                             (let [x     (/ (* yaw w) 180)
                                                   y     (/ (* pitch h) 360)
                                                   color (if (> pitch 180) white black)]
                                               (stroke-line canvas (- x 3) y (+ x 3) y (/ 2.0 (sin (to-radians yaw))) color)))
                                      (doseq [yaw   (remove #{15 90 165} (range 10 175 5))
                                              pitch (range 15 375 30)]
                                             (let [x     (/ (* yaw w) 180)
                                                   y     (/ (* pitch h) 360)
                                                   color (if (or (<= pitch 180) (<= yaw 15) (>= yaw 165)) black white)
                                                   dy    (/ 3.0 (sin (to-radians yaw)))]
                                               (stroke-line canvas x (- y dy) x (+ y dy) 2.0 color)))
                                      (doseq [pitch (remove #(zero? (mod % 30)) (range 10 360 10))]
                                             (let [x1 (/ (* 15 w) 180)
                                                   x2 (/ (* 165 w) 180)
                                                   y  (/ (* pitch h) 360)
                                                   color (if (<= pitch 180) black white)]
                                               (with-rect rect x1 (- y 3) 7 7 (fill-rect canvas rect 0.0 color))
                                               (with-rect rect (- x2 6) (- y 3) 7 7 (fill-rect canvas rect 0.0 color))))
                                      (doseq [yaw [45 75 105 135]
                                              pitch (range 0 390 30)]
                                             (let [x    (/ (* yaw w) 180)
                                                   y    (+ (/ (* pitch h) 360) (if (#{75 105} yaw)
                                                                                 (case (long pitch) 0 -9 180 9 360 -9 0) 0))
                                                   fg   (if (or (<= pitch 180) (>= pitch 360)) black white)
                                                   bg   (if (or (<= pitch 180) (>= pitch 360)) white black)
                                                   text (str (/ (mod (- 180 pitch) 360) 10))
                                                   tw   (text-width text (fonts yaw))
                                                   th   (* 18 (::scale-y (fonts yaw)))
                                                   pad  4]
                                               (with-rect rect (- x (/ tw 2) pad) (- y (/ th 2)) (+ tw (* 2 pad)) th (fill-rect canvas rect 3.0 bg))
                                               (draw-text canvas (- x (/ tw 2)) (- y (/ th 2)) tw th text (fonts yaw) fg)))
                                      (doseq [yaw  [30 60 120 150]
                                              pitch (range 15 375 30)]
                                             (let [x    (/ (* yaw w) 180)
                                                   y    (/ (* pitch h) 360)
                                                   fg   (if (<= pitch 180) black white)
                                                   bg   (if (<= pitch 180) white black)
                                                   text (str (/ (- yaw 90) 10))
                                                   tw   (text-width text (fonts yaw))
                                                   th   (* 18 (::scale-y (fonts yaw)))
                                                   pad  4]
                                               (when (or (zero? (mod (+ pitch 15) 60)) (#{60 120} yaw))
                                                 (with-rect rect (- x (/ tw 2) pad) (- y (/ th 2)) (+ tw (* 2 pad)) th
                                                   (fill-rect canvas rect 3.0 bg))
                                                 (draw-text canvas (- x (/ tw 2)) (- y (/ th 2)) tw th text (fonts yaw) fg))))
                                      (doseq [pitch (range 0 361 1)]
                                             (let [x     (/ w 2)
                                                   y     (/ (* pitch h) 360)
                                                   color (if (<= pitch 180) black white)
                                                   five  (zero? (mod pitch 5))]
                                               (stroke-line canvas (- x (if five 15 10)) y (- x 3) y (if five 1.0 0.3) color)
                                               (stroke-line canvas (+ x (if five 15 10)) y (+ x 3) y (if five 1.0 0.3) color)))))))
                              (render-nuklear-gui gui w h)
                              (destroy-nuklear-gui-with-font gui)))
        (let [img (texture->image tex)]
          (destroy-texture tex)
          img)))))


(defn make-navball
  "Initialise navball textures and program"
  [gui]
  (let [image           (slurp-image "data/texture/navball-orbit.png" true)
        texture         (make-rgb-texture :sfsim.texture/linear :sfsim.texture/repeat image)
        framebuffer     (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL30/GL_RGB32F
                                               (long (scale gui 200)) (long (scale gui 200)))
        vertex-source   (slurp "resources/shaders/gui/vertex-navball.glsl")
        fragment-source (slurp "resources/shaders/gui/fragment-navball.glsl")
        program         (make-program :sfsim.render/vertex [vertex-source] :sfsim.render/fragment [fragment-source])
        indices         [0 1 2 3]
        vertices        [1.0 1.0 0.5, -1.0 1.0 0.5, -1.0 -1.0 0.5, 1.0 -1.0 0.5]
        vao             (make-vertex-array-object program indices vertices ["point" 3])
        image           (NkImage/create)
        handle          (NkHandle/create)]
    (generate-mipmap texture)
    (.id handle (:sfsim.texture/texture framebuffer))
    (.handle image handle)
    (assoc gui
           ::navball-texture texture
           ::navball-framebuffer framebuffer
           ::navball-program program
           ::navball-vao vao
           ::navball-image image)))


(defn destroy-navball
  "Destroy navball textures and program"
  [gui]
  (destroy-vertex-array-object (::navball-vao gui))
  (destroy-program (::navball-program gui))
  (destroy-texture (::navball-framebuffer gui))
  (destroy-texture (::navball-texture gui)))


(defn navball-prepare
  "Render navball to framebuffer"
  [gui orientation]
  (let [navball  (::navball-texture gui)
        tex      (::navball-framebuffer gui)
        program  (::navball-program gui)
        vao      (::navball-vao gui)]
    (framebuffer-render (scale gui 200) (scale gui 200) :sfsim.render/cullback nil [tex]
                        (use-program program)
                        (uniform-sampler program "navball" 0)
                        (uniform-vector2 program "resolution" (vec2 (scale gui 200) (scale gui 200)))
                        (uniform-matrix3 program "orientation" (quaternion->matrix orientation))
                        (use-textures {0 navball})
                        (clear (vec3 0.0 1.0 0.0))
                        (render-quads vao))))


(defn draw-navball
  "Draw navball texture"
  [gui canvas canvas-rect]
  (let
    [img (::navball-image gui)
     x0  (.x ^NkRect canvas-rect)
     y0  (.y ^NkRect canvas-rect)
     w   (.w ^NkRect canvas-rect)
     h   (.h ^NkRect canvas-rect)]
    (with-colors
      [white 255 255 255
       green   0 255   0]
      (with-rect rect (+ x0 (scale gui 2)) (+ y0 (scale gui 2)) (- w (scale gui 4)) (- h (scale gui 4))
        (Nuklear/nk_draw_image canvas rect img white))
      (stroke-polyline canvas [(+ x0 (/ w 2)) (+ y0 (/ h 3)) (+ x0 (/ w 2)) (+ y0 (/ (* 2 h) 3))]
                       (scale gui 2) green)
      (stroke-polyline canvas [[(+ x0 (/ w 3)) (+ y0 (/ h 2))] [(+ x0 (/ (* 2 w) 3)) (+ y0 (/ h 2))]]
                       (scale gui 2) green)
      (stroke-arc canvas (+ x0 (/ w 2)) (+ y0 (/ h 2)) (/ w 18) 0.0 Math/PI (scale gui 2) green))))


(set! *unchecked-math* :warn-on-boxed)


(defn- rate->x
  [xs ws minimum maximum rate]
  (+ ^double xs (/ (* (- ^double rate ^long minimum) ^double ws)
                   (- ^long maximum ^long minimum))))


(defn- indicator-vertical
  [orientation x y0 h midy]
  (case orientation
    :top-indicator
    [[(- ^double x (/ ^double h 4.0)) y0]
     [(+ ^double x (/ ^double h 4.0)) y0]
     [x midy]]
    :bottom-indicator
    [[(- ^double x (/ ^double h 4.0)) (+ ^double y0 ^double h)]
     [(+ ^double x (/ ^double h 4.0)) (+ ^double y0 ^double h)]
     [x midy]]))


(defn- indicator-horizontal
  [x0 y w midx]
  [[midx y]
   [(+ ^double x0 ^double w) (- ^double y (/ ^double w 4.0))]
   [(+ ^double x0 ^double w) (+ ^double y (/ ^double w 4.0))]])


(defn- tick-line-vertical
  [orientation x y0 h midy major-tick?]
  (case orientation
    :top-indicator    [x midy x (+ ^double y0 (double (if major-tick? h (* ^double h 0.75))))]
    :bottom-indicator [x midy x (+ ^double y0 (double (if major-tick? 0.0 (* ^double h 0.25))))]))


(defn- tick-line-horizontal
  [x0 y w midx major-tick?]
  [(+ ^double x0 (if major-tick? 0.0 (* ^double w 0.25))) y midx y])


(defn horizontal-scale
  "Display rate scale."
  {:malli/schema [:=> [:cat :some :some :some :int :int :int :double keyword?] :any]}
  [gui canvas canvas-rect minimum maximum step current orientation]
  (let [font            (::bitmap-font gui)
        x0              (.x ^NkRect canvas-rect)
        y0              (.y ^NkRect canvas-rect)
        w               (.w ^NkRect canvas-rect)
        h               (.h ^NkRect canvas-rect)
        xs              (+ x0 (/ w 10.0))
        ws              (/ (* w 8) 10.0)
        midy            (+ y0 (/ h 2.0))
        clamped-current (min ^double (max ^double current (double minimum)) (double maximum))
        current-x       (rate->x xs ws minimum maximum clamped-current)]
    (with-colors
      [bg       0   0   0
       green    0 255   0
       bright 202 213 197]
      (draw-text canvas x0 y0 (/ w 10) h (str (abs ^long minimum)) font bright)
      (draw-text-right canvas (+ x0 (/ (* w 9) 10)) y0 (/ w 10) h (str (abs ^long maximum)) font bright)
      (stroke-line canvas xs midy (+ xs ws) midy (scale gui 2.0) bright)
      (doseq [rate (range minimum (+ ^long maximum ^long step) step)]
        (let [x             (rate->x xs ws minimum maximum rate)
              major-tick?   (#{minimum 0 maximum} rate)
              [x1 y1 x2 y2] (tick-line-vertical orientation x y0 h midy major-tick?)]
          (stroke-line canvas x1 y1 x2 y2 (scale gui 2.0) bright)))
      (fill-polygon canvas
                    (indicator-vertical orientation current-x y0 h midy)
                    green))))


(defn vertical-scale
  "Display rate scale vertically."
  {:malli/schema [:=> [:cat :some :some :some :int :int :int :double] :any]}
  [gui canvas canvas-rect minimum maximum step current]
  (let [font            (::bitmap-font gui)
        x0              (.x ^NkRect canvas-rect)
        y0              (.y ^NkRect canvas-rect)
        w               (.w ^NkRect canvas-rect)
        h               (.h ^NkRect canvas-rect)
        ys              (+ y0 (/ h 10.0))
        hs              (/ (* h 8) 10.0)
        midx            (+ x0 (/ w 2.0))
        clamped-current (min ^double (max ^double current (double minimum)) (double maximum))
        current-y       (rate->x ys hs minimum maximum clamped-current)]
    (with-colors
      [bg       0   0   0
       green    0 255   0
       bright 202 213 197]
      (draw-text canvas x0 y0 w (/ h 10) (str (abs ^long minimum)) font bright)
      (draw-text canvas x0 (+ y0 (/ (* h 9) 10)) w (/ h 10) (str (abs ^long maximum)) font bright)
      (stroke-line canvas midx ys midx (+ ys hs) (scale gui 2.0) bright)
      (doseq [rate (range minimum (+ ^long maximum ^long step) step)]
        (let [y             (rate->x ys hs minimum maximum rate)
              major-tick?   (#{minimum 0 maximum} rate)
              [x1 y1 x2 y2] (tick-line-horizontal x0 y w midx major-tick?)]
          (stroke-line canvas x1 y1 x2 y2 (scale gui 2.0) bright)))
      (fill-polygon canvas
                    (indicator-horizontal x0 current-y w midx)
                    green))))


(defn draw-roll-rate
  "Display roll rate scale"
  {:malli/schema [:=> [:cat :some :some :some :int :int :int :double] :any]}
  [gui canvas canvas-rect minimum maximum step current]
  (horizontal-scale gui canvas canvas-rect minimum maximum step current :top-indicator))


(defn draw-yaw-rate
  "Display yaw rate scale"
  {:malli/schema [:=> [:cat :some :some :some :int :int :int :double] :any]}
  [gui canvas canvas-rect minimum maximum step current]
  (horizontal-scale gui canvas canvas-rect minimum maximum step current :bottom-indicator))


(defn draw-pitch-rate
  "Display pitch rate scale"
  {:malli/schema [:=> [:cat :some :some :some :int :int :int :double] :any]}
  [gui canvas canvas-rect minimum maximum step current]
  (vertical-scale gui canvas canvas-rect minimum maximum step current))


(defn navball-mfd
  "Display navball widget"
  [gui rotation-rates]
  (widget gui canvas canvas-rect
          (let [x0         (.x ^NkRect canvas-rect)
                y0         (.y ^NkRect canvas-rect)
                w          (.w ^NkRect canvas-rect)
                h          (.h ^NkRect canvas-rect)
                roll-rate  (to-degrees (:sfsim.physics/roll-rate rotation-rates))
                pitch-rate (to-degrees (:sfsim.physics/pitch-rate rotation-rates))
                yaw-rate   (to-degrees (:sfsim.physics/yaw-rate rotation-rates))]
            (with-colors
              [bg       0   0   0
               border  82 185 142]
              (fill-rect canvas canvas-rect 0.0 bg)
              (with-rect rect (+ x0 (scale gui 1)) (+ y0 (scale gui 1)) (- w (scale gui 2)) (- h (scale gui 2))
                (stroke-rect canvas rect 0.0 (scale gui 3.0) border))
              (let [x0 (+ x0 (scale gui 3))
                    y0 (+ y0 (scale gui 3))
                    w  (- w (scale gui 6))
                    h  (- h (scale gui 6))]
                (with-rect rect (+ x0 (* w 0.1)) y0 (* w 0.8) (* h 0.1)
                  (draw-roll-rate gui canvas rect -5 5 1 roll-rate))
                (with-rect rect (+ x0 (* w 0.9)) (+ y0 (* h 0.1)) (* w 0.1) (* h 0.8)
                  (draw-pitch-rate gui canvas rect -5 5 1 pitch-rate))
                (with-rect rect (+ x0 (* w 0.1)) (+ y0 (* h 0.9)) (* w 0.8) (* h 0.1)
                  (draw-yaw-rate gui canvas rect -5 5 1 yaw-rate))
                (with-rect rect (+ x0 (* w 0.1)) (+ y0 (* w 0.1)) (* w 0.8) (* h 0.8)
                  (draw-navball gui canvas rect)))))))


(defn information-display
  [gui w h state frametime time-lapse]
  (let [controls (-> state :input :sfsim.input/controls)
        text     (format "vs = %.1f m/s, v = %.1f m/s, %s%s%s, fps = %5.1f, time x = %2d"
                         (:sfsim.physics/display-vertical-speed (:physics state))
                         (:sfsim.physics/display-speed (:physics state))
                         (if (:sfsim.input/rcs controls) "RCS" "aerofoil")
                         (if (:sfsim.input/brake controls) ", brake"
                           (if (:sfsim.input/parking-brake controls) ", parking brake" ""))
                         (if (:sfsim.input/air-brake controls) ", air brake" "")
                         (/ 1.0 ^double frametime)
                         time-lapse)]
    (without-window-padding gui
      (nuklear-window gui "Orbit" (scale gui 10) (- ^long h (scale gui (+ 10 256)))
                      (scale gui 256) (scale gui 256) :widget
                      (layout-row-dynamic gui (scale gui 256) 1)
                      (orbit-mfd gui (physics/orbital-parameters config/planet-config (:physics state))))
      (nuklear-window gui "Navball" (- ^long w (scale gui (+ 10 256))) (- ^long h (scale gui (+ 10 256)))
                      (scale gui 256) (scale gui 256) :widget
                      (layout-row-dynamic gui (scale gui 256) 1)
                      (navball-prepare gui (physics/orbit-orientation (:physics state)))
                      (navball-mfd gui (physics/rotation-rates (:physics state))))
      (when (-> state :input :sfsim.input/pause)
        (nuklear-window gui "Pause" (scale gui (+ 20 128)) (scale gui 10) (scale gui 80) (scale gui text-row-height) :widget
                        (layout-row-dynamic gui (scale gui text-row-height) 1)
                        (widget gui canvas canvas-rect
                                (let [font (::bitmap-font gui)
                                      x0   (.x ^NkRect canvas-rect)
                                      y0   (.y ^NkRect canvas-rect)
                                      w    (.w ^NkRect canvas-rect)
                                      h    (.h ^NkRect canvas-rect)
                                      tw   (text-width "Pause" font)]
                                  (with-colors
                                    [bg  255 255 255
                                     red 255   0   0]
                                    (fill-rect canvas canvas-rect 0.0 bg)
                                    (draw-text canvas (+ x0 (/ (- w ^double tw) 2)) y0 tw h "Pause" font red)))))))
    (nuklear-window gui "Information" (scale gui (+ 20 256)) (- ^long h (scale gui (+ 10 ^long text-height)))
                    (scale gui 720) (scale gui (* ^long text-height 1)) :widget
                    (layout-row-dynamic gui (scale gui text-row-height) 1)
                    (text-label gui text))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
