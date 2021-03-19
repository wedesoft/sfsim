(ns sfsim25.matrix4x4-test
  (:require [clojure.test :refer :all]
            [sfsim25.matrix4x4 :refer :all]))

(deftest display-test
  (testing "Display 4x4 matrix"
    (is (= "(matrix4x4 1.0 2.0 3.0 4.0 2.0 3.0 4.0 5.0 3.0 4.0 5.0 6.0 4.0 5.0 6.0 7.0)"
           (str (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7))))))

(deftest equality-test
  (testing "Equality of 4x4 matrices"
    (is (= (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7) (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7)))))
