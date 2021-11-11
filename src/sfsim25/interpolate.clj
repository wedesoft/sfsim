(ns sfsim25.interpolate
    "N-dimensional interpolation"
    (:require [clojure.core.matrix :refer :all]))

(set! *unchecked-math* true)

(defn linear-mapping
  "Linear mapping onto interpolation table of given size"
  [minimum maximum size]
  (fn [& point] (map (fn [x a b n] (-> x (- a) (/ (- b a)) (* (dec n)))) point minimum maximum size)))

(defn inverse-linear-mapping
  "Inverse linear mapping to get sample values for lookup table"
  [minimum maximum size]
  (fn [& indices] (map (fn [i a b n] (-> i (/ (dec n)) (* (- b a)) (+ a))) indices minimum maximum size)))

(defn- sample-function
  "Recursively take samples from a function"
  [sample-fun size args]
  (if (empty? size)
    (sample-fun args)
    (vec (map #(sample-function sample-fun (rest size) (conj args %)) (range (first size))))))

(defn make-lookup-table
  "Create n-dimensional lookup table using given function to sample and inverse mapping"
  [fun inverse-mapping size]
  (sample-function (fn [args] (apply fun (apply inverse-mapping args))) size []))

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
  [fun mapping inverse-mapping size]
  (interpolate-table (make-lookup-table fun inverse-mapping size) mapping))

(set! *unchecked-math* false)
