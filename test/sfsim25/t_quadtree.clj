(ns sfsim25.t-quadtree
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix add)]
            [clojure.math :refer (tan)]
            [sfsim25.quadtree :refer :all :as quadtree]
            [sfsim25.cubemap :refer (cube-map) :as cubemap]
            [sfsim25.util :as util]))

(fact "Determine the size of a quad on the screen"
  (quad-size 2 33 6378000.0 1024 1000000.0 60.0) => (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (tan (Math/toRadians 30.0)))
  (quad-size 2 33 6378000.0 1024 0.0 60.0) => (/ (* 512 (/ 6378000.0 2 32)) (tan (Math/toRadians 30.0))))

(facts "Decide whether to increase quadtree level or not"
  (increase-level? 33 6378000.0 6357000.0 1280 60.0 5 3 (matrix [200000 0 0]) 5 2 0 1) => truthy
    (provided
      (cubemap/tile-center 5 2 0 1 6378000.0 6357000.0) => (matrix [50000 0 0])
      (quadtree/quad-size 2 33 6378000.0 1280 150000.0 60.0) => 10.0))

(tabular "Load normals, scale factors and colors for a tile"
  (fact (?k (load-tile-data 3 2 2 1)) => ?result
    (provided
      (util/slurp-image "globe/3/2/1/2.png") => "2.png"
      (util/slurp-floats "globe/3/2/1/2.scale") => "2.scale"
      (util/slurp-floats "globe/3/2/1/2.normals") => "2.normals"
      (util/slurp-bytes "globe/3/2/1/2.water") => "2.water"))
  ?k       ?result
  :colors  "2.png"
  :normals "2.normals"
  :water   "2.water"
  :face    3
  :level   2
  :y       2
  :x       1)

(tabular "Get information for loading sub tiles"
  (fact (nth (sub-tiles-info 3 2 2 1) ?i) => ?result)
  ?i ?result
  0  {:face 3 :level 3 :y 4 :x 2}
  1  {:face 3 :level 3 :y 4 :x 3}
  2  {:face 3 :level 3 :y 5 :x 2}
  3  {:face 3 :level 3 :y 5 :x 3})

(fact "Load multiple tiles"
  (load-tiles-data [{:face 3 :level 2 :y 3 :x 1} {:face 2 :level 3 :y 1 :x 0}]) => [:data-a :data-b]
    (provided
      (load-tile-data 3 2 3 1) => :data-a
      (load-tile-data 2 3 1 0) => :data-b))

(facts "Check whether specified tree node is a leaf"
  (is-leaf? {:data "test"}) => true
  (is-leaf? nil) => false
  (is-leaf? {:0 {}}) => false
  (is-leaf? {:1 {}}) => false
  (is-leaf? {:2 {}}) => false
  (is-leaf? {:3 {}}) => false)

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

(tabular "Convert tile path to face, level, y and x"
  (fact (tile-meta-data ?path) => {:face ?face :level ?level :y ?y :x ?x})
  ?path      ?face ?level ?y ?x
  [:5]       5     0      0  0
  [:5 :0]    5     1      0  0
  [:5 :1]    5     1      0  1
  [:5 :2]    5     1      1  0
  [:5 :3]    5     1      1  1
  [:5 :1 :0] 5     2      0  2
  [:5 :2 :1] 5     2      2  1)

(facts "Get tile metadata for multiple tiles"
  (tiles-meta-data []) => []
  (tiles-meta-data [[:5 :2 :1]]) => [{:face 5 :level 2 :y 2 :x 1}])

(facts "Add tiles to the quad tree"
  (quadtree-add {} [] []) => {}
  (quadtree-add {} [[:5 :2]] [{:face 5 :level 1 :y 1 :x 0}]) => {:5 {:2 {:face 5 :level 1 :y 1 :x 0}}})

(facts "Extract list of nodes from quad tree"
  (quadtree-extract {} []) => []
  (quadtree-extract {:5 {:2 {:level 2}}} [[:5 :2]]) => [{:level 2}])

(facts "Remove tiles from quad tree"
  (quadtree-drop {:3 {}} []) => {:3 {}}
  (quadtree-drop {:3 {}} [[:3]]) => {})

(facts "Update tiles of quad tree"
  (quadtree-update {:3 {}} [] identity) => {:3 {}}
  (quadtree-update {:3 {:id 5}} [[:3]] #(update % :id inc)) => {:3 {:id 6}}
  (quadtree-update {:3 {:id 5}} [[:3]] #(update %1 :id (partial + %2)) [2]) => {:3 {:id 7}})

(defn keyword->int [x] (Integer/parseInt (name x)))
(defn int->keyword [x] (keyword (str x)))

(doseq [face (range 6) [dy dx] [[0 -1] [0 1] [-1 0] [1 0]]]
  (fact "Check consistency of neighbouring faces with cube-map coordinates"
    (cube-map face (+ 0.5 (* dy 0.5)) (+ 0.5 (* dx 0.5))) =>
      (add (cube-map face 0.5 0.5) (cube-map (keyword->int (first (neighbour-path [(int->keyword face)] dy dx))) 0.5 0.5))))

(tabular "Same tile or neighbouring tiles on the same face"
  (fact (neighbour-path ?path ?dy ?dx) => ?result)
  ?path      ?dy ?dx ?result
  [:0]        0   0  [:0]
  [:1]        0   0  [:1]
  [:2]        0   0  [:2]
  [:3]        0   0  [:3]
  [:4]        0   0  [:4]
  [:5]        0   0  [:5]
  [:5 :0]     0   0  [:5 :0]
  [:5 :1]     0   0  [:5 :1]
  [:5 :2]     0   0  [:5 :2]
  [:5 :3]     0   0  [:5 :3]
  [:5 :2]    -1   0  [:5 :0]
  [:5 :3]    -1   0  [:5 :1]
  [:5 :1]     0  -1  [:5 :0]
  [:5 :3]     0  -1  [:5 :2]
  [:5 :0]     1   0  [:5 :2]
  [:5 :1]     1   0  [:5 :3]
  [:5 :0]     0   1  [:5 :1]
  [:5 :2]     0   1  [:5 :3]
  [:5 :3 :0]  0   0  [:5 :3 :0]
  [:5 :3 :2] -1   0  [:5 :3 :0]
  [:5 :3 :0]  0   1  [:5 :3 :1]
  [:5 :3 :0] -1   0  [:5 :1 :2]
  [:5 :3 :1] -1   0  [:5 :1 :3]
  [:5 :3 :0]  0  -1  [:5 :2 :1]
  [:5 :3 :2]  0  -1  [:5 :2 :3]
  [:5 :1 :2]  1   0  [:5 :3 :0]
  [:5 :1 :3]  1   0  [:5 :3 :1]
  [:5 :2 :1]  0   1  [:5 :3 :0]
  [:5 :2 :3]  0   1  [:5 :3 :2]
  [:1 :0]    -1   0  [:0 :2]
  [:2 :0]    -1   0  [:0 :3]
  [:3 :0]    -1   0  [:0 :1]
  [:4 :0]    -1   0  [:0 :0]
  [:1 :0]     0  -1  [:4 :1]
  [:2 :0]     0  -1  [:1 :1]
  [:3 :0]     0  -1  [:2 :1]
  [:4 :0]     0  -1  [:3 :1]
  [:1 :1]     0   1  [:2 :0]
  [:2 :1]     0   1  [:3 :0]
  [:3 :1]     0   1  [:4 :0]
  [:4 :1]     0   1  [:1 :0]
  [:1 :2]     1   0  [:5 :0]
  [:2 :2]     1   0  [:5 :1]
  [:3 :2]     1   0  [:5 :3]
  [:4 :2]     1   0  [:5 :2]
  [:0 :2]     1   0  [:1 :0]
  [:0 :3]     0   1  [:2 :0]
  [:0 :1]    -1   0  [:3 :0]
  [:0 :0]     0  -1  [:4 :0]
  [:5 :0]    -1   0  [:1 :2]
  [:5 :1]     0   1  [:2 :2]
  [:5 :3]     1   0  [:3 :2]
  [:5 :2]     0  -1  [:4 :2])

(tabular "Get the four neighbours for a given path of a tile"
  (fact (?k (neighbour-paths [:5 :1 :2])) => ?path)
  ?k     ?path
  :sfsim25.quadtree/up    [:5 :1 :0]
  :sfsim25.quadtree/left  [:5 :0 :3]
  :sfsim25.quadtree/down  [:5 :3 :0]
  :sfsim25.quadtree/right [:5 :1 :3])

(facts "Get a list of paths of the leaves"
  (leaf-paths {:1 {} :2 {}})        => [[:1] [:2]]
  (leaf-paths {:5 {:1 {}}})         => [[:5 :1]]
  (leaf-paths {:5 {:vao 42 :1 {}}}) => [[:5 :1]])

(facts "Update quad tree with neighbourhood information"
  (get-in (check-neighbours {:5 {} :1 {}})      [:5 :sfsim25.quadtree/up]     ) => true
  (get-in (check-neighbours {:5 {}})            [:5 :sfsim25.quadtree/up]     ) => false
  (get-in (check-neighbours {:5 {} :4 {}})      [:5 :sfsim25.quadtree/left]   ) => true
  (get-in (check-neighbours {:5 {}})            [:5 :sfsim25.quadtree/left]   ) => false
  (get-in (check-neighbours {:1 {} :5 {}})      [:1 :sfsim25.quadtree/down]   ) => true
  (get-in (check-neighbours {:5 {:1 {} :3 {}}}) [:5 :1 :sfsim25.quadtree/down]) => true)

(facts "Update level of detail (LOD)"
  (with-redefs [quadtree/load-tile-data (fn [face level y x] (fact face => 2 level => 1) {:id (+ (* y 2) x 1)})]
    (let [face     (fn [f] {:face f, :level 0, :y 0, :x 0})
          flat     {:0 (face 0), :1 (face 1), :2 (face 2), :3 (face 3), :4 (face 4), :5 (face 5)}
          quad     (fn [f] (merge (face f) {:0 {:id 1} :1 {:id 2} :2 {:id 3} :3 {:id 4}}))
          one-face {:0 (face 0), :1 (face 1), :2 (quad 2) , :3 (face 3), :4 (face 4), :5 (face 5)}]
      (:tree (update-level-of-detail flat (constantly false) false)) => flat
      (:drop (update-level-of-detail flat (constantly false) false)) => []
      (:drop (update-level-of-detail one-face (constantly false) false)) => [{:id 1} {:id 2} {:id 3} {:id 4}]
      (:tree (update-level-of-detail one-face (constantly false) false)) => flat
      (:load (update-level-of-detail flat (constantly false) false)) => []
      (:load (update-level-of-detail flat #(= %& [2 0 0 0]) false)) => [[:2 :0] [:2 :1] [:2 :2] [:2 :3]]
      (:tree (update-level-of-detail flat #(= %& [2 0 0 0]) false)) => one-face
      (get-in (update-level-of-detail flat (constantly false) true) [:tree :2 :sfsim25.quadtree/up]) => true)))

(fact "Get path of parent node"
  (parent-path [:1 :2 :3]) => [:1 :2])

(facts "Add parent information to a tile"
  (add-parent-info {:level 3} {:level 2}) => {:level 3 :parent {:level 2}}
  (add-parent-info {:level 3} {:level 2 :1 {:level 3}}) => {:level 3 :parent {:level 2}}
  (tabular "Preserve specific keys"
    (fact (add-parent-info {:level 3} {?k 42}) => {:level 3 :parent {?k 42}})
    ?k
    :level
    :face
    :x
    :y
    :heightfield
    :colors
    :normals))

(facts "Add parent information to multiple locations in tree"
  (update-tree-parents {:level 0} []) => {:level 0}
  (update-tree-parents {:level 0 :2 {:level 1}} [[:2]]) => {:level 0 :2 {:level 1 :parent {:level 0}}})
