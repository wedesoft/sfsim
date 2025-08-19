;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [malli.core :as m]
    [fastmath.matrix :refer (mulv inverse)]
    [fastmath.vector :refer (vec3 mag normalize mult add sub cross)]
    [sfsim.jolt :as jolt]
    [sfsim.astro :as astro]
    [sfsim.quaternion :as q]
    [sfsim.matrix :refer (matrix->quaternion)]
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
  (let [delta-speed0 (scale (subtract (subtract (::position y1) (::position y0)) (scale (::speed y0) dt)) (/ 1.0 ^double dt))
        delta-speed1 (subtract (subtract (::speed y1) (::speed y0)) delta-speed0)]
    [delta-speed0 delta-speed1]))


(defn gravitation
  "Determine gravitation from planetary object"
  [^Vec3 center ^double mass]
  (fn [position _speed]
      (let [radial-vector (sub position center)
            radius        (mag radial-vector)
            direction     (normalize radial-vector)
            gravity       (/ (* mass ^double gravitational-constant) (sqr radius))]
        (mult direction (- gravity)))))


(defn state-change
  "State change from position-dependent acceleration"
  [acceleration]
  (fn [{::keys [position speed]} _dt]
    {::position speed ::speed (acceleration position speed)}))


(defn state-add
  "Add two state hashmaps"
  [state1 state2]
  (merge-with add state1 state2))


(defn state-scale
  "Scale a state hashmap"
  [state scale]
  (into {} (for [[k v] state] [k (mult v scale)])))


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
  (swap! state assoc ::domain ::surface)
  (swap! state assoc ::position (vec3 0 0 0))  ; Position offset is zero
  (jolt/set-translation (::body @state) position)  ; Jolt handles position information to detect collisions with surface
  (jolt/set-orientation (::body @state) orientation))  ; Jolt handles orientation information


(defmethod set-pose ::orbit
  [_domain state position orientation]
  (swap! state assoc ::domain ::orbit)
  (swap! state assoc ::position position)  ; Store double precision position
  (jolt/set-translation (::body @state) (vec3 0 0 0))  ; Jolt only handles position changes
  (jolt/set-orientation (::body @state) orientation))  ; Jolt handles orientation information


(defmulti set-speed (fn [domain _state _linear-velocity _angular-velocity] domain))


(defmethod set-speed ::surface
  [_domain state linear-velocity angular-velocity]
  (swap! state assoc ::speed (vec3 0 0 0))
  (jolt/set-linear-velocity (::body @state) linear-velocity)
  (jolt/set-angular-velocity (::body @state) angular-velocity))


(defmethod set-speed ::orbit
  [_domain state linear-velocity angular-velocity]
  (swap! state assoc ::speed linear-velocity)
  (jolt/set-linear-velocity (::body @state) (vec3 0 0 0))
  (jolt/set-angular-velocity (::body @state) angular-velocity))


(defmulti set-domain (fn [target _jd-ut state] [(::domain @state) target]))


(defmethod set-domain :default
  [target _jd-ut state]
  (assert (= target (::domain @state))))


(defmethod set-domain [::surface ::orbit]
  [_target jd-ut state]
  (let [earth-to-icrs          (astro/earth-to-icrs jd-ut)
        earth-orientation      (matrix->quaternion earth-to-icrs)
        position               (jolt/get-translation (::body @state))
        orientation            (jolt/get-orientation (::body @state))
        linear-velocity        (jolt/get-linear-velocity (::body @state))
        angular-velocity       (jolt/get-angular-velocity (::body @state))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity position)]
    (set-pose ::orbit state (mulv earth-to-icrs position) (q/* earth-orientation orientation))
    (set-speed ::orbit state
               (mulv earth-to-icrs (add linear-velocity earth-local-speed))
               (mulv earth-to-icrs (add angular-velocity earth-angular-velocity)))))


(defmethod set-domain [::orbit ::surface]
  [_target jd-ut state]
  (let [icrs-to-earth          (inverse (astro/earth-to-icrs jd-ut))
        icrs-orientation       (matrix->quaternion icrs-to-earth)
        position               (mulv icrs-to-earth (::position @state))
        orientation            (jolt/get-orientation (::body @state))
        linear-velocity        (mulv icrs-to-earth (::speed @state))
        angular-velocity       (mulv icrs-to-earth (jolt/get-angular-velocity (::body @state)))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity position)]
    (set-pose ::surface state position (q/* icrs-orientation orientation))
    (set-speed ::surface state (sub linear-velocity earth-local-speed) (sub angular-velocity earth-angular-velocity))))


(defmulti update-state (fn [state _dt _acceleration] (::domain @state)))


(defmethod update-state ::surface
  [state dt gravitation]
  (let [body         (::body @state)
        mass         (jolt/get-mass body)
        initial      {::position (jolt/get-translation body) ::speed (jolt/get-linear-velocity body)}
        omega        (vec3 0 0 astro/earth-rotation-speed)
        acceleration (fn [position speed] (reduce add [(gravitation position speed)
                                                       (centrifugal-acceleration omega position)
                                                       (coriolis-acceleration omega speed)]))
        final        (runge-kutta initial dt (state-change acceleration) state-add state-scale)
        [dv1 dv2]    (matching-scheme initial dt final mult sub)]
    (jolt/set-gravity (vec3 0 0 0))
    (jolt/add-impulse body (mult dv1 mass))
    (jolt/update-system dt 1)
    (jolt/add-impulse body (mult dv2 mass))))


(defmethod update-state ::orbit
  [state dt gravitation]
  (let [body      (::body @state)
        mass      (jolt/get-mass body)
        initial   {::position (::position @state) ::speed (::speed @state)}
        final     (runge-kutta initial dt (state-change gravitation) state-add state-scale)
        [dv1 dv2] (matching-scheme initial dt final mult sub)]
    (jolt/set-gravity (vec3 0 0 0))
    (jolt/add-impulse body (mult dv1 mass))
    (jolt/update-system dt 1)
    (jolt/add-impulse body (mult dv2 mass))
    (swap! state update ::position #(add % (jolt/get-translation body)))
    (swap! state update ::speed #(add % (jolt/get-linear-velocity body)))
    (jolt/set-translation body (vec3 0 0 0))
    (jolt/set-linear-velocity body (vec3 0 0 0))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
