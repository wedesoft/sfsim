(ns sfsim25.vector4-test
  (:require [clojure.test :refer :all]
            [sfsim25.vector4 :refer :all]))

(deftest display-test
  (testing "Display 4D vector"
    (is (= "(vector4 2.0 3.0 5.0 7.0") (str (->Vector4 2 3 5 7)))))

(deftest equality-test
  (testing "Equality of 4D vectors"
    (is (= (->Vector4 2 3 5 7) (->Vector4 2 3 5 7)))))
