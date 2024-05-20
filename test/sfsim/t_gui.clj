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
             [org.lwjgl.nuklear Nuklear NkAllocator NkContext NkUserFont NkBuffer NkRect NkConvertConfig NkPluginAllocI
              NkPluginFreeI NkDrawVertexLayoutElement]
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
               max-index-buffer    (* 128 1024)
               program             (make-gui-program)
               null-texture        (make-null-texture)
               vao                 (make-vertex-array-stream program max-index-buffer max-vertex-buffer)
               vertex-layout       (make-vertex-layout)
               config              (make-gui-config null-texture vertex-layout)
               stack               (MemoryStack/stackPush)
               allocator           (make-allocator)
               font                (NkUserFont/create)
               context             (make-gui-context allocator font)
               cmds                (make-gui-buffer allocator buffer-initial-size)
               rect                (NkRect/malloc stack)
               slider              (BufferUtils/createIntBuffer 1)]
           (setup-vertex-attrib-pointers program [GL11/GL_FLOAT "position" 2 GL11/GL_FLOAT "texcoord" 2 GL11/GL_UNSIGNED_BYTE "color" 4])
           (.put slider 0 50)
           (when (Nuklear/nk_begin context "test slider" (Nuklear/nk_rect 0 0 160 32 rect) 0)
             (Nuklear/nk_layout_row_dynamic context 32 1)
             (Nuklear/nk_slider_int context 0 slider 100 1))
           (render-gui context config cmds program vao 160 32)
           (GL30/glBindVertexArray 0)
           (GL15/glBindBuffer GL15/GL_ARRAY_BUFFER 0)
           (GL15/glBindBuffer GL15/GL_ELEMENT_ARRAY_BUFFER 0)
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-gui-context context)
           (destroy-null-texture null-texture)
           (destroy-vertex-layout vertex-layout)
           (destroy-gui-config config)
           (destroy-gui-buffer cmds)
           (MemoryStack/stackPop)
           (destroy-allocator allocator))) => (is-image "test/sfsim/fixtures/gui/slider.png" 0.0))

(GLFW/glfwTerminate)
