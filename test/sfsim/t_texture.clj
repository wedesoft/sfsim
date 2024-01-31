(ns sfsim.t-texture
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [sfsim.texture :refer :all]
              [sfsim.render :refer :all]
              [sfsim.image :refer :all])
    (:import [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.texture]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(fact "Size of 1D texture"
      (with-invisible-window
        (let [tex (make-float-texture-1d :linear :clamp (float-array [0 1 0 1]))]
          (:width tex) => 4
          (destroy-texture tex))))

(fact "Size of 2D RGB texture"
      (with-invisible-window
        (let [tex (make-rgb-texture :linear :clamp (slurp-image "test/sfsim/fixtures/render/pattern.png"))]
          (:width tex) => 2
          (:height tex) => 2
          (destroy-texture tex))))

(fact "Size of 2D depth texture"
      (with-invisible-window
        (let [tex (make-depth-texture :linear :clamp #:sfsim.image{:width 2 :height 1 :data (float-array [0 0])})]
          (:width tex) => 2
          (:height tex) => 1
          (destroy-texture tex))))

(fact "Size of 3D texture"
      (with-invisible-window
        (let [tex (make-float-texture-3d :linear :clamp
                                         #:sfsim.image{:width 3 :height 2 :depth 1 :data (float-array (repeat 6 0))})]
          (:width tex) => 3
          (:height tex) => 2
          (:depth tex) => 1
          (destroy-texture tex))))

(GLFW/glfwTerminate)
