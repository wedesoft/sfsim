(ns sfsim25.atmosphere
  "Functions for computing the atmosphere"
  (:require [clojure.core.matrix :refer :all]
            [sfsim25.util :refer :all])
  (:import [mikera.vectorz Vector]))

(set! *unchecked-math* true)

(defn air-density
  "Compute pressure of atmosphere at specified height"
  ^double [^double height ^double base ^double scale]
  (* base (Math/exp (- (/ height scale)))))

(defn density-table
  "Create a lookup table for air density values"
  ^floats [^double base ^long size ^double height ^double scale]
  (float-array (map #(air-density (* % (/ height (dec size))) base scale) (range size))))

(defn ray-sphere
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap [^Vector centre ^double radius ^Vector origin ^Vector direction]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)
        discriminant  (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr radius))))]
    (if (> discriminant 0)
      (let [length2 (/ (Math/sqrt discriminant) direction-sqr)
            middle  (- (/ (dot direction offset) direction-sqr))]
        (if (< middle length2)
          {:distance 0.0 :length (max 0.0 (+ middle length2))}
          {:distance (- middle length2) :length (* 2 length2)}))
      {:distance 0.0 :length 0.0})))

(defn ray-ellipsoid
  "Compute intersection of line with ellipsoid"
  [centre radius1 radius2 origin direction]
  (let [factor (/ radius1 radius2)
        scale  (fn [v] (matrix [(mget v 0) (mget v 1) (* factor (mget v 2))]))]
  (ray-sphere (scale centre) radius1 (scale origin) (scale direction))))

(set! *unchecked-math* false)
