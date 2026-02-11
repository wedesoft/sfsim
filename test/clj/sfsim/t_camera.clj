;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-camera
  (:require
    [clojure.math :refer (sqrt exp to-radians to-degrees)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [fastmath.matrix :refer (mat3x3 eye)]
    [fastmath.vector :refer (vec3)]
    [sfsim.conftest :refer (roughly-vector roughly-matrix roughly-quaternion)]
    [sfsim.quaternion :as q]
    [sfsim.astro :as astro]
    [sfsim.jolt :as jolt]
    [sfsim.physics :refer (make-physics-state set-pose set-speed)]
    [sfsim.camera :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(jolt/jolt-init)


(def radius 6378000.0)

(def state (atom (make-camera-state)))
(def sphere (jolt/create-and-add-dynamic-body (jolt/sphere-settings 1.0 1000.0) (vec3 0 0 0) (q/->Quaternion 1 0 0 0)))
(def physics-state (atom (make-physics-state sphere)))
(swap! physics-state set-pose :sfsim.physics/surface (vec3 0 0 (- radius)) (q/->Quaternion 1 0 0 0))
(swap! physics-state set-speed :sfsim.physics/surface (vec3 0 1 0) (vec3 0 0 0))


(facts "Camera initialisation"
       (:sfsim.camera/domain @state) => :sfsim.camera/slow
       (:sfsim.camera/distance @state) => 60.0
       (:sfsim.camera/roll @state) => 0.0
       (:sfsim.camera/pitch @state) => (roughly (to-radians -10.0) 1e-6)
       (:sfsim.camera/yaw @state) => 0.0
       (:sfsim.camera/target-distance @state) => 60.0
       (:sfsim.camera/target-roll @state) => 0.0
       (:sfsim.camera/target-pitch @state) => (roughly (to-radians -10.0) 1e-6)
       (:sfsim.camera/target-yaw @state) => 0.0)


(facts "Determine horizon system"
       (horizon-system (vec3 0 0 -1) (vec3 0 100 0)) => (roughly-matrix (eye 3) 1e-6)
       (horizon-system (vec3 0 1 -1) (vec3 0 100 0)) => (roughly-matrix (eye 3) 1e-6)
       (horizon-system (vec3 1 0  0) (vec3 0 100 0)) => (roughly-matrix (mat3x3 0 0 -1, 0 1 0, 1 0 0) 1e-6)
       (horizon-system (vec3 0 1  0) (vec3 0 100 0)) => (roughly-matrix (mat3x3 0 0 1, 0 1 0, -1 0 0) 1e-6))


(tabular "Convert Euler angles to quaternion"
       (facts (euler->quaternion (to-radians ?yaw) (to-radians ?pitch) (to-radians ?roll))
              => (roughly-quaternion (q/->Quaternion ?real ?imag ?jmag ?kmag) 1e-6)
             (map to-degrees ((juxt :yaw :pitch :roll) (quaternion->euler (q/->Quaternion ?real ?imag ?jmag ?kmag))))
             => (roughly-vector [?yaw ?pitch ?roll] 1e-2))
       ?yaw ?pitch ?roll ?real      ?imag     ?jmag      ?kmag
        0.0  0.0    0.0 1.0        0.0        0.0        0.0
       90.0  0.0    0.0 (sqrt 0.5) 0.0        (sqrt 0.5) 0.0
        0.0 90.0    0.0 (sqrt 0.5) (sqrt 0.5) 0.0        0.0
        0.0  0.0   90.0 (sqrt 0.5) 0.0        0.0        (sqrt 0.5)
       10.0 20.0   30.0 0.951549   0.189308   0.038135   0.239298)


(facts "Get reference direction depending on domain"
       (get-forward-direction :sfsim.camera/slow @physics-state) => (vec3 1 0 0)
       (get-forward-direction :sfsim.camera/fast @physics-state) => (vec3 0 1 0))


(facts "Get camera pose"
       (swap! state assoc :sfsim.camera/pitch 0.0)
       (swap! state assoc :sfsim.camera/target-pitch 0.0)
       (swap! state assoc :sfsim.camera/distance 0.0)
       (:sfsim.camera/position (get-camera-pose @state @physics-state))
       => (roughly-vector (vec3 0 0 (- radius)) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose @state @physics-state))
       => (roughly-quaternion (q/->Quaternion 0.5 -0.5 -0.5 0.5) 1e-6)
       (swap! state assoc :sfsim.camera/distance 60.0)
       (:sfsim.camera/position (get-camera-pose @state @physics-state))
       => (roughly-vector (vec3 -60 0 (- radius)) 1e-3)
       (swap! physics-state set-pose :sfsim.physics/surface (vec3 0 0 radius) (q/->Quaternion 0 0 1 0))
       (:sfsim.camera/position (get-camera-pose @state @physics-state))
       => (roughly-vector (vec3 60 0 radius) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose @state @physics-state))
       => (roughly-quaternion (q/->Quaternion 0.5 0.5 0.5 0.5) 1e-6)
       (swap! state assoc :sfsim.camera/yaw (to-radians 90.0))
       (:sfsim.camera/position (get-camera-pose @state @physics-state))
       => (roughly-vector (vec3 0 60 radius) 1e-3)
       (:sfsim.camera/orientation (get-camera-pose @state @physics-state))
       => (roughly-quaternion (q/->Quaternion 0 0 (sqrt 0.5) (sqrt 0.5)) 1e-6)
       (swap! state assoc :sfsim.camera/yaw (to-radians 0.0))
       (swap! state assoc :sfsim.camera/pitch (to-radians 90.0))
       (:sfsim.camera/orientation (get-camera-pose @state @physics-state))
       => (roughly-quaternion (q/->Quaternion 0 (sqrt 0.5) (sqrt 0.5) 0) 1e-6)
       (swap! state assoc :sfsim.camera/pitch (to-radians 0.0))
       (swap! state assoc :sfsim.camera/roll (to-radians 90.0))
       (:sfsim.camera/orientation (get-camera-pose @state @physics-state))
       => (roughly-quaternion (q/->Quaternion 0 (sqrt 0.5) 0 (sqrt 0.5)) 1e-6)
       (swap! state assoc :sfsim.camera/roll (to-radians 0.0)))


(def neutral-input #:sfsim.input{:camera-rotate-x 0.0 :camera-rotate-y 0.0 :camera-rotate-z 0.0 :camera-distance-change 0.0})


(facts "Control camera pose"
       (-> (update-camera-pose @state 0.25 (assoc neutral-input :sfsim.input/camera-rotate-y 8.0)) :sfsim.camera/target-yaw) => 2.0
       (-> (update-camera-pose @state 0.25 (assoc neutral-input :sfsim.input/camera-rotate-x 8.0)) :sfsim.camera/target-pitch) => 2.0
       (-> (update-camera-pose @state 0.25 (assoc neutral-input :sfsim.input/camera-rotate-z 8.0)) :sfsim.camera/target-roll) => 2.0
       (-> (update-camera-pose @state 0.25 (assoc neutral-input :sfsim.input/camera-distance-change 8.0))
           :sfsim.camera/target-distance) => (roughly (* 60.0 (exp 2.0)) 1e-6)
       (swap! state assoc :sfsim.camera/yaw 0.0)
       (swap! state assoc :sfsim.camera/pitch 0.0)
       (swap! state assoc :sfsim.camera/roll 0.0)
       (swap! state assoc :sfsim.camera/distance 0.0)
       (swap! state assoc :sfsim.camera/target-yaw 1.0)
       (swap! state assoc :sfsim.camera/target-pitch 2.0)
       (swap! state assoc :sfsim.camera/target-roll 4.0)
       (swap! state assoc :sfsim.camera/target-distance 8.0)
       (swap! state update-camera-pose 1.0 neutral-input)
       (:sfsim.camera/yaw @state) => (roughly 0.75 1e-3)
       (:sfsim.camera/pitch @state) => (roughly 1.5 1e-3)
       (:sfsim.camera/roll @state) => (roughly 3.0 1e-3)
       (:sfsim.camera/distance @state) => (roughly 6.0 1e-3))


(facts "Change camera coordinate system"
       (swap! state assoc :sfsim.camera/yaw 0.0)
       (swap! state assoc :sfsim.camera/pitch 0.0)
       (swap! state assoc :sfsim.camera/roll 0.0)
       (swap! state assoc :sfsim.camera/target-yaw (to-radians 5.0))
       (swap! state assoc :sfsim.camera/target-pitch 0.0)
       (swap! state assoc :sfsim.camera/target-roll 0.0)
       (let [camera-orientation (:sfsim.camera/orientation (get-camera-pose @state @physics-state))]
         (swap! state set-mode :sfsim.camera/fast @physics-state)
         (:sfsim.camera/domain @state) => :sfsim.camera/fast
         (to-degrees (:sfsim.camera/yaw @state)) => (roughly 90.0 1e-3)
         (to-degrees (:sfsim.camera/target-yaw @state)) => (roughly 95.0 1e-3)
         (:sfsim.camera/orientation (get-camera-pose @state @physics-state)) => (roughly-quaternion camera-orientation 1e-6)
         (swap! state set-mode :sfsim.camera/fast @physics-state)
         (swap! state set-mode :sfsim.camera/slow @physics-state)
         (:sfsim.camera/domain @state) => :sfsim.camera/slow
         (to-degrees (:sfsim.camera/yaw @state)) => (roughly 0.0 1e-3)
         (to-degrees (:sfsim.camera/target-yaw @state)) => (roughly 5.0 1e-3)
         (swap! state set-mode :sfsim.camera/slow @physics-state)))


(jolt/remove-and-destroy-body sphere)
(jolt/jolt-destroy)


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
