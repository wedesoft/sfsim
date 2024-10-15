(ns sfsim.t-jolt
    (:require [midje.sweet :refer :all]
              [sfsim.jolt :refer :all]))

(jolt-init)

(facts "Object layers"
       NON-MOVING-LAYER => 0
       MOVING-LAYER => 1
       NUM-LAYERS => 2)

(jolt-destroy)
