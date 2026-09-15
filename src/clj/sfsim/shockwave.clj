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
      [sfsim.render :refer (uniform-float use-program uniform-int render-quads framebuffer-render uniform-sampler use-textures)]
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


(defmacro jump-flooding-step
  [program vao shockwave-radius size & body]
  `(fn [flood# step#]
       (let [result# (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/zero GL30/GL_RGBA32F ~size ~size)]
         (framebuffer-render ~size ~size :sfsim.render/noculling nil [result#]
                             (use-program ~program)
                             (uniform-sampler ~program "flood" 0)
                             (uniform-int ~program "size" ~size)
                             (uniform-int ~program "step" step#)
                             (uniform-float ~program "scale" (/ (* 2.0 ~shockwave-radius) ~size))
                             ~@body
                             (use-textures {0 flood#})
                             (render-quads ~vao))
         (destroy-texture flood#)
         result#)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
