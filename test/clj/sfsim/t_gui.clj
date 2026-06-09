;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-gui
  (:require
    [clojure.math :refer (PI to-radians cos sin floor ceil)]
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


(defmacro gui-control-test
  [gui width height scale & body]
  `(gui-offscreen-render ~width ~height
                         (let [~gui (make-nuklear-gui-with-font ~scale)]
                           (nuklear-dark-style ~gui)
                           (nuklear-window ~gui "control test window" 0 0 ~width ~height :widget
                                           ~@body)
                           (render-nuklear-gui ~gui ~width ~height)
                           (destroy-nuklear-gui-with-font ~gui))))


(defmacro widget-test
  [gui canvas rect w h & body]
  `(gui-control-test
     ~gui ~w ~h 1.0
     (layout-row-dynamic ~gui (- ~h 8.0) 1)
     (widget ~gui ~canvas ~rect ~@body)))


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
         (let [bitmap-font         (make-bitmap-font "resources/fonts/b612.ttf" 512 512 18)
               font                (make-font bitmap-font 1.0 1.0)
               gui1                (make-nuklear-gui (:sfsim.gui/font font) 1.0)
               gui2                (make-nuklear-gui (:sfsim.gui/font font) 1.0)]
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
           (destroy-bitmap-font bitmap-font))))


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
          (text-width "Test" (:sfsim.gui/bitmap-font gui)) => (roughly 29.630 1e-3)
          (destroy-nuklear-gui-with-font gui))))


(fact "Define rectangle"
      (with-rect rect 5 3 20 10 [(.x rect) (.y rect) (.w rect) (.h rect)]) => [5.0 3.0 20.0 10.0])

(fact "Define colour"
      (with-color fg 63 127 255 [(.r fg) (.g fg) (.b fg)]) => [63 127 -1])


(fact "Define colours"
      (with-colors [] 42) => 42
      (with-colors [fg 63 127 255] [(.r fg) (.g fg) (.b fg)]) => [63 127 -1])


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


(fact "Test stroke line"
      (widget-test
        gui canvas rect 160 80
        (with-color red 255 0 0
          (stroke-line canvas 10 10 150 70 3.0 red)))
      => (is-image "test/clj/sfsim/fixtures/gui/stroke-line.png" 0.10))


(fact "Test drawing of text"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (draw-text canvas (.x rect) (.y rect) (.w rect) (.h rect) "Some red text" (:sfsim.gui/bitmap-font gui) red)))
      => (is-image "test/clj/sfsim/fixtures/gui/text.png" 0.10))


(fact "Test drawing of text right"
      (widget-test
        gui canvas rect 160 24
        (with-color red 255 0 0
          (draw-text-right canvas (.x rect) (.y rect) (.w rect) (.h rect) "Some red text" (:sfsim.gui/bitmap-font gui) red)))
      => (is-image "test/clj/sfsim/fixtures/gui/text-right.png" 0.10))


(fact "Test drawing with larger font"
      (gui-offscreen-render
        320 48
        (let [gui         (make-nuklear-gui-with-font 1.0)
              bitmap-font (make-bitmap-font "resources/fonts/b612.ttf" 1024 1024 36)
              font        (make-font bitmap-font 1.0 1.0)]
          (nuklear-dark-style gui)
          (nuklear-window gui "control test window" 0 0 320 48 :widget
                          (layout-row-dynamic gui 40.0 1)
                          (widget gui canvas rect
                                  (with-colors [bg 0 0 0
                                                red 255 0 0]
                                    (Nuklear/nk_draw_text canvas rect "Some red text"  (:sfsim.gui/font font) bg red))))
                          (render-nuklear-gui gui 320 48)
                          (destroy-bitmap-font bitmap-font)
                          (destroy-nuklear-gui-with-font gui)))
      => (is-image "test/clj/sfsim/fixtures/gui/text-large.png" 0.10))


(def orbital-params #:sfsim.physics{:periapsis-altitude 280000.0
                                    :apoapsis-altitude 7544246.555
                                    :radius 6378000.0
                                    :semi-major-axis 1.0290123277258096E7
                                    :semi-minor-axis 9627788.819867665
                                    :altitude 280000.0
                                    :eccentricity 0.35297179435015597
                                    :orbital-period 5421.2
                                    :time-since-periapsis 1253.2
                                    :time-since-apoapsis -3242.0
                                    :velocity 9000.0
                                    :inclination (to-radians 3.5)
                                    :longitude-ascending-node (to-radians 359.96)
                                    :argument-of-periapsis (to-radians 30.0)
                                    :true-anomaly (to-radians 45.0)})


(fact "Render orbit MFD"
      (doseq [s [1 2]]
             (gui-offscreen-render
               (quot 264 s) (quot 264 s)
               (let [gui (make-nuklear-gui-with-font (/ 1.0 s))]
                 (nuklear-dark-style gui)
                 (nuklear-window gui "control test window" 0 0 (quot 264 s) (quot 264 s) :widget
                                 (layout-row-dynamic gui (/ 256.0 s) 1)
                                 (orbit-mfd gui orbital-params))
                 (render-nuklear-gui gui (quot 264 s) (quot 264 s))
                 (destroy-nuklear-gui-with-font gui)))
             => (is-image (format "test/clj/sfsim/fixtures/gui/orbit-%d.png" s) 0.10)))


(fact "Render navball texture"
      (spit-png
        "/tmp/navball.png"
        (let [w 512 h 1024]
          (gui-offscreen-render
            w h
            (let [gui   (make-nuklear-gui-with-font 1.0)
                  yaw   [30 45 60 75 105 120 135 150]
                  fonts (zipmap yaw (map (fn [x] (make-font (:sfsim.gui/bitmap-font gui) 1.0 (/ 1.0 (sin (to-radians x))))) yaw))]
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
                                   y    (+ (/ (* pitch h) 360) (if (#{75 105} yaw) (case pitch 0 -9 180 9 360 -9 0) 0))
                                   fg   (if (or (<= pitch 180) (>= pitch 360)) black white)
                                   bg   (if (or (<= pitch 180) (>= pitch 360)) white black)
                                   text (str (/ (mod (+ pitch 180) 360) 10))
                                   tw   (text-width text (fonts yaw))
                                   th   (* 18 (:sfsim.gui/scale-y (fonts yaw)))
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
                                   th   (* 18 (:sfsim.gui/scale-y (fonts yaw)))
                                   pad  4]
                               (when (or (zero? (mod (+ pitch 15) 60)) (#{60 120} yaw))
                                 (with-rect rect (- x (/ tw 2) pad) (- y (/ th 2)) (+ tw (* 2 pad)) th
                                   (fill-rect canvas rect 3.0 bg))
                                 (draw-text canvas (- x (/ tw 2)) (- y (/ th 2)) tw th text (fonts yaw) fg))))))))
              (render-nuklear-gui gui w h)
              (destroy-nuklear-gui-with-font gui))
            w h)) true)
      ; (shell/sh "display" "-display" ":0.0" "/tmp/navball.png")
      )


(GLFW/glfwTerminate)
