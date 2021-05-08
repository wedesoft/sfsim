(ns sfsim25.quadtree-test
  (:require [clojure.test :refer :all]
            [midje.sweet :refer :all]
            [sfsim25.quadtree :refer :all :as quadtree]
            [sfsim25.cubemap :refer (cube-map) :as cubemap]
            [sfsim25.util :as util]
            [sfsim25.vector3 :refer (->Vector3) :as v]))

(fact "Determine the size of a quad on the screen"
  (quad-size 2 33 6378000.0 1024 1000000.0 60.0) => (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (Math/tan (Math/toRadians 30.0))))

(facts "Decide whether to increase quadtree level or not"
  (increase-level? 33 6378000.0 6357000.0 1280 60.0 5 3 (->Vector3 200000 0 0) 5 2 0 1) => truthy
  (provided
    (cubemap/tile-center 5 2 0 1 6378000.0 6357000.0) => (->Vector3 50000 0 0)
    (quadtree/quad-size 2 33 6378000.0 1280 150000.0 60.0) => 10.0))

(tabular
  (fact "Load normals, scale factors and colors for a tile"
    (k? (load-tile-data 3 2 2 1)) => result?
    (provided
      (util/slurp-image "globe/3/2/1/2.png") => "2.png"
      (util/slurp-floats "globe/3/2/1/2.scale") => "2.scale"
      (util/slurp-floats "globe/3/2/1/2.normals") => "2.normals"
      (util/slurp-bytes "globe/3/2/1/2.water") => "2.water"))
  k? result?
  :colors  "2.png"
  :normals "2.normals"
  :water   "2.water"
  :face    3
  :level   2
  :y       2
  :x       1)

(tabular
  (fact "Get information for loading sub tiles"
    (nth (sub-tiles-info 3 2 2 1) i?) => result?)
  i? result?
  0  {:face 3 :level 3 :y 4 :x 2}
  1  {:face 3 :level 3 :y 4 :x 3}
  2  {:face 3 :level 3 :y 5 :x 2}
  3  {:face 3 :level 3 :y 5 :x 3})

(fact "Load multiple tiles"
  (load-tiles-data [{:face 3 :level 2 :y 3 :x 1} {:face 2 :level 3 :y 1 :x 0}]) => [:data-a :data-b]
  (provided
    (load-tile-data 3 2 3 1) => :data-a
    (load-tile-data 2 3 1 0) => :data-b))

(facts "Determine list of tiles to remove"
  (let [quad     {:5 {:face 2 :level 1 :y 0 :x 0 :0 {} :1 {} :2 {} :3 {}}}
        sub-quad {:5 {:0 {} :1 {} :2 {:0 {} :1 {} :2 {} :3 {}} :3 {}}}
        basic    {:0 {} :1 {} :2 {} :3 {} :4 {} :5 {}}]
    (tiles-to-drop {}   (fn [face level y x] false)) => []
    (tiles-to-drop quad (fn [face level y x] false)) => [[:5 :0] [:5 :1] [:5 :2] [:5 :3]]
    (tiles-to-drop quad (fn [face level y x] (= [face level y x] [2 1 0 0]))) => []
    (tiles-to-drop sub-quad (fn [face level y x] false)) => [[:5 :2 :0] [:5 :2 :1] [:5 :2 :2] [:5 :2 :3]]
    (tiles-to-drop basic (fn [face level y x] false)) => []))

(facts "Determine list of tiles to load"
  (let [basic    {:0 {} :1 {} :2 {} :3 {} :4 {} :5 {:face 5 :level 1 :y 0 :x 0}}
        quad     {:0 {} :1 {} :2 {} :3 {} :4 {} :5 {:face 5 :level 1 :y 0 :x 0 :0 {} :1 {} :2 {} :3 {}}}
        sub-quad {:0 {} :1 {} :2 {} :3 {} :4 {} :5 {:face 5 :level 1 :y 0 :x 0 :0 {} :1 {:face 5 :level 2 :y 0 :x 1} :2 {} :3 {}}}]
    (tiles-to-load basic (fn [face level y x] false)) => []
    (tiles-to-load {}  (fn [face level y x] false)) => [[:0] [:1] [:2] [:3] [:4] [:5]]
    (tiles-to-load basic (fn [face level y x] (= [face level y x] [5 1 0 0]))) => [[:5 :0] [:5 :1] [:5 :2] [:5 :3]]
    (tiles-to-load quad (fn [face level y x] (= [face level y x] [5 1 0 0]))) => []
    (tiles-to-load sub-quad (fn [f l y x] (contains? #{[5 1] [5 2]} [f l]))) => [[:5 :1 :0] [:5 :1 :1] [:5 :1 :2] [:5 :1 :3]]
    (tiles-to-load sub-quad (fn [face level y x] (= [5 2] [face level]))) => []))

(deftest tile-meta-data-test
  (testing "Convert tile path to face, level, y and x"
    (are [face level y x path] (= {:face face :level level :y y :x x} (tile-meta-data path))
      5 0 0 0 [:5]
      5 1 0 0 [:5 :0]
      5 1 0 1 [:5 :1]
      5 1 1 0 [:5 :2]
      5 1 1 1 [:5 :3]
      5 2 0 2 [:5 :1 :0]
      5 2 2 1 [:5 :2 :1])))

(deftest tiles-meta-data-test
  (testing "Get tile metadata for multiple tiles"
    (is (= [] (tiles-meta-data [])))
    (is (= [{:face 5 :level 2 :y 2 :x 1}] (tiles-meta-data [[:5 :2 :1]])))))

(deftest quadtree-add-test
  (testing "Add tiles to the quad tree"
    (is (= {}  (quadtree-add {} [] [])))
    (is (= {:5 {:2 {:face 5 :level 1 :y 1 :x 0}}} (quadtree-add {} [[:5 :2]] [{:face 5 :level 1 :y 1 :x 0}])))))

(deftest quadtree-extract-test
  (testing "Extract list of nodes from quad tree"
    (is (= [] (quadtree-extract {} [])))
    (is (= [{:level 2}] (quadtree-extract {:5 {:2 {:level 2}}} [[:5 :2]])))))

(deftest quadtree-drop-test
  (testing "Remove tiles from quad tree"
    (is (= {:3 {}} (quadtree-drop {:3 {}} [])))
    (is (= {} (quadtree-drop {:3 {}} [[:3]])))))

(defn keyword->int [x] (Integer/parseInt (name x)))
(defn int->keyword [x] (keyword (str x)))

(deftest neighbour-path-test
  (testing "Check consistency of neighbouring faces with cube-map coordinates"
    (doseq [face (range 6) [dy dx] [[0 -1] [0 1] [-1 0] [1 0]]]
      (is (= (cube-map face (+ 0.5 (* dy 0.5)) (+ 0.5 (* dx 0.5)))
             (v/+ (cube-map face 0.5 0.5)
                  (cube-map (keyword->int (first (neighbour-path [(int->keyword face)] dy dx))) 0.5 0.5))))))
  (testing "Same tile or neighbouring tiles on the same face"
    (are [result path dy dx] (= result (neighbour-path path dy dx))
      [:0]       [:0]        0  0
      [:1]       [:1]        0  0
      [:2]       [:2]        0  0
      [:3]       [:3]        0  0
      [:4]       [:4]        0  0
      [:5]       [:5]        0  0
      [:5 :0]    [:5 :0]     0  0
      [:5 :1]    [:5 :1]     0  0
      [:5 :2]    [:5 :2]     0  0
      [:5 :3]    [:5 :3]     0  0
      [:5 :0]    [:5 :2]    -1  0
      [:5 :1]    [:5 :3]    -1  0
      [:5 :0]    [:5 :1]     0 -1
      [:5 :2]    [:5 :3]     0 -1
      [:5 :2]    [:5 :0]     1  0
      [:5 :3]    [:5 :1]     1  0
      [:5 :1]    [:5 :0]     0  1
      [:5 :3]    [:5 :2]     0  1
      [:5 :3 :0] [:5 :3 :0]  0  0
      [:5 :3 :0] [:5 :3 :2] -1  0
      [:5 :3 :1] [:5 :3 :0]  0  1
      [:5 :1 :2] [:5 :3 :0] -1  0
      [:5 :1 :3] [:5 :3 :1] -1  0
      [:5 :2 :1] [:5 :3 :0]  0 -1
      [:5 :2 :3] [:5 :3 :2]  0 -1
      [:5 :3 :0] [:5 :1 :2]  1  0
      [:5 :3 :1] [:5 :1 :3]  1  0
      [:5 :3 :0] [:5 :2 :1]  0  1
      [:5 :3 :2] [:5 :2 :3]  0  1
      [:0 :2]    [:1 :0]    -1  0
      [:0 :3]    [:2 :0]    -1  0
      [:0 :1]    [:3 :0]    -1  0
      [:0 :0]    [:4 :0]    -1  0
      [:4 :1]    [:1 :0]     0 -1
      [:1 :1]    [:2 :0]     0 -1
      [:2 :1]    [:3 :0]     0 -1
      [:3 :1]    [:4 :0]     0 -1
      [:2 :0]    [:1 :1]     0  1
      [:3 :0]    [:2 :1]     0  1
      [:4 :0]    [:3 :1]     0  1
      [:1 :0]    [:4 :1]     0  1
      [:5 :0]    [:1 :2]     1  0
      [:5 :1]    [:2 :2]     1  0
      [:5 :3]    [:3 :2]     1  0
      [:5 :2]    [:4 :2]     1  0
      [:1 :0]    [:0 :2]     1  0
      [:2 :0]    [:0 :3]     0  1
      [:3 :0]    [:0 :1]    -1  0
      [:4 :0]    [:0 :0]     0 -1
      [:1 :2]    [:5 :0]    -1  0
      [:2 :2]    [:5 :1]     0  1
      [:3 :2]    [:5 :3]     1  0
      [:4 :2]    [:5 :2]     0 -1)))

(deftest neighbour-paths-test
  (testing "Get the four neighbours for a given path of a tile"
    (let [neighbours (neighbour-paths [:5 :1 :2])]
      (is (= [:5 :1 :0] (:up    neighbours)))
      (is (= [:5 :0 :3] (:left  neighbours)))
      (is (= [:5 :3 :0] (:down  neighbours)))
      (is (= [:5 :1 :3] (:right neighbours))))))

(deftest leaf-paths-test
  (testing "Get a list of paths of the leaves"
    (is (= [[:1] [:2]] (leaf-paths {:1 {} :2 {}})))
    (is (= [[:5 :1]] (leaf-paths {:5 {:1 {}}})))
    (is (= [[:5 :1]] (leaf-paths {:5 {:vao 42 :1 {}}})))))

(deftest check-neighbours-test
  (testing "Update quad tree with neighbourhood information"
    (is (= 1 (get-in (check-neighbours {:5 {} :1 {}}) [:5 :up])))
    (is (= 0 (get-in (check-neighbours {:5 {}}) [:5 :up])))
    (is (= 1 (get-in (check-neighbours {:5 {} :4 {}}) [:5 :left])))
    (is (= 0 (get-in (check-neighbours {:5 {}}) [:5 :left])))
    (is (= 1 (get-in (check-neighbours {:1 {} :5 {}}) [:1 :down])))
    (is (= 1 (get-in (check-neighbours {:5 {:1 {} :3 {}}}) [:5 :1 :down])))))
