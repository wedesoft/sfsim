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


(def sphere (make-sphere 0.5 1000.0 (vec3 2 3 5) (q/->Quaternion 0 1 0 0) (vec3 0 0 0) (vec3 (/ PI 2) 0 0)))


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
       (let [box (make-box (vec3 0.2 0.3 0.5) 1000.0 (vec3 2 3 5) (q/->Quaternion 0 1 0 0) (vec3 0 0 0) (vec3 (/ PI 2) 0 0))]
         (get-translation box) => (vec3 2 3 5)
         (get-rotation box) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1)
         (get-linear-velocity box) => (vec3 0 0 0)
         (get-angular-velocity box) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6)
         (remove-and-destroy-body box)))


(fact "Mesh prevents object from dropping"
      (let [mesh   (make-mesh #:sfsim.quadtree{:vertices [(vec3 -1 0 -1) (vec3 1 0 -1) (vec3 1 0 1) (vec3 -1 0 1)]
                                               :triangles [[0 3 1] [1 3 2]]}
                              1e+4 (vec3 0 -1 0) (q/->Quaternion 1 0 0 0))
            sphere (make-sphere 1.0 1000.0 (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0) (vec3 0 0 0) (vec3 0 0 0))]
        (set-friction mesh 0.5)
        (set-restitution mesh 0.2)
        (set-friction sphere 0.5)
        (set-restitution sphere 0.2)
        (optimize-broad-phase)
        (set-gravity (vec3 0 -1 0))
        (update-system 1.0 1)
        (get-translation sphere) => (vec3 0 0 0)
        (remove-and-destroy-body sphere)
        (remove-and-destroy-body mesh)))


(fact "Convex hull prevents object from dropping"
      (let [hull   (make-convex-hull [(vec3 -1 0 -1) (vec3 1 0 -1) (vec3 1 0 1) (vec3 -1 0 1) (vec3 0 -1 0)]
                                     0.05 1000.0 (vec3 0 -1.5 0) (q/->Quaternion 1 0 0 0))
            sphere (make-sphere 1.0 1000.0 (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0) (vec3 0 0 0) (vec3 0 0 0))]
        (set-gravity (vec3 0 0 0))
        (set-friction hull 0.5)
        (set-restitution hull 0.2)
        (set-friction sphere 0.5)
        (set-restitution sphere 0.2)
        (optimize-broad-phase)
        (set-linear-velocity sphere (vec3 0 -1 0))
        (update-system 1.0 1)
        (get-linear-velocity sphere) => (vec3 0 0 0)
        (get-linear-velocity hull) => (vec3 0 0 0)
        (get-translation sphere) => (vec3 0 0 0)
        (get-translation hull) => (vec3 0 0 0)
        (remove-and-destroy-body sphere)
        (remove-and-destroy-body hull)))


(jolt-destroy)
