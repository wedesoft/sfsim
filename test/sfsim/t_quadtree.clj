(ns sfsim.t-quadtree
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [fastmath.vector :refer (vec3 add)]
            [clojure.math :refer (tan to-radians)]
            [sfsim.quadtree :refer :all :as quadtree]
            [sfsim.cubemap :refer (cube-map) :as cubemap]
            [sfsim.image :as image]
            [sfsim.util :as util]))

(mi/collect! {:ns ['sfsim.quadtree]})
(mi/instrument! {:report (pretty/thrower)})

(fact "Determine the size of a quad on the screen"
  (quad-size 2 33 6378000.0 1024 1000000.0 60.0) => (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (tan (to-radians 30.0)))
  (quad-size 2 33 6378000.0 1024 0.0 60.0) => (/ (* 512 (/ 6378000.0 2 32)) (tan (to-radians 30.0))))

(facts "Decide whether to increase quadtree level or not"
  (increase-level? 33 6378000.0 1280 60.0 5 3 (vec3 200000 0 0) 5 2 0 1) => truthy
    (provided
      (cubemap/tile-center 5 2 0 1 6378000.0) => (vec3 50000 0 0)
      (quadtree/quad-size 2 33 6378000.0 1280 150000.0 60.0) => 10.0))

(tabular "Load normals, scale factors and colors for a tile"
  (fact (?k (load-tile-data 3 2 2 1 6378000.0)) => ?result
    (provided
      (image/slurp-image "data/globe/3/2/1/2.jpg")       => "2.jpg"
      (image/slurp-image "data/globe/3/2/1/2.night.jpg") => "2.night.jpg"
      (util/slurp-floats "data/globe/3/2/1/2.surf")     => "2.surf"
      (image/slurp-normals "data/globe/3/2/1/2.png")     => "2.png"
      (util/slurp-bytes "data/globe/3/2/1/2.water")     => "2.water"
      (cubemap/tile-center 3 2 2 1 6378000.0)           => :tile-center))
  ?k       ?result
  :sfsim.planet/day     "2.jpg"
  :sfsim.planet/night   "2.night.jpg"
  :sfsim.planet/surface "2.surf"
  :sfsim.planet/normals "2.png"
  :sfsim.planet/water   "2.water"
  :face    3
  :level   2
  :y       2
  :x       1
  :center  :tile-center)

(tabular "Get information for loading sub tiles"
  (fact (nth (sub-tiles-info 3 2 2 1) ?i) => ?result)
  ?i ?result
  0  {:face 3 :level 3 :y 4 :x 2}
  1  {:face 3 :level 3 :y 4 :x 3}
  2  {:face 3 :level 3 :y 5 :x 2}
  3  {:face 3 :level 3 :y 5 :x 3})

(fact "Load multiple tiles"
  (load-tiles-data [{:face 3 :level 2 :y 3 :x 1} {:face 2 :level 3 :y 1 :x 0}] 6378000.0) => [:data-a :data-b]
    (provided
      (load-tile-data 3 2 3 1 6378000.0) => :data-a
      (load-tile-data 2 3 1 0 6378000.0) => :data-b))

(facts "Check whether specified tree node is a leaf"
  (is-leaf? {:data "test"}) => true
  (is-leaf? nil) => false
  (is-leaf? {:0 {}}) => false
  (is-leaf? {:1 {}}) => false
  (is-leaf? {:2 {}}) => false
  (is-leaf? {:3 {}}) => false
  (is-leaf? {:4 {}}) => false
  (is-leaf? {:5 {}}) => false)

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
  (quadtree-update {:3 {:5 {:id 7}}} [[:3 :5]] #(update % :id inc)) => {:3 {:5 {:id 8}}}
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
  ?k                      ?path
  :sfsim.quadtree/up    [:5 :1 :0]
  :sfsim.quadtree/left  [:5 :0 :3]
  :sfsim.quadtree/down  [:5 :3 :0]
  :sfsim.quadtree/right [:5 :1 :3])

(facts "Get a list of paths of the leaves"
  (leaf-paths {:1 {} :2 {}})        => [[:1] [:2]]
  (leaf-paths {:5 {:1 {}}})         => [[:5 :1]]
  (leaf-paths {:5 {:vao 42 :1 {}}}) => [[:5 :1]])

(facts "Update quad tree with neighbourhood information"
  (get-in (check-neighbours {:5 {} :1 {}})      [:5 :sfsim.quadtree/up]     ) => true
  (get-in (check-neighbours {:5 {}})            [:5 :sfsim.quadtree/up]     ) => false
  (get-in (check-neighbours {:5 {} :4 {}})      [:5 :sfsim.quadtree/left]   ) => true
  (get-in (check-neighbours {:5 {}})            [:5 :sfsim.quadtree/left]   ) => false
  (get-in (check-neighbours {:1 {} :5 {}})      [:1 :sfsim.quadtree/down]   ) => true
  (get-in (check-neighbours {:5 {:1 {} :3 {}}}) [:5 :1 :sfsim.quadtree/down]) => true)

(facts "Update level of detail (LOD)"
  (with-redefs [quadtree/load-tile-data (fn [face level y x radius] (fact face => 2 level => 1) {:id (+ (* y 2) x 1)})]
    (let [face     (fn [f] {:face f, :level 0, :y 0, :x 0})
          flat     {:0 (face 0), :1 (face 1), :2 (face 2), :3 (face 3), :4 (face 4), :5 (face 5)}
          quad     (fn [f] (merge (face f) {:0 {:id 1} :1 {:id 2} :2 {:id 3} :3 {:id 4}}))
          one-face {:0 (face 0), :1 (face 1), :2 (quad 2) , :3 (face 3), :4 (face 4), :5 (face 5)}
          radius   6378000.0]
      (:tree (update-level-of-detail flat radius (constantly false) false)) => flat
      (:drop (update-level-of-detail flat radius (constantly false) false)) => []
      (:drop (update-level-of-detail one-face radius (constantly false) false)) => [{:id 1} {:id 2} {:id 3} {:id 4}]
      (:tree (update-level-of-detail one-face radius (constantly false) false)) => flat
      (:load (update-level-of-detail flat radius (constantly false) false)) => []
      (:load (update-level-of-detail flat radius #(= %& [2 0 0 0]) false)) => [[:2 :0] [:2 :1] [:2 :2] [:2 :3]]
      (:tree (update-level-of-detail flat radius #(= %& [2 0 0 0]) false)) => one-face
      (get-in (update-level-of-detail flat radius (constantly false) true) [:tree :2 :sfsim.quadtree/up]) => true)))
