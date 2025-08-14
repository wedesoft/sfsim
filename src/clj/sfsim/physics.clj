;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [malli.core :as m]
    [fastmath.vector :refer (vec3 mag normalize mult sub cross)]
    [sfsim.jolt :as jolt]
    [sfsim.util :refer (sqr)])
  (:import [fastmath.vector Vec3]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def add-schema (m/schema [:=> [:cat :some :some] :some]))
(def scale-schema (m/schema [:=> [:cat :some :double] :some]))


(def gravitational-constant 6.67430e-11)


(defn euler
  "Euler integration method"
  [y0 dt dy + *]
  (+ y0 (* (dy y0 dt) dt)))


(defn runge-kutta
  "Runge-Kutta integration method"
  {:malli/schema [:=> [:cat :some :double [:=> [:cat :some :double] :some] add-schema scale-schema] :some]}
  [y0 dt dy + *]
  (let [dt2 (/ ^double dt 2.0)
        k1  (dy y0                0.0)
        k2  (dy (+ y0 (* k1 dt2)) dt2)
        k3  (dy (+ y0 (* k2 dt2)) dt2)
        k4  (dy (+ y0 (* k3 dt)) dt)]
    (+ y0 (* (reduce + [k1 (* k2 2.0) (* k3 2.0) k4]) (/ ^double dt 6.0)))))


(defn matching-scheme
  "Use two custom acceleration values to make semi-implicit Euler result match a ground truth after the integration step"
  [y0 dt y1 scale subtract]
  (let [delta-speed0 (scale (subtract (subtract (:position y1) (:position y0)) (scale (:speed y0) dt)) (/ 1.0 ^double dt))
        delta-speed1 (subtract (subtract (:speed y1) (:speed y0)) delta-speed0)]
    [delta-speed0 delta-speed1]))


(defn gravitation
  "Determine gravitation from planetary object"
  [^Vec3 center ^double mass]
  (fn [position]
      (let [radial-vector (sub position center)
            radius        (mag radial-vector)
            direction     (normalize radial-vector)
            gravity       (/ (* mass ^double gravitational-constant) (sqr radius))]
        (mult direction (- gravity)))))


(defn state-change
  "State change from position-dependent acceleration"
  [acceleration]
  (fn [{:keys [position speed]} _dt]
    {:position speed :speed (acceleration position)}))


(defn centrifugal-acceleration
  "Determine centrifugal acceleration in a rotating coordinate system"
  [omega position]
  (sub (cross omega (cross omega position))))


(defn coriolis-acceleration
  "Determine coriolis acceleration for a moving particle in a rotating coordinate system"
  [omega speed]
  (sub (mult (cross omega speed) 2.0)))


(defmulti set-pose (fn [domain _state _position _orientation] domain))


(defmethod set-pose ::surface
  [_domain state position orientation]
  (swap! state assoc ::position (vec3 0 0 0))  ; Position offset is zero
  (jolt/set-translation (::body @state) position)  ; Jolt handles position information to detect collisions with surface
  (jolt/set-orientation (::body @state) orientation))  ; Jolt handles orientation information


(defmethod set-pose ::orbit
  [_domain state position orientation]
  (swap! state assoc ::position position)  ; Store double precision position
  (jolt/set-translation (::body @state) (vec3 0 0 0))  ; Jolt only handles position changes
  (jolt/set-orientation (::body @state) orientation))  ; Jolt handles orientation information


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
