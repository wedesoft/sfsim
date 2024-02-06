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
  :sfsim.quadtree/face    3
  :sfsim.quadtree/level   2
  :sfsim.quadtree/y       2
  :sfsim.quadtree/x       1
  :sfsim.quadtree/center  :tile-center)

(tabular "Get information for loading sub tiles"
  (fact (nth (sub-tiles-info 3 2 2 1) ?i) => ?result)
  ?i ?result
  0  #:sfsim.quadtree{:face 3 :level 3 :y 4 :x 2}
  1  #:sfsim.quadtree{:face 3 :level 3 :y 4 :x 3}
  2  #:sfsim.quadtree{:face 3 :level 3 :y 5 :x 2}
  3  #:sfsim.quadtree{:face 3 :level 3 :y 5 :x 3})

(fact "Load multiple tiles"
  (load-tiles-data [#:sfsim.quadtree{:face 3 :level 2 :y 3 :x 1} #:sfsim.quadtree{:face 2 :level 3 :y 1 :x 0}] 6378000.0)
  => [:data-a :data-b]
    (provided
      (load-tile-data 3 2 3 1 6378000.0) => :data-a
      (load-tile-data 2 3 1 0 6378000.0) => :data-b))

(facts "Check whether specified tree node is a leaf"
  (is-leaf? {:data "test"}) => true
  (is-leaf? nil) => false
  (is-leaf? {:face0 {}}) => false
  (is-leaf? {:face1 {}}) => false
  (is-leaf? {:face2 {}}) => false
  (is-leaf? {:face3 {}}) => false
  (is-leaf? {:face4 {}}) => false
  (is-leaf? {:face5 {}}) => false
  (is-leaf? {:0 {}}) => false
  (is-leaf? {:1 {}}) => false
  (is-leaf? {:2 {}}) => false
  (is-leaf? {:3 {}}) => false)

(facts "Determine list of tiles to remove"
  (let [quad     {:face5 {:sfsim.quadtree/face 2 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                          :0 {} :1 {} :2 {} :3 {}}}
        sub-quad {:face5 {:0 {} :1 {} :2 {:0 {} :1 {} :2 {} :3 {}} :3 {}}}
        basic    {:face0 {} :face1 {} :face2 {} :face3 {} :face4 {} :face5 {}}]
    (tiles-to-drop {}   (fn [face level y x] false)) => []
    (tiles-to-drop quad (fn [face level y x] false)) => [[:face5 :0] [:face5 :1] [:face5 :2] [:face5 :3]]
    (tiles-to-drop quad (fn [face level y x] (= [face level y x] [2 1 0 0]))) => []
    (tiles-to-drop sub-quad (fn [face level y x] false)) => [[:face5 :2 :0] [:face5 :2 :1] [:face5 :2 :2] [:face5 :2 :3]]
    (tiles-to-drop basic (fn [face level y x] false)) => []))

(facts "Determine list of tiles to load"
  (let [basic    {:face0 {} :face1 {} :face2 {} :face3 {} :face4 {}
                  :face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0}}
        quad     {:face0 {} :face1 {} :face2 {} :face3 {} :face4 {}
                  :face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                          :0 {} :1 {} :2 {} :3 {}}}
        sub-quad {:face0 {} :face1 {} :face2 {} :face3 {} :face4 {}
                  :face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                          :0 {} :1 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 2 :sfsim.quadtree/y 0 :sfsim.quadtree/x 1}
                          :2 {} :3 {}}}]
    (tiles-to-load basic (fn [face level y x] false)) => []
    (tiles-to-load {}  (fn [face level y x] false)) => [[:face0] [:face1] [:face2] [:face3] [:face4] [:face5]]
    (tiles-to-load basic (fn [face level y x] (= [face level y x] [5 1 0 0])))
    => [[:face5 :0] [:face5 :1] [:face5 :2] [:face5 :3]]
    (tiles-to-load quad (fn [face level y x] (= [face level y x] [5 1 0 0]))) => []
    (tiles-to-load sub-quad (fn [f l y x] (contains? #{[5 1] [5 2]} [f l])))
    => [[:face5 :1 :0] [:face5 :1 :1] [:face5 :1 :2] [:face5 :1 :3]]
    (tiles-to-load sub-quad (fn [face level y x] (= [5 2] [face level]))) => []))

(tabular "Convert tile path to face, level, y and x"
  (fact (tile-meta-data ?path) => {:sfsim.quadtree/face ?face :sfsim.quadtree/level ?level :sfsim.quadtree/y ?y :sfsim.quadtree/x ?x})
  ?path         ?face ?level ?y ?x
  [:face5]       5     0      0  0
  [:face5 :0]    5     1      0  0
  [:face5 :1]    5     1      0  1
  [:face5 :2]    5     1      1  0
  [:face5 :3]    5     1      1  1
  [:face5 :1 :0] 5     2      0  2
  [:face5 :2 :1] 5     2      2  1)

(facts "Get tile metadata for multiple tiles"
  (tiles-meta-data []) => []
  (tiles-meta-data [[:face5 :2 :1]])
  => [{:sfsim.quadtree/face 5 :sfsim.quadtree/level 2 :sfsim.quadtree/y 2 :sfsim.quadtree/x 1}])

(facts "Add tiles to the quad tree"
  (quadtree-add {} [] []) => {}
  (quadtree-add {} [[:face5 :2]] [{:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 1 :sfsim.quadtree/x 0}])
  => {:face5 {:2 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 1 :sfsim.quadtree/x 0}}})

(facts "Extract list of nodes from quad tree"
  (quadtree-extract {} []) => []
  (quadtree-extract {:face5 {:2 {:sfsim.quadtree/level 2}}} [[:face5 :2]]) => [{:sfsim.quadtree/level 2}])

(facts "Remove tiles from quad tree"
  (quadtree-drop {:face3 {}} []) => {:face3 {}}
  (quadtree-drop {:face3 {}} [[:face3]]) => {})

(facts "Update tiles of quad tree"
  (quadtree-update {:face3 {}} [] identity) => {:face3 {}}
  (quadtree-update {:face3 {:id 5}} [[:face3]] #(update % :id inc)) => {:face3 {:id 6}}
  (quadtree-update {:face3 {:5 {:id 7}}} [[:face3 :5]] #(update % :id inc)) => {:face3 {:5 {:id 8}}}
  (quadtree-update {:face3 {:id 5}} [[:face3]] #(update %1 :id (partial + %2)) [2]) => {:face3 {:id 7}})

(def keyword->int {:face0 0 :face1 1 :face2 2 :face3 3 :face4 4 :face5 5})
(defn int->keyword [x] (keyword (str "face" x)))

(doseq [face (range 6) [dy dx] [[0 -1] [0 1] [-1 0] [1 0]]]
  (fact "Check consistency of neighbouring faces with cube-map coordinates"
    (cube-map face (+ 0.5 (* dy 0.5)) (+ 0.5 (* dx 0.5))) =>
      (add (cube-map face 0.5 0.5) (cube-map (keyword->int (first (neighbour-path [(int->keyword face)] dy dx))) 0.5 0.5))))

(tabular "Same tile or neighbouring tiles on the same face"
  (fact (neighbour-path ?path ?dy ?dx) => ?result)
  ?path      ?dy ?dx ?result
  [:face0]        0   0  [:face0]
  [:face1]        0   0  [:face1]
  [:face2]        0   0  [:face2]
  [:face3]        0   0  [:face3]
  [:face4]        0   0  [:face4]
  [:face5]        0   0  [:face5]
  [:face5 :0]     0   0  [:face5 :0]
  [:face5 :1]     0   0  [:face5 :1]
  [:face5 :2]     0   0  [:face5 :2]
  [:face5 :3]     0   0  [:face5 :3]
  [:face5 :2]    -1   0  [:face5 :0]
  [:face5 :3]    -1   0  [:face5 :1]
  [:face5 :1]     0  -1  [:face5 :0]
  [:face5 :3]     0  -1  [:face5 :2]
  [:face5 :0]     1   0  [:face5 :2]
  [:face5 :1]     1   0  [:face5 :3]
  [:face5 :0]     0   1  [:face5 :1]
  [:face5 :2]     0   1  [:face5 :3]
  [:face5 :3 :0]  0   0  [:face5 :3 :0]
  [:face5 :3 :2] -1   0  [:face5 :3 :0]
  [:face5 :3 :0]  0   1  [:face5 :3 :1]
  [:face5 :3 :0] -1   0  [:face5 :1 :2]
  [:face5 :3 :1] -1   0  [:face5 :1 :3]
  [:face5 :3 :0]  0  -1  [:face5 :2 :1]
  [:face5 :3 :2]  0  -1  [:face5 :2 :3]
  [:face5 :1 :2]  1   0  [:face5 :3 :0]
  [:face5 :1 :3]  1   0  [:face5 :3 :1]
  [:face5 :2 :1]  0   1  [:face5 :3 :0]
  [:face5 :2 :3]  0   1  [:face5 :3 :2]
  [:face1 :0]    -1   0  [:face0 :2]
  [:face2 :0]    -1   0  [:face0 :3]
  [:face3 :0]    -1   0  [:face0 :1]
  [:face4 :0]    -1   0  [:face0 :0]
  [:face1 :0]     0  -1  [:face4 :1]
  [:face2 :0]     0  -1  [:face1 :1]
  [:face3 :0]     0  -1  [:face2 :1]
  [:face4 :0]     0  -1  [:face3 :1]
  [:face1 :1]     0   1  [:face2 :0]
  [:face2 :1]     0   1  [:face3 :0]
  [:face3 :1]     0   1  [:face4 :0]
  [:face4 :1]     0   1  [:face1 :0]
  [:face1 :2]     1   0  [:face5 :0]
  [:face2 :2]     1   0  [:face5 :1]
  [:face3 :2]     1   0  [:face5 :3]
  [:face4 :2]     1   0  [:face5 :2]
  [:face0 :2]     1   0  [:face1 :0]
  [:face0 :3]     0   1  [:face2 :0]
  [:face0 :1]    -1   0  [:face3 :0]
  [:face0 :0]     0  -1  [:face4 :0]
  [:face5 :0]    -1   0  [:face1 :2]
  [:face5 :1]     0   1  [:face2 :2]
  [:face5 :3]     1   0  [:face3 :2]
  [:face5 :2]     0  -1  [:face4 :2])

(tabular "Get the four neighbours for a given path of a tile"
  (fact (?k (neighbour-paths [:face5 :1 :2])) => ?path)
  ?k                      ?path
  :sfsim.quadtree/up    [:face5 :1 :0]
  :sfsim.quadtree/left  [:face5 :0 :3]
  :sfsim.quadtree/down  [:face5 :3 :0]
  :sfsim.quadtree/right [:face5 :1 :3])

(facts "Get a list of paths of the leaves"
       (set (leaf-paths {:face1 {} :face2 {}})) => #{[:face1] [:face2]}
       (leaf-paths {:face5 {:1 {}}})            => [[:face5 :1]]
       (leaf-paths {:face5 {:vao 42 :1 {}}})    => [[:face5 :1]])

(facts "Update quad tree with neighbourhood information"
  (get-in (check-neighbours {:face5 {} :face1 {}})  [:face5 :sfsim.quadtree/up]     ) => true
  (get-in (check-neighbours {:face5 {}})            [:face5 :sfsim.quadtree/up]     ) => false
  (get-in (check-neighbours {:face5 {} :face4 {}})  [:face5 :sfsim.quadtree/left]   ) => true
  (get-in (check-neighbours {:face5 {}})            [:face5 :sfsim.quadtree/left]   ) => false
  (get-in (check-neighbours {:face1 {} :face5 {}})  [:face1 :sfsim.quadtree/down]   ) => true
  (get-in (check-neighbours {:face5 {:1 {} :3 {}}}) [:face5 :1 :sfsim.quadtree/down]) => true)

(facts "Update level of detail (LOD)"
  (with-redefs [quadtree/load-tile-data (fn [face level y x radius] (fact face => 2 level => 1) {:id (+ (* y 2) x 1)})]
    (let [face     (fn [f] {:sfsim.quadtree/face f, :sfsim.quadtree/level 0, :sfsim.quadtree/y 0, :sfsim.quadtree/x 0})
          flat     {:face0 (face 0), :face1 (face 1), :face2 (face 2), :face3 (face 3), :face4 (face 4), :face5 (face 5)}
          quad     (fn [f] (merge (face f) {:0 {:id 1} :1 {:id 2} :2 {:id 3} :3 {:id 4}}))
          one-face {:face0 (face 0), :face1 (face 1), :face2 (quad 2) , :face3 (face 3), :face4 (face 4), :face5 (face 5)}
          radius   6378000.0]
      (:tree (update-level-of-detail flat radius (constantly false) false)) => flat
      (:drop (update-level-of-detail flat radius (constantly false) false)) => []
      (:drop (update-level-of-detail one-face radius (constantly false) false)) => [{:id 1} {:id 2} {:id 3} {:id 4}]
      (:tree (update-level-of-detail one-face radius (constantly false) false)) => flat
      (:load (update-level-of-detail flat radius (constantly false) false)) => []
      (:load (update-level-of-detail flat radius #(= %& [2 0 0 0]) false)) => [[:face2 :0] [:face2 :1] [:face2 :2] [:face2 :3]]
      (:tree (update-level-of-detail flat radius #(= %& [2 0 0 0]) false)) => one-face
      (get-in (update-level-of-detail flat radius (constantly false) true) [:tree :face2 :sfsim.quadtree/up]) => true)))
