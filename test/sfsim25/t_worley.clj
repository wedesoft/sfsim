(ns sfsim25.t-worley
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (dimensionality dimension-count mget matrix)]
              [sfsim25.worley :refer :all :as worley]))

(facts "Create a 3D grid with a random point in each cell"
       (dimensionality (random-point-grid 1 1)) => 4
       (map #(dimension-count (random-point-grid 1 1) %) (range 4)) => [1 1 1 3]
       (map #(mget (random-point-grid 1 1 identity) 0 0 0 %) (range 3)) => [1.0 1.0 1.0]
       (map #(dimension-count (random-point-grid 2 1) %) (range 4)) => [2 2 2 3]
       (mget (reduce nth (random-point-grid 2 8 identity) [0 0 0]) 0) => 4.0
       (mget (reduce nth (random-point-grid 2 8 identity) [0 0 1]) 0) => 8.0
       (mget (reduce nth (random-point-grid 2 8 identity) [0 1 0]) 1) => 8.0
       (mget (reduce nth (random-point-grid 2 8 identity) [1 0 0]) 2) => 8.0)

(facts "Extract point from specified cell"
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 0 0 0) => (matrix [1 2 3])
       (extract-point-from-grid [[[(matrix [1 2 3]) (matrix [4 5 6])]]] 10 0 0 1) => (matrix [4 5 6])
       (extract-point-from-grid [[[(matrix [1 2 3])] [(matrix [4 5 6])]]] 10 0 1 0) => (matrix [4 5 6])
       (extract-point-from-grid [[[(matrix [1 2 3])]] [[(matrix [4 5 6])]]] 10 1 0 0) => (matrix [4 5 6])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 0 0 1) => (matrix [11 2 3])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 0 1 0) => (matrix [1 12 3])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 1 0 0) => (matrix [1 2 13])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 0 0 -1) => (matrix [-9 2 3])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 0 -1 0) => (matrix [1 -8 3])
       (extract-point-from-grid [[[(matrix [1 2 3])]]] 10 -1 0 0) => (matrix [1 2 -7]))

(facts "Closest distance of point in grid"
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1])]]] 1 2 (matrix [1 1 1])) => 0.0
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1])]]] 1 2 (matrix [0.5 1 1])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1]) (matrix [3 1 1])]]] 2 4 (matrix [3 1 1])) => 0.0
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1])] [(matrix [1 3 1])]]] 2 4 (matrix [1 3 1])) => 0.0
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1])]] [[(matrix [1 1 3])]]] 2 4 (matrix [1 1 3])) => 0.0
       (closest-distance-to-point-in-grid [[[(matrix [0.25 1 1])]]] 1 2 (matrix [1.75 1 1])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1.75 1 1])]]] 1 2 (matrix [0.25 1 1])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1 0.25 1])]]] 1 2 (matrix [1 1.75 1])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1 1.75 1])]]] 1 2 (matrix [1 0.25 1])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1 1 0.25])]]] 1 2 (matrix [1 1 1.75])) => 0.5
       (closest-distance-to-point-in-grid [[[(matrix [1 1 1.75])]]] 1 2 (matrix [1 1 0.25])) => 0.5)

(facts "Normalize values of a vector"
       (normalize-vector [1.0])         => [1.0]
       (normalize-vector [0.0 1.0 2.0]) => [0.0 0.5 1.0]
       (normalize-vector [1.0])         => vector?)

(facts "Invert values of a vector"
       (invert-vector []) => []
       (invert-vector [0.0]) => [1.0]
       (invert-vector [1.0]) => [0.0]
       (invert-vector [1.0]) => vector?)

(facts "Create 3D Worley noise"
       (with-redefs [worley/random-point-grid (fn [n size] (facts n => 1 size => 2) [[[(matrix [0.5 0.5 0.5])]]])]
         (nth (worley-noise 1 2) 0) => 1.0
         (count (worley-noise 1 2)) => 8
         (apply min (worley-noise 1 2)) => 0.0))
