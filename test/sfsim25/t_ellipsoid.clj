(ns sfsim25.t-ellipsoid
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
            [sfsim25.ellipsoid :refer :all]))

(facts "Compute intersection of line with ellipsoid"
  (let [ellipsoid (fn [z] #:sfsim25.ellipsoid{:ellipsoid-centre (matrix [0 0 z]) :ellipsoid-radius1 1 :ellipsoid-radius2 0.5})]
    (ray-ellipsoid-intersection (ellipsoid 0) #:sfsim25.ray{:origin (matrix [-2 0  0]) :direction (matrix [1 0 0])})
    => {:distance 1.0 :length 2.0}
    (ray-ellipsoid-intersection (ellipsoid 0) #:sfsim25.ray{:origin (matrix [ 0 0 -2]) :direction (matrix [0 0 1])})
    => {:distance 1.5 :length 1.0}
    (ray-ellipsoid-intersection (ellipsoid 4) #:sfsim25.ray{:origin (matrix [ 0 0  2]) :direction (matrix [0 0 1])})
    => {:distance 1.5 :length 1.0}))
