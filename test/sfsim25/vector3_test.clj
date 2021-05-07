(ns sfsim25.vector3-test
  (:refer-clojure :exclude [+ - *])
  (:require [midje.sweet :refer :all]
            [clojure.core :as c]
            [sfsim25.vector3 :refer :all]))

(facts "Get components of 3D vector"
  (:x (->Vector3 2 3 5)) => 2.0
  (:y (->Vector3 2 3 5)) => 3.0
  (:z (->Vector3 2 3 5)) => 5.0)

(fact "Convert vector to sequence"
  (vals (->Vector3 2 3 5)) => [2.0 3.0 5.0])

(fact "Norm of 3D vector"
  (norm (->Vector3 0.36 0.48 0.8)) => 1.0)

(fact "Normalization of 3D vector"
  (normalize (->Vector3 3 0 0)) => (->Vector3 1 0 0))

(fact "Add vectors"
  (+ (->Vector3 2 3 5))                                      => (->Vector3  2  3  5)
  (+ (->Vector3 2 3 5) (->Vector3 3 5 7))                    => (->Vector3  5  8 12)
  (+ (->Vector3 2 3 5) (->Vector3 3 5 7) (->Vector3 5 7 11)) => (->Vector3 10 15 23))

(fact "Subtract two vectors"
  (- (->Vector3 5 8 12) (->Vector3 3 5 7)) => (->Vector3 2 3 5))

(fact "Negate vector"
  (- (->Vector3 2 3 -5)) => (->Vector3 -2 -3 5))

(tabular
  (fact "Cross product of two vectors"
    (cross-product ?x ?y) => ?z)
    ?x                ?y                ?z
    (->Vector3 1 0 0) (->Vector3 0 1 0) (->Vector3  0  0  1)
    (->Vector3 1 0 0) (->Vector3 0 0 1) (->Vector3  0 -1  0)
    (->Vector3 0 1 0) (->Vector3 1 0 0) (->Vector3  0  0 -1)
    (->Vector3 0 1 0) (->Vector3 0 0 1) (->Vector3  1  0  0)
    (->Vector3 0 0 1) (->Vector3 1 0 0) (->Vector3  0  1  0)
    (->Vector3 0 0 1) (->Vector3 0 1 0) (->Vector3 -1  0  0))

(fact "Scaling of vector"
  (* 2 (->Vector3 2 3 5)) => (->Vector3 4 6 10))

(fact "Dot product of two vectors"
  (inner-product (->Vector3 2 3 5) (->Vector3 7 11 13)) => (float (c/+ (c/* 2 7) (c/* 3 11) (c/* 5 13))))
