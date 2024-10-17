(ns sfsim.t-jolt
    (:require [midje.sweet :refer :all]
              [fastmath.vector :refer (vec3)]
              [sfsim.jolt :refer :all]))

(jolt-init)

(facts "Object layers"
       NON-MOVING-LAYER => 0
       MOVING-LAYER => 1
       NUM-LAYERS => 2)

(def sphere (make-sphere 0.5))

(remove-and-destroy-body sphere)

(jolt-destroy)
