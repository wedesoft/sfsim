;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.camera
    "Camera movement math"
    (:require
      [clojure.math :refer (to-radians pow exp atan2 hypot)]
      [fastmath.vector :refer (vec3 mult add div cross normalize mag dot)]
      [fastmath.matrix :refer (cols->mat col)]
      [sfsim.quaternion :as q]
      [sfsim.matrix :refer (matrix->quaternion quaternion->matrix)]
      [sfsim.physics :as physics]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-camera-state
  []
  (atom {::domain ::slow
         ::distance 60.0
         ::roll 0.0
         ::pitch (to-radians -10.0)
         ::yaw 0.0
         ::target-distance 60.0
         ::target-roll 0.0
         ::target-pitch (to-radians -10.0)
         ::target-yaw 0.0}))


(defn unit-cross
  "Determine a vector which is orthogonal to both a and b"
  [a b]
  (let [c      (cross a b)
        length (mag c)]
    (if (zero? length)
      (q/orthogonal b)
      (div c length))))


(defn euler->quaternion
  "Convert Euler angles to quaternion for camera system"
  [yaw pitch roll]
  (let [rotation-y (q/rotation yaw (vec3 0 1 0))
        rotation-x (q/rotation pitch (vec3 1 0 0))
        rotation-z (q/rotation roll (vec3 0 0 1))]
    (q/* (q/* rotation-y rotation-x) rotation-z)))


(defn quaternion->euler
  "Convert quaternion to Euler angles"
  [quaternion]
  (let [matrix    (quaternion->matrix quaternion)
        x-axis    (col matrix 0)
        z-axis    (col matrix 2)
        yaw       (atan2 (z-axis 0) (z-axis 2))
        pitch     (atan2 (- ^double (z-axis 1)) (hypot (z-axis 0) (z-axis 2)))
        yaw-pitch (euler->quaternion yaw pitch 0.0)
        x-prime   (q/rotate-vector yaw-pitch (vec3 1 0 0))
        y-prime   (q/rotate-vector yaw-pitch (vec3 0 1 0))
        roll      (atan2 (dot y-prime x-axis) (dot x-prime x-axis))]
    {:yaw yaw :pitch pitch :roll roll}))


(defmulti get-forward-direction (fn [domain _jd-ut _physics-state] domain))


(defmethod get-forward-direction ::slow
  [_domain jd-ut physics-state]
  (let [orientation (physics/get-orientation :sfsim.physics/surface jd-ut physics-state)]
    (q/rotate-vector orientation (vec3 1 0 0))))


(defmethod get-forward-direction ::fast
  [_domain jd-ut physics-state]
  (physics/get-linear-speed :sfsim.physics/surface jd-ut physics-state))


(defn horizon-system
  "Determine horizon-aligned camera matrix for given nose or speed vector and position"
  [nose-or-speed position]
  (let [up       (normalize position)
        right    (unit-cross nose-or-speed up)
        backward (cross right up)]
    (cols->mat right up backward)))


(defn horizon-for-domain
  "Determine horizon-align4ed camera matrix for given camera domain and physics state"
  [physics-state jd-ut domain]
  (let [position      (physics/get-position :sfsim.physics/surface jd-ut physics-state)
        nose-or-speed (get-forward-direction domain jd-ut physics-state)]
    (horizon-system nose-or-speed position)))


(defn camera->horizon
  "Quaternion for converting camera vectors to horizon vectors"
  [camera-state]
  (let [yaw   (::yaw @camera-state)
        pitch (::pitch @camera-state)
        roll  (::roll @camera-state)]
    (euler->quaternion yaw pitch roll)))


(defn get-camera-pose
  "Get camera pose in surface coordinates"
  [camera-state physics-state jd-ut]
  (let [position           (physics/get-position :sfsim.physics/surface jd-ut physics-state)
        domain             (::domain @camera-state)
        horizon            (matrix->quaternion (horizon-for-domain physics-state jd-ut domain))
        camera-orientation (q/* horizon (camera->horizon camera-state))
        relative-position  (q/rotate-vector camera-orientation (mult (vec3 0 0 1) ^double (::distance @camera-state)))]
    {::position (add position relative-position) ::orientation camera-orientation}))


(defn update-camera-pose
  "Update the camera position according to user input"
  [camera-state ^double dt input-state]
  (let [weight-previous (pow 0.25 dt)
        mix             (fn [prev target] (+ (* ^double prev weight-previous) (* ^double target (- 1.0 weight-previous))))]
    (swap! camera-state update ::target-yaw + (* dt ^double (:sfsim.input/camera-rotate-y input-state)))
    (swap! camera-state update ::target-pitch + (* dt ^double (:sfsim.input/camera-rotate-x input-state)))
    (swap! camera-state update ::target-roll + (* dt ^double (:sfsim.input/camera-rotate-z input-state)))
    (swap! camera-state update ::target-distance * (exp (* dt ^double (:sfsim.input/camera-distance-change input-state))))
    (swap! camera-state update ::yaw mix (::target-yaw @camera-state))
    (swap! camera-state update ::pitch mix (::target-pitch @camera-state))
    (swap! camera-state update ::roll mix (::target-roll @camera-state))
    (swap! camera-state update ::distance mix (::target-distance @camera-state))))


(defn horizons-angle
  "Determine angle between forward direction of horizon systems"
  [physics-state jd-ut]
  (let [slow-horizon (horizon-for-domain physics-state jd-ut ::slow)
        fast-horizon (horizon-for-domain physics-state jd-ut ::fast)]
    (atan2 (dot (col slow-horizon 0) (col fast-horizon 2)) (dot (col slow-horizon 2) (col fast-horizon 2)))))


(defmulti set-mode (fn [target state _jd-ut _physics-state] [(::domain @state) target]))


(defmethod set-mode :default
  [target state _jd-ut _physics-state]
  (assert (= target (::domain @state))))


(defmethod set-mode [::slow ::fast]
  [_target state jd-ut physics-state]
  (let [delta-angle (horizons-angle physics-state jd-ut)]
    (swap! state update ::yaw - delta-angle)
    (swap! state update ::target-yaw - delta-angle)
    (swap! state assoc ::domain ::fast)))


(defmethod set-mode [::fast ::slow]
  [_target state jd-ut physics-state]
  (let [delta-angle (horizons-angle physics-state jd-ut)]
    (swap! state update ::yaw + delta-angle)
    (swap! state update ::target-yaw + delta-angle)
    (swap! state assoc ::domain ::slow)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
