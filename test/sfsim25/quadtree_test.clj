(ns sfsim25.quadtree-test
  (:require [clojure.test :refer :all]
            [sfsim25.quadtree :refer :all]
            [sfsim25.cubemap :as cubemap]
            [sfsim25.vector3 :refer (->Vector3)]))

(deftest quad-size-test
  (testing "Determine the size of a quad on the screen"
    (is (= (quad-size 2 33 6378000.0 1024 1000000.0 60.0)
           (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (Math/tan (Math/toRadians 30.0)))))))
