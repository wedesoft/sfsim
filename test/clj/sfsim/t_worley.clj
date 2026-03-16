;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-worley
  (:require
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.util :refer (dimension-count)]
    [sfsim.worley :refer :all :as worley]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Create a 3D grid with a random point in each cell"
       (map #(dimension-count (random-point-grid 1 1) %) (range 4)) => [1 1 1 3]
       (map #(reduce nth (random-point-grid 1 1 identity) [0 0 0 %]) (range 3)) => [1.0 1.0 1.0]
       (map #(dimension-count (random-point-grid 2 1) %) (range 4)) => [2 2 2 3]
       (reduce nth (random-point-grid 2 8 identity) [0 0 0 0]) => 4.0
       (reduce nth (random-point-grid 2 8 identity) [0 0 1 0]) => 8.0
       (reduce nth (random-point-grid 2 8 identity) [0 1 0 1]) => 8.0
       (reduce nth (random-point-grid 2 8 identity) [1 0 0 2]) => 8.0)


(facts "Extract point from specified cell"
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 0 0 0) => (vec3 1 2 3)
       (extract-point-from-grid [[[(vec3 1 2 3) (vec3 4 5 6)]]] 10 0 0 1) => (vec3 4 5 6)
       (extract-point-from-grid [[[(vec3 1 2 3)] [(vec3 4 5 6)]]] 10 0 1 0) => (vec3 4 5 6)
       (extract-point-from-grid [[[(vec3 1 2 3)]] [[(vec3 4 5 6)]]] 10 1 0 0) => (vec3 4 5 6)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 0 0 1) => (vec3 11 2 3)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 0 1 0) => (vec3 1 12 3)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 1 0 0) => (vec3 1 2 13)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 0 0 -1) => (vec3 -9 2 3)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 0 -1 0) => (vec3 1 -8 3)
       (extract-point-from-grid [[[(vec3 1 2 3)]]] 10 -1 0 0) => (vec3 1 2 -7))


(facts "Closest distance of point in grid"
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1)]]] 1 2 (vec3 1 1 1)) => 0.0
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1)]]] 1 2 (vec3 0.5 1 1)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1) (vec3 3 1 1)]]] 2 4 (vec3 3 1 1)) => 0.0
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1)] [(vec3 1 3 1)]]] 2 4 (vec3 1 3 1)) => 0.0
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1)]] [[(vec3 1 1 3)]]] 2 4 (vec3 1 1 3)) => 0.0
       (closest-distance-to-point-in-grid [[[(vec3 0.25 1 1)]]] 1 2 (vec3 1.75 1 1)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1.75 1 1)]]] 1 2 (vec3 0.25 1 1)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1 0.25 1)]]] 1 2 (vec3 1 1.75 1)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1 1.75 1)]]] 1 2 (vec3 1 0.25 1)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1 1 0.25)]]] 1 2 (vec3 1 1 1.75)) => 0.5
       (closest-distance-to-point-in-grid [[[(vec3 1 1 1.75)]]] 1 2 (vec3 1 1 0.25)) => 0.5)


(facts "Normalize values of a vector"
       (normalize-vector [1.0])         => [1.0]
       (normalize-vector [0.0 1.0 2.0]) => [0.0 0.5 1.0]
       (normalize-vector [1.0])         => vector?)


(facts "Invert values of a vector"
       (invert-vector []) => []
       (invert-vector [0.0]) => [1.0]
       (invert-vector [1.0]) => [0.0]
       (invert-vector [1.0]) => vector?)


(facts "Create 3D Worley noise"
       (with-redefs [worley/random-point-grid (fn [^long n ^long size] (facts n => 1 size => 2) [[[(vec3 0.5 0.5 0.5)]]])]
         (nth (worley-noise 1 2) 0) => 1.0
         (count (worley-noise 1 2)) => 8
         (apply min (worley-noise 1 2)) => 0.0))
