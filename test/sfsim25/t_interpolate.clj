(ns sfsim25.t-interpolate
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [sfsim25.interpolate :refer :all]
              [sfsim25.util :refer (sqr)]))

(tabular "1D linear mapping"
         (fact ((linear-forward [-2] [4] [16]) ?x) => [?result])
         ?x ?result
         -2  0.0
          4 15.0
          0  5.0)

(fact "2D linear mapping"
      ((linear-forward [-2 -1] [4 1] [16 5]) 4 0) => [15.0 2.0])

(tabular "1D inverse linear sampling"
         (fact ((linear-backward [-2] [4] [16]) ?i) => [?result])
         ?i ?result
          0 -2.0
         15  4.0
          5  0.0)

(fact "2D inverse linear mapping"
      ((linear-backward [-2 -1] [4 1] [16 5]) 15 2) => [4.0 0.0])

(facts "Pair of mapping and inverse mapping"
       ((:sfsim25.interpolate/forward (linear-space [-2 -1] [4 1] [16 5])) 4 0) => [15.0 2.0]
       ((:sfsim25.interpolate/backward (linear-space [-2 -1] [4 1] [16 5])) 15 2) => [4.0 0.0]
       (:sfsim25.interpolate/shape (linear-space [-2 -1] [4 1] [16 5])) => [16 5])

(fact "Create linear table of function"
      (make-lookup-table sqr (linear-space [-3] [2] [6])) => [9.0 4.0 1.0 0.0 1.0 4.0]
      (make-lookup-table sqr (linear-space [-3] [2] [6])) => vector?)

(fact "Create 2D table of function"
      (make-lookup-table * (linear-space [1 3] [2 5] [2 3])) => [[3.0 4.0 5.0] [6.0 8.0 10.0]]
      (make-lookup-table * (linear-space [1 3] [2 5] [2 3])) => vector?)

(tabular "Clip value to given range"
         (fact (clip ?i 16) => ?result)
         ?i ?result
          0.0  0.0
         15.0 15.0
         -2.0  0.0
         16.0 15.0)

(tabular "Mix between values"
         (fact (mix -2.0 4.0 ?s) => ?result)
         ?s  ?result
         0.0 -2.0
         1.0  4.0
         0.5  1.0)

(facts "Shape of nested vector"
       (dimensions [1 2 3]) => [3]
       (dimensions [[1 2 3] [4 5 6]]) => [2 3]
       (dimensions [(matrix [1 2 3]) (matrix [4 5 6])]) => [2])

(tabular "Linear interpolation using a table of scalar values"
         (fact ((interpolation-table [9 4 1 0 1 4] (linear-forward [-3] [2] [6])) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)

(fact "Linear interpolation using a table of vectors"
      ((interpolation-table [(matrix [2 3 5]) (matrix [3 5 9])] (linear-forward [-1] [1] [2])) 0) => (matrix [2.5 4.0 7.0]))

(tabular "Linear interpolation using a 2D table"
         (fact ((interpolation-table [[2 3 5] [7 11 13]] (linear-forward [?y0 ?x0] [?y1 ?x1] [2 3])) ?y ?x) => ?result)
         ?y0  ?x0 ?y1 ?x1  ?y  ?x  ?result
          0   0   1   2    0   0   2.0
          0   0   1   2    0   2   5.0
          0   0   1   2    0   1.5 4.0
          0   0   1   2    1   0   7.0
          0   0   1   2    0.5 0   4.5
         -3   0   1   2   -1   0   4.5)

(tabular "Linear interpolation of scalar function"
         (fact ((interpolate-function sqr (linear-space [-3] [2] [6])) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)
