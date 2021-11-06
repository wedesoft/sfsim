(ns sfsim25.interpolate
    "N-dimensional interpolation"
    (:require [clojure.core.matrix :refer :all]))

(defn linear-mapping
  "Linear mapping onto interpolation table of given size"
  [minimum maximum size]
  (fn [x] (-> x (- minimum) (/ (- maximum minimum)) (* (dec size)))))

(defn linear-sampling
  "Inverse linear mapping to get sample values for lookup table"
  [minimum maximum size]
  (fn [i] (-> i (/ (dec size)) (* (- maximum minimum)) (+ minimum))))

(defn lookup
  "Create 1-dimensional lookup table"
  [fun sampling size]
  (vec (map (comp fun sampling) (range size))))

(defn table
  "Create 1-dimensional lookup table using linear sampling"
  [fun minimum maximum size]
  (lookup fun (linear-sampling minimum maximum size) size))

(defn clip
  "Clip a value to [0, size - 1]"
  [value size]
  (min (max value 0) (dec size)))

(defn mix
  "Linear mixing of values"
  [a b scalar]
  (+ (* (- 1 scalar) a) (* scalar b)))

(defn interpolate
  "Linear interpolation of function"
  [fun minimum maximum size]
  (let [mapping (linear-mapping minimum maximum size)
        lut     (table fun minimum maximum size)]
    (fn [x]
        (let [i (clip (mapping x) size)
              u (Math/floor i)
              v (clip (inc u) size)
              s (- i u)]
          (mix (nth lut u) (nth lut v) s)))))
