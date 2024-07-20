(ns sfsim.t-gui
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [fastmath.vector :refer (vec3 vec4)]
              [fastmath.matrix :refer (eye)]
              [sfsim.conftest :refer (is-image roughly-vector)]
              [sfsim.render :refer :all]
              [sfsim.texture :refer :all]
              [sfsim.image :refer :all]
              [sfsim.gui :refer :all]
              [sfsim.util :refer :all])
    (:import [org.lwjgl BufferUtils]
             [org.lwjgl.system MemoryUtil MemoryStack]
             [org.lwjgl.opengl GL11 GL12 GL14 GL15 GL30]
             [org.lwjgl.nuklear Nuklear NkAllocator NkContext NkUserFont NkBuffer NkRect NkConvertConfig NkPluginAllocI
              NkPluginFreeI NkDrawVertexLayoutElement]
             [org.lwjgl.stb STBTTFontinfo STBTTPackedchar STBTruetype STBTTPackContext]
             [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.gui]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(defmacro gui-framebuffer-render
  [width height & body]
  `(let [tex# (make-empty-texture-2d :sfsim.texture/linear :sfsim.texture/clamp GL11/GL_RGB8 ~width ~height)]
     (framebuffer-render ~width ~height :sfsim.render/noculling nil [tex#] ~@body)
     (let [img# (texture->image tex#)]
       (destroy-texture tex#)
       img#)))

(defmacro gui-offscreen-render
  [width height & body]
  `(with-invisible-window
     (gui-framebuffer-render ~width ~height ~@body)))

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
             (clear (vec3 0 0 0))
             (use-program program)
             (uniform-matrix4 program "projection" (gui-matrix 160 120))
             (uniform-sampler program "tex" 0)
             (use-textures {0 tex})
             (render-quads vao)
             (destroy-texture tex)
             (destroy-vertex-array-object vao)
             (destroy-program program)))) => (is-image "test/sfsim/fixtures/gui/mode.png" 0.1))

(fact "Render font to bitmap"
  (let [bitmap-font (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18)]
    (:sfsim.gui/image bitmap-font)) => (is-image "test/sfsim/fixtures/gui/font.png" 7.22 false))

(defmacro gui-control-test
  [gui & body]
  `(gui-offscreen-render 160 40
     (let [buffer-initial-size# (* 4 1024)
           bitmap-font#         (setup-font-texture (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18))
           ~gui                 (make-nuklear-gui (:sfsim.gui/font bitmap-font#) buffer-initial-size#)]
       (nuklear-dark-style ~gui)
       (nuklear-window ~gui "control test window" 0 0 160 40
         (layout-row-dynamic ~gui 32 1)
         ~@body)
       (render-nuklear-gui ~gui 160 40)
       (destroy-nuklear-gui ~gui)
       (destroy-font-texture bitmap-font#))))

(facts "Render a slider"
       (gui-control-test gui (slider-int gui 0 50 100 1)) => (is-image "test/sfsim/fixtures/gui/slider.png" 0.1))

(fact "Use font to render button"
      (gui-control-test gui (button-label gui "Test Button")) => (is-image "test/sfsim/fixtures/gui/button.png" 0.03))

(facts "Test rendering with two GUI contexts"
       (with-invisible-window
         (let [buffer-initial-size (* 4 1024)
               bitmap-font         (setup-font-texture (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18))
               gui1                (make-nuklear-gui (:sfsim.gui/font bitmap-font) buffer-initial-size)
               gui2                (make-nuklear-gui (:sfsim.gui/font bitmap-font) buffer-initial-size)]
           (gui-framebuffer-render 320 40
             (nuklear-window gui1 "window-1" 0 0 160 40
                             (layout-row-dynamic gui1 32 1)
                             (button-label gui1 "Button A"))
             (nuklear-window gui2 "window-2" 160 0 160 40
                             (layout-row-dynamic gui2 32 1)
                             (button-label gui2 "Button B"))
             (render-nuklear-gui gui1 320 40)
             (render-nuklear-gui gui2 320 40)) => (is-image "test/sfsim/fixtures/gui/guis.png" 0.03)
           (destroy-nuklear-gui gui2)
           (destroy-nuklear-gui gui1)
           (destroy-font-texture bitmap-font))))

(fact "Render a text label"
      (gui-control-test gui (text-label gui "Test Label")) => (is-image "test/sfsim/fixtures/gui/label.png" 0.03))

(fact "Render an edit field"
      (let [data (gui-edit-data "initial" 31 :sfsim.gui/filter-ascii)]
        (gui-control-test gui (edit-field gui data)) => (is-image "test/sfsim/fixtures/gui/edit.png" 0.03)
        (gui-edit-get data) => "initial"))

(GLFW/glfwTerminate)
