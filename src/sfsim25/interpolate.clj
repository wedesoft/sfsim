(ns sfsim25.interpolate
    "N-dimensional interpolation"
    (:require [clojure.core.matrix :refer :all]))

(set! *unchecked-math* true)

(defn linear-forward
  "Linear mapping onto interpolation table of given shape"
  [minima maxima shape]
  (fn [& point] (map (fn [^double x ^double a ^double b ^long n] (-> x (- a) (/ (- b a)) (* (dec n)))) point minima maxima shape)))

(defn linear-backward
  "Inverse linear mapping to get sample values for lookup table"
  [minima maxima shape]
  (fn [& indices] (map (fn [^long i ^double a ^double b ^long n] (-> i (/ (dec n)) (* (- b a)) (+ a))) indices minima maxima shape)))

(defn linear-space
  "Create forward and backward mapping for linear sampling"
  [minima maxima shape]
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
  [a b ^double scalar]
  (add (mul (- 1 scalar) a) (mul scalar b)))

(defn dimensions
  "Return shape of lookup table"
  [lut]
  (if (vector? lut)
    (into [(count lut)] (dimensions (nth lut 0)))
    []))

(defn- interpolate-value
  "Linear interpolation for point in table"
  [lut ^clojure.lang.PersistentVector point]
  (if (seq point)
    (let [size       (count lut)
          [c & args] point
          i          (clip c size)
          u          (Math/floor i)
          v          (clip (inc u) size)
          s          (- i u)]
      (mix (interpolate-value (nth lut u) args) (interpolate-value (nth lut v) args) s))
    lut))

(defn interpolation-table
  "Linear interpolation using lookup table and mapping function"
  [^clojure.lang.PersistentVector lut mapping]
  (fn [& coords] (interpolate-value lut (apply mapping coords))))

(defn interpolate-function
  "Linear interpolation of function"
  [fun space]
  (interpolation-table (make-lookup-table fun space) (::forward space)))

(set! *unchecked-math* false)
