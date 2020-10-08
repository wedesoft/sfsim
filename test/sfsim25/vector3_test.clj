(ns sfsim25.vector3-test
  (:require [clojure.test :refer :all]
            [sfsim25.vector3 :refer :all]))

(deftest display-test
  (testing "Display 3D vector"
    (is (= "(vector3 2.0 3.0 5.0)" (str (make-vector3 2 3 5))))))
