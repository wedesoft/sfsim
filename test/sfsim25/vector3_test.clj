(ns sfsim25.vector3-test
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.test :refer :all]
            [clojure.core :as c]
            [sfsim25.vector3 :refer :all]))

(deftest display-test
  (testing "Display 3D vector"
    (is (= "(vector3 2.0 3.0 5.0)" (str (->Vector3 2 3 5))))))

(deftest component-test
  (testing "Get components of 3D vector"
    (is (= 2.0 (x (->Vector3 2 3 5))))
    (is (= 3.0 (y (->Vector3 2 3 5))))
    (is (= 5.0 (z (->Vector3 2 3 5))))))

(deftest seqable-test
  (testing "Convert vector to sequence"
    (is (= [2.0 3.0 5.0] (seq (->Vector3 2 3 5))))))

(deftest norm-test
  (testing "Norm of 3D vector"
    (is (= 1.0 (norm (->Vector3 0.36 0.48 0.8))))))

(deftest normalize-test
  (testing "Normalization of 3D vector"
    (is (= (->Vector3 1 0 0) (normalize (->Vector3 3 0 0))))))

(deftest add-test
  (testing "Add vectors"
    (is (= (->Vector3 2 3 5) (+ (->Vector3 2 3 5))))
    (is (= (->Vector3 5 8 12) (+ (->Vector3 2 3 5) (->Vector3 3 5 7))))
    (is (= (->Vector3 10 15 23) (+ (->Vector3 2 3 5) (->Vector3 3 5 7) (->Vector3 5 7 11))))))

(deftest subtract-test
  (testing "Subtract two vectors"
    (is (= (->Vector3 2 3 5) (- (->Vector3 5 8 12) (->Vector3 3 5 7))))))

(deftest negative-test
  (testing "Negate vector"
    (is (= (->Vector3 -2 -3 5) (- (->Vector3 2 3 -5))))))

(deftest cross-product-test
  (testing "Cross product of two vectors"
    (is (= (->Vector3  0  0  1) (cross-product (->Vector3 1 0 0) (->Vector3 0 1 0))))
    (is (= (->Vector3  0 -1  0) (cross-product (->Vector3 1 0 0) (->Vector3 0 0 1))))
    (is (= (->Vector3  0  0 -1) (cross-product (->Vector3 0 1 0) (->Vector3 1 0 0))))
    (is (= (->Vector3  1  0  0) (cross-product (->Vector3 0 1 0) (->Vector3 0 0 1))))
    (is (= (->Vector3  0  1  0) (cross-product (->Vector3 0 0 1) (->Vector3 1 0 0))))
    (is (= (->Vector3 -1  0  0) (cross-product (->Vector3 0 0 1) (->Vector3 0 1 0))))))

(deftest scale-test
  (testing "Scaling of vector"
    (is (= (->Vector3 4 6 10) (* 2 (->Vector3 2 3 5))))))

(deftest inner-product-test
  (testing "Dot product of two vectors"
    (is (= (float (c/+ (c/* 2 7) (c/* 3 11) (c/* 5 13))) (inner-product (->Vector3 2 3 5) (->Vector3 7 11 13))))))
