(ns sfsim.t-gui
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [fastmath.vector :refer (vec4)]
              [sfsim.conftest :refer (roughly-vector)]
              [sfsim.render :refer :all]
              [sfsim.texture :refer :all]
              [sfsim.image :refer :all]
              [sfsim.gui :refer :all])
    (:import [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.gui]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

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
                                              #:sfsim.image{:width 1 :height 1 :data (byte-array [?r2 ?g2 ?b2 ?a2])})
                   output   (texture-render-color 1 1 true
                                                  (use-program program)
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

(GLFW/glfwTerminate)
