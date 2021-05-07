(ns sfsim25.matrix3x3-test
  (:refer-clojure :exclude [* -])
  (:require [midje.sweet :refer :all]
            [sfsim25.matrix3x3 :refer :all]
            [sfsim25.vector3 :refer (->Vector3)]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(fact "Convert 3x3 matrix to sequence"
  (vals (->Matrix3x3 1 2 3, 4 5 6, 7 8 9)) => [1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0])

(fact "3D matrix-vector multiplication"
  (* (->Matrix3x3 1 2 3, 4 5 6, 7 8 9) (->Vector3 1 2 3)) => (->Vector3 14 32 50))

(fact "Matrix norm"
  (norm (->Matrix3x3 1 2 3, 4 5 6, 7 8 9)) => (Math/sqrt 285))

(fact "Difference of matrices"
  (- (->Matrix3x3 3 5 7, 11 13 17, 19 23 29) (->Matrix3x3 2 3 5, 7 11 13, 17 19 23)) => (->Matrix3x3 1 2 2, 4 2 4, 2 4 6))

(fact "Negate matrix"
  (- (->Matrix3x3 1 2 3, 4 5 6, 7 8 9)) => (->Matrix3x3 -1 -2 -3, -4 -5 -6, -7 -8 -9))

(fact "Identity matrix"
  (identity-matrix) => (->Matrix3x3 1 0 0, 0 1 0, 0 0 1))

(def pi Math/PI)
(def ca (/ (Math/sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(defn approx-matrix [m] (fn [x] (< (norm (- m x)) 1e-6)))

(facts "Rotation matrices"
  (rotation-x (/ pi 6)) => (approx-matrix (->Matrix3x3 1 0 0, 0 ca -sa, 0 sa ca))
  (rotation-y (/ pi 6)) => (approx-matrix (->Matrix3x3 ca 0 sa, 0 1 0, -sa 0 ca))
  (rotation-z (/ pi 6)) => (approx-matrix (->Matrix3x3 ca -sa 0, sa ca 0, 0 0 1)))

(facts "Comvert rotation quaternion to rotation matrix"
  (quaternion->matrix (->Quaternion 1 0 0 0))                => (approx-matrix (->Matrix3x3 1 0 0 0 1 0 0 0 1))
  (quaternion->matrix (rotation (/ pi 6) (->Vector3 1 0 0))) => (approx-matrix (rotation-x (/ pi 6)))
  (quaternion->matrix (rotation (/ pi 6) (->Vector3 0 1 0))) => (approx-matrix (rotation-y (/ pi 6)))
  (quaternion->matrix (rotation (/ pi 6) (->Vector3 0 0 1))) => (approx-matrix (rotation-z (/ pi 6))))
