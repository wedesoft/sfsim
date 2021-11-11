(ns sfsim25.t-interpolate
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [sfsim25.interpolate :refer :all]
              [sfsim25.util :refer (sqr)]))

(tabular "1D linear mapping"
         (fact ((linear-mapping [-2] [4] [16]) ?x) => [?result])
         ?x ?result
         -2  0
          4 15
          0  5)

(fact "2D linear mapping"
      ((linear-mapping [-2 -1] [4 1] [16 5]) 4 0) => [15 2])

(tabular "1D inverse linear sampling"
         (fact ((inverse-linear-mapping [-2] [4] [16]) ?i) => [?result])
         ?i ?result
          0 -2
         15  4
          5  0)

(fact "2D inverse linear mapping"
      ((inverse-linear-mapping [-2 -1] [4 1] [16 5]) 15 2) => [4 0])

(fact "Create linear table of function"
      (make-lookup-table sqr (inverse-linear-mapping [-3] [2] [6]) [6]) => [9.0 4.0 1.0 0.0 1.0 4.0])

(fact "Create 2D table of function"
      (make-lookup-table * (inverse-linear-mapping [1 3] [2 5] [2 3]) [2 3]) => [[3 4 5] [6 8 10]])

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
         (fact ((interpolate-table [9 4 1 0 1 4] (linear-mapping [-3] [2] [6])) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)

(fact "Linear interpolation using a table of vectors"
      ((interpolate-table [(matrix [2 3 5]) (matrix [3 5 9])] (linear-mapping [-1] [1] [2])) 0) => (matrix [2.5 4 7]))

(tabular "Linear interpolation using a 2D table"
         (fact ((interpolate-table [[2 3 5] [7 11 13]] (linear-mapping [?y0 ?x0] [?y1 ?x1] [2 3])) ?y ?x) => ?result)
         ?y0  ?x0 ?y1 ?x1  ?y  ?x  ?result
          0   0   1   2    0   0   2.0
          0   0   1   2    0   2   5.0
          0   0   1   2    0   1.5 4.0
          0   0   1   2    1   0   7.0
          0   0   1   2    0.5 0   4.5
         -3   0   1   2   -1   0   4.5)

(tabular "Linear interpolation of scalar function"
         (fact ((interpolate-function sqr (linear-mapping [-3] [2] [6]) (inverse-linear-mapping [-3] [2] [6]) [6]) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)
