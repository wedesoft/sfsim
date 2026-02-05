;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.physics
  "Physics related functions except for Jolt bindings"
  (:require
    [clojure.math :refer (cos sin to-radians)]
    [fastmath.matrix :refer (mulv inverse)]
    [fastmath.vector :refer (vec3 mag normalize mult add sub cross)]
    [malli.core :as m]
    [sfsim.jolt :as jolt]
    [sfsim.astro :as astro]
    [sfsim.quaternion :as q]
    [sfsim.matrix :refer (matrix->quaternion)]
    [sfsim.util :refer (sqr clamp)])
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
  "Use two custom speed changes to make semi-implicit Euler result match a ground truth after the integration step"
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


(defn make-physics-state
  [body]
  {::body body
   ::display-speed 0.0
   ::throttle 0.0
   ::air-brake 0.0
   ::gear 1.0})


(defn set-control-inputs
  [state inputs ^double dt]
  (-> state
      (assoc ::throttle (:sfsim.input/throttle inputs))
      (update ::air-brake (fn [^double x] (clamp (+ x (* (if (:sfsim.input/air-brake inputs) 2.0 -2.0) dt)) 0.0 1.0)))
      (update ::gear (fn [^double x] (clamp (+ x (* (if (:sfsim.input/gear-down inputs) 0.5 -0.5) dt)) 0.0 1.0)))))


(defmulti set-pose (fn [_state domain _position _orientation] domain))


(defmethod set-pose ::surface
  [state _domain position orientation]
  (jolt/set-translation (::body state) position)  ; Jolt handles position information to detect collisions with surface
  (jolt/set-orientation (::body state) orientation)  ; Jolt handles orientation information 
  (-> state
      (assoc ::domain ::surface)
      (assoc ::position (vec3 0 0 0))))  ; Position offset is zero 


(defmethod set-pose ::orbit
  [state _domain position orientation]
  (jolt/set-translation (::body state) (vec3 0 0 0))  ; Jolt only handles position changes
  (jolt/set-orientation (::body state) orientation)  ; Jolt handles orientation information 
  (-> state
      (assoc ::domain ::orbit)
      (assoc ::position position)))  ; Store double precision position 


(defmulti get-position (fn [domain _jd-ut state] [(::domain state) domain]))


(defmethod get-position [::surface ::surface]
  [_domain _jd-ut state]
  (jolt/get-translation (::body state)))


(defmethod get-position [::surface ::orbit]
  [_domain jd-ut state]
  (let [earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (mulv earth-to-icrs (jolt/get-translation (::body state)))))


(defmethod get-position [::orbit ::surface]
  [_domain jd-ut state]
  (let [icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (mulv icrs-to-earth (::position state))))


(defmethod get-position [::orbit ::orbit]
  [_domain _jd-ut state]
  (::position state))


(defmulti get-orientation (fn [domain _jd-ut state] [(::domain state) domain]))


(defmethod get-orientation [::surface ::surface]
  [_domain _jd-ut state]
  (jolt/get-orientation (::body state)))


(defmethod get-orientation [::surface ::orbit]
  [_domain jd-ut state]
  (let [earth-to-icrs     (astro/earth-to-icrs jd-ut)
        earth-orientation (matrix->quaternion earth-to-icrs)]
    (q/* earth-orientation (jolt/get-orientation (::body state)))))


(defmethod get-orientation [::orbit ::surface]
  [_domain jd-ut state]
  (let [icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))
        icrs-orientation (matrix->quaternion icrs-to-earth)]
    (q/* icrs-orientation (jolt/get-orientation (::body state)))))


(defmethod get-orientation [::orbit ::orbit]
  [_domain _jd-ut state]
  (jolt/get-orientation (::body state)))


(defmulti set-speed (fn [_state domain _linear-velocity _angular-velocity] domain))


(defmethod set-speed ::surface
  [state _domain linear-velocity angular-velocity]
  (jolt/set-linear-velocity (::body state) linear-velocity)
  (jolt/set-angular-velocity (::body state) angular-velocity)
  (assoc state ::speed (vec3 0 0 0)))


(defmethod set-speed ::orbit
  [state _domain linear-velocity angular-velocity]
  (jolt/set-linear-velocity (::body state) (vec3 0 0 0))
  (jolt/set-angular-velocity (::body state) angular-velocity)
  (assoc state ::speed linear-velocity))


(defmulti get-linear-speed (fn [domain _jd-ut state] [(::domain state) domain]))


(defmethod get-linear-speed [::surface ::surface]
  [_domain _jd-ut state]
  (jolt/get-linear-velocity (::body state)))


(defmethod get-linear-speed [::surface ::orbit]
  [_domain jd-ut state]
  (let [position               (jolt/get-translation (::body state))
        linear-velocity        (jolt/get-linear-velocity (::body state))
        earth-to-icrs          (astro/earth-to-icrs jd-ut)
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity position)]
    (mulv earth-to-icrs (add linear-velocity earth-local-speed))))


(defmethod get-linear-speed [::orbit ::surface]
  [_domain jd-ut state]
  (let [icrs-to-earth          (inverse (astro/earth-to-icrs jd-ut))
        linear-velocity        (mulv icrs-to-earth (::speed state))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-local-speed      (cross earth-angular-velocity (get-position ::surface jd-ut state))]
    (sub linear-velocity earth-local-speed)))


(defmethod get-linear-speed [::orbit ::orbit]
  [_domain _jd-ut state]
  (::speed state))


(defmulti get-angular-speed (fn [domain _jd-ut state] [(::domain state) domain]))


(defmethod get-angular-speed [::surface ::surface]
  [_domain _jd-ut state]
  (jolt/get-angular-velocity (::body state)))


(defmethod get-angular-speed [::surface ::orbit]
  [_domain jd-ut state]
  (let [angular-velocity       (jolt/get-angular-velocity (::body state))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)
        earth-to-icrs          (astro/earth-to-icrs jd-ut)]
    (mulv earth-to-icrs (add angular-velocity earth-angular-velocity))))


(defmethod get-angular-speed [::orbit ::surface]
  [_domain jd-ut state]
  (let [icrs-to-earth          (inverse (astro/earth-to-icrs jd-ut))
        angular-velocity       (mulv icrs-to-earth (jolt/get-angular-velocity (::body state)))
        earth-angular-velocity (vec3 0 0 astro/earth-rotation-speed)]
    (sub angular-velocity earth-angular-velocity)))


(defmethod get-angular-speed [::orbit ::orbit]
  [_domain _jd-ut state]
  (jolt/get-angular-velocity (::body state)))


(defmulti set-domain (fn [state target _jd-ut] [(::domain state) target]))


(defmethod set-domain :default
  [state target _jd-ut]
  (assert (= target (::domain state)))
  state)


(defmethod set-domain [::surface ::orbit]
  [state _target jd-ut]
  (let [position      (get-position ::orbit jd-ut state)
        orientation   (get-orientation ::orbit jd-ut state)
        linear-speed  (get-linear-speed ::orbit jd-ut state)
        angular-speed (get-angular-speed ::orbit jd-ut state)]
    (-> state
        (set-pose ::orbit position orientation)
        (set-speed ::orbit linear-speed angular-speed))))


(defmethod set-domain [::orbit ::surface]
  [state _target jd-ut]
  (let [position         (get-position ::surface jd-ut state)
        orientation      (get-orientation ::surface jd-ut state)
        linear-velocity  (get-linear-speed ::surface jd-ut state)
        angular-velocity (get-angular-speed ::surface jd-ut state)]
    (-> state
        (set-pose ::surface position orientation)
        (set-speed ::surface linear-velocity angular-velocity))))


(defmulti update-state (fn [state _dt _acceleration] (::domain state)))


(defmethod update-state ::surface
  [state dt gravitation]
  (let [body         (::body state)
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
    (let [display-speed (jolt/get-linear-velocity body)]
      (jolt/add-impulse body (mult dv2 mass))
      (assoc state ::display-speed display-speed))))


(defmethod update-state ::orbit
  [state dt gravitation]
  (let [body      (::body state)
        mass      (jolt/get-mass body)
        position  (::position state)
        speed     (::speed state)
        initial   {::position position ::speed speed}
        final     (runge-kutta initial dt (state-change gravitation) state-add state-scale)
        [dv1 dv2] (matching-scheme initial dt final mult sub)]
    (jolt/set-gravity (vec3 0 0 0))
    (jolt/add-impulse body (mult dv1 mass))
    (jolt/update-system dt 1)
    (let [display-speed (jolt/get-linear-velocity body)]
      (jolt/add-impulse body (mult dv2 mass))
      (let [delta-position (add (mult speed dt) (jolt/get-translation body))
            delta-speed (jolt/get-linear-velocity body)]
        (jolt/set-translation body (vec3 0 0 0))
        (jolt/set-linear-velocity body (vec3 0 0 0))
        (-> state
            (assoc ::display-speed display-speed)
            (update ::position add delta-position)
            (update ::speed add delta-speed))))))


(defmulti add-force (fn [domain _jd-ut state _force_] [domain (::domain state)]))


(defmethod add-force [::surface ::surface]
  [_domain _jd-ut state force_]
  (jolt/add-force (::body state) force_))


(defmethod add-force [::orbit ::surface]
  [_domain jd-ut state force_]
  (let [icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (jolt/add-force (::body state) (mulv icrs-to-earth force_))))


(defmethod add-force [::surface ::orbit]
  [_domain jd-ut state force_]
  (let [earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (jolt/add-force (::body state) (mulv earth-to-icrs force_))))


(defmethod add-force [::orbit ::orbit]
  [_domain _jd-ut state force_]
  (jolt/add-force (::body state) force_))


(defmulti add-torque (fn [domain _jd-ut state _torque_] [domain (::domain state)]))


(defmethod add-torque [::surface ::surface]
  [_domain _jd-ut state torque_]
  (jolt/add-torque (::body state) torque_))


(defmethod add-torque [::orbit ::surface]
  [_domain jd-ut state torque_]
  (let [icrs-to-earth (inverse (astro/earth-to-icrs jd-ut))]
    (jolt/add-torque (::body state) (mulv icrs-to-earth torque_))))


(defmethod add-torque [::surface ::orbit]
  [_domain jd-ut state torque_]
  (let [earth-to-icrs (astro/earth-to-icrs jd-ut)]
    (jolt/add-torque (::body state) (mulv earth-to-icrs torque_))))


(defmethod add-torque [::orbit ::orbit]
  [_domain _jd-ut state torque_]
  (jolt/add-torque (::body state) torque_))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
