(ns sfsim25.interpolate
    "N-dimensional interpolation"
    (:require [clojure.core.matrix :refer :all]))

(set! *unchecked-math* true)

(defn linear-mapping
  "Linear mapping onto interpolation table of given shape"
  [minima maxima shape]
  (fn [& point] (map (fn [x a b n] (-> x (- a) (/ (- b a)) (* (dec n)))) point minima maxima shape)))

(defn inverse-linear-mapping
  "Inverse linear mapping to get sample values for lookup table"
  [minima maxima shape]
  (fn [& indices] (map (fn [i a b n] (-> i (/ (dec n)) (* (- b a)) (+ a))) indices minima maxima shape)))

(defn linear-space
  "Create forward and backward mapping for linear sampling"
  [minima maxima shape]
  {::forward (linear-mapping minima maxima shape) ::backward (inverse-linear-mapping minima maxima shape) ::shape shape})

(defn- sample-function
  "Recursively take samples from a function"
  [sample-fun shape args]
  (if (empty? shape)
    (sample-fun args)
    (vec (map #(sample-function sample-fun (rest shape) (conj args %)) (range (first shape))))))

(defn make-lookup-table
  "Create n-dimensional lookup table using given function to sample and inverse mapping"
  [fun space]
  (sample-function (fn [args] (apply fun (apply (::backward space) args))) (::shape space) []))

(defn clip
  "Clip a value to [0, size - 1]"
  [value size]
  (min (max value 0) (dec size)))

(defn mix
  "Linear mixing of values"
  [a b scalar]
  (add (mul (- 1 scalar) a) (mul scalar b)))

(defn dimensions
  "Return shape of lookup table"
  [lut]
  (if (vector? lut)
    (into [(count lut)] (dimensions (nth lut 0)))
    []))

(defn- interpolation
  "Linear interpolation for point in table"
  [lut point]
  (if (empty? point)
    lut
    (let [size       (count lut)
          [c & args] point
          i          (clip c size)
          u          (Math/floor i)
          v          (clip (inc u) size)
          s          (- i u)]
      (mix (interpolation (nth lut u) args) (interpolation (nth lut v) args) s))))

(defn interpolate-table
  "Linear interpolation using lookup table and mapping function"
  [lut mapping]
  (fn [& coords] (interpolation lut (apply mapping coords))))

(defn interpolate-function
  "Linear interpolation of function"
  [fun space]
  (interpolate-table (make-lookup-table fun space) (::forward space)))

(set! *unchecked-math* false)
