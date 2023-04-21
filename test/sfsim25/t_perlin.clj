(ns sfsim25.t-perlin
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (matrix dimensionality dimension-count mget)]
              [sfsim25.perlin :refer :all :as perlin]))

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
