(ns sfsim25.quaternion-test
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.test :refer :all]
            [clojure.core :as c]
            [sfsim25.quaternion :refer :all]
            [sfsim25.vector3 :refer [vector3]]))

(def o (quaternion 1 0 0 0))
(def -o (quaternion -1 0 0 0))
(def i (quaternion 0 1 0 0))
(def -i (quaternion 0 -1 0 0))
(def j (quaternion 0 0 1 0))
(def -j (quaternion 0 0 -1 0))
(def k (quaternion 0 0 0 1))
(def -k (quaternion 0 0 0 -1))

(deftest display-test
  (testing "Display quaternion"
    (is (= "(quaternion 2.0 3.0 5.0 7.0)" (str (quaternion 2 3 5 7))))))

(deftest component-test
  (testing "Get components of quaternion"
    (is (= 2.0 (real (quaternion 2 3 5 7))))
    (is (= 3.0 (imag (quaternion 2 3 5 7))))
    (is (= 5.0 (jmag (quaternion 2 3 5 7))))
    (is (= 7.0 (kmag (quaternion 2 3 5 7))))))

(deftest add-test
  (testing "Add two quaternions"
    (is (= (quaternion 6 8 10 12) (+ (quaternion 1 2 3 4) (quaternion 5 6 7 8))))))

(deftest subtract-test
  (testing "Subtract two quaternions"
    (is (= (quaternion 1 2 3 4) (- (quaternion 6 8 10 12) (quaternion 5 6 7 8))))))

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
    (is (= 1.0 (norm (quaternion 0.216 0.288 0.48 0.8))))))

(deftest normalize-test
  (testing "Normalize a quaternion"
    (is
      (let [q (quaternion 0.216 0.288 0.48 0.8)]
        (= q (normalize (* (quaternion 2 0 0 0) q)))))))

(deftest conjugate-test
  (testing "Conjugate of quaternion"
    (is (= (quaternion 2 -3 -5 -7) (conjugate (quaternion 2 3 5 7))))))

(deftest inverse-test
  (testing "Inverse of quaternion"
    (is (= (quaternion 0.216 -0.288 -0.48 -0.8) (inverse (quaternion 0.216 0.288 0.48 0.8))))
    (is (= (quaternion 0.108 -0.144 -0.24 -0.4) (inverse (quaternion 0.432 0.576 0.96 1.6))))))

(deftest vector-conversion-test
  (testing "Convert 3D vector to quaternion and back"
    (is (= (quaternion 0 2 3 5) (vector3->quaternion (vector3 2 3 5))))
    (is (= (vector3 2 3 5) (quaternion->vector3 (quaternion 0 2 3 5))))))

(def pi Math/PI)
(def e (Math/exp 1))
(def -e (c/- e))

(deftest exp-test
  (testing "Exponentiation of quaternions"
    (are [result component q] (< (Math/abs (c/- result (component (exp q)))) 1e-6)
      -1.0 real (quaternion 0 0 pi 0)
       0.0 jmag (quaternion 0 0 pi 0)
       0.0 real (quaternion 0 0 (/ pi 2) 0)
       1.0 jmag (quaternion 0 0 (/ pi 2) 0)
      -e   real (quaternion 1 0 pi 0)
       e   jmag (quaternion 1 0 (/ pi 2) 0)
       0.0 kmag (quaternion 0 0 0 pi)
       1.0 kmag (quaternion 0 0 0 (/ pi 2))
       0.8 imag (quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0)
       0.6 jmag (quaternion 0 (c/* 0.4 pi) (c/* 0.3 pi) 0))))
