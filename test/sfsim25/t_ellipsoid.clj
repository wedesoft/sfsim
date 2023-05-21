(ns sfsim25.t-ellipsoid
  (:require [midje.sweet :refer :all]
            [fastmath.vector :refer (vec3)]
            [sfsim25.ellipsoid :refer :all]))

(facts "Compute intersection of line with ellipsoid"
  (let [ellipsoid (fn [z] #:sfsim25.ellipsoid{:centre (vec3 0 0 z) :major-radius 1 :minor-radius 0.5})]
    (ray-ellipsoid-intersection (ellipsoid 0) #:sfsim25.ray{:origin (vec3 -2 0  0) :direction (vec3 1 0 0)})
    => #:sfsim25.intersection{:distance 1.0 :length 2.0}
    (ray-ellipsoid-intersection (ellipsoid 0) #:sfsim25.ray{:origin (vec3  0 0 -2) :direction (vec3 0 0 1)})
    => #:sfsim25.intersection{:distance 1.5 :length 1.0}
    (ray-ellipsoid-intersection (ellipsoid 4) #:sfsim25.ray{:origin (vec3  0 0  2) :direction (vec3 0 0 1)})
    => #:sfsim25.intersection{:distance 1.5 :length 1.0}))
