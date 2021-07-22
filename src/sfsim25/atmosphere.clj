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

(defn ray-sphere
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap [^Vector centre ^double radius ^Vector origin ^Vector direction]
  (let [offset       (sub origin centre)
        discriminant (- (sqr (dot direction offset)) (- (sqr (length offset)) (sqr radius)))]
    (if (> discriminant 0)
      (let [length2 (Math/sqrt discriminant)
            middle  (- (dot direction offset))]
        (if (< middle length2)
          {:distance 0.0 :length (max 0.0 (+ middle length2))}
          {:distance (- middle length2) :length (* 2 length2)}))
      {:distance 0.0 :length 0.0})))

(set! *unchecked-math* false)
