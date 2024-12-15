(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [malli.core :as m]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(def add-schema (m/schema [:=> [:cat :some :some] :some]))
(def scale-schema (m/schema [:=> [:cat :double :some] :some]))


(defn runge-kutta
  "Runge-Kutta integration method"
  {:malli/schema [:=> [:cat :some :double [:=> [:cat :some :double] :some] add-schema scale-schema] :some]}
  [y0 dt dy + *]
  (let [dt2 (/ dt 2.0)
        k1  (dy y0                0.0)
        k2  (dy (+ y0 (* dt2 k1)) dt2)
        k3  (dy (+ y0 (* dt2 k2)) dt2)
        k4  (dy (+ y0 (* dt  k3)) dt)]
    (+ y0 (* (/ dt 6.0) (reduce + [k1 (* 2.0 k2) (* 2.0 k3) k4])))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
