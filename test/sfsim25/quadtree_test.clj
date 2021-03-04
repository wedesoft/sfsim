(ns sfsim25.quadtree-test
  (:require [clojure.test :refer :all]
            [sfsim25.quadtree :refer :all :as quadtree]
            [sfsim25.cubemap :as cubemap]
            [sfsim25.vector3 :refer (->Vector3)]))

(deftest quad-size-test
  (testing "Determine the size of a quad on the screen"
    (is (= (quad-size 2 33 6378000.0 1024 1000000.0 60.0)
           (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (Math/tan (Math/toRadians 30.0)))))))

(deftest increase-level-test
  (testing "Increase quadtree level or not"
    (with-redefs [cubemap/tile-center (fn [& args] (is (= args [5 2 0 1 6378000.0 6357000.0])) (->Vector3 50000 0 0))
                  quadtree/quad-size (fn [& args] (is (= args [2 33 6378000.0 1280 150000.0 60.0])) 10.0)]
      (is (increase-level? 5 2 33 0 1 6378000.0 6357000.0 1280 60.0 5 (->Vector3 200000 0 0)))
      (is (not (increase-level? 5 2 33 0 1 6378000.0 6357000.0 1280 60.0 15 (->Vector3 200000 0 0)))))))
