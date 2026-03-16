;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-interpolate
  (:require
    [clojure.math :refer (hypot)]
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.interpolate :refer :all]
    [sfsim.util :refer (sqr)]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(tabular "1D linear mapping"
         (fact ((:sfsim.interpolate/forward (linear-space [-2.0] [4.0] [16])) ?x) => [?result])
         ?x   ?result
         -2.0  0.0
         +4.0 15.0
         +0.0  5.0)


(fact "2D linear mapping"
      ((:sfsim.interpolate/forward (linear-space [-2.0 -1.0] [4.0 1.0] [16 5])) 4.0 0.0) => [15.0 2.0])


(tabular "1D inverse linear sampling"
         (fact ((:sfsim.interpolate/backward (linear-space [-2.0] [4.0] [16])) ?i) => [?result])
         ?i ?result
         +0.0 -2.0
         15.0  4.0
         +5.0  0.0)


(fact "2D inverse linear mapping"
      ((:sfsim.interpolate/backward (linear-space [-2.0 -1.0] [4.0 1.0] [16 5])) 15 2) => [4.0 0.0])


(facts "Shape of linear space"
       (:sfsim.interpolate/shape (linear-space [-2.0] [4.0] [16])) => [16]
       (:sfsim.interpolate/shape (linear-space [-2.0 -1.0] [4.0 1.0] [16 5])) => [16 5])


(fact "Create linear table of function"
      (make-lookup-table sqr (linear-space [-3.0] [2.0] [6])) => [9.0 4.0 1.0 0.0 1.0 4.0]
      (make-lookup-table sqr (linear-space [-3.0] [2.0] [6])) => vector?)


(fact "Create 2D table of function"
      (make-lookup-table * (linear-space [1.0 3.0] [2.0 5.0] [2 3])) => [[3.0 4.0 5.0] [6.0 8.0 10.0]]
      (make-lookup-table * (linear-space [1.0 3.0] [2.0 5.0] [2 3])) => vector?)


(tabular "Clip value to given range"
         (fact (clip ?i 16) => ?result)
         ?i ?result
         +0.0  0.0
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
         (fact ((interpolation-table [9 4 1 0 1 4] (linear-space [-3.0] [2.0] [6])) ?x) => ?result)
         ?x   ?result
         -3.0 9.0
         +1.5 2.5
         -5.0 9.0
         +3.0 4.0)


(fact "Linear interpolation using a table of vectors"
      ((interpolation-table [(vec3 2 3 5) (vec3 3 5 9)] (linear-space [-1.0] [1.0] [2])) 0.0) => (vec3 2.5 4.0 7.0))


(tabular "Linear interpolation using a 2D table"
         (fact ((interpolation-table [[2 3 5] [7 11 13]] (linear-space [?y0 ?x0] [?y1 ?x1] [2 3])) ?y ?x) => ?result)
         ?y0  ?x0 ?y1 ?x1  ?y  ?x  ?result
         +0.0  0.0 1.0 2.0  0   0   2.0
         +0.0  0.0 1.0 2.0  0   2   5.0
         +0.0  0.0 1.0 2.0  0   1.5 4.0
         +0.0  0.0 1.0 2.0  1   0   7.0
         +0.0  0.0 1.0 2.0  0.5 0   4.5
         -3.0  0.0 1.0 2.0 -1   0   4.5)


(tabular "Linear interpolation of scalar function"
         (fact ((interpolate-function sqr (linear-space [-3.0] [2.0] [6])) ?x) => ?result)
         ?x   ?result
         -3.0 9.0
         +1.5 2.5
         -5.0 9.0
         +3.0 4.0)


(facts "Combine transformations to create non-linear space"
       (let [radius-space {:sfsim.interpolate/forward #(vector (hypot %1 %2)) :sfsim.interpolate/backward #(vector %1 0.0)}
             combined     (compose-space (linear-space [0.0] [1.0] [101]) radius-space)]
         (:sfsim.interpolate/shape combined) => [101]
         ((:sfsim.interpolate/forward combined) 3.0 4.0) => [500.0]
         ((:sfsim.interpolate/backward combined) 500.0) => [5.0 0.0]))
