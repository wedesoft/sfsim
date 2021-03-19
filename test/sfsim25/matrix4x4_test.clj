(ns sfsim25.matrix4x4-test
  (:require [clojure.test :refer :all]
            [sfsim25.matrix4x4 :refer :all]
            [sfsim25.matrix3x3 :refer (->Matrix3x3)]
            [sfsim25.vector3 :refer (->Vector3)]))

(deftest display-test
  (testing "Display 4x4 matrix"
    (is (= "(matrix4x4 1.0 2.0 3.0 4.0 2.0 3.0 4.0 5.0 3.0 4.0 5.0 6.0 4.0 5.0 6.0 7.0)"
           (str (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7))))))

(deftest equality-test
  (testing "Equality of 4x4 matrices"
    (is (= (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7) (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7)))))

(deftest matrix3x3->matrix4x4-test
  (testing "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
    (is (= (->Matrix4x4 1 2 3 4 5 6 7 8 9 10 11 12 0 0 0 1)
           (matrix3x3->matrix4x4 (->Matrix3x3 1 2 3 5 6 7 9 10 11) (->Vector3 4 8 12))))))
