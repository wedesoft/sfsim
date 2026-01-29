;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.camera
    "Camera movement math"
    (:require
      [clojure.math :refer (to-radians)]
      [fastmath.vector :refer (vec3 mult add sub cross normalize)]
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
         ::target-roll 0.0
         ::target-pitch (to-radians -10.0)
         ::target-yaw 0.0}))


(defn get-camera-pose
  "Get camera pose in surface coordinates"
  [camera-state physics-state jd-ut]
  (let [position           (physics/get-position :sfsim.physics/surface jd-ut physics-state)
        orientation        (physics/get-orientation :sfsim.physics/surface jd-ut physics-state)
        nose               (q/rotate-vector orientation (vec3 1 0 0))
        up                 (normalize position)
        right              (normalize (cross nose up))
        backward           (cross right up)
        horizon            (matrix->quaternion (cols->mat right up backward))
        yaw                (q/rotation (::yaw @camera-state) (vec3 0 1 0))
        pitch              (q/rotation (::pitch @camera-state) (vec3 1 0 0))
        camera-orientation (q/* horizon (q/* yaw pitch))
        relative-position  (q/rotate-vector camera-orientation (mult (vec3 0 0 1) ^double (::distance @camera-state)))]
    {::position (add position relative-position) ::orientation camera-orientation}))


(defn update-camera-pose
  "Update the camera position according to user input"
  [camera-state ^double dt input-state]
  (swap! camera-state update ::yaw + (* dt ^double (:sfsim.input/camera-rotate-y input-state)))
  (swap! camera-state update ::pitch + (* dt ^double (:sfsim.input/camera-rotate-x input-state))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
