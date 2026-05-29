;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-gui
  (:require
    [clojure.math :refer (PI)]
    [clojure.java.shell :as shell]
    [fastmath.matrix :refer (eye)]
    [fastmath.vector :refer (vec3 vec4)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (is-image roughly-vector)]
    [sfsim.gui :refer :all]
    [sfsim.image :refer :all]
    [sfsim.render :refer :all]
    [sfsim.texture :refer :all]
    [sfsim.util :refer :all])
  (:import
    (org.lwjgl
      BufferUtils)
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.opengl
      GL11
      GL12)
    (org.lwjgl.system
      MemoryStack)
    (org.lwjgl.nuklear
      NkColor
      NkRect
      NkVec2
      Nuklear)))


(mi/collect! {:ns (all-ns)})
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
                             +1.0 -1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1,
                             -1.0  1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1,
                             +1.0  1.0 0.5 0.5 ?r1 ?g1 ?b1 ?a1]
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
                                  vertices [0   0 0.5 0.5 0 0 0 1
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
                              (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/gui/projection.png" 0.08))


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
                                   vertices [0   0 0.5 0.5 1 0 0 1.0
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
                               (with-overlay-blending
                                 (clear (vec3 0 0 0))
                                 (use-program program)
                                 (uniform-matrix4 program "projection" (gui-matrix 160 120))
                                 (uniform-sampler program "tex" 0)
                                 (use-textures {0 tex})
                                 (render-quads vao)
                                 (destroy-texture tex)
                                 (destroy-vertex-array-object vao)
                                 (destroy-program program)))) => (is-image "test/clj/sfsim/fixtures/gui/mode.png" 22.0))


(fact "Render font to bitmap"
      (let [bitmap-font (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18)]
        (:sfsim.gui/image bitmap-font)) => (is-image "test/clj/sfsim/fixtures/gui/font.png" 7.22 false))


(defmacro gui-control-test
  [gui width height scale & body]
  `(gui-offscreen-render ~width ~height
                         (let [~gui (make-nuklear-gui-with-font ~scale)]
                           (nuklear-dark-style ~gui)
                           (nuklear-window ~gui "control test window" 0 0 ~width ~height :widget
                                           ~@body)
                           (render-nuklear-gui ~gui ~width ~height)
                           (destroy-nuklear-gui-with-font ~gui))))


(facts "Render a slider"
       (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (slider-int gui 0 50 100 1))
       => (is-image "test/clj/sfsim/fixtures/gui/slider.png" 0.1))


(fact "Use font to render button"
      (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (button-label gui "Test Button"))
      => (is-image "test/clj/sfsim/fixtures/gui/button.png" 0.30))


(fact "Use font to render large button"
      (gui-control-test gui 320 80 2.0 (layout-row-dynamic gui 64.0 1) (button-label gui "Large Button"))
      => (is-image "test/clj/sfsim/fixtures/gui/large-button.png" 0.30))


(facts "Test rendering with two GUI contexts"
       (with-invisible-window
         (let [bitmap-font         (setup-font-texture (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18))
               gui1                (make-nuklear-gui (:sfsim.gui/font bitmap-font) 1.0)
               gui2                (make-nuklear-gui (:sfsim.gui/font bitmap-font) 1.0)]
           (gui-framebuffer-render 320 40
                                   (nuklear-window gui1 "window-1" 0 0 160 40 :widget
                                                   (layout-row-dynamic gui1 32.0 1)
                                                   (button-label gui1 "Button A"))
                                   (nuklear-window gui2 "window-2" 160 0 160 40 :widget
                                                   (layout-row-dynamic gui2 32.0 1)
                                                   (button-label gui2 "Button B"))
                                   (render-nuklear-gui gui1 320 40)
                                   (render-nuklear-gui gui2 320 40)) => (is-image "test/clj/sfsim/fixtures/gui/guis.png" 0.10)
           (destroy-nuklear-gui gui2)
           (destroy-nuklear-gui gui1)
           (destroy-font-texture bitmap-font))))


(fact "Render a text label"
      (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (text-label gui "Test Label"))
      => (is-image "test/clj/sfsim/fixtures/gui/label.png" 0.17))


(fact "Render an edit field"
      (let [data (edit-data "initial" 31 :sfsim.gui/filter-ascii)]
        (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (edit-field gui data))
        => (is-image "test/clj/sfsim/fixtures/gui/edit.png" 0.21)
        (edit-get data) => "initial"
        (edit-set data "final")
        (edit-get data) => "final"))


(fact "Dynamic row layout with fractions"
      (gui-control-test gui 160 40 1.0
                        (layout-row gui 38 3
                                    (layout-row-push gui 0.2)
                                    (text-label gui "One")
                                    (layout-row-push gui 0.3)
                                    (text-label gui "Two")
                                    (layout-row-push gui 0.5)
                                    (text-label gui "Three"))) => (is-image "test/clj/sfsim/fixtures/gui/dynamic-layout.png" 0.10))


(fact "Render check box"
      (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (check-label gui "Check box" true))
      => (is-image "test/clj/sfsim/fixtures/gui/checkbox.png" 0.17))


(fact "Render radio button"
      (gui-control-test gui 160 40 1.0 (layout-row-dynamic gui 32.0 1) (option-label gui "Radio button" true))
      => (is-image "test/clj/sfsim/fixtures/gui/radio.png" 0.17))


(facts "Convert number to formatted string"
       (java.util.Locale/setDefault java.util.Locale/US)
       (float-str 0.0) => "  0.00"
       (float-str PI) => "  3.14"
       (float-str 123.456) => "123.46"
       (float-str 7733.0) => "7.733k"
       (float-str 12345.6) => "12.35k"
       (float-str 123456.7) => "123.5k"
       (float-str 6378000.0) => "6.378M"
       (float-str 1e+7) => "10.00M"
       (float-str 1e+8) => "100.0M"
       (float-str 1e+9) => "1.000G"
       (float-str 1e+10) => "10.00G"
       (float-str 1e+11) => "100.0G"
       (float-str 1e+12) => "1.000T"
       (float-str 1e+13) => "10.00T"
       (float-str 1e+14) => "100.0T"
       (float-str 1e+15) => " 1e+15")


(fact "Render orbit MFD"
      (spit-png "/tmp/orbit.png"
                (gui-offscreen-render 256 256
                         (let [gui (make-nuklear-gui-with-font 1.0)]
                           (nuklear-dark-style gui)
                           (let [stack (MemoryStack/stackPush)
                                 nk-vec2 (NkVec2/malloc stack)
                                 style (.style (:sfsim.gui/context gui))
                                 win   (.window style)]
                             (.x nk-vec2 (scale gui 0))
                             (.y nk-vec2 (scale gui 0))
                             (.padding win nk-vec2)
                             (nuklear-window gui "control test window" 0 0 256 256 :widget
                                             (let [fg      (NkColor/malloc stack)
                                                   bg      (NkColor/malloc stack)
                                                   rect    (NkRect/malloc stack)
                                                   context (:sfsim.gui/context gui)]
                                               (let [canvas (Nuklear/nk_window_get_canvas context)]
                                                 (layout-row-dynamic gui 256.0 1)
                                                 (Nuklear/nk_widget rect context)
                                                 (Nuklear/nk_rect 0 0 256 256 rect)
                                                 (Nuklear/nk_rgb 0 0 0 bg)
                                                 (Nuklear/nk_fill_rect canvas rect 0.0 bg)
                                                 (Nuklear/nk_rgb 0 0 0 bg)
                                                 (Nuklear/nk_rgb 0 255 0 fg)
                                                 (Nuklear/nk_rect 5 5 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "PeA" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 5 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 160000) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 25 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "ApA" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 25 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 240000) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 45 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "Alt" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 45 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 183253) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 65 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "Ecc" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 65 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (format "%6.4f" 0.02134) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 85 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "T" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 85 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 5421.2) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 105 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "PeT" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 105 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 5368.1) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 125 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "ApT" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 125 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 2657.8) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 145 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "Vel" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 145 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (float-str 7733.2) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 165 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "Inc" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 165 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (format "%6.2f°" 3.5) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 5 185 40 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect "LAN" (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 (Nuklear/nk_rect 45 185 55 20 rect)
                                                 (Nuklear/nk_draw_text canvas rect (format "%6.2f°" 359.96) (:sfsim.gui/font (:sfsim.gui/bitmap-font gui)) bg fg)
                                                 )))
                             (MemoryStack/stackPop))
                           (render-nuklear-gui gui 256 256)
                           (destroy-nuklear-gui-with-font gui)))
                true)
      ; (println (shell/sh "/usr/bin/display" "-display" ":0.0" "/tmp/orbit.png"))
      )


(GLFW/glfwTerminate)
