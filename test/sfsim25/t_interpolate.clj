(ns sfsim25.t-interpolate
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer (matrix)]
              [clojure.math :refer (hypot)]
              [sfsim25.interpolate :refer :all]
              [sfsim25.util :refer (sqr)]))

(tabular "1D linear mapping"
         (fact ((:sfsim25.interpolate/forward (linear-space [-2] [4] [16])) ?x) => [?result])
         ?x ?result
         -2  0.0
          4 15.0
          0  5.0)

(fact "2D linear mapping"
      ((:sfsim25.interpolate/forward (linear-space [-2 -1] [4 1] [16 5])) 4 0) => [15.0 2.0])

(tabular "1D inverse linear sampling"
         (fact ((:sfsim25.interpolate/backward (linear-space [-2] [4] [16])) ?i) => [?result])
         ?i ?result
          0 -2.0
         15  4.0
          5  0.0)

(fact "2D inverse linear mapping"
      ((:sfsim25.interpolate/backward (linear-space [-2 -1] [4 1] [16 5])) 15 2) => [4.0 0.0])

(facts "Shape of linear space"
       (:sfsim25.interpolate/shape (linear-space [-2] [4] [16])) => [16]
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
         ?s   ?result
         0.0  -2.0
         1.0   4.0
         0.5   1.0
         0.25 -0.5)

(tabular "Linear interpolation using a table of scalar values"
         (fact ((interpolation-table [9 4 1 0 1 4] (linear-space [-3] [2] [6])) ?x) => ?result)
         ?x   ?result
         -3   9.0
          1.5 2.5
         -5   9.0
          3   4.0)

(fact "Linear interpolation using a table of vectors"
      ((interpolation-table [(matrix [2 3 5]) (matrix [3 5 9])] (linear-space [-1] [1] [2])) 0) => (matrix [2.5 4.0 7.0]))

(tabular "Linear interpolation using a 2D table"
         (fact ((interpolation-table [[2 3 5] [7 11 13]] (linear-space [?y0 ?x0] [?y1 ?x1] [2 3])) ?y ?x) => ?result)
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

(facts "Combine transformations to create non-linear space"
       (let [radius-space {:sfsim25.interpolate/forward #(vector (hypot %1 %2)) :sfsim25.interpolate/backward #(vector %1 0)}
             combined     (compose-space (linear-space [0] [1] [101]) radius-space)]
         (:sfsim25.interpolate/shape combined) => [101]
         ((:sfsim25.interpolate/forward combined) 3 4) => [500.0]
         ((:sfsim25.interpolate/backward combined) 500) => [5.0 0]))
