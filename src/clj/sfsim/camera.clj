;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.camera
    "Camera movement math"
    (:require
      [fastmath.vector :refer (vec3 mult add)]
      [sfsim.physics :as physics]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn make-camera-state
  []
  (atom {::distance 60.0
         ::roll 0.0
         ::pitch 10.0
         ::yaw 0.0
         ::target-roll 0.0
         ::target-pitch 10.0
         ::target-yaw 0.0}))


(defn get-camera-pose
  "Get camera pose in surface coordinates"
  [camera-state physics-state jd-ut]
  (let [position           (physics/get-position :sfsim.physics/surface jd-ut physics-state)
        orientation        (physics/get-orientation :sfsim.physics/surface jd-ut physics-state)
        forward            (vec3 1 0 0)
        relative-position  (mult forward (- (::distance @camera-state)))]
    {::position (add position relative-position)}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
