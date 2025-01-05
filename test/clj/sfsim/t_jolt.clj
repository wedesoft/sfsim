(ns sfsim.t-jolt
  (:require
    [clojure.math :refer (PI)]
    [coffi.mem :as mem]
    [fastmath.matrix :refer (mat3x3)]
    [fastmath.vector :refer (vec3)]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-matrix roughly-vector)]
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


(def sphere-mass (* (/ 4 3) PI 0.5 0.5 0.5 1000))


(facts "Test applying force to sphere for a single physics update"
       (set-gravity (vec3 0 0 0))
       (set-linear-velocity sphere (vec3 0 0 0))
       (add-force sphere (vec3 sphere-mass 0 0))
       (update-system 1.0 2)
       (get-linear-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-6)
       (update-system 1.0 2)
       (get-linear-velocity sphere) => (roughly-vector (vec3 1 0 0) 1e-6))


(def sphere-inertia (* (/ 2 5) sphere-mass 0.5 0.5))


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
        (set-gravity (vec3 0 0 0))
        (set-friction compound 0.5)
        (set-restitution compound 1.0)
        (set-friction box 0.5)
        (set-restitution box 1.0)
        (optimize-broad-phase)
        (set-linear-velocity box (vec3 1 0 0))
        (dotimes [i 20] (update-system 0.1 1))
        (get-linear-velocity box) => (roughly-vector (vec3 -0.333 0 0) 1e-3)
        (get-linear-velocity compound) => (roughly-vector (vec3 0.666 0 0) 1e-3)
        (remove-and-destroy-body box)
        (remove-and-destroy-body compound)))


(def wheel-base {:sfsim.jolt/position (vec3 0.0 0.0 0.0)
                 :sfsim.jolt/width 0.1
                 :sfsim.jolt/radius 0.1
                 :sfsim.jolt/inertia 0.1
                 :sfsim.jolt/suspension-min-length 0.1
                 :sfsim.jolt/suspension-max-length 0.3})
(def wheel1 (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 -0.5 -0.5)))
(def wheel2 (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 -0.5 -0.5)))
(def wheel3 (assoc wheel-base :sfsim.jolt/position (vec3 -0.5 -0.5 +0.5)))
(def wheel4 (assoc wheel-base :sfsim.jolt/position (vec3 +0.5 -0.5 +0.5)))


(fact "Create and add vehicle constraint"
      (let [floor   (create-and-add-static-body (box-settings (vec3 100.0 0.5 100.0) 1000000.0)
                                                (vec3 0.0 -2.0 0.0) (q/->Quaternion 1 0 0 0))
            body    (create-and-add-dynamic-body (box-settings (vec3 0.5 0.3 0.5) 1000.0)
                                                 (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0))
            vehicle (create-and-add-vehicle-constraint body [wheel1 wheel2 wheel3 wheel4])]
        (set-gravity (vec3 0 -1 0))
        (set-friction floor 0.3)
        (set-restitution floor 0.2)
        (optimize-broad-phase)
        (dotimes [i 50] (update-system 0.1 1))
        (get-translation body) => (roughly-vector (vec3 0 -0.613 0) 1e-3)
        (remove-and-destroy-constraint vehicle)
        (remove-and-destroy-body body)))


(jolt-destroy)
