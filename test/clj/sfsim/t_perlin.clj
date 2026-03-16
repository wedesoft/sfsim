;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-perlin
  (:require
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.perlin :refer :all :as perlin]
    [sfsim.util :refer (dimension-count)]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Return random gradient vector"
       (random-gradient #(nth %  0)) => (vec3  1  1  0)
       (random-gradient #(nth %  1)) => (vec3 -1  1  0)
       (random-gradient #(nth %  2)) => (vec3  1 -1  0)
       (random-gradient #(nth %  3)) => (vec3 -1 -1  0)
       (random-gradient #(nth %  4)) => (vec3  1  0  1)
       (random-gradient #(nth %  5)) => (vec3 -1  0  1)
       (random-gradient #(nth %  6)) => (vec3  1  0 -1)
       (random-gradient #(nth %  7)) => (vec3 -1  0 -1)
       (random-gradient #(nth %  8)) => (vec3  0  1  1)
       (random-gradient #(nth %  9)) => (vec3  0 -1  1)
       (random-gradient #(nth % 10)) => (vec3  0  1 -1)
       (random-gradient #(nth % 11)) => (vec3  0 -1 -1))


(facts "Create a 3D grid with random gradient vectors"
       (map #(dimension-count (random-gradient-grid 1) %) (range 4)) => [1 1 1 3]
       (map #(reduce nth (random-gradient-grid 1 (constantly (vec3 1.0  1.0  0.0))) [0 0 0 %]) (range 3)) => [1.0  1.0  0.0]
       (map #(reduce nth (random-gradient-grid 1 (constantly (vec3 0.0 -1.0 -1.0))) [0 0 0 %]) (range 3)) => [0.0 -1.0 -1.0]
       (map #(dimension-count (random-gradient-grid 2) %) (range 4)) => [2 2 2 3])


(facts "Determine division point belongs to"
       (determine-division (vec3 0.5 0.5 0.5)) => [0 0 0]
       (determine-division (vec3 2.5 3.5 5.5)) => [2 3 5])


(tabular "Get 3D vectors pointing to corners of division"
         (fact (nth (corner-vectors (vec3 2.1 3.2 5.3)) ?i) => (roughly-vector (vec3 ?x ?y ?z) 1e-6))
         ?i ?x    ?y   ?z
         0   0.1  0.2  0.3
         1  -0.9  0.2  0.3
         2   0.1 -0.8  0.3
         3  -0.9 -0.8  0.3
         4   0.1  0.2 -0.7
         5  -0.9  0.2 -0.7
         6   0.1 -0.8 -0.7
         7  -0.9 -0.8 -0.7)


(def identity-array (vec (for [z (range 4)] (vec (for [y (range 4)] (vec (for [x (range 4)] (vec3 x y z))))))))


(tabular "Get 3D gradient vectors from corners of division"
         (fact (nth (corner-gradients identity-array (vec3 ?x ?y ?z)) ?i) => (vec3 ?gx ?gy ?gz))
         ?x  ?y  ?z  ?i ?gx ?gy ?gz
         0.5 0.5 0.5 0  0.0 0.0 0.0
         1.5 0.5 0.5 0  1.0 0.0 0.0
         0.5 1.5 0.5 0  0.0 1.0 0.0
         0.5 0.5 1.5 0  0.0 0.0 1.0
         0.5 0.5 0.5 1  1.0 0.0 0.0
         0.5 0.5 0.5 2  0.0 1.0 0.0
         0.5 0.5 0.5 3  1.0 1.0 0.0
         0.5 0.5 0.5 4  0.0 0.0 1.0
         0.5 0.5 0.5 5  1.0 0.0 1.0
         0.5 0.5 0.5 6  0.0 1.0 1.0
         0.5 0.5 0.5 7  1.0 1.0 1.0
         3.5 3.5 3.5 0  3.0 3.0 3.0
         3.5 3.5 3.5 1  0.0 3.0 3.0
         3.5 3.5 3.5 2  3.0 0.0 3.0
         3.5 3.5 3.5 3  0.0 0.0 3.0
         3.5 3.5 3.5 4  3.0 3.0 0.0
         3.5 3.5 3.5 5  0.0 3.0 0.0
         3.5 3.5 3.5 6  3.0 0.0 0.0
         3.5 3.5 3.5 7  0.0 0.0 0.0)


(facts "Determine influence values"
       (influence-values [] []) => []
       (influence-values [(vec3 2 3 5)] [(vec3 1.0 0.0 0.0)]) => [2.0]
       (influence-values [(vec3 2 3 5)] [(vec3 0.0 1.0 0.0)]) => [3.0]
       (influence-values [(vec3 2 3 5)] [(vec3 0.0 0.0 1.0)]) => [5.0])


(tabular "Monotonous and point-symmetric ease curve"
         (fact (ease-curve ?t) => (roughly ?result 1e-4))
         ?t  ?result
         0.0 0.0
         1.0 1.0
         0.5 0.5
         0.2 0.05792
         0.8 0.94208)


(tabular "Determine weights for interpolation"
         (fact (nth (interpolation-weights ?easing (vec3 ?x ?y ?z)) ?i) => (roughly ?result 1e-4))
         ?easing    ?x  ?y  ?z  ?i ?result
         identity   0.0 0.0 0.0 0  1.0
         identity   0.0 0.0 0.0 1  0.0
         identity   0.3 0.0 0.0 0  0.7
         identity   0.3 0.0 0.0 1  0.3
         identity   0.0 0.3 0.0 0  0.7
         identity   0.0 0.3 0.0 2  0.3
         identity   0.0 0.0 0.3 0  0.7
         identity   0.0 0.0 0.3 4  0.3
         identity   1.3 0.0 0.0 0  0.7
         ease-curve 0.2 0.0 0.0 0  0.94208
         ease-curve 0.0 0.2 0.0 0  0.94208
         ease-curve 0.0 0.0 0.2 0  0.94208)


(def gradient-grid
  [[[(vec3 1 1 0) (vec3 1 0 -1)] [(vec3 1 0 1) (vec3 -1 -1 0)]]
   [[(vec3 1 0 1) (vec3 -1 0 1)] [(vec3 1 0 -1) (vec3 -1 0 -1)]]])


(facts "Normalize values of a vector"
       (normalize-vector [0.0 1.0])     => [0.0 1.0]
       (normalize-vector [0.0 1.0 2.0]) => [0.0 0.5 1.0]
       (normalize-vector [1.0 2.0 3.0]) => [0.0 0.5 1.0]
       (normalize-vector [0.0 1.0])     => vector?)


(facts "Compute a single sample of Perlin noise"
       (perlin-noise-sample gradient-grid 2 4 (vec3 0.5 0.5 0.5)) => (roughly  0.30273 1e-5)
       (perlin-noise-sample gradient-grid 2 4 (vec3 1.5 0.5 0.5)) => (roughly -0.21457 1e-5))


(facts "Create 3D Perlin noise"
       (with-redefs [perlin/random-gradient-grid (fn [n] (fact n => 2) gradient-grid)]
         (nth (perlin-noise 2 4) 0) => (roughly 0.74821 1e-5)
         (count (perlin-noise 2 4)) => 64
         (apply min (perlin-noise 2 4)) => 0.0
         (apply max (perlin-noise 2 4)) => 1.0))
