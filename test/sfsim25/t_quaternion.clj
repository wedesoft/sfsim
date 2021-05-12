(ns sfsim25.t-quaternion
  (:refer-clojure :exclude [+ - *])
  (:require [midje.sweet :refer :all]
            [clojure.core :as c]
            [sfsim25.quaternion :refer :all]
            [sfsim25.vector3 :refer (->Vector3) :as v]))

(def  o (->Quaternion  1  0  0  0))
(def -o (->Quaternion -1  0  0  0))
(def  i (->Quaternion  0  1  0  0))
(def -i (->Quaternion  0 -1  0  0))
(def  j (->Quaternion  0  0  1  0))
(def -j (->Quaternion  0  0 -1  0))
(def  k (->Quaternion  0  0  0  1))
(def -k (->Quaternion  0  0  0 -1))

(facts "Get components of quaternion"
  (:a (->Quaternion 2 3 5 7)) => 2.0
  (:b (->Quaternion 2 3 5 7)) => 3.0
  (:c (->Quaternion 2 3 5 7)) => 5.0
  (:d (->Quaternion 2 3 5 7)) => 7.0)

(fact "Add two quaternions"
  (+ (->Quaternion 1 2 3 4) (->Quaternion 5 6 7 8)) => (->Quaternion 6 8 10 12))

(fact "Subtract two quaternions"
  (- (->Quaternion 6 8 10 12) (->Quaternion 5 6 7 8)) => (->Quaternion 1 2 3 4))

(tabular "Multiply two quaternions"
  (fact (* ?a ?b) => ?c)
  ?a ?b ?c
  o  o  o
  o  i  i
  o  j  j
  o  k  k
  i  o  i
  i  i -o
  i  j  k
  i  k -j
  j  o  j
  j  i -k
  j  j -o
  j  k  i
  k  o  k
  k  i  j
  k  j -i
  k  k -o)

(fact "Norm of quaternion"
  (norm (->Quaternion 0.216 0.288 0.48 0.8)) => 1.0)

(fact "Normalize a quaternion"
  (normalize (->Quaternion (c/* 2 0.216) (c/* 2 0.288) (c/* 2 0.48) (c/* 2 0.8))) => (->Quaternion 0.216 0.288 0.48 0.8))

(fact "Conjugate of quaternion"
  (conjugate (->Quaternion 2 3 5 7)) => (->Quaternion 2 -3 -5 -7))

(facts "Inverse of quaternion"
  (inverse (->Quaternion 0.216 0.288 0.48 0.8)) => (->Quaternion 0.216 -0.288 -0.48 -0.8)
  (inverse (->Quaternion 0.432 0.576 0.96 1.6)) => (->Quaternion 0.108 -0.144 -0.24 -0.4))

(facts "Convert 3D vector to quaternion and back"
  (vector3->quaternion (->Vector3 2 3 5))      => (->Quaternion 0 2 3 5)
  (quaternion->vector3 (->Quaternion 0 2 3 5)) => (->Vector3 2 3 5))

(def pi Math/PI)
(def e (Math/exp 1))
(def -e (c/- e))

(tabular "Exponentation of quaternions"
  (fact (?component (exp ?q)) => (roughly ?result 1e-6))
  ?q                                           ?component ?result
  (->Quaternion 0 0 pi 0)                      :a         -1.0
  (->Quaternion 0 0 pi 0)                      :c          0.0
  (->Quaternion 0 0 (/ pi 2) 0)                :a          0.0
  (->Quaternion 0 0 (/ pi 2) 0)                :c          1.0
  (->Quaternion 1 0 pi 0)                      :a         -e
  (->Quaternion 1 0 (/ pi 2) 0)                :c          e
  (->Quaternion 0 0 0 pi)                      :d          0.0
  (->Quaternion 0 0 0 (/ pi 2))                :d          1.0
  (->Quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0) :b          0.8
  (->Quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0) :c          0.6)

(tabular "Represent rotation using quaternion"
  (fact (?component (rotation ?angle ?axis)) => (roughly ?result 1e-6))
  ?axis                     ?angle     ?component ?result
  (->Vector3 0 0 1)         0          :a         1.0
  (->Vector3 0 0 1)         (c/* 2 pi) :a        -1.0
  (->Vector3 0.36 0.48 0.8) pi         :b         0.36
  (->Vector3 0.36 0.48 0.8) pi         :d         0.8
  (->Vector3 0.36 0.48 0.8) (/ pi 3)   :b         0.18)

(defn roughly-vector [y] (fn [x] (< (v/norm (v/- y x)) 1e-6)))

(facts "Rotate a vector using a rotation quaternion"
  (rotate-vector (rotation 0 (->Vector3 1 0 0)) (->Vector3 2 4 8))        => (roughly-vector (->Vector3 2  4 8))
  (rotate-vector (rotation (/ pi 2) (->Vector3 1 0 0)) (->Vector3 2 4 8)) => (roughly-vector (->Vector3 2 -8 4)))
