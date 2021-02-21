(ns sfsim25.quaternion-test
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.test :refer :all]
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

(deftest display-test
  (testing "Display quaternion"
    (is (= "(quaternion 2.0 3.0 5.0 7.0)" (str (->Quaternion 2 3 5 7))))))

(deftest component-test
  (testing "Get components of quaternion"
    (is (= 2.0 (real (->Quaternion 2 3 5 7))))
    (is (= 3.0 (imag (->Quaternion 2 3 5 7))))
    (is (= 5.0 (jmag (->Quaternion 2 3 5 7))))
    (is (= 7.0 (kmag (->Quaternion 2 3 5 7))))))

(deftest add-test
  (testing "Add two quaternions"
    (is (= (->Quaternion 6 8 10 12) (+ (->Quaternion 1 2 3 4) (->Quaternion 5 6 7 8))))))

(deftest subtract-test
  (testing "Subtract two quaternions"
    (is (= (->Quaternion 1 2 3 4) (- (->Quaternion 6 8 10 12) (->Quaternion 5 6 7 8))))))

(deftest multiply-test
  (testing "Multiply two quaternions"
    (are [result a b] (= result (* a b))
       o o o
       i o i
       j o j
       k o k
       i i o
      -o i i
       k i j
      -j i k
       j j o
      -k j i
      -o j j
       i j k
       k k o
       j k i
      -i k j
      -o k k)))

(deftest norm-test
  (testing "Norm of quaternion"
    (is (= 1.0 (norm (->Quaternion 0.216 0.288 0.48 0.8))))))

(deftest normalize-test
  (testing "Normalize a quaternion"
    (is
      (let [q (->Quaternion 0.216 0.288 0.48 0.8)]
        (= q (normalize (* (->Quaternion 2 0 0 0) q)))))))

(deftest conjugate-test
  (testing "Conjugate of quaternion"
    (is (= (->Quaternion 2 -3 -5 -7) (conjugate (->Quaternion 2 3 5 7))))))

(deftest inverse-test
  (testing "Inverse of quaternion"
    (is (= (->Quaternion 0.216 -0.288 -0.48 -0.8) (inverse (->Quaternion 0.216 0.288 0.48 0.8))))
    (is (= (->Quaternion 0.108 -0.144 -0.24 -0.4) (inverse (->Quaternion 0.432 0.576 0.96 1.6))))))

(deftest vector-conversion-test
  (testing "Convert 3D vector to quaternion and back"
    (is (= (->Quaternion 0 2 3 5) (vector3->quaternion (->Vector3 2 3 5))))
    (is (= (->Vector3 2 3 5) (quaternion->vector3 (->Quaternion 0 2 3 5))))))

(def pi Math/PI)
(def e (Math/exp 1))
(def -e (c/- e))

(deftest exp-test
  (testing "Exponentiation of quaternions"
    (are [result component q] (< (Math/abs (c/- result (component (exp q)))) 1e-6)
      -1.0 real (->Quaternion 0 0 pi 0)
       0.0 jmag (->Quaternion 0 0 pi 0)
       0.0 real (->Quaternion 0 0 (/ pi 2) 0)
       1.0 jmag (->Quaternion 0 0 (/ pi 2) 0)
      -e   real (->Quaternion 1 0 pi 0)
       e   jmag (->Quaternion 1 0 (/ pi 2) 0)
       0.0 kmag (->Quaternion 0 0 0 pi)
       1.0 kmag (->Quaternion 0 0 0 (/ pi 2))
       0.8 imag (->Quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0)
       0.6 jmag (->Quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0))))

(deftest rotation-test
  (testing "Represent rotation using quaternion"
    (are [result component angle axis] (< (Math/abs (c/- result (component (rotation angle axis)))) 1e-6)
       1.0  real 0          (->Vector3 0 0 1)
      -1.0  real (c/* 2 pi) (->Vector3 0 0 1)
       0.36 imag pi         (->Vector3 0.36 0.48 0.8)
       0.8  kmag pi         (->Vector3 0.36 0.48 0.8)
       0.18 imag (/ pi 3)   (->Vector3 0.36 0.48 0.8))))

(deftest rotate-vector-test
  (testing "Rotate a vector using a rotation quaternion"
    (is (= (->Vector3 2 4 8) (rotate-vector (rotation 0 (->Vector3 1 0 0)) (->Vector3 2 4 8))))
    (is (< (v/norm (v/- (->Vector3 2 -8 4) (rotate-vector (rotation (/ pi 2) (->Vector3 1 0 0)) (->Vector3 2 4 8)))) 1e-6))))
