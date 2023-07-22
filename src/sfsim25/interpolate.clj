(ns sfsim25.interpolate
    "N-dimensional interpolation"
    (:require [fastmath.vector :refer (add mult)]
              [clojure.math :refer (floor)]
              [sfsim25.util :refer (comp*)])
    (:import [fastmath.protocols VectorProto])
    )

(set! *unchecked-math* true)

(defn- linear-forward
  "Linear mapping onto interpolation table of given shape"
  [^clojure.lang.ISeq minima ^clojure.lang.ISeq maxima ^clojure.lang.ISeq shape]
  (fn [& point] (map (fn [^double x ^double a ^double b ^long n] (-> x (- a) (/ (- b a)) (* (dec n)))) point minima maxima shape)))

(defn- linear-backward
  "Inverse linear mapping to get sample values for lookup table"
  [minima maxima shape]
  (fn [& indices] (map (fn [^long i ^double a ^double b ^long n] (-> i (/ (dec n)) (* (- b a)) (+ a))) indices minima maxima shape)))

(defn linear-space
  "Create forward and backward mapping for linear sampling"
  [^clojure.lang.ISeq minima ^clojure.lang.ISeq maxima ^clojure.lang.ISeq shape]
  {::forward (linear-forward minima maxima shape) ::backward (linear-backward minima maxima shape) ::shape shape})

(defn- sample-function
  "Recursively take samples from a function"
  [sample-fun shape args map-fun]
  (if (seq shape)
    (vec (map-fun #(sample-function sample-fun (rest shape) (conj args %) map) (range (first shape))))
    (sample-fun args)))

(defn make-lookup-table
  "Create n-dimensional lookup table using given function to sample and inverse mapping"
  ^clojure.lang.PersistentVector [fun space]
  (sample-function (fn [args] (apply fun (apply (::backward space) args))) (::shape space) [] pmap))

(defn clip
  "Clip a value to [0, size - 1]"
  ^double [^double value ^long size]
  (min (max value 0) (dec size)))

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
  [^clojure.lang.PersistentVector lookup-table {:sfsim25.interpolate/keys [forward]}]
  (fn [& coords] (interpolate-value lookup-table (apply forward coords))))

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

(set! *unchecked-math* false)
