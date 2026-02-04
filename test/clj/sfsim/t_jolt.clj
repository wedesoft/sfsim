;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-jolt
  (:require
    [clojure.math :refer (PI)]
    [fastmath.matrix :refer (mat3x3 mat4x4)]
    [fastmath.vector :refer (vec3)]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-matrix roughly-vector)]
    [sfsim.matrix :as matrix]
    [sfsim.jolt :refer :all]
    [sfsim.quaternion :as q]))


(jolt-init)


(facts "Object layers"
       NON-MOVING-LAYER => 0
       MOVING-LAYER => 1
       NUM-LAYERS => 2)


(def sphere (create-and-add-dynamic-body (sphere-settings 0.5 1000.0) (vec3 2 3 5) (q/->Quaternion 0 1 0 0)))
(set-angular-velocity sphere (vec3 (/ PI 2) 0 0))


(facts "Get position vector, rotation matrix, and velocities of sphere body"
       (get-translation sphere) => (vec3 2 3 5)
       (get-rotation sphere) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1)
       (get-orientation sphere) => (q/->Quaternion 0 1 0 0)
       (get-linear-velocity sphere) => (vec3 0 0 0)
       (get-angular-velocity sphere) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6))


(facts "Check position and speed after time step"
       (set-gravity (vec3 0 -1 0))
       (update-system 1.0 1)
       (get-translation sphere) => (vec3 2 2 5)
       (get-linear-velocity sphere) => (vec3 0 -1 0)
       (get-rotation sphere) => (roughly-matrix (mat3x3 1 0 0, 0 0 1, 0 -1 0) 1e-6)
       (get-angular-velocity sphere) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6))


(facts "Test setting position of sphere"
       (set-translation sphere (vec3 1 2 3))
       (get-translation sphere) => (vec3 1 2 3)
       (set-translation sphere (vec3 2 3 5))
       (get-translation sphere) => (vec3 2 3 5))


(facts "Test setting orientation of sphere"
       (set-orientation sphere (q/->Quaternion 0 1 0 0))
       (get-orientation sphere) => (q/->Quaternion 0 1 0 0)
       (set-orientation sphere (q/->Quaternion 1 0 0 0))
       (get-orientation sphere) => (q/->Quaternion 1 0 0 0))


(def sphere-mass (get-mass sphere))


(facts "Test applying force to sphere for a single physics update"
       (set-gravity (vec3 0 0 0))
       (set-translation sphere (vec3 0 0 0))
       (set-linear-velocity sphere (vec3 0 0 0))
       (add-force sphere (vec3 sphere-mass 0 0))
       (update-system 1.0 2)
       (get-linear-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-7)
       (get-translation sphere) => (roughly-vector (vec3 0.75 0 0) 1e-7)
       (update-system 1.0 2)
       (get-linear-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-7)
       (get-translation sphere) => (roughly-vector (vec3 1.75 0 0) 1e-7))


(facts "Test applying impulse to sphere"
       (set-linear-velocity sphere (vec3 0 0 0))
       (add-impulse sphere (vec3 sphere-mass 0 0))
       (get-linear-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-7))


(def sphere-inertia (* (/ 2 5) sphere-mass 0.5 0.5))


(facts "Test applying angular impulse to sphere"
       (set-angular-velocity sphere (vec3 0 0 0))
       (add-angular-impulse sphere (vec3 sphere-inertia 0 0))
       (get-angular-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-7))


(facts "Test applying torque to sphere for a single physics update"
       (set-angular-velocity sphere (vec3 0 0 0))
       (add-torque sphere (vec3 sphere-inertia 0 0))
       (update-system 1.0 2)
       (get-angular-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-6)
       (update-system 1.0 2)
       (get-angular-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-6))


(remove-and-destroy-body sphere)


(facts "Get position vector, rotation matrix, and velocities of box body"
       (let [box (create-and-add-dynamic-body (box-settings (vec3 0.2 0.3 0.5) 1000.0) (vec3 2 3 5) (q/->Quaternion 0 1 0 0))]
         (set-angular-velocity box (vec3 (/ PI 2) 0 0))
         (get-translation box) => (vec3 2 3 5)
         (get-rotation box) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1)
         (get-linear-velocity box) => (vec3 0 0 0)
         (get-angular-velocity box) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6)
         (remove-and-destroy-body box)))


(facts "Get mass and inertial matrix of body"
       (let [box (create-and-add-dynamic-body (box-settings (vec3 0.25 0.25 0.25) 1000.0) (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
             inertia (* (/ 125 12) (+ (* 0.5 0.5) (* 0.5 0.5)))]
         (get-mass box) => 125.0
         (get-inertia box) => (roughly-matrix (mat3x3 inertia 0 0, 0 inertia 0, 0 0 inertia) 1e-6)
         (remove-and-destroy-body box)))


(fact "Test restitution of sphere"
      (let [sphere1 (create-and-add-dynamic-body (sphere-settings 1.0 1000.0) (vec3 0.0  2.0 0.0) (q/->Quaternion 1 0 0 0))
            sphere2 (create-and-add-dynamic-body (sphere-settings 1.0 1000.0) (vec3 0.0 -2.0 0.0) (q/->Quaternion 1 0 0 0))]
        (set-linear-velocity sphere1 (vec3 0 -1 0))
        (set-linear-velocity sphere2 (vec3 0  0 0))
        (set-gravity (vec3 0 0 0))
        (set-friction sphere1 0.2)
        (set-restitution sphere1 1.0)
        (set-friction sphere2 0.2)
        (set-restitution sphere2 1.0)
        (optimize-broad-phase)
        (dotimes [i 40] (update-system 0.1 1))
        (get-linear-velocity sphere1) => (roughly-vector (vec3 0  0 0) 1e-6)
        (get-linear-velocity sphere2) => (roughly-vector (vec3 0 -1 0) 1e-6)
        (remove-and-destroy-body sphere1)
        (remove-and-destroy-body sphere2)))


(fact "Mesh prevents object from dropping"
      (let [mesh   (create-and-add-static-body
                     (mesh-settings #:sfsim.quadtree{:vertices [(vec3 -1 0 -1) (vec3 1 0 -1) (vec3 1 0 1) (vec3 -1 0 1)]
                                                     :triangles [[0 3 1] [1 3 2]]}
                                    1e+4)
                     (vec3 0 -1 0) (q/->Quaternion 1 0 0 0))
            sphere (create-and-add-dynamic-body (sphere-settings 1.0 1000.0) (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))]
        (set-friction mesh 0.5)
        (set-restitution mesh 0.2)
        (set-friction sphere 0.5)
        (set-restitution sphere 0.2)
        (optimize-broad-phase)
        (set-gravity (vec3 0 -1 0))
        (dotimes [i 10] (update-system 0.1 1))
        (get-translation sphere) => (roughly-vector (vec3 0 0 0) 1e-6)
        (remove-and-destroy-body sphere)
        (remove-and-destroy-body mesh)))


(fact "Convex hull detects sphere collision"
      (let [hull   (create-and-add-dynamic-body
                     (convex-hull-settings [(vec3 -1 0 -1) (vec3 1 0 -1) (vec3 1 0 1) (vec3 -1 0 1) (vec3 0 -2 0)]
                                           0.01 1000.0)
                     (vec3 0 -2.0 0) (q/->Quaternion 1 0 0 0))
            sphere (create-and-add-dynamic-body (sphere-settings 1.0 1000.0) (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))]
        (set-gravity (vec3 0 0 0))
        (set-friction hull 0.5)
        (set-restitution hull 1.0)
        (set-friction sphere 0.5)
        (set-restitution sphere 1.0)
        (optimize-broad-phase)
        (set-linear-velocity sphere (vec3 0 -1 0))
        (set-linear-velocity hull (vec3 0 0 0))
        (dotimes [i 20] (update-system 0.1 1))
        (get-linear-velocity sphere) => (roughly-vector (vec3 0 -0.222 0) 1e-3)
        (get-linear-velocity hull) => (roughly-vector (vec3 0 -1.222 0) 1e-3)
        (remove-and-destroy-body sphere)
        (remove-and-destroy-body hull)))


(fact "Convex hull throws exception when too few points"
      (convex-hull-settings [(vec3 0 0 0)] 0.01 1000.0) => (throws RuntimeException))


(fact "Convert model point groups to compound of convex hulls"
      (let [translate-y (mat4x4 1 0 0 0, 0 1 0 -1, 0 0 1 0, 0 0 0 1)
            points      [(vec3 -1 2 -1) (vec3 1 2 -1) (vec3 1 2 1) (vec3 -1 2 1) (vec3 0 4 0)]
            model       {:sfsim.model/transform translate-y
                         :sfsim.model/children [{:sfsim.model/transform translate-y
                                                 :sfsim.model/children points}]}
            floor       (create-and-add-static-body (box-settings (vec3 100.0 0.5 100.0) 1000000.0)
                                                    (vec3 0.0 -0.5 0.0) (q/->Quaternion 1 0 0 0))
            hulls       (create-and-add-dynamic-body (compound-of-convex-hulls-settings model 0.01 1000.0)
                                                     (vec3 0.0 1.0 0.0) (q/->Quaternion 1 0 0 0))]
        (get-center-of-mass hulls) => (vec3 0.0 0.5 0.0)
        (set-gravity (vec3 0 -1 0))
        (set-friction hulls 0.5)
        (set-restitution hulls 0.1)
        (set-friction floor 0.5)
        (set-restitution floor 0.1)
        (optimize-broad-phase)
        (dotimes [i 50] (update-system 0.1 1))
        (get-linear-velocity hulls) => (roughly-vector (vec3 0 0 0) 1e-3)
        (get-translation hulls) => (roughly-vector (vec3 0 0 0) 5e-2)
        (remove-and-destroy-body hulls)
        (remove-and-destroy-body floor)))


(fact "Static compound shape collision with sphere"
      (let [compound (create-and-add-dynamic-body
                       (static-compound-settings [{:sfsim.jolt/shape (box-settings (vec3 0.5 0.5 0.5) 1000.0)
                                                   :sfsim.jolt/position (vec3 -2.0 0.0 0.0)
                                                   :sfsim.jolt/rotation (q/->Quaternion 1 0 0 0)}
                                                  {:sfsim.jolt/shape (box-settings (vec3 0.5 0.5 0.5) 1000.0)
                                                   :sfsim.jolt/position (vec3 +2.0 0.0 0.0)
                                                   :sfsim.jolt/rotation (q/->Quaternion 1 0 0 0)}])
                       (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))
            box      (create-and-add-dynamic-body (box-settings (vec3 0.5 0.5 0.5) 1000.0)
                                                  (vec3 -4.0 0.0 0.0) (q/->Quaternion 1 0 0 0))]
        (get-center-of-mass compound) => (vec3 0.0 0.0 0.0)
        (set-gravity (vec3 0 0 0))
        (set-friction compound 0.5)
        (set-restitution compound 1.0)
        (set-friction box 0.5)
        (set-restitution box 1.0)
        (optimize-broad-phase)
        (set-linear-velocity box (vec3 1 0 0))
        (dotimes [_i 20] (update-system 0.1 1))
        (get-linear-velocity box) => (roughly-vector (vec3 -0.333 0 0) 1e-3)
        (get-linear-velocity compound) => (roughly-vector (vec3 0.666 0 0) 1e-3)
        (remove-and-destroy-body box)
        (remove-and-destroy-body compound)))


(fact "Static compound requires at least one child"
      (static-compound-settings []) => (throws RuntimeException))


(def wheel-base {:sfsim.jolt/width 0.1
                 :sfsim.jolt/radius 0.1
                 :sfsim.jolt/inertia 0.1
                 :sfsim.jolt/suspension-min-length 0.1
                 :sfsim.jolt/suspension-max-length 0.3
                 :sfsim.jolt/stiffness 3200.0
                 :sfsim.jolt/damping 250.0
                 :sfsim.jolt/max-brake-torque 10.0})
(def wheel1 (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 -0.5 -0.5)))
(def wheel2 (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 -0.5 -0.5)))
(def wheel3 (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 +0.5 -0.5)))
(def wheel4 (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 +0.5 -0.5)))


(facts "Create and add vehicle constraint"
       (let [floor   (create-and-add-static-body (box-settings (vec3 100.0 100.0 0.5) 1000000.0)
                                                 (vec3 0.0 0.0 -2.0) (q/->Quaternion 1 0 0 0))
             body    (create-and-add-dynamic-body (box-settings (vec3 0.5 0.5 0.3) 1000.0)
                                                  (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))
             vehicle (create-and-add-vehicle-constraint body (vec3 0 0 1) (vec3 0 0 1) (vec3 1 0 0) [wheel1 wheel2 wheel3 wheel4])]
         (set-gravity (vec3 0 0 -1))
         (set-friction floor 0.3)
         (set-restitution floor 0.2)
         (optimize-broad-phase)
         (dotimes [i 25] (update-system 0.1 1))
         (get-translation body) => (roughly-vector (vec3 0 0 -0.631) 1e-3)
         (matrix/get-translation (get-wheel-local-transform vehicle 0 (vec3 0 1 0) (vec3 0 0 1)))
         => (roughly-vector (vec3 -0.5 -0.5 -0.780) 1e-3)
         (matrix/get-translation (get-wheel-local-transform vehicle 1 (vec3 0 1 0) (vec3 0 0 1)))
         => (roughly-vector (vec3 +0.5 -0.5 -0.780) 1e-3)
         (matrix/get-translation (get-wheel-local-transform vehicle 2 (vec3 0 1 0) (vec3 0 0 1)))
         => (roughly-vector (vec3 -0.5 +0.5 -0.780) 1e-3)
         (matrix/get-translation (get-wheel-local-transform vehicle 3 (vec3 0 1 0) (vec3 0 0 1)))
         => (roughly-vector (vec3 +0.5 +0.5 -0.780) 1e-3)
         (get-suspension-length vehicle 0) => (roughly 0.280 1e-3)
         (get-suspension-length vehicle 1) => (roughly 0.280 1e-3)
         (get-suspension-length vehicle 2) => (roughly 0.280 1e-3)
         (get-suspension-length vehicle 3) => (roughly 0.280 1e-3)
         (get-wheel-rotation-angle vehicle 0) => (roughly 0.0 1e-3)
         (get-wheel-rotation-angle vehicle 1) => (roughly 0.0 1e-3)
         (get-wheel-rotation-angle vehicle 2) => (roughly 0.0 1e-3)
         (get-wheel-rotation-angle vehicle 3) => (roughly 0.0 1e-3)
         (has-hit-hard-point vehicle 0) => false
         (has-hit-hard-point vehicle 1) => false
         (has-hit-hard-point vehicle 2) => false
         (has-hit-hard-point vehicle 3) => false
         (remove-and-destroy-constraint vehicle)
         (remove-and-destroy-body floor)
         (remove-and-destroy-body body)))


(def wheel1-inv (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 -0.5 +0.5)))
(def wheel2-inv (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 -0.5 +0.5)))
(def wheel3-inv (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 +0.5 +0.5)))
(def wheel4-inv (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 +0.5 +0.5)))


(fact "Vehicle constraint should work with mesh and vehicle with -z up vector"
      (let [floor   (create-and-add-static-body
                      (mesh-settings #:sfsim.quadtree {:vertices [(vec3 -100 -100 0) (vec3 100 -100 0)
                                                                  (vec3 100 100 0) (vec3 -100 100 0)]
                                                       :triangles [[0 1 3] [1 2 3]]} 1e+6)
                                                (vec3 0.0 0.0 -2.0) (q/->Quaternion 1 0 0 0))
            body    (create-and-add-dynamic-body (box-settings (vec3 0.5 0.5 0.3) 1000.0)
                                                 (vec3 0.0 0.0 0.0) (q/->Quaternion 0 1 0 0))
            vehicle (create-and-add-vehicle-constraint body (vec3 0 0 1) (vec3 0 0 -1) (vec3 1 0 0)
                                                       [wheel1-inv wheel2-inv wheel3-inv wheel4-inv])]
        (set-gravity (vec3 0 0 -1))
        (set-friction floor 0.3)
        (set-restitution floor 0.2)
        (optimize-broad-phase)
        (dotimes [_i 25] (update-system 0.1 1))
        (get-translation body) => (roughly-vector (vec3 0 0 -1.102) 1e-3)
        (matrix/get-translation (get-wheel-local-transform vehicle 0 (vec3 0 1 0) (vec3 0 0 -1)))
        => (roughly-vector (vec3 -0.5 -0.5 0.798) 1e-3)
        (remove-and-destroy-constraint vehicle)
        (remove-and-destroy-body floor)
        (remove-and-destroy-body body)))


(facts "Wheel brakes should slow down vehicle"
       (let [floor   (create-and-add-static-body
                       (box-settings (vec3 100.0 100.0 0.5) 1000.0) (vec3 0.0 0.0 -1.353) (q/->Quaternion 1 0 0 0))
             body    (create-and-add-dynamic-body (box-settings (vec3 0.5 0.5 0.3) 1000.0)
                                                  (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))
             vehicle (create-and-add-vehicle-constraint body (vec3 0 0 1) (vec3 0 0 1) (vec3 1 0 0) [wheel1 wheel2 wheel3 wheel4])]
         (set-gravity (vec3 0 0 -1))
         (set-friction floor 0.5)
         (set-restitution floor 0.2)
         (set-linear-velocity body (vec3 1 0 0))
         (optimize-broad-phase)
         (dotimes [_i 10] (update-system 0.1 1))
         (get-translation body) => (roughly-vector (vec3 0.938 0.0 0.0) 1e-3)
         (set-brake-input vehicle 1.0)
         (dotimes [_i 10] (update-system 0.1 1))
         (get-translation body) => (roughly-vector (vec3 1.684 0.0 -0.003) 1e-3)
         (remove-and-destroy-constraint vehicle)
         (remove-and-destroy-body floor)
         (remove-and-destroy-body body)))


(jolt-destroy)
