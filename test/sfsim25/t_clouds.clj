(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (ecount mget)]
              [sfsim25.clouds :refer :all]))

(facts "Create a vector of random points"
       (count (random-points 0 64))          => 0
       (count (random-points 1 64))          => 1
       (random-points 1 64)                  => vector?
       (ecount (first (random-points 1 64))) => 3
       (mget (first (random-points 1 64)) 0) => #(>= % 0)
       (mget (first (random-points 1 64)) 0) => #(<= % 64))
