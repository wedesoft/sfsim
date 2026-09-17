;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.shockwave
    (:require
      [clojure.math :refer (sqrt exp)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [sfsim.render :refer (uniform-float use-program uniform-int render-quads framebuffer-render uniform-sampler use-textures
                            make-program destroy-program make-vertex-array-object destroy-vertex-array-object)]
      [sfsim.texture :refer (make-empty-texture-2d destroy-texture)]
      )
    (:import
      (org.lwjgl.opengl
        GL30)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def shockfront (slurp "resources/shaders/shockwave/shockfront.glsl"))


(def curvature (slurp "resources/shaders/shockwave/curvature.glsl"))


(def vertex-quad (slurp "resources/shaders/shockwave/vertex-quad.glsl"))


(def fragment-jump-flooding-init (slurp "resources/shaders/shockwave/fragment-jump-flooding-init.glsl"))


(def fragment-jump-flooding-step (slurp "resources/shaders/shockwave/fragment-jump-flooding-step.glsl"))


(defn make-shockwave-renderer
  [depth-source normal-source shockfront size shockwave-radius max-curvature-radius]
  (let [indices      [0 1 3 2]
        vertices     [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
        program-init (make-program :sfsim.render/vertex [vertex-quad]
                                   :sfsim.render/fragment [fragment-jump-flooding-init curvature depth-source normal-source])
        program-step (make-program :sfsim.render/vertex [vertex-quad]
                                   :sfsim.render/fragment [fragment-jump-flooding-step shockfront])
        vao          (make-vertex-array-object program-init indices vertices ["point" 3 "uv" 2])]
    {::size                 size
     ::shockwave-radius     shockwave-radius
     ::max-curvature-radius max-curvature-radius
     ::program-init         program-init
     ::program-step         program-step
     ::vao                  vao}))


(defn destroy-shockwave-renderer
  [{::keys [program-init program-step vao]}]
  (destroy-vertex-array-object vao)
  (destroy-program program-step)
  (destroy-program program-init))


(defn jump-flooding-initialisation
  [{::keys [program-init vao size shockwave-radius max-curvature-radius]} fun]
  (let [flood (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
    (framebuffer-render size size :sfsim.render/cullback nil [flood]
                        (use-program program-init)
                        (uniform-int program-init "size" size)
                        (uniform-float program-init "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
                        (uniform-float program-init "shockwave_radius" shockwave-radius)
                        (uniform-float program-init "max_curvature_radius" max-curvature-radius)
                        (fun program-init)
                        (render-quads vao))
    flood))


(defn jump-flooding-step
  [{::keys [program-step vao size shockwave-radius]} fun]
  (fn [flood step]
      (let [result (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F size size)]
        (framebuffer-render size size :sfsim.render/noculling nil [result]
                            (use-program program-step)
                            (uniform-sampler program-step "flood" 0)
                            (uniform-int program-step "size" size)
                            (uniform-int program-step "step" step)
                            (uniform-float program-step "scale" (/ (* 2.0 ^double shockwave-radius) ^long size))
                            (fun program-step)
                            (use-textures {0 flood})
                            (render-quads vao))
        (destroy-texture flood)
        result)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
