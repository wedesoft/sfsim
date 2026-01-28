;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-quaternion
  (:refer-clojure :exclude [+ - *])
  (:require
    [clojure.core :as c]
    [clojure.math :refer (PI E)]
    [fastmath.vector :refer (vec3 mag dot)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.quaternion :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(def o (->Quaternion  1  0  0  0))
(def -o (->Quaternion -1  0  0  0))
(def i (->Quaternion  0  1  0  0))
(def -i (->Quaternion  0 -1  0  0))
(def j (->Quaternion  0  0  1  0))
(def -j (->Quaternion  0  0 -1  0))
(def k (->Quaternion  0  0  0  1))
(def -k (->Quaternion  0  0  0 -1))


(facts "Get components of quaternion"
       (:real (->Quaternion 2 3 5 7)) => 2.0
       (:imag (->Quaternion 2 3 5 7)) => 3.0
       (:jmag (->Quaternion 2 3 5 7)) => 5.0
       (:kmag (->Quaternion 2 3 5 7)) => 7.0)


(fact "Add two quaternions"
      (+ (->Quaternion 1 2 3 4) (->Quaternion 5 6 7 8)) => (->Quaternion 6 8 10 12))


(fact "Subtract two quaternions"
      (- (->Quaternion 6 8 10 12) (->Quaternion 5 6 7 8)) => (->Quaternion 1 2 3 4))


(fact "Negate quaternions"
      (- (->Quaternion 2 3 5 7)) => (->Quaternion -2 -3 -5 -7))


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


(fact "Scale quaternion by real number"
      (scale (->Quaternion 2 3 5 7) 2.0) => (->Quaternion 4 6 10 14))


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
       (vector->quaternion (vec3 2 3 5))           => (->Quaternion 0 2 3 5)
       (quaternion->vector (->Quaternion 0 2 3 5)) => (vec3 2.0 3.0 5.0))


(def -E (c/- E))


(tabular "Exponentation of quaternions"
         (fact (?component (exp ?q)) => (roughly ?result 1e-6))
         ?q                                           ?component    ?result
         (->Quaternion 0 0 PI 0)                      :real         -1.0
         (->Quaternion 0 0 PI 0)                      :jmag          0.0
         (->Quaternion 0 0 (/ PI 2) 0)                :real          0.0
         (->Quaternion 0 0 (/ PI 2) 0)                :jmag          1.0
         (->Quaternion 1 0 PI 0)                      :real         -E
         (->Quaternion 1 0 (/ PI 2) 0)                :jmag          E
         (->Quaternion 0 0 0 PI)                      :kmag          0.0
         (->Quaternion 0 0 0 (/ PI 2))                :kmag          1.0
         (->Quaternion 0 (c/* 0.4 PI) (c/* 0.3 PI) 0) :imag          0.8
         (->Quaternion 0 (c/* 0.4 PI) (c/* 0.3 PI) 0) :jmag          0.6)


(tabular "Represent rotation using quaternion"
         (fact (?component (rotation ?angle ?axis)) => (roughly ?result 1e-6))
         ?axis                ?angle     ?component    ?result
         (vec3 0 0 1)         0.0        :real         1.0
         (vec3 0 0 1)         (c/* 2 PI) :real        -1.0
         (vec3 0.36 0.48 0.8) PI         :imag         0.36
         (vec3 0.36 0.48 0.8) PI         :kmag         0.8
         (vec3 0.36 0.48 0.8) (/ PI 3)   :imag         0.18)


(facts "Generate orthogonal vector"
       (dot (orthogonal (vec3 1 0 0)) (vec3 1 0 0)) => 0.0
       (mag (orthogonal (vec3 1 0 0))) => 1.0
       (mag (orthogonal (vec3 2 0 0))) => 1.0
       (dot (orthogonal (vec3 0 1 0)) (vec3 0 1 0)) => 0.0
       (mag (orthogonal (vec3 0 1 0))) => 1.0
       (mag (orthogonal (vec3 0 2 0))) => 1.0
       (dot (orthogonal (vec3 0 0 1)) (vec3 0 0 1)) => 0.0
       (mag (orthogonal (vec3 0 0 1))) => 1.0
       (mag (orthogonal (vec3 0 0 2))) => 1.0)


(facts "Rotate a vector using a rotation quaternion"
       (rotate-vector (rotation 0.0 (vec3 1 0 0)) (vec3 2 4 8))        => (roughly-vector (vec3 2  4 8) 1e-6)
       (rotate-vector (rotation (/ PI 2) (vec3 1 0 0)) (vec3 2 4 8)) => (roughly-vector (vec3 2 -8 4) 1e-6))


(fact "Rotate vector u onto vector b"
      (rotate-vector (vector-to-vector-rotation (vec3 1 0 0) (vec3 1 0 0)) (vec3 1 0 0)) => (roughly-vector (vec3 1 0 0) 1e-6)
      (rotate-vector (vector-to-vector-rotation (vec3 1 0 0) (vec3 0 1 0)) (vec3 1 0 0)) => (roughly-vector (vec3 0 1 0) 1e-6)
      (rotate-vector (vector-to-vector-rotation (vec3 2 0 0) (vec3 0 3 0)) (vec3 2 0 0)) => (roughly-vector (vec3 0 2 0) 1e-6)
      (rotate-vector (vector-to-vector-rotation (vec3 1 0 0) (vec3 -1 0 0)) (vec3 2 0 0)) => (roughly-vector (vec3 -2 0 0) 1e-6))
