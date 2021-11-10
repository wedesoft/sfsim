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

(defn sample-function
  "Create 1-dimensional lookup table"
  [fun inverse-mapping size]
  (vec (map (comp fun first inverse-mapping) (range size))))

(defn make-lookup-table
  "Create 1-dimensional lookup table using linear sampling"
  [fun minimum maximum size]
  (if (empty? size)
    (fun)
    (sample-function #(make-lookup-table (partial fun %) (rest minimum) (rest maximum) (rest size))
                     (inverse-linear-mapping [(first minimum)] [(first maximum)] [(first size)])
                     (first size))))

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
  (if (empty? minimum)
    (fn [] lut)
    (fn [x & coords]
        (let [size    (count lut)
              mapping (linear-mapping [(first minimum)] [(first maximum)] [size])
              i       (clip (first (mapping x)) size)
              u       (Math/floor i)
              v       (clip (inc u) size)
              s       (- i u)]
          (mix (apply (interpolation (nth lut u) (rest minimum) (rest maximum)) coords)
               (apply (interpolation (nth lut v) (rest minimum) (rest maximum)) coords)
               s)))))

(defn interpolate
  "Linear interpolation of function"
  [fun minimum maximum size]
  (interpolation (make-lookup-table fun minimum maximum size) minimum maximum))

(set! *unchecked-math* false)
