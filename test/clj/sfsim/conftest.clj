;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.conftest
  (:require
    [clojure.java.io :as io]
    [clojure.math :refer (sqrt)]
    [fastmath.matrix :as fm]
    [sfsim.image :refer (get-vector3 slurp-image spit-png)]
    [sfsim.render :refer (make-program make-vertex-array-object render-quads texture-render-color use-program
                                       with-invisible-window destroy-program destroy-vertex-array-object)]
    [sfsim.shaders :as shaders]
    [sfsim.quaternion :as q]
    [sfsim.util :refer (byte->ubyte)]
    [sfsim.texture :refer (rgb-texture->vectors3 destroy-texture)]))


(defn roughly-matrix
  "compare matrix with expected value."
  [expected error]
  (fn [actual]
    (let [difference (fm/sub expected actual)]
      (<= (sqrt (apply + (fm/mat->array (fm/emulm difference difference)))) error))))


(defn roughly-vector
  "Compare vector with expected value."
  [expected error]
  (fn [actual]
    (and (== (count expected) (count actual))
         (<= (sqrt (apply + (mapv (comp #(* % %) -) actual expected))) error))))


(defn roughly-quaternion
  "Compare quaternion with expected value"
  [expected error]
  (fn [actual]
      (println expected actual)
      (<= (q/norm (q/- actual expected)) error)))


(defn roughly-quaternion
  "Compare quaternion with expected value."
  [expected error]
  (fn [actual]
    (<= (sqrt (apply + (mapv (comp #(* % %) #(- (% actual) (% expected))) [:real :imag :jmag :kmag]))) error)))


(defn tap [x] (println x) x)


(defn rgba-dist
  [c1 c2]
  (apply max (mapv #(abs (- (byte->ubyte %1) (byte->ubyte %2))) c1 c2)))


(defn average-rgba-dist
  [data1 data2]
  (let [distances (mapv rgba-dist (partition 4 data1) (partition 4 data2))]
    (float (/ (reduce + distances) (count distances)))))


(defn is-image
  "Compare RGB components of image and ignore alpha values."
  ([filename tolerance] (is-image filename tolerance true))
  ([filename tolerance flip]
   (fn [other]
     (if (.exists (io/file filename))
       (let [{:sfsim.image/keys [width height data]} (slurp-image filename flip)]
         (and (== (:sfsim.image/width other) width)
              (== (:sfsim.image/height other) height)
              (let [avg-dist (average-rgba-dist (:sfsim.image/data other) data)]
                (or (<= avg-dist tolerance)
                    (do
                      (println (format "Average deviation from %s averages %5.2f > %5.2f." filename avg-dist tolerance))
                      false)))))
       (do
         (println (format "Recording image fixture %s." filename))
         (spit-png filename other flip))))))


(defn shader-test
  [setup probe & shaders]
  (fn [uniforms args]
    (with-invisible-window
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                   :sfsim.render/fragment (conj shaders (apply probe args)))
            vao      (make-vertex-array-object program indices vertices ["point" 3])
            tex      (texture-render-color 1 1 true
                                           (use-program program)
                                           (apply setup program uniforms)
                                           (render-quads vao))
            img      (rgb-texture->vectors3 tex)]
        (destroy-texture tex)
        (destroy-vertex-array-object vao)
        (destroy-program program)
        (get-vector3 img 0 0)))))
