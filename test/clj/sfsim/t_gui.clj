;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-gui
  (:require
    [clojure.math :refer (PI to-radians cos sin)]
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
       (float-str 0.0) => "   0.00"
       (float-str PI) => "   3.14"
       (float-str (- PI)) => "  -3.14"
       (float-str 123.456) => " 123.46"
       (float-str -123.456) => "-123.46"
       (float-str 7733.0) => " 7.733k"
       (float-str 12345.6) => " 12.35k"
       (float-str 123456.7) => " 123.5k"
       (float-str 6378000.0) => " 6.378M"
       (float-str 1e+7) => " 10.00M"
       (float-str 1e+8) => " 100.0M"
       (float-str 1e+9) => " 1.000G"
       (float-str 1e+10) => " 10.00G"
       (float-str 1e+11) => " 100.0G"
       (float-str 1e+12) => " 1.000T"
       (float-str 1e+13) => " 10.00T"
       (float-str 1e+14) => " 100.0T"
       (float-str 1e+15) => "  1e+15")


(fact "Text width"
      (gui-offscreen-render
        256 256
        (let [gui (make-nuklear-gui-with-font 1.0)]
          (text-width gui "Test") => (roughly 29.630 1e-3)
          (destroy-nuklear-gui-with-font gui))))


(fact "Define rectangle"
      (with-rect rect 5 3 20 10 [(.x rect) (.y rect) (.w rect) (.h rect)]) => [5.0 3.0 20.0 10.0])

(fact "Define colour"
      (with-color fg 63 127 255 [(.r fg) (.g fg) (.b fg)]) => [63 127 -1])


(fact "Define colours"
      (with-colors [] 42) => 42
      (with-colors [fg 63 127 255] [(.r fg) (.g fg) (.b fg)]) => [63 127 -1])


(defmacro widget-test
  [gui canvas rect w h & body]
  `(gui-control-test
     ~gui ~w ~h 1.0
     (layout-row-dynamic ~gui (- ~h 8.0) 1)
     (widget ~gui ~canvas ~rect ~@body)))


(fact "Test filled rect"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (fill-rect canvas rect 0.0 red)))
      => (is-image "test/clj/sfsim/fixtures/gui/filled-rect.png" 0.10))


(fact "Test stroke rect"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (stroke-rect canvas rect 0.0 3.0 red)))
      => (is-image "test/clj/sfsim/fixtures/gui/stroke-rect.png" 0.10))


(fact "Test filled circle"
      (widget-test
        gui canvas rect 160 160
        (with-color red 255 0 0
          (fill-circle canvas rect red)))
      => (is-image "test/clj/sfsim/fixtures/gui/filled-circle.png" 0.10))


(fact "Test stroke circle"
      (widget-test
        gui canvas rect 160 160
        (with-color red 255 0 0
          (stroke-circle canvas rect 3.0 red)))
      => (is-image "test/clj/sfsim/fixtures/gui/stroke-circle.png" 0.10))


(fact "Test drawing of text"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (draw-text gui canvas (.x rect) (.y rect) (.w rect) (.h rect) "Some red text" red)))
      => (is-image "test/clj/sfsim/fixtures/gui/text.png" 0.10))


(fact "Test drawing of text right"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (draw-text-right gui canvas (.x rect) (.y rect) (.w rect) (.h rect) "Some red text" red)))
      => (is-image "test/clj/sfsim/fixtures/gui/text-right.png" 0.10))


(fact "Render orbit MFD"
      (gui-offscreen-render
        256 256
        (let [gui (make-nuklear-gui-with-font 1.0)]
          (nuklear-dark-style gui)
          (without-window-padding gui
            (nuklear-window gui "control test window" 0 0 256 256 :widget
                            (let [stack                    (MemoryStack/stackPush)
                                  rect                     (NkRect/malloc stack)
                                  context                  (:sfsim.gui/context gui)
                                  argument-of-periapsis    (to-radians 30.0)
                                  true-anomaly             (to-radians 45.0)
                                  radius                   6378000.0
                                  periapsis                6658000.0
                                  apoapsis                 13922246.555
                                  altitude                 280000.0
                                  periapsis-altitude       (- periapsis radius)
                                  apoapsis-altitude        (- apoapsis radius)
                                  semi-major-axis          1.0290123277258096E7
                                  semi-minor-axis          9627788.819867665
                                  eccentricity             0.35297179435015597
                                  orbital-period           5421.2
                                  time-since-periapsis     -1253.2
                                  time-since-apoapsis      3242.0
                                  velocity                 9000.0
                                  inclination              3.5
                                  longitude-ascending-node 359.96
                                  orbital-params           #:sfsim.physics{:periapsis periapsis
                                                                           :apoapsis apoapsis
                                                                           :semi-major-axis semi-major-axis
                                                                           :semi-minor-axis semi-minor-axis
                                                                           :altitude altitude
                                                                           :eccentricity eccentricity
                                                                           :orbital-period orbital-period
                                                                           :time-since-periapsis time-since-periapsis
                                                                           :time-since-apoapsis time-since-apoapsis
                                                                           :velocity velocity
                                                                           :inclination inclination
                                                                           :longitude-ascending-node longitude-ascending-node
                                                                           :argument-of-periapsis argument-of-periapsis
                                                                           :true-anomaly true-anomaly}]
                              ; (layout-row-dynamic gui 32.0 1)
                              ; (button-label gui "Test")
                              (layout-row-dynamic gui 256.0 1)
                              (with-colors
                                [bg       0   0   0
                                 fg      64 211  71
                                 border  82 185 142
                                 bright 202 213 197
                                 title  129 226 207]
                                (widget gui canvas canvas-rect
                                        (fill-rect canvas canvas-rect 0.0 bg)
                                        (stroke-rect canvas canvas-rect 0.0 3.0 border)
                                        (let [scale  (/ 120 (max apoapsis radius))
                                              earth  (* scale radius)
                                              n      44]
                                          (with-rect rect (- 128 earth) (- 128 earth) (* 2 earth) (* 2 earth)
                                            (stroke-circle canvas rect 2.0 bright))
                                          (let [f (fn [a] (let [r (distance-for-anomaly orbital-params a)]
                                                           [(* scale r (cos a)) (* scale r (sin a))]))
                                                g (fn [a] (let [[x y] (f a)]
                                                            [(+ 128 (- (* (cos argument-of-periapsis) x) (* (sin argument-of-periapsis) y)))
                                                             (- 128 (+ (* (sin argument-of-periapsis) x) (* (cos argument-of-periapsis) y)))]))]
                                            (doseq [i (range n)]
                                                   (let [a (to-radians (/ (* 360 i) n))
                                                         b (to-radians (/ (* 360 (inc i)) n))
                                                         [x0 y0] (orbit-point orbital-params 128 128 scale a)
                                                         [x1 y1] (orbit-point orbital-params 128 128 scale b)]
                                                     (stroke-line canvas x0 y0 x1 y1 2.0 fg)))
                                            (let [[x y] (orbit-point orbital-params 128 128 scale true-anomaly)]
                                              (Nuklear/nk_stroke_line canvas 128 128 x y 2.0 fg)
                                              (with-rect rect (- x 2) (- y 2) 5 5
                                                (fill-circle canvas rect fg)))
                                            (let [[x y] (orbit-point orbital-params 128 128 scale (- argument-of-periapsis))]
                                              (with-rect rect (- x 3) (- y 3) 7 7
                                                (fill-rect canvas rect 0.0 fg)))
                                            (let [[x y] (orbit-point orbital-params 128 128 scale (- PI argument-of-periapsis))]
                                              (with-rect rect (- x 2) (- y 2) 5 5
                                                (fill-rect canvas rect 0.0 bg)
                                                (stroke-rect canvas rect 0.0 2.0 fg)))))
                                        (draw-text gui canvas 5 5 40 20 "Earth" title)
                                        (draw-text gui canvas 5 25 40 20 "PeA" fg)
                                        (draw-text gui canvas 45 25 55 20 (float-str periapsis-altitude) fg)
                                        (draw-text gui canvas 5 45 40 20 "ApA" fg)
                                        (draw-text gui canvas 45 45 55 20 (float-str apoapsis-altitude) fg)
                                        (draw-text gui canvas 5 65 40 20 "Alt" fg)
                                        (draw-text gui canvas 45 65 55 20 (float-str altitude) fg)
                                        (draw-text gui canvas 5 85 40 20 "Ecc" fg)
                                        (draw-text gui canvas 45 85 55 20 (format "%7.4f" eccentricity) fg)
                                        (draw-text gui canvas 5 105 40 20 "T" fg)
                                        (draw-text gui canvas 45 105 55 20 (float-str orbital-period) fg)
                                        (draw-text gui canvas 5 125 40 20 "PeT" fg)
                                        (draw-text gui canvas 45 125 55 20 (float-str time-since-periapsis) fg)
                                        (draw-text gui canvas 5 145 40 20 "ApT" fg)
                                        (draw-text gui canvas 45 145 55 20 (float-str time-since-apoapsis) fg)
                                        (draw-text gui canvas 5 165 40 20 "Vel" fg)
                                        (draw-text gui canvas 45 165 55 20 (float-str velocity) fg)
                                        (draw-text gui canvas 5 185 40 20 "Inc" fg)
                                        (draw-text-right gui canvas 45 185 55 20 (format "%6.2f°" inclination) fg)
                                        (draw-text gui canvas 5 205 40 20 "LAN" fg)
                                        (draw-text-right gui canvas 45 205 55 20 (format "%6.2f°" longitude-ascending-node) fg)
                                        (MemoryStack/stackPop))))))
          (render-nuklear-gui gui 256 256)
          (destroy-nuklear-gui-with-font gui)))
      => (is-image "test/clj/sfsim/fixtures/gui/orbit.png" 0.10))


(GLFW/glfwTerminate)
