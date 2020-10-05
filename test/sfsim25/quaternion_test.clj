(ns sfsim25.quaternion-test
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
    (is (= (make-quaternion 6 8 10 12) (add (make-quaternion 1 2 3 4) (make-quaternion 5 6 7 8))))))

(deftest subtract-test
  (testing "Subtract two quaternions"
    (is (= (make-quaternion 1 2 3 4) (subtract (make-quaternion 6 8 10 12) (make-quaternion 5 6 7 8))))))

(deftest multiply-test
  (testing "Multiply two quaternions"
    (are [result a b] (= result (multiply a b))
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
