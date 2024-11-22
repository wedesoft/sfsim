(ns sfsim.t-jolt
    (:require [midje.sweet :refer :all]
              [sfsim.conftest :refer (roughly-matrix roughly-vector)]
              [clojure.math :refer (PI)]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3)]
              [coffi.mem :as mem]
              [sfsim.quaternion :as q]
              [sfsim.jolt :refer :all]))

(jolt-init)

(facts "Object layers"
       NON-MOVING-LAYER => 0
       MOVING-LAYER => 1
       NUM-LAYERS => 2)

(def sphere (make-sphere 0.5 1000.0 (vec3 2 3 5) (q/->Quaternion 0 1 0 0) (vec3 0 0 0) (vec3 (/ PI 2) 0 0)))

(facts "Get position vector, rotation matrix, and velocities of sphere body"
       (get-translation sphere) => (vec3 2 3 5)
       (get-rotation sphere) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1)
       (get-linear-velocity sphere) => (vec3 0 0 0)
       (get-angular-velocity sphere) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6))

(set-gravity (vec3 0 -1 0))

(update-system 1.0 1)

(facts "Check position and speed after time step"
       (get-translation sphere) => (vec3 2 2 5)
       (get-linear-velocity sphere) => (vec3 0 -1 0)
       (get-rotation sphere) => (roughly-matrix (mat3x3 1 0 0, 0 0 1, 0 -1 0) 1e-6)
       (get-angular-velocity sphere) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6))

(remove-and-destroy-body sphere)

(def box (make-box (vec3 0.2 0.3 0.5) 1000.0 (vec3 2 3 5) (q/->Quaternion 0 1 0 0) (vec3 0 0 0) (vec3 (/ PI 2) 0 0)))

(facts "Get position vector, rotation matrix, and velocities of box body"
       (get-translation box) => (vec3 2 3 5)
       (get-rotation box) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1)
       (get-linear-velocity box) => (vec3 0 0 0)
       (get-angular-velocity box) => (roughly-vector (vec3 (/ PI 2) 0 0) 1e-6))

(remove-and-destroy-body box)

(def mesh (make-mesh [(vec3 -1 0 -1) (vec3 1 0 -1) (vec3 1 0 1) (vec3 -1 0 1)] [[0 3 1] [1 3 2]]
                     1e+4 (vec3 0 -1 0) (q/->Quaternion 1 0 0 0)))
(set-friction mesh 0.5)
(set-restitution mesh 0.2)

(def sphere (make-sphere 1.0 1000.0 (vec3 0.0 0.0 0.0) (q/->Quaternion 1 0 0 0) (vec3 0 0 0) (vec3 0 0 0)))
(set-friction sphere 0.5)
(set-restitution sphere 0.2)

(optimize-broad-phase)
(update-system 1.0 1)

(fact "Mesh prevents object from dropping"
      (get-translation sphere) => (vec3 0 0 0))

(remove-and-destroy-body sphere)
(remove-and-destroy-body mesh)

(jolt-destroy)
