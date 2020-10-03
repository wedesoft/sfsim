(ns sfsim25.quaternion-test
  (:require [clojure.test :refer :all]
            [sfsim25.quaternion :refer :all]))

(deftest add-test
  (testing "Add two quaternions"
    (is (= (make-quaternion 6 8 10 12) (add (make-quaternion 1 2 3 4) (make-quaternion 5 6 7 8))))))
