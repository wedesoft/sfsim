;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.camera
    "Camera movement math"
    (:require
      [clojure.math :refer (to-radians pow exp)]
      [fastmath.vector :refer (vec3 mult add div cross normalize mag)]
      [fastmath.matrix :refer (cols->mat)]
      [sfsim.quaternion :as q]
      [sfsim.matrix :refer (matrix->quaternion)]
      [sfsim.physics :as physics]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-camera-state
  []
  (atom {::distance 60.0
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


(defn horizon-system
  "Determine horizon matrix for given nose or speed vector and position"
  [nose-or-speed position]
  (let [up       (normalize position)
        right    (unit-cross nose-or-speed up)
        backward (cross right up)]
    (cols->mat right up backward)))


(defn get-camera-pose
  "Get camera pose in surface coordinates"
  [camera-state physics-state jd-ut]
  (let [position           (physics/get-position :sfsim.physics/surface jd-ut physics-state)
        orientation        (physics/get-orientation :sfsim.physics/surface jd-ut physics-state)
        nose               (q/rotate-vector orientation (vec3 1 0 0))
        horizon            (matrix->quaternion (horizon-system nose position))
        yaw                (q/rotation (::yaw @camera-state) (vec3 0 1 0))
        pitch              (q/rotation (::pitch @camera-state) (vec3 1 0 0))
        roll               (q/rotation (::roll @camera-state) (vec3 0 0 1))
        camera-orientation (q/* horizon (q/* (q/* yaw pitch) roll))
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


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
