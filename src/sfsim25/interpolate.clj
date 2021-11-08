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
  (add (mul (- 1 scalar) a) (mul scalar b)))

(defn dimensions
  "Return shape of lookup table"
  [lut]
  (if (vector? lut)
    (into [(count lut)] (dimensions (nth lut 0)))
    []))

(defn interpolation
  "Linear interpolation of data table"
  [lut minimum maximum]
  (if (= (count (dimensions lut)) 1); TODO: interpolation for empty minimum and maximum
    (fn [x]
        (let [size    (count lut)
              mapping (linear-mapping (first minimum) (first maximum) size)
              i       (clip (mapping x) size)
              u       (Math/floor i)
              v       (clip (inc u) size)
              s       (- i u)]
          (mix (nth lut u) (nth lut v) s)))
    (fn [y x]
        (let [size    (count lut)
              mapping (linear-mapping (first minimum) (first maximum) size)
              i       (clip (mapping y) size)
              u       (Math/floor i)
              v       (clip (inc u) size)
              s       (- i u)]
          (mix ((interpolation (nth lut u) (rest minimum) (rest maximum)) x)
               ((interpolation (nth lut v) (rest minimum) (rest maximum)) x)
               s)))))

(defn interpolate
  "Linear interpolation of function"
  [fun minimum maximum size]
  (interpolation (table fun (first minimum) (first maximum) size) minimum maximum))
