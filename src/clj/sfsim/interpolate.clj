;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.interpolate
  "N-dimensional interpolation"
  (:require
    [clojure.math :refer (floor)]
    [fastmath.vector :refer (add mult)]
    [malli.core :as m]
    [sfsim.util :refer (comp* N)])
  (:import
    (fastmath.protocols
      VectorProto)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(def interpolation-space (m/schema [:map [::shape [:vector N]] [::forward fn?] [::backward fn?]]))


(defn- linear-forward
  "Linear mapping onto interpolation table of given shape"
  {:malli/schema [:=> [:cat [:vector :double] [:vector :double] [:vector N]] [:=> [:cat [:* :double]] [:vector :double]]]}
  [minima maxima shape]
  (fn linear-forward
    [& point]
    (mapv (fn linear-forward-component [^double x ^double a ^double b ^long n]
              (-> x (- a) (/ (- b a)) (* (dec n))))
          point minima maxima shape)))


(defn- linear-backward
  "Inverse linear mapping to get sample values for lookup table"
  {:malli/schema [:=> [:cat [:vector :double] [:vector :double] [:vector N]] [:=> [:cat [:* :double]] [:vector :double]]]}
  [minima maxima shape]
  (fn linear-backward
    [& indices]
    (mapv (fn linear-backward-component [^double i ^double a ^double b ^long n]
              (-> i (/ (dec n)) (* (- b a)) (+ a)))
          indices minima maxima shape)))


(defn linear-space
  "Create forward and backward mapping for linear sampling"
  {:malli/schema [:=> [:cat [:vector :double] [:vector :double] [:vector N]] interpolation-space]}
  [^clojure.lang.ISeq minima ^clojure.lang.ISeq maxima ^clojure.lang.ISeq shape]
  {::forward (linear-forward minima maxima shape) ::backward (linear-backward minima maxima shape) ::shape shape})


(defn- sample-function
  "Recursively take samples from a function"
  {:malli/schema [:=> [:cat fn? [:vector N] [:vector :double] fn?]]}
  [sample-fun shape args map-fun]
  (if (seq shape)
    (vec (map-fun #(sample-function sample-fun (rest shape) (conj args (double %)) map) (range (first shape))))
    (sample-fun args)))


(def table
  (m/schema [:schema {:registry {::slice [:vector [:or :double [:ref ::slice]]]}}
             [:ref ::slice]]))


(defn make-lookup-table
  "Create n-dimensional lookup table using given function to sample and inverse mapping"
  {:malli/schema [:=> [:cat fn? interpolation-space] table]}
  [fun space]
  (sample-function (fn lookup-sample [args] (apply fun (apply (::backward space) args))) (::shape space) [] pmap))


(defn clip
  "Clip a value to [0, size - 1]"
  ^double [^double value ^long size]
  (min (max value 0.0) (dec size)))


(defn mix
  "Linear mixing of values"
  [^VectorProto a ^VectorProto b ^double scalar]
  (add (mult a (- 1 scalar)) (mult b scalar)))


(defn- interpolate-value
  "Linear interpolation for point in table"
  [lookup-table ^clojure.lang.PersistentVector ^clojure.lang.ISeq point]
  (if (seq point)
    (let [size       (count lookup-table)
          [c & args] point
          i          (clip c size)
          u          (floor i)
          v          (clip (inc u) size)
          s          (- i u)]
      (mix (interpolate-value (nth lookup-table u) args) (interpolate-value (nth lookup-table v) args) s))
    lookup-table))


(defn interpolation-table
  "Linear interpolation using lookup table and mapping function"
  [^clojure.lang.PersistentVector lookup-table {:sfsim.interpolate/keys [forward]}]
  (fn interpolation-table [& coords] (interpolate-value lookup-table (apply forward coords))))


(defn interpolate-function
  "Linear interpolation of function"
  [fun space]
  (interpolation-table (make-lookup-table fun space) space))


(defn compose-space
  "Chain forward and backward transformation functions"
  [f g]
  {::shape (::shape f)
   ::forward (comp* (::forward f) (::forward g))
   ::backward (comp* (::backward g) (::backward f))})


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
