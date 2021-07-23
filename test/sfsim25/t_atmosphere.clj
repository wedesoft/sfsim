(ns sfsim25.t-atmosphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix)]
            [sfsim25.atmosphere :refer :all]))

(def radius1 6378000.0)
(def radius2 6357000.0)

(facts "Compute approximate air density at different heights"
  (air-density 0 1.225 8429) => 1.225
  (air-density 8429 1.225 8429) => (roughly (/ 1.225 Math/E) 1e-6)
  (air-density (* 2 8429) 1.225 8429) => (roughly (/ 1.225 Math/E Math/E) 1e-6))

(facts "Compute intersection of line with sphere"
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [0 1 0])) => {:distance 0.0 :length 0.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [ 0 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 1.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [ 2 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 0.0}
  (ray-sphere (matrix [0 0 3]) 1 (matrix [-2 0 3]) (matrix [2 0 0])) => {:distance 0.5 :length 1.0})

(facts "Compute intersection of line with ellipsoid"
  (ray-ellipsoid (matrix [0 0 0]) 1 0.5 (matrix [-2 0  0]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
  (ray-ellipsoid (matrix [0 0 0]) 1 0.5 (matrix [ 0 0 -2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0}
  (ray-ellipsoid (matrix [0 0 4]) 1 0.5 (matrix [ 0 0  2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0})
