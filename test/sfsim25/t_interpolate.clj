(ns sfsim25.t-interpolate
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [sfsim25.interpolate :refer :all]))

(tabular "Linear mapping"
         (fact ((linear-mapping -2 4 16) ?x) => (roughly ?result 1e-6))
         ?x ?result
         -2  0
          4 15
          0  5)

(tabular "Linear sampling"
         (fact ((linear-sampling -2 4 16) ?i) => (roughly ?result 1e-6))
         ?i ?result
          0 -2
         15  4
          5  0)

(fact "Make lookup table of function"
      (lookup #(* % %) (linear-sampling -3 2 6) 6) => [9 4 1 0 1 4])

(fact "Create linear table of function"
      (table #(* % %) -3 2 6) => [9 4 1 0 1 4])

(tabular "Clip value to given range"
         (fact (clip ?i 16) => ?result)
         ?i ?result
          0  0
         15 15
         -2  0
         16 15)

(tabular "Mix between values"
         (fact (mix -2 4 ?s) => ?result)
         ?s  ?result
         0   -2
         1    4
         0.5  1.0)

(tabular "Linear interpolation of function"
         (fact ((interpolate #(* % %) -3 2 6) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)
