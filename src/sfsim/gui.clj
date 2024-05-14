(ns sfsim.gui
    (:require [fastmath.matrix :as fm]
              [sfsim.matrix :refer (fmat4)]
              [sfsim.util :refer (N)]
              [sfsim.render :refer (make-program)]))

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
