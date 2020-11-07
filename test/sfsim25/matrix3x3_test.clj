(ns sfsim25.matrix3x3-test
  (:refer-clojure :exclude [*])
  (:require [clojure.test :refer :all]
            [sfsim25.matrix3x3 :refer :all]
            [sfsim25.vector3 :refer (vector3)]))

(deftest display-test
  (testing "Display 3x3 matrix"
    (is (= "(matrix3x3 1.0 2.0 3.0 4.0 5.0 6.0 7.0 8.0 9.0)" (str (matrix3x3 1 2 3 4 5 6 7 8 9))))))

(deftest matrix-vector-dot
  (testing "Matrix-vector multiplication"
    (is (= (vector3 14 32 50) (* (matrix3x3 1 2 3 4 5 6 7 8 9) (vector3 1 2 3))))))
