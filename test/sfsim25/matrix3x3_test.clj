(ns sfsim25.matrix3x3-test
  (:refer-clojure :exclude [* -])
  (:require [clojure.test :refer :all]
            [sfsim25.matrix3x3 :refer :all]
            [sfsim25.vector3 :refer (->Vector3)]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(deftest display-test
  (testing "Display 3x3 matrix"
    (is (= "(matrix3x3 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0)" (str (->Matrix3x3 1 2 3, 4 5 6, 7 8 9))))))

(deftest seqable-test
  (testing "Convert 3x3 matrix to sequence"
    (is (= [1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0] (seq (->Matrix3x3 1 2 3, 4 5 6, 7 8 9))))))

(deftest matrix-vector-dot-test
  (testing "3D matrix-vector multiplication"
    (is (= (->Vector3 14 32 50) (* (->Matrix3x3 1 2 3, 4 5 6, 7 8 9) (->Vector3 1 2 3))))))

(deftest matrix-norm-test
  (testing "Matrix norm"
    (is (= (Math/sqrt 285) (norm (->Matrix3x3 1 2 3, 4 5 6, 7 8 9))))))

(deftest matrix-difference-test
  (testing "Difference of matrices"
    (is (= (->Matrix3x3 1 2 2, 4 2 4, 2 4 6) (- (->Matrix3x3 3 5 7, 11 13 17, 19 23 29) (->Matrix3x3 2 3 5, 7 11 13, 17 19 23))))))

(deftest negate-matrix-test
  (testing "Negate matrix"
    (is (= (->Matrix3x3 -1 -2 -3, -4 -5 -6, -7 -8 -9) (- (->Matrix3x3 1 2 3, 4 5 6, 7 8 9))))))

(deftest identity-matrix-test
  (testing "Identity matrix"
    (is (= (->Matrix3x3 1 0 0, 0 1 0, 0 0 1) (identity-matrix)))))

(def pi Math/PI)
(def ca (/ (Math/sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(deftest rotation-matrix-test
  (testing "Rotation matrices"
    (is (< (norm (- (->Matrix3x3 1 0 0, 0 ca -sa, 0 sa ca) (rotation-x (/ pi 6)))) 1e-6))
    (is (< (norm (- (->Matrix3x3 ca 0 sa, 0 1 0, -sa 0 ca) (rotation-y (/ pi 6)))) 1e-6))
    (is (< (norm (- (->Matrix3x3 ca -sa 0, sa ca 0, 0 0 1) (rotation-z (/ pi 6)))) 1e-6))))

(deftest quaternion->matrix-test
  (testing "Convert quaternion to rotation matrix"
    (is (< (norm (- (->Matrix3x3 1 0 0 0 1 0 0 0 1) (quaternion->matrix (->Quaternion 1 0 0 0)))) 1e-6))
    (is (< (norm (- (rotation-x (/ pi 6)) (quaternion->matrix (rotation (/ pi 6) (->Vector3 1 0 0))))) 1e-6))
    (is (< (norm (- (rotation-y (/ pi 6)) (quaternion->matrix (rotation (/ pi 6) (->Vector3 0 1 0))))) 1e-6))
    (is (< (norm (- (rotation-z (/ pi 6)) (quaternion->matrix (rotation (/ pi 6) (->Vector3 0 0 1))))) 1e-6))))
