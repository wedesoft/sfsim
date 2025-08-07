;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [malli.core :as m]
    [fastmath.vector :refer (vec3 mag normalize mult)]
    [sfsim.util :refer (sqr)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def add-schema (m/schema [:=> [:cat :some :some] :some]))
(def scale-schema (m/schema [:=> [:cat :double :some] :some]))


(def gravitational-constant 6.67430e-11)


(defn euler
  "Euler integration method"
  [y0 dt dy + *]
  (+ y0 (* dt (dy y0 dt))))


(defn runge-kutta
  "Runge-Kutta integration method"
  {:malli/schema [:=> [:cat :some :double [:=> [:cat :some :double] :some] add-schema scale-schema] :some]}
  [y0 dt dy + *]
  (let [dt2 (/ ^double dt 2.0)
        k1  (dy y0                0.0)
        k2  (dy (+ y0 (* dt2 k1)) dt2)
        k3  (dy (+ y0 (* dt2 k2)) dt2)
        k4  (dy (+ y0 (* dt  k3)) dt)]
    (+ y0 (* (/ ^double dt 6.0) (reduce + [k1 (* 2.0 k2) (* 2.0 k3) k4])))))


(defn matching-scheme
  "Use two custom acceleration values to make semi-implicit Euler result match a ground truth after two steps"
  [y0 dt y1 scale subtract]
  (let [delta-speed0 (scale (/ 1.0 ^double dt) (subtract (subtract (:position y1) (:position y0)) (scale dt (:speed y0))))
        delta-speed1 (subtract (subtract (:speed y1) (:speed y0)) delta-speed0)]
    [delta-speed0 delta-speed1]))


(defn gravitation
  "Determine gravitation from planetary object"
  [^double mass]
  (fn [position]
      (let [radius    (mag position)
            direction (normalize position)
            gravity   (/ (* mass ^double gravitational-constant) (sqr radius))]
        (mult direction (- gravity)))))


(defn state-change
  "State change from position-dependent acceleration"
  [acceleration]
  (fn [{:keys [position speed]} _dt]
    {:position speed :speed (acceleration position)}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
