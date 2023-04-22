(ns sfsim25.t-perlin
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (matrix dimensionality dimension-count mget)]
              [sfsim25.perlin :refer :all :as perlin]
              [sfsim25.conftest :refer (roughly-matrix)]))

(facts "Return random gradient vector"
       (random-gradient #(nth %  0)) => (matrix [ 1  1  0])
       (random-gradient #(nth %  1)) => (matrix [-1  1  0])
       (random-gradient #(nth %  2)) => (matrix [ 1 -1  0])
       (random-gradient #(nth %  3)) => (matrix [-1 -1  0])
       (random-gradient #(nth %  4)) => (matrix [ 1  0  1])
       (random-gradient #(nth %  5)) => (matrix [-1  0  1])
       (random-gradient #(nth %  6)) => (matrix [ 1  0 -1])
       (random-gradient #(nth %  7)) => (matrix [-1  0 -1])
       (random-gradient #(nth %  8)) => (matrix [ 0  1  1])
       (random-gradient #(nth %  9)) => (matrix [ 0 -1  1])
       (random-gradient #(nth % 10)) => (matrix [ 0  1 -1])
       (random-gradient #(nth % 11)) => (matrix [ 0 -1 -1]))

(facts "Create a 3D grid with random gradient vectors"
       (dimensionality (random-gradient-grid 1)) => 4
       (map #(dimension-count (random-gradient-grid 1) %) (range 4)) => [1 1 1 3]
       (map #(mget (random-gradient-grid 1 (constantly (matrix [1.0  1.0  0.0]))) 0 0 0 %) (range 3)) => [1.0  1.0  0.0]
       (map #(mget (random-gradient-grid 1 (constantly (matrix [0.0 -1.0 -1.0]))) 0 0 0 %) (range 3)) => [0.0 -1.0 -1.0]
       (map #(dimension-count (random-gradient-grid 2) %) (range 4)) => [2 2 2 3])

(facts "Determine division point belongs to"
       (determine-division (matrix [0.5 0.5 0.5])) => [0 0 0]
       (determine-division (matrix [2.5 3.5 5.5])) => [2 3 5])

(tabular "Get 3D vectors pointing to corners of division"
         (fact (nth (corner-vectors (matrix [2.1 3.2 5.3])) ?i) => (roughly-matrix (matrix [?x ?y ?z]) 1e-6))
         ?i ?x    ?y   ?z
         0   0.1  0.2  0.3
         1  -0.9  0.2  0.3
         2   0.1 -0.8  0.3
         3  -0.9 -0.8  0.3
         4   0.1  0.2 -0.7
         5  -0.9  0.2 -0.7
         6   0.1 -0.8 -0.7
         7  -0.9 -0.8 -0.7)

(tabular "Monotonous ease curve"
         (fact (ease-curve ?t) => (roughly ?result 1e-4))
         ?t  ?result
         0.0 0.0
         1.0 1.0
         0.5 0.5
         0.2 0.05792
         0.8 0.94208)
