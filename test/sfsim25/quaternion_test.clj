(ns sfsim25.quaternion-test
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.test :refer :all]
            [sfsim25.quaternion :refer :all]))

(def o (make-quaternion 1 0 0 0))
(def -o (make-quaternion -1 0 0 0))
(def i (make-quaternion 0 1 0 0))
(def -i (make-quaternion 0 -1 0 0))
(def j (make-quaternion 0 0 1 0))
(def -j (make-quaternion 0 0 -1 0))
(def k (make-quaternion 0 0 0 1))
(def -k (make-quaternion 0 0 0 -1))

(deftest display-test
  (testing "Display quaternion"
    (is (= "(quaternion 2.0 3.0 5.0 7.0)" (str (make-quaternion 2 3 5 7))))))

(deftest add-test
  (testing "Add two quaternions"
    (is (= (make-quaternion 6 8 10 12) (+ (make-quaternion 1 2 3 4) (make-quaternion 5 6 7 8))))))

(deftest subtract-test
  (testing "Subtract two quaternions"
    (is (= (make-quaternion 1 2 3 4) (- (make-quaternion 6 8 10 12) (make-quaternion 5 6 7 8))))))

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
    (is (= 1.0 (norm (make-quaternion 0.216 0.288 0.48 0.8))))))

(deftest normalize-test
  (testing "Normalize a quaternion"
    (is
      (let [q (make-quaternion 0.216 0.288 0.48 0.8)]
        (= q (normalize (* (make-quaternion 2 0 0 0) q)))))))

(deftest conjugate-test
  (testing "Conjugate of quaternion"
    (is (= (make-quaternion 0.216 -0.288 -0.48 -0.8) (conjugate (make-quaternion 0.216 0.288 0.48 0.8))))
    (is (= (make-quaternion 0.108 -0.144 -0.24 -0.4) (conjugate (make-quaternion 0.432 0.576 0.96 1.6))))))
