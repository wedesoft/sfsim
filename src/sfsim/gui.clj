(ns sfsim.gui
    (:require [fastmath.matrix :as fm]
              [sfsim.matrix :refer (fmat4)]
              [sfsim.util :refer (N)]
              [sfsim.render :refer (make-program)]
              [sfsim.texture :refer (make-rgba-texture)])
    (:import [org.lwjgl.opengl GL11 GL14]
             [org.lwjgl.nuklear NkDrawNullTexture]))

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

(defn make-null-texture
  "Make texture with single white pixel"
  {:malli/schema [:=> :cat :some]}
  []
  (let [image   #:sfsim.image {:width 1 :height 1 :data (byte-array [-1 -1 -1 -1])}
        texture (make-rgba-texture :sfsim.texture/nearest :sfsim.texture/clamp image)
        result  (NkDrawNullTexture/create)]
    (.id (.texture result) (:sfsim.texture/texture texture))
    (.set (.uv result) 0.5 0.5)
    result))

(defn destroy-null-texture
  "Destroy single pixel texture"
  [null-texture]
  (GL11/glDeleteTextures ^long (.id (.texture null-texture))))

(defn setup-gui-rendering
  [width height]
  (GL11/glViewport 0 0 width height)
  (GL11/glEnable GL11/GL_BLEND)
  (GL14/glBlendEquation GL14/GL_FUNC_ADD)
  (GL14/glBlendFunc GL14/GL_SRC_ALPHA GL14/GL_ONE_MINUS_SRC_ALPHA)
  (GL11/glDisable GL11/GL_CULL_FACE)
  (GL11/glDisable GL11/GL_DEPTH_TEST))
