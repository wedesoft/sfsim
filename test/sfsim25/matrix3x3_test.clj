(ns sfsim25.matrix3x3-test
  (:refer-clojure :exclude [* -])
  (:require [clojure.test :refer :all]
            [sfsim25.matrix3x3 :refer :all]
            [sfsim25.vector3 :refer (vector3)]))

(deftest display-test
  (testing "Display 3x3 matrix"
    (is (= "(matrix3x3 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0)" (str (matrix3x3 1 2 3 4 5 6 7 8 9))))))

(deftest matrix-vector-dot-test
  (testing "Matrix-vector multiplication"
    (is (= (vector3 14 32 50) (* (matrix3x3 1 2 3 4 5 6 7 8 9) (vector3 1 2 3))))))

(deftest matrix-norm-test
  (testing "Matrix norm"
    (is (= (Math/sqrt 285) (norm (matrix3x3 1 2 3 4 5 6 7 8 9))))))

(deftest matrix-difference-test
  (testing "Difference of matrices"
    (is (= (matrix3x3 1 2 2 4 2 4 2 4 6) (- (matrix3x3 3 5 7 11 13 17 19 23 29) (matrix3x3 2 3 5 7 11 13 17 19 23))))))

(deftest negate-matrix-test
  (testing "Negate matrix"
    (is (= (matrix3x3 -1 -2 -3 -4 -5 -6 -7 -8 -9) (- (matrix3x3 1 2 3 4 5 6 7 8 9))))))

(def pi Math/PI)
(def ca (/ (Math/sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(deftest rotation-matrix-test
  (testing "Rotation matrices"
    (is (< (norm (- (matrix3x3 1 0 0 0 ca -sa 0 sa ca) (rotation-x (/ pi 6)))) 1e-6))
    (is (< (norm (- (matrix3x3 ca 0 sa 0 1 0 -sa 0 ca) (rotation-y (/ pi 6)))) 1e-6))
    (is (< (norm (- (matrix3x3 ca -sa 0 sa ca 0 0 0 1) (rotation-z (/ pi 6)))) 1e-6))))
