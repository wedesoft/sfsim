(ns sfsim.t-gui
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [fastmath.vector :refer (vec4)]
              [fastmath.matrix :refer (eye)]
              [sfsim.conftest :refer (is-image roughly-vector)]
              [sfsim.render :refer :all]
              [sfsim.texture :refer :all]
              [sfsim.image :refer :all]
              [sfsim.gui :refer :all])
    (:import [org.lwjgl BufferUtils]
             [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.opengl GL11 GL12 GL14 GL15 GL30]
             [org.lwjgl.nuklear Nuklear NkAllocator NkContext NkUserFont NkBuffer NkRect NkConvertConfig NkPluginAllocI NkPluginFreeI NkDrawVertexLayoutElement]
             [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.gui]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(defmacro gui-offscreen-render
  [width height & body]
  `(with-invisible-window
     (let [tex# (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGB8 ~width ~height)]
       (framebuffer-render ~width ~height :sfsim.render/noculling nil [tex#] ~@body)
       (let [img# (texture->image tex#)]
         (destroy-texture tex#)
         img#))))

(tabular "Instantiate GUI program"
         (fact
           (with-invisible-window
             (let [indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1,
                              1.0 -1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1,
                             -1.0  1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1,
                              1.0  1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1]
                   program  (make-gui-program)
                   vao      (make-vertex-array-object program indices vertices ["position" 2 "texcoord" 2 "color" 4])
                   tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp
                                              #:sfsim.image{:width 1 :height 1 :data (byte-array [?r2 ?g2 ?b2 ?a2]) :channels 4})
                   output   (texture-render-color 1 1 true
                                                  (use-program program)
                                                  (uniform-matrix4 program "projection" (eye 4))
                                                  (uniform-sampler program "tex" 0)
                                                  (use-textures {0 tex})
                                                  (render-quads vao))
                   img      (rgba-texture->vectors4 output)]
               (destroy-texture output)
               (destroy-texture tex)
               (destroy-vertex-array-object vao)
               (destroy-program program)
               (get-vector4 img 0 0))) => (roughly-vector (vec4 ?r3 ?g3 ?b3 ?a3) 1e-6))
         ?r1 ?g1 ?b1 ?a1 ?r2 ?g2 ?b2 ?a2 ?r3 ?g3 ?b3 ?a3
         0.0 0.0 0.0 0.0  -1  -1  -1  -1 0.0 0.0 0.0 0.0
         1.0 1.0 1.0 1.0  -1  -1  -1  -1 1.0 1.0 1.0 1.0
         1.0 1.0 1.0 1.0   0   0   0   0 0.0 0.0 0.0 1.0)

(fact "Test GUI transformation matrix"
     (gui-offscreen-render 160 120
       (let [indices  [0 2 3 1]
             vertices [  0   0 0.5 0.5 0 0 0 1
                       160   0 0.5 0.5 1 0 0 1
                         0 120 0.5 0.5 0 1 0 1
                       160 120 0.5 0.5 0 0 1 1]
             program  (make-gui-program)
             vao      (make-vertex-array-object program indices vertices ["position" 2 "texcoord" 2 "color" 4])
             tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp
                                        #:sfsim.image{:width 1 :height 1 :data (byte-array [-1 -1 -1 -1]) :channels 4})]
         (use-program program)
         (uniform-matrix4 program "projection" (gui-matrix 160 120))
         (uniform-sampler program "tex" 0)
         (use-textures {0 tex})
         (render-quads vao)
         (destroy-texture tex)
         (destroy-vertex-array-object vao)
         (destroy-program program))) => (is-image "test/sfsim/fixtures/gui/projection.png" 0.0))

(facts "Create null texture"
       (with-invisible-window
         (let [null-texture (make-null-texture)
               buffer       (BufferUtils/createByteBuffer 4)]
           (with-texture GL11/GL_TEXTURE_2D (.id (.texture null-texture))
             (GL11/glGetTexImage GL11/GL_TEXTURE_2D 0 GL12/GL_RGBA GL11/GL_UNSIGNED_BYTE buffer))
           (.x (.uv null-texture)) => 0.5
           (.y (.uv null-texture)) => 0.5
           (doseq [i (range 4)] (.get buffer i) => -1)
           (destroy-null-texture null-texture))))

(facts "Set up rendering mode"
       (gui-offscreen-render 160 120
         (let [indices  [0 2 3 1 4 5 7 6]
               vertices [  0   0 0.5 0.5 1 0 0 1.0
                         100   0 0.5 0.5 1 0 0 1.0
                           0  80 0.5 0.5 1 0 0 1.0
                         100  80 0.5 0.5 1 0 0 1.0
                          60  40 0.5 0.5 0 1 0 0.5
                         160  40 0.5 0.5 0 1 0 0.5
                          60 120 0.5 0.5 0 1 0 0.5
                         160 120 0.5 0.5 0 1 0 0.5]
               program  (make-gui-program)
               vao      (make-vertex-array-object program indices vertices ["position" 2 "texcoord" 2 "color" 4])
               pixel    #:sfsim.image{:width 1 :height 1 :data (byte-array [-1 -1 -1 -1]) :channels 4}
               tex      (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp pixel)]
           (with-blending
             (use-program program)
             (uniform-matrix4 program "projection" (gui-matrix 160 120))
             (uniform-sampler program "tex" 0)
             (use-textures {0 tex})
             (render-quads vao)
             (destroy-texture tex)
             (destroy-vertex-array-object vao)
             (destroy-program program)))) => (is-image "test/sfsim/fixtures/gui/mode.png" 0.0))

(facts "Render a slider"
       (gui-offscreen-render 160 32
         (let [buffer-initial-size (* 4 1024)
               max-vertex-buffer   (* 512 1024)
               max-element-buffer  (* 128 1024)
               program             (make-gui-program)
               null-texture        (make-null-texture)
               vertex-array-object (GL30/glGenVertexArrays)
               array-buffer        (GL15/glGenBuffers)
               index-buffer        (GL15/glGenBuffers)
               vertex-layout       (NkDrawVertexLayoutElement/malloc 4)
               stack               (MemoryStack/stackPush)
               allocator           (NkAllocator/create)
               context             (NkContext/create)
               font                (NkUserFont/create)
               cmds                (NkBuffer/create)
               config              (NkConvertConfig/calloc stack)
               rect                (NkRect/malloc stack)
               slider              (BufferUtils/createIntBuffer 1)]
           (GL30/glBindVertexArray vertex-array-object)
           (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER array-buffer)
           (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER index-buffer)
           (GL15/glBufferData GL15/GL_ARRAY_BUFFER max-vertex-buffer GL15/GL_STREAM_DRAW)
           (GL15/glBufferData GL15/GL_ELEMENT_ARRAY_BUFFER max-element-buffer GL15/GL_STREAM_DRAW)
           (setup-vertex-attrib-pointers program [GL11/GL_FLOAT "position" 2 GL11/GL_FLOAT "texcoord" 2 GL11/GL_UNSIGNED_BYTE "color" 4])
           (-> vertex-layout (.position 0) (.attribute Nuklear/NK_VERTEX_POSITION) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 0))
           (-> vertex-layout (.position 1) (.attribute Nuklear/NK_VERTEX_TEXCOORD) (.format Nuklear/NK_FORMAT_FLOAT) (.offset 8))
           (-> vertex-layout (.position 2) (.attribute Nuklear/NK_VERTEX_COLOR) (.format Nuklear/NK_FORMAT_R8G8B8A8) (.offset 16))
           (-> vertex-layout (.position 3) (.attribute Nuklear/NK_VERTEX_ATTRIBUTE_COUNT) (.format Nuklear/NK_FORMAT_COUNT) (.offset 0))
           (.flip vertex-layout)
           (.alloc allocator (reify NkPluginAllocI (invoke [this handle old size] (MemoryUtil/nmemAllocChecked size))))
           (.mfree allocator (reify NkPluginFreeI (invoke [this handle ptr] (MemoryUtil/nmemFree ptr))))
           (Nuklear/nk_init context allocator font)
           (Nuklear/nk_buffer_init cmds allocator buffer-initial-size)
           (doto config
                 (.vertex_layout vertex-layout)
                 (.vertex_size 20)
                 (.vertex_alignment 4)
                 (.tex_null null-texture)
                 (.circle_segment_count 22)
                 (.curve_segment_count 22)
                 (.arc_segment_count 22)
                 (.global_alpha 1.0)
                 (.shape_AA Nuklear/NK_ANTI_ALIASING_ON)
                 (.line_AA Nuklear/NK_ANTI_ALIASING_ON))
           (.put slider 0 50)
           (when (Nuklear/nk_begin context "test slider" (Nuklear/nk_rect 0 0 160 32 rect) 0)
             (Nuklear/nk_layout_row_dynamic context 32 1)
             (Nuklear/nk_slider_int context 0 slider 100 1))
           (GL11/glViewport 0 0 160 32)
           (use-program program)
           (uniform-matrix4 program "projection" (gui-matrix 160 32))
           (let [vertices (GL15/glMapBuffer GL15/GL_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-vertex-buffer nil)
                 elements (GL15/glMapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER GL15/GL_WRITE_ONLY max-element-buffer nil)
                 vbuf     (NkBuffer/malloc stack)
                 ebuf     (NkBuffer/malloc stack)]
             (Nuklear/nk_buffer_init_fixed vbuf vertices)
             (Nuklear/nk_buffer_init_fixed ebuf elements)
             (Nuklear/nk_convert context cmds vbuf ebuf config))
           (GL15/glUnmapBuffer GL15/GL_ELEMENT_ARRAY_BUFFER)
           (GL15/glUnmapBuffer GL15/GL_ARRAY_BUFFER)
           (loop [cmd (Nuklear/nk__draw_begin context cmds) offset 0]
                 (when cmd
                   (when (not (zero? (.elem_count cmd)))
                     (GL11/glBindTexture GL11/GL_TEXTURE_2D (.id (.texture cmd)))
                     (let [clip-rect (.clip_rect cmd)]
                       (GL11/glScissor (int (.x clip-rect))
                                       (int (- 32 (int (+ (.y clip-rect) (.h clip-rect)))))
                                       (int (.w clip-rect))
                                       (int (.h clip-rect))))
                     (GL11/glDrawElements GL11/GL_TRIANGLES (.elem_count cmd) GL11/GL_UNSIGNED_SHORT offset))
                   (recur (Nuklear/nk__draw_next cmd cmds context) (+ offset (* 2 (.elem_count cmd))))))
           (Nuklear/nk_clear context)
           (Nuklear/nk_buffer_clear cmds)
           (destroy-null-texture null-texture)
           (Nuklear/nk_free context)
           (MemoryStack/stackPop)
           (.free (.alloc allocator))
           (.free (.mfree allocator))
           (GL30/glBindVertexArray 0)
           (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
           (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
           (GL15/glDeleteBuffers array-buffer)
           (GL15/glDeleteBuffers index-buffer)
           (GL30/glDeleteVertexArrays vertex-array-object)
           (destroy-program program))) => (is-image "test/sfsim/fixtures/gui/slider.png" 0.0))

(GLFW/glfwTerminate)
