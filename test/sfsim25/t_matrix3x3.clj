(ns sfsim25.t-matrix3x3
  (:refer-clojure :exclude [* -])
  (:require [clojure.core :as c]
            [midje.sweet :refer :all]
            [sfsim25.matrix3x3 :refer :all]
            [sfsim25.vector3 :refer (->Vector3)]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(def pi Math/PI)

(defn roughly-matrix [m] (fn [x] (< (norm (- m x)) 1e-6)))

(fact "Multiply two 3x3 matrices"
  (x (->Matrix3x3 1 2 3, 4 5 6, 7 8 9) (->Matrix3x3 2 3 4, 5 6 7, 8 9 10)) => (->Matrix3x3 36 42 48, 81 96 111, 126 150 174))

(facts "Get permutations of vector"
  (permutations []) => [[]]
  (permutations [1]) => [[1]]
  (permutations [1 2]) => [[1 2] [2 1]]
  (permutations [1 2 3]) => [[1 2 3] [1 3 2] [2 1 3] [2 3 1] [3 1 2] [3 2 1]])

(facts "Count number of inversions in a permutation"
  (count-inversions [1 2 3]) => 0
  (count-inversions [2 1 3]) => 1
  (count-inversions [3 1 2]) => 2
  (count-inversions [3 2 1]) => 3)

(facts "Parity of permutation"
  (parity-of-permutation [1 2 3]) => (exactly c/+)
  (parity-of-permutation [2 1 3]) => (exactly c/-)
  (parity-of-permutation [3 1 2]) => (exactly c/+)
  (parity-of-permutation [3 2 1]) => (exactly c/-))

(fact "Determinant of 3x3 matrix"
  (determinant3x3 (->Matrix3x3 2 3 5, 7 11 13, 17 19 23)) => -78.0)
