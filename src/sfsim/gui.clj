(ns sfsim.gui
    (:require [sfsim.render :refer (make-program)]))

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
