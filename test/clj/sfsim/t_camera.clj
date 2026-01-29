;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-camera
  (:require
    [clojure.math :refer (cos sin sqrt to-radians)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [fastmath.vector :refer (vec3 mult sub)]
    [sfsim.conftest :refer (roughly-vector roughly-quaternion)]
    [sfsim.quaternion :as q]
    [sfsim.astro :as astro]
    [sfsim.jolt :as jolt]
    [sfsim.physics :refer (make-physics-state get-position get-orientation set-pose)]
    [sfsim.camera :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(jolt/jolt-init)


(def radius 6378000.0)

(defn position-from-lon-lat ;; TODO: remove this
  [longitude latitude height]
  (let [point (vec3 (* (cos longitude) (cos latitude)) (* (sin longitude) (cos latitude)) (sin latitude))]
    (mult point (+ radius height))))


(defn orientation-from-lon-lat ;; TODO: remove this
  [longitude latitude]
  (let [radius-vector (position-from-lon-lat longitude latitude 1.0)]
    (q/vector-to-vector-rotation (vec3 0 0 1) (sub radius-vector))))


(def state (make-camera-state))
(def sphere (jolt/create-and-add-dynamic-body (jolt/sphere-settings 1.0 1000.0) (vec3 0 0 0) (q/->Quaternion 1 0 0 0)))
(def physics-state (make-physics-state sphere))


(facts "Camera initialisation"
       (:sfsim.camera/distance @state) => 60.0
       (:sfsim.camera/roll @state) => 0.0
       (:sfsim.camera/pitch @state) => (roughly (to-radians -10.0) 1e-6)
       (:sfsim.camera/yaw @state) => 0.0
       (:sfsim.camera/target-roll @state) => 0.0
       (:sfsim.camera/target-pitch @state) => (roughly (to-radians -10.0) 1e-6)
       (:sfsim.camera/target-yaw @state) => 0.0)

(facts "Get camera pose"
       (swap! state assoc :sfsim.camera/pitch 0.0)
       (swap! state assoc :sfsim.camera/target-pitch 0.0)
       (swap! state assoc :sfsim.camera/distance 0.0)
       (set-pose :sfsim.physics/surface physics-state (vec3 0 0 (- radius)) (q/->Quaternion 1 0 0 0))
       (:sfsim.camera/position (get-camera-pose state physics-state astro/T0)) => (roughly-vector (vec3 0 0 (- radius)) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose state physics-state astro/T0)) => (roughly-quaternion (q/->Quaternion 0.5 -0.5 -0.5 0.5) 1e-6)
       (swap! state assoc :sfsim.camera/distance 60.0)
       (:sfsim.camera/position (get-camera-pose state physics-state astro/T0)) => (roughly-vector (vec3 -60 0 (- radius)) 1e-3)
       (set-pose :sfsim.physics/surface physics-state (vec3 0 0 radius) (q/->Quaternion 0 0 1 0))
       (:sfsim.camera/position (get-camera-pose state physics-state astro/T0)) => (roughly-vector (vec3 60 0 radius) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose state physics-state astro/T0)) => (roughly-quaternion (q/->Quaternion 0.5 0.5 0.5 0.5) 1e-6)
       (swap! state assoc :sfsim.camera/yaw (to-radians 90.0))
       (:sfsim.camera/position (get-camera-pose state physics-state astro/T0)) => (roughly-vector (vec3 0 60 radius) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose state physics-state astro/T0)) => (roughly-quaternion (q/->Quaternion 0 0 (sqrt 0.5) (sqrt 0.5)) 1e-6)
       (swap! state assoc :sfsim.camera/yaw (to-radians 0.0))
       (swap! state assoc :sfsim.camera/pitch (to-radians 90.0))
       (:sfsim.camera/orientation (get-camera-pose state physics-state astro/T0)) => (roughly-quaternion (q/->Quaternion 0 (sqrt 0.5) (sqrt 0.5) 0) 1e-6)
       (swap! state assoc :sfsim.camera/pitch (to-radians 0.0)))


(def neutral-input #:sfsim.input{:camera-rotate-x 0.0 :camera-rotate-y 0.0 :camera-rotate-z 0.0})

(facts "Control camera pose"
       (update-camera-pose state 0.25 (assoc neutral-input :sfsim.input/camera-rotate-y 8.0))
       (:sfsim.camera/yaw @state) => 2.0
       (update-camera-pose state 0.25 (assoc neutral-input :sfsim.input/camera-rotate-x 8.0))
       (:sfsim.camera/pitch @state) => 2.0
       )


(jolt/remove-and-destroy-body sphere)
(jolt/jolt-destroy)


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
