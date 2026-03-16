;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-texture
  (:require
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.image :refer :all]
    [sfsim.render :refer :all]
    [sfsim.texture :refer :all])
  (:import
    (org.lwjgl.glfw
      GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(fact "Size of 1D texture"
      (with-invisible-window
        (let [tex (make-float-texture-1d :sfsim.texture/linear :sfsim.texture/clamp (float-array [0 1 0 1]))]
          (:sfsim.texture/width tex) => 4
          (destroy-texture tex))))


(fact "Size of 2D RGB texture"
      (with-invisible-window
        (let [tex (make-rgb-texture :sfsim.texture/linear :sfsim.texture/clamp
                                    (slurp-image "test/clj/sfsim/fixtures/render/pattern.png"))]
          (:sfsim.texture/width tex) => 2
          (:sfsim.texture/height tex) => 2
          (destroy-texture tex))))


(fact "Size of 2D depth texture"
      (with-invisible-window
        (let [img #:sfsim.image{:width 2 :height 1 :data (float-array [0 0])}
              tex (make-depth-texture :sfsim.texture/linear :sfsim.texture/clamp img)]
          (:sfsim.texture/width tex) => 2
          (:sfsim.texture/height tex) => 1
          (destroy-texture tex))))


(fact "Size of 3D texture"
      (with-invisible-window
        (let [tex (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp
                                         #:sfsim.image{:width 3 :height 2 :depth 1 :data (float-array (repeat 6 0))})]
          (:sfsim.texture/width tex) => 3
          (:sfsim.texture/height tex) => 2
          (:sfsim.texture/depth tex) => 1
          (destroy-texture tex))))


(fact "Size of texture array"
      (with-invisible-window
        (let [tex (make-rgb-texture-array :sfsim.texture/linear :sfsim.texture/clamp
                                          (repeat 3 (slurp-image "test/clj/sfsim/fixtures/render/pattern.png")))]
          (:sfsim.texture/width tex) => 2
          (:sfsim.texture/height tex) => 2
          (:sfsim.texture/depth tex) => 3
          (destroy-texture tex))))


(fact "Size of 4D texture (represented using 2D texture)"
      (with-invisible-window
        (let [data (float-array (repeat (* 4 3 2 1 4) 0))
              tex  (make-vector-texture-4d :sfsim.texture/linear :sfsim.texture/clamp
                                           #:sfsim.image{:width 4 :height 3 :depth 2 :hyperdepth 1 :data data})]
          (:sfsim.texture/width tex) => 4
          (:sfsim.texture/height tex) => 3
          (:sfsim.texture/depth tex) => 2
          (:sfsim.texture/hyperdepth tex) => 1
          (destroy-texture tex))))


(fact "Create floating-point cube map and read them out"
      (with-invisible-window
        (let [cubemap (make-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp
                                          (mapv (fn [i] #:sfsim.image{:width 1 :height 1 :data (float-array [(inc i)])}) (range 6)))]
          (doseq [i (range 6)]
            (get-float (float-cubemap->floats cubemap i) 0 0) => (float (inc i)))
          (:sfsim.texture/width cubemap) => 1
          (:sfsim.texture/height cubemap) => 1
          (destroy-texture cubemap))))


(facts "Convert byte buffer to byte array"
       (let [buffer (make-byte-buffer (byte-array [1 2 3 4]))]
         (byte-buffer->array buffer) => bytes?
         (seq (byte-buffer->array buffer)) => [1 2 3 4]))


(GLFW/glfwTerminate)
