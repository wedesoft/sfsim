(ns sfsim.t-jolt
    (:require [midje.sweet :refer :all]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3)]
              [sfsim.quaternion :as q]
              [sfsim.jolt :refer :all]))

(jolt-init)

(facts "Object layers"
       NON-MOVING-LAYER => 0
       MOVING-LAYER => 1
       NUM-LAYERS => 2)

(def sphere (make-sphere 0.5 (vec3 2 3 5) (q/->Quaternion 0 1 0 0)))

(facts "Get position vector and rotation matrix of sphere body"
       (get-translation sphere) => (vec3 2 3 5)
       (get-rotation sphere) => (mat3x3 1 0 0, 0 -1 0, 0 0 -1))

(set-gravity (vec3 0 -1 0))

(update-system 1.0)

(facts "Check position and speed after time step"
       (get-translation sphere) => (vec3 2 2 5)
       (get-linear-velocity sphere) => (vec3 0 -1 0))

(remove-and-destroy-body sphere)

(jolt-destroy)
