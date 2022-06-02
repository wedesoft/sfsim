(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (ecount mget matrix)]
              [sfsim25.clouds :refer :all]))

(facts "Create a vector of random points"
       (random-points 0 64)                  => []
       (count (random-points 1 64))          => 1
       (random-points 1 64)                  => vector?
       (ecount (first (random-points 1 64))) => 3
       (mget (first (random-points 1 64)) 0) => #(>= % 0)
       (mget (first (random-points 1 64)) 0) => #(<= % 64))

(facts "Repeat point cloud in each direction"
       (repeat-points 10 [])                         => []
       (count (repeat-points 10 [(matrix [2 3 5])])) => 7
       (repeat-points 10 [(matrix [2 3 5])])         => vector?
       (nth (repeat-points 10 [(matrix [2 3 5])]) 0) => (matrix [ 2  3  5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 1) => (matrix [-8  3  5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 2) => (matrix [12  3  5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 3) => (matrix [ 2 -7  5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 4) => (matrix [ 2 13  5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 5) => (matrix [ 2  3 -5])
       (nth (repeat-points 10 [(matrix [2 3 5])]) 6) => (matrix [ 2  3 15]))

(facts "Normalise values of a vector"
       (normalise-vector [1.0])         => [1.0]
       (normalise-vector [0.0 1.0 2.0]) => [0.0 0.5 1.0]
       (normalise-vector [1.0])         => vector?)

(facts "Invert values of a vector"
       (invert-vector []) => []
       (invert-vector [0.0]) => [1.0]
       (invert-vector [1.0]) => [0.0]
       (invert-vector [1.0]) => vector?)
