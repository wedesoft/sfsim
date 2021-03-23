(ns sfsim25.quadtree-test
  (:require [clojure.test :refer :all]
            [sfsim25.quadtree :refer :all :as quadtree]
            [sfsim25.cubemap :as cubemap]
            [sfsim25.util :as util]
            [sfsim25.vector3 :refer (->Vector3)]))

(deftest quad-size-test
  (testing "Determine the size of a quad on the screen"
    (is (= (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (Math/tan (Math/toRadians 30.0)))
           (quad-size 2 33 6378000.0 1024 1000000.0 60.0)))))

(deftest increase-level-test
  (testing "Increase quadtree level or not"
    (with-redefs [cubemap/tile-center (fn [& args] (is (= args [5 2 0 1 6378000.0 6357000.0])) (->Vector3 50000 0 0))
                  quadtree/quad-size (fn [& args] (is (= args [2 33 6378000.0 1280 150000.0 60.0])) 10.0)]
      (is (increase-level? 5 2 33 0 1 6378000.0 6357000.0 1280 60.0 5 (->Vector3 200000 0 0)))
      (is (not (increase-level? 5 2 33 0 1 6378000.0 6357000.0 1280 60.0 15 (->Vector3 200000 0 0)))))))

(deftest load-tile-data-test
  (testing "Load normals, scale factors and colors for a tile"
    (with-redefs [util/slurp-image (fn [file-name] ({"globe/3/2/1/2.png" "2.png"} file-name))
                  util/slurp-floats (fn [file-name] ({"globe/3/2/1/2.scale" "2.scale"
                                                      "globe/3/2/1/2.normals" "2.normals"} file-name))
                  util/slurp-bytes (fn [file-name] ({"globe/3/2/1/2.water" "2.water"} file-name))]
      (let [data (load-tile-data 3 2 2 1)]
        (are [result k] (= result (k data))
          "2.png"     :colors
          "2.normals" :normals
          "2.water"   :water
          3           :face
          2           :level
          2           :y
          1           :x)))))

(deftest sub-tiles-info-test
  (testing "Get information for loading sub tiles"
    (let [info (sub-tiles-info 3 2 2 1)]
      (are [result i] (= result (nth info i))
        {:face 3 :level 3 :y 4 :x 2} 0
        {:face 3 :level 3 :y 4 :x 3} 1
        {:face 3 :level 3 :y 5 :x 2} 2
        {:face 3 :level 3 :y 5 :x 3} 3))))

(deftest load-tiles-data-test
  (testing "Load multiple tiles"
    (with-redefs [load-tile-data (fn [face level y x] (str face \- level \- y \- x))]
      (is (= ["3-2-3-1" "2-3-1-0"] (load-tiles-data [{:face 3 :level 2 :y 3 :x 1} {:face 2 :level 3 :y 1 :x 0}]))))))
