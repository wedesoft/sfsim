(ns sfsim25.quaternion-test
  (:require [clojure.test :refer :all]
            [sfsim25.quaternion :refer :all])
  (:import [sfsim25.quaternion Quaternion]))

(deftest add-test
  (testing "Add two quaternions"
    (is (= (Quaternion. 6 8 10 12) (add (Quaternion. 1 2 3 4) (Quaternion. 5 6 7 8))))))
