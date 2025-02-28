(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (cos sin)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn basic-drag
  "Basic cosine shaped drag function"
  {:malli/schema [:=> [:cat :double :double] [:=> [:cat :double] :double]]}
  [min-drag max-drag]
  (fn [angle-of-attack] (+ min-drag (* 0.5 (- max-drag min-drag) (- 1 (cos (* 2 angle-of-attack)))))))


(defn basic-lift
  "Basic sinus shaped lift function"
  {:malli/schema [:=> [:cat :double] [:=> [:cat :double] :double]]}
  [max-lift]
  (fn [angle-of-attack] (* max-lift (sin (* 2 angle-of-attack)))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
