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

(tabular "Inverse linear sampling"
         (fact ((inverse-linear-mapping -2 4 16) ?i) => (roughly ?result 1e-6))
         ?i ?result
          0 -2
         15  4
          5  0)

(fact "Make lookup table of function"
      (sample-function #(* % %) (inverse-linear-mapping -3 2 6) 6) => [9 4 1 0 1 4])

(fact "Create linear table of function"
      (make-lookup-table #(* % %) [-3] [2] [6]) => [9 4 1 0 1 4])

(fact "Create 2D table of function"
      (make-lookup-table * [1 3] [2 5] [2 3]) => [[3 4 5] [6 8 10]])

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

(facts "Shape of nested vector"
       (dimensions [1 2 3]) => [3]
       (dimensions [[1 2 3] [4 5 6]]) => [2 3]
       (dimensions [(matrix [1 2 3]) (matrix [4 5 6])]) => [2])

(tabular "Linear interpolation using a table of scalar values"
         (fact ((interpolation [9 4 1 0 1 4] [-3] [2]) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)

(fact "Linear interpolation using a table of vectors"
      ((interpolation [(matrix [2 3 5]) (matrix [3 5 9])] [-1] [1]) 0) => (matrix [2.5 4 7]))

(tabular "Linear interpolation using a 2D table"
         (fact ((interpolation [[2 3 5] [7 11 13]] [?y0 ?x0] [?y1 ?x1]) ?y ?x) => ?result)
         ?y0  ?x0 ?y1 ?x1  ?y  ?x  ?result
          0   0   1   2    0   0   2.0
          0   0   1   2    0   2   5.0
          0   0   1   2    0   1.5 4.0
          0   0   1   2    1   0   7.0
          0   0   1   2    0.5 0   4.5
         -3   0   1   2   -1   0   4.5)

(tabular "Linear interpolation of scalar function"
         (fact ((interpolate #(* % %) [-3] [2] [6]) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)
