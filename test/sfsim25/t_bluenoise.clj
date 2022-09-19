(ns sfsim25.t-bluenoise
    (:require [clojure.math :refer (exp)]
              [midje.sweet :refer :all]
              [sfsim25.bluenoise :refer :all]))

(fact "Generate indices for 2D array"
      (indices-2d 3) => [0 1 2 3 4 5 6 7 8])

(facts "Pick n random values from array"
       (pick-n [0 1 2 3] 2 identity) => [0 1]
       (pick-n [0 1 2 3] 2 reverse) => [3 2]
       (count (pick-n [0 1 2 3] 2)) => 2)

(facts "Scatter given indices on boolean array"
       (scatter-mask [] 2) => [false false false false]
       (scatter-mask [2] 2) => [false false true false])

(facts "Weighting function to determine clusters and voids"
       ((density-function 1) 0 0) => (roughly 1.0 1e-6)
       ((density-function 1) 1 0) => (roughly (exp -0.5))
       ((density-function 1) 0 1) => (roughly (exp -0.5))
       ((density-function 1) -1 0) => (roughly (exp -0.5))
       ((density-function 1) 0 -1) => (roughly (exp -0.5))
       ((density-function 2) 2 0) => (roughly (exp -0.5)))

(facts "Index of largest element"
       (argmax-with-mask [5 3 2] (repeat 3 true)) => 0
       (argmax-with-mask [3 5 2] (repeat 3 true)) => 1
       (argmax-with-mask [3 5 2] [true false true]) => 0)

(facts "Index of largest element"
       (argmin-with-mask [2 3 5] (repeat 3 false)) => 0
       (argmin-with-mask [3 2 5] (repeat 3 false)) => 1
       (argmin-with-mask [3 2 5] [false true false]) => 0)

(facts "Wrap coordinate"
       (wrap 0 5) => 0
       (wrap 2 5) => 2
       (wrap 5 5) => 0
       (wrap 3 5) => -2
       (wrap 2 4) => -2)

(facts "Compute sample of correlation of binary image with density function"
       (density-sample (repeat 4 false) 2 (fn [dx dy] 1) 0 0) => 0
       (density-sample [true false false false] 2 (fn [dx dy] 1) 0 0) => 1
       (density-sample [true false false false] 2 (fn [dx dy] 2) 0 0) => 2
       (density-sample (repeat 4 true) 2 (fn [dx dy] 1) 0 0) => 4
       (density-sample (repeat 9 true) 3 (fn [dx dy] dx) 1 1) => 0
       (density-sample (repeat 9 true) 3 (fn [dx dy] dy) 1 1) => 0
       (density-sample [false false true false] 2 (fn [dx dy] dx) 1 1) => -1
       (density-sample [false true false false] 2 (fn [dx dy] dy) 1 1) => -1
       (density-sample (repeat 9 true) 3 (fn [dx dy] dx) 2 2) => 0
       (density-sample (repeat 9 true) 3 (fn [dx dy] dy) 2 2) => 0)

(facts "Compute dither array for given boolean mask"
       (density-array [true false false false] 2 (fn [dx dy] dx)) => [0 -1 0 -1]
       (density-array [true false false false] 2 (fn [dx dy] dx)) => vector?)

(facts "Add/subtract sample from dither array"
       (density-change [0 -1 0 -1] 2 + (fn [dx dy] dx) 0) => [0 -2 0 -2]
       (density-change [0 -1 0 -1] 2 - (fn [dx dy] dx) 0) => [0 0 0 0])

(facts "Initial binary pattern generator"
       (seed-pattern [true false false false] 2 (density-function 1.9)) => [false false false true]
       (seed-pattern [true true false false] 2 (density-function 1.9)) => [true false false true])

(facts "Phase 1 dithering"
       (dither-phase1 [true false false false] 2 1 (density-function 1.5)) => [0 0 0 0]
       (dither-phase1 [true false false true] 2 2 (density-function 1.5)) => [0 0 0 1])

(facts "Phase 2 dithering"
       (dither-phase2 [true false false true] 2 2 [0 0 0 1] (density-function 1.5)) => [0 0 0 1]
       (dither-phase2 [true false false false] 2 1 [0 0 0 0] (density-function 1.5)) => [0 0 0 1])

(fact "Phase 3 dithering"
      (dither-phase3 [true false false true] 2 2 [0 0 0 1] (density-function 1.5)) => [0 3 2 1])
