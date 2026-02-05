;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-quadtree
  (:require
    [clojure.math :refer (tan to-radians)]
    [fastmath.vector :refer (vec3 add mag)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.cubemap :refer (cube-map) :as cubemap]
    [sfsim.image :as image]
    [sfsim.plane :as plane]
    [sfsim.quadtree :refer :all :as quadtree]
    [sfsim.util :as util])
 (:import
    [clojure.lang
     Keyword]
    [fastmath.vector
     Vec3]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(def face0 :sfsim.cubemap/face0)
(def face1 :sfsim.cubemap/face1)
(def face2 :sfsim.cubemap/face2)
(def face3 :sfsim.cubemap/face3)
(def face4 :sfsim.cubemap/face4)
(def face5 :sfsim.cubemap/face5)
(def quad0 :sfsim.quadtree/quad0)
(def quad1 :sfsim.quadtree/quad1)
(def quad2 :sfsim.quadtree/quad2)
(def quad3 :sfsim.quadtree/quad3)


(fact "Determine the size of a quad on the screen"
      (quad-size 2 33 6378000.0 1024 1000000.0 60.0) => (/ (* 512 (/ (/ 6378000.0 2 32) 1000000.0)) (tan (to-radians 30.0)))
      (quad-size 2 33 6378000.0 1024 0.0 60.0) => (/ (* 512 (/ 6378000.0 2 32)) (tan (to-radians 30.0))))


(facts "Decide whether to increase quadtree level or not"
       (increase-level? 33 6378000.0 1280 60.0 5 3 (vec3 200000 0 0) face5 2 0 1) => truthy
       (provided
         (cubemap/tile-center face5 2 0 1 6378000.0) => (vec3 50000 0 0)
         (quadtree/quad-size 2 33 6378000.0 1280 150000.0 60.0) => 10.0))


(tabular "Load normals, scale factors and colors for a tile"
         (fact (?k (load-tile-data :sfsim.cubemap/face3 2 2 1 6378000.0)) => ?result
               (provided
                 (quadtree/access-cube-tar "data/globe/3/2/1.tar")     => "1.tar"
                 (image/slurp-image-tar    "1.tar" "2.jpg")            => "3/2/1/2.jpg"
                 (image/slurp-image-tar    "1.tar" "2.night.jpg")      => "3/2/1/2.night.jpg"
                 (util/slurp-floats-gz-tar "1.tar" "2.surf.gz")        => "3/2/1/2.surf"
                 (image/slurp-normals-tar  "1.tar" "2.png")            => "3/2/1/2.png"
                 (util/slurp-bytes-gz-tar  "1.tar" "2.water.gz")       => "3/2/1/2.water"
                 (cubemap/tile-center :sfsim.cubemap/face3 2 2 1 6378000.0)           => :tile-center))
         ?k       ?result
         :sfsim.planet/day      "3/2/1/2.jpg"
         :sfsim.planet/night    "3/2/1/2.night.jpg"
         :sfsim.planet/surface  "3/2/1/2.surf"
         :sfsim.planet/normals  "3/2/1/2.png"
         :sfsim.planet/water    "3/2/1/2.water"
         :sfsim.quadtree/face   :sfsim.cubemap/face3
         :sfsim.quadtree/level  2
         :sfsim.quadtree/y      2
         :sfsim.quadtree/x      1
         :sfsim.quadtree/center :tile-center)


(tabular "Get information for loading sub tiles"
         (fact (nth (sub-tiles-info face3 2 2 1) ?i) => ?result)
         ?i ?result
         0  #:sfsim.quadtree{:face face3 :level 3 :y 4 :x 2}
         1  #:sfsim.quadtree{:face face3 :level 3 :y 4 :x 3}
         2  #:sfsim.quadtree{:face face3 :level 3 :y 5 :x 2}
         3  #:sfsim.quadtree{:face face3 :level 3 :y 5 :x 3})


(fact "Load multiple tiles"
      (load-tiles-data [#:sfsim.quadtree{:face face3 :level 2 :y 3 :x 1} #:sfsim.quadtree{:face face2 :level 3 :y 1 :x 0}] 6378000.0)
      => [:data-a :data-b]
      (provided
        (load-tile-data face3 2 3 1 6378000.0) => :data-a
        (load-tile-data face2 3 1 0 6378000.0) => :data-b))


(facts "Check whether specified tree node is a leaf"
       (is-leaf? {:data "test"}) => true
       (is-leaf? nil) => false
       (is-leaf? {face0 {}}) => false
       (is-leaf? {face1 {}}) => false
       (is-leaf? {face2 {}}) => false
       (is-leaf? {face3 {}}) => false
       (is-leaf? {face4 {}}) => false
       (is-leaf? {face5 {}}) => false
       (is-leaf? {quad0 {}}) => false
       (is-leaf? {quad1 {}}) => false
       (is-leaf? {quad2 {}}) => false
       (is-leaf? {quad3 {}}) => false)


(facts "Determine list of tiles to remove"
       (let [quad     {face5 {:sfsim.quadtree/face 2 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                              quad0 {} quad1 {} quad2 {} quad3 {}}}
             sub-quad {face5 {quad0 {} quad1 {} quad2 {quad0 {} quad1 {} quad2 {} quad3 {}} quad3 {}}}
             basic    {face0 {} face1 {} face2 {} face3 {} face4 {} face5 {}}]
         (tiles-to-drop {}   (fn [face level y x] false)) => []
         (tiles-to-drop quad (fn [face level y x] false)) => [[face5 quad0] [face5 quad1] [face5 quad2] [face5 quad3]]
         (tiles-to-drop quad (fn [face level y x] (= [face level y x] [2 1 0 0]))) => []
         (tiles-to-drop sub-quad (fn [face level y x] false))
         => [[face5 quad2 quad0] [face5 quad2 quad1] [face5 quad2 quad2] [face5 quad2 quad3]]
         (tiles-to-drop basic (fn [face level y x] false)) => []))


(facts "Get list of tile paths"
       (tiles-path-list {}) => []
       (tiles-path-list {face0 {}}) => [[face0]]
       (tiles-path-list {face1 {}}) => [[face1]]
       (tiles-path-list {face2 {}}) => [[face2]]
       (tiles-path-list {face3 {}}) => [[face3]]
       (tiles-path-list {face4 {}}) => [[face4]]
       (tiles-path-list {face5 {}}) => [[face5]]
       (tiles-path-list {face5 {quad0 {}}}) => [[face5 quad0] [face5]]
       (tiles-path-list {face5 {quad1 {}}}) => [[face5 quad1] [face5]]
       (tiles-path-list {face5 {quad2 {}}}) => [[face5 quad2] [face5]]
       (tiles-path-list {face5 {quad3 {quad1 {}}}}) => [[face5 quad3 quad1] [face5 quad3] [face5]]
       (tiles-path-list {face5 {quad3 {}}}) => [[face5 quad3] [face5]])


(facts "Determine list of tiles to load"
       (let [basic    {face0 {} face1 {} face2 {} face3 {} face4 {}
                       face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0}}
             quad     {face0 {} face1 {} face2 {} face3 {} face4 {}
                       face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                              quad0 {} quad1 {} quad2 {} quad3 {}}}
             sub-quad {face0 {} face1 {} face2 {} face3 {} face4 {}
                       face5 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 0 :sfsim.quadtree/x 0
                              quad0 {} quad1 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 2 :sfsim.quadtree/y 0 :sfsim.quadtree/x 1}
                              quad2 {} quad3 {}}}]
         (tiles-to-load basic (fn [face level y x] false)) => []
         (tiles-to-load {}  (fn [face level y x] false)) => [[face0] [face1] [face2] [face3] [face4] [face5]]
         (tiles-to-load basic (fn [face level y x] (= [face level y x] [5 1 0 0])))
         => [[face5 quad0] [face5 quad1] [face5 quad2] [face5 quad3]]
         (tiles-to-load quad (fn [face level y x] (= [face level y x] [5 1 0 0]))) => []
         (tiles-to-load sub-quad (fn [f l y x] (contains? #{[5 1] [5 2]} [f l])))
         => [[face5 quad1 quad0] [face5 quad1 quad1] [face5 quad1 quad2] [face5 quad1 quad3]]
         (tiles-to-load sub-quad (fn [face level y x] (= [5 2] [face level]))) => []))


(tabular "Convert tile path to face, level, y and x"
         (fact (tile-meta-data ?path) => {:sfsim.quadtree/face ?face :sfsim.quadtree/level ?level :sfsim.quadtree/y ?y :sfsim.quadtree/x ?x})
         ?path               ?face ?level ?y ?x
         [face5]             face5  0      0  0
         [face5 quad0]       face5  1      0  0
         [face5 quad1]       face5  1      0  1
         [face5 quad2]       face5  1      1  0
         [face5 quad3]       face5  1      1  1
         [face5 quad1 quad0] face5  2      0  2
         [face5 quad2 quad1] face5  2      2  1)


(facts "Get tile metadata for multiple tiles"
       (tiles-meta-data []) => []
       (tiles-meta-data [[face5 quad2 quad1]])
       => [{:sfsim.quadtree/face face5 :sfsim.quadtree/level 2 :sfsim.quadtree/y 2 :sfsim.quadtree/x 1}])


(facts "Add tiles to the quad tree"
       (quadtree-add {} [] []) => {}
       (quadtree-add {} [[face5 quad2]] [{:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 1 :sfsim.quadtree/x 0}])
       => {face5 {quad2 {:sfsim.quadtree/face 5 :sfsim.quadtree/level 1 :sfsim.quadtree/y 1 :sfsim.quadtree/x 0}}})


(facts "Extract list of nodes from quad tree"
       (quadtree-extract {} []) => []
       (quadtree-extract {face5 {quad2 {:sfsim.quadtree/level 2}}} [[face5 quad2]]) => [{:sfsim.quadtree/level 2}])


(facts "Remove tiles from quad tree"
       (quadtree-drop {face3 {}} []) => {face3 {}}
       (quadtree-drop {face3 {}} [[face3]]) => {})


(facts "Update tiles of quad tree"
       (quadtree-update {face3 {}} [] identity) => {face3 {}}
       (quadtree-update {face3 {:id 5}} [[face3]] #(update % :id inc)) => {face3 {:id 6}}
       (quadtree-update {face3 {:5 {:id 7}}} [[face3 :5]] #(update % :id inc)) => {face3 {:5 {:id 8}}}
       (quadtree-update {face3 {:id 5}} [[face3]] #(update %1 :id (partial + %2)) [2]) => {face3 {:id 7}})


(def keyword->int {face0 0 face1 1 face2 2 face3 3 face4 4 face5 5})
(def int->keyword {0 face0 1 face1 2 face2 3 face3 4 face4 5 face5})


(doseq [face (range 6) [dy dx] [[0 -1] [0 1] [-1 0] [1 0]]]
  (fact "Check consistency of neighbouring faces with cube-map coordinates"
        (cube-map (int->keyword face) (+ 0.5 (* dy 0.5)) (+ 0.5 (* dx 0.5)))
        => (add (cube-map (int->keyword face) 0.5 0.5)
                (cube-map (first (neighbour-path [(int->keyword face)] dy dx)) 0.5 0.5))))


(tabular "Same tile or neighbouring tiles on the same face"
         (fact (neighbour-path ?path ?dy ?dx) => ?result)
         ?path                ?dy ?dx ?result
         [face0]              0   0  [face0]
         [face1]              0   0  [face1]
         [face2]              0   0  [face2]
         [face3]              0   0  [face3]
         [face4]              0   0  [face4]
         [face5]              0   0  [face5]
         [face5 quad0]        0   0  [face5 quad0]
         [face5 quad1]        0   0  [face5 quad1]
         [face5 quad2]        0   0  [face5 quad2]
         [face5 quad3]        0   0  [face5 quad3]
         [face5 quad2]       -1   0  [face5 quad0]
         [face5 quad3]       -1   0  [face5 quad1]
         [face5 quad1]        0  -1  [face5 quad0]
         [face5 quad3]        0  -1  [face5 quad2]
         [face5 quad0]        1   0  [face5 quad2]
         [face5 quad1]        1   0  [face5 quad3]
         [face5 quad0]        0   1  [face5 quad1]
         [face5 quad2]        0   1  [face5 quad3]
         [face5 quad3 quad0]  0   0  [face5 quad3 quad0]
         [face5 quad3 quad2] -1   0  [face5 quad3 quad0]
         [face5 quad3 quad0]  0   1  [face5 quad3 quad1]
         [face5 quad3 quad0] -1   0  [face5 quad1 quad2]
         [face5 quad3 quad1] -1   0  [face5 quad1 quad3]
         [face5 quad3 quad0]  0  -1  [face5 quad2 quad1]
         [face5 quad3 quad2]  0  -1  [face5 quad2 quad3]
         [face5 quad1 quad2]  1   0  [face5 quad3 quad0]
         [face5 quad1 quad3]  1   0  [face5 quad3 quad1]
         [face5 quad2 quad1]  0   1  [face5 quad3 quad0]
         [face5 quad2 quad3]  0   1  [face5 quad3 quad2]
         [face1 quad0]       -1   0  [face0 quad2]
         [face2 quad0]       -1   0  [face0 quad3]
         [face3 quad0]       -1   0  [face0 quad1]
         [face4 quad0]       -1   0  [face0 quad0]
         [face1 quad0]        0  -1  [face4 quad1]
         [face2 quad0]        0  -1  [face1 quad1]
         [face3 quad0]        0  -1  [face2 quad1]
         [face4 quad0]        0  -1  [face3 quad1]
         [face1 quad1]        0   1  [face2 quad0]
         [face2 quad1]        0   1  [face3 quad0]
         [face3 quad1]        0   1  [face4 quad0]
         [face4 quad1]        0   1  [face1 quad0]
         [face1 quad2]        1   0  [face5 quad0]
         [face2 quad2]        1   0  [face5 quad1]
         [face3 quad2]        1   0  [face5 quad3]
         [face4 quad2]        1   0  [face5 quad2]
         [face0 quad2]        1   0  [face1 quad0]
         [face0 quad3]        0   1  [face2 quad0]
         [face0 quad1]       -1   0  [face3 quad0]
         [face0 quad0]        0  -1  [face4 quad0]
         [face5 quad0]       -1   0  [face1 quad2]
         [face5 quad1]        0   1  [face2 quad2]
         [face5 quad3]        1   0  [face3 quad2]
         [face5 quad2]        0  -1  [face4 quad2])


(tabular "Get the four neighbours for a given path of a tile"
         (fact (?k (neighbour-paths [face5 quad1 quad2])) => ?path)
         ?k                      ?path
         :sfsim.quadtree/up    [face5 quad1 quad0]
         :sfsim.quadtree/left  [face5 quad0 quad3]
         :sfsim.quadtree/down  [face5 quad3 quad0]
         :sfsim.quadtree/right [face5 quad1 quad3])


(facts "Get a list of paths of the leaves"
       (set (leaf-paths {face1 {} face2 {}}))  => #{[face1] [face2]}
       (leaf-paths {face5 {quad1 {}}})         => [[face5 quad1]]
       (leaf-paths {face5 {:vao 42 quad1 {}}}) => [[face5 quad1]])


(facts "Update quad tree with neighbourhood information"
       (get-in (check-neighbours {face5 {} face1 {}})         [face5 :sfsim.quadtree/up]) => true
       (get-in (check-neighbours {face5 {}})                  [face5 :sfsim.quadtree/up]) => false
       (get-in (check-neighbours {face5 {} face4 {}})         [face5 :sfsim.quadtree/left]) => true
       (get-in (check-neighbours {face5 {}})                  [face5 :sfsim.quadtree/left]) => false
       (get-in (check-neighbours {face1 {} face5 {}})         [face1 :sfsim.quadtree/down]) => true
       (get-in (check-neighbours {face5 {quad1 {} quad3 {}}}) [face5 quad1 :sfsim.quadtree/down]) => true)


(facts "Update level of detail (LOD)"
       (with-redefs [quadtree/load-tile-data (fn [face level y x radius] (fact face => face2 level => 1) {:id (+ (* y 2) x 1)})]
         (let [face     (fn [f] {:sfsim.quadtree/face f, :sfsim.quadtree/level 0, :sfsim.quadtree/y 0, :sfsim.quadtree/x 0})
               flat     {face0 (face face0), face1 (face face1), face2 (face face2),
                         face3 (face face3), face4 (face face4), face5 (face face5)}
               quad     (fn [f] (merge (face f) {quad0 {:id 1} quad1 {:id 2} quad2 {:id 3} quad3 {:id 4}}))
               one-face {face0 (face face0), face1 (face face1), face2 (quad face2),
                         face3 (face face3), face4 (face face4), face5 (face face5)}
               radius   6378000.0]
           (:tree (update-level-of-detail flat radius (constantly false) false)) => flat
           (:drop (update-level-of-detail flat radius (constantly false) false)) => []
           (:drop (update-level-of-detail one-face radius (constantly false) false)) => [{:id 1} {:id 2} {:id 3} {:id 4}]
           (:tree (update-level-of-detail one-face radius (constantly false) false)) => flat
           (:load (update-level-of-detail flat radius (constantly false) false)) => []
           (:load (update-level-of-detail flat radius #(= %& [face2 0 0 0]) false))
           => [[face2 quad0] [face2 quad1] [face2 quad2] [face2 quad3]]
           (:tree (update-level-of-detail flat radius #(= %& [face2 0 0 0]) false)) => one-face
           (get-in (update-level-of-detail flat radius (constantly false) true) [:tree face2 :sfsim.quadtree/up]) => true)))


(fact "Get tile indices, coordinates of pixel within tile and position in pixel"
      (let [extract (juxt :sfsim.quadtree/row :sfsim.quadtree/column :sfsim.quadtree/tile-y :sfsim.quadtree/tile-x
                          :sfsim.quadtree/dy :sfsim.quadtree/dx)]
        (extract (tile-coordinates 1.0  0.5  0 2)) => [0 0 0 0 1.0 0.5]
        (extract (tile-coordinates 0.25 0.25 0 3)) => [0 0 0 0 0.5 0.5]
        (extract (tile-coordinates 0.75 0.25 0 3)) => [0 0 1 0 0.5 0.5]
        (extract (tile-coordinates 0.25 0.75 0 3)) => [0 0 0 1 0.5 0.5]
        (extract (tile-coordinates 0.75 0.25 1 2)) => [1 0 0 0 0.5 0.5]
        (extract (tile-coordinates 0.25 0.75 1 2)) => [0 1 0 0 0.5 0.5]))


(fact "Determine triangle of quad the specified coordinate is in"
      (tile-triangle 0.25 0.75 true) => [[0 0] [0 1] [1 1]]
      (tile-triangle 0.75 0.25 true) => [[0 0] [1 1] [1 0]]
      (tile-triangle 0.25 0.25 false) => [[0 0] [0 1] [1 0]]
      (tile-triangle 0.75 0.75 false) => [[1 0] [0 1] [1 1]])


(defn cube-i-mock
  ^double [^Keyword face ^Vec3 point]
  (facts face => 2, point => (vec3 0.4 0.6 1)) 0.25)


(defn cube-j-mock
  ^double [^Keyword face ^Vec3 point]
  (facts face => 2, point => (vec3 0.4 0.6 1)) 0.75)


(fact "Get distance of surface to planet center for given radial vector"
      (with-redefs [planet-config #:sfsim.planet{:level 6 :tilesize 65 :radius 6378000.0}
                    cubemap/project-onto-cube (fn [point] (fact point => (vec3 2 3 5)) (vec3 0.4 0.6 1))
                    cubemap/determine-face (fn [point] (fact point => (vec3 0.4 0.6 1)) 2)
                    cubemap/cube-i cube-i-mock
                    cubemap/cube-j cube-j-mock
                    quadtree/tile-coordinates (fn [^double j ^double i ^long level ^long tilesize]
                                                (facts j => 0.75, i => 0.25, level => 6, tilesize => 65)
                                                #:sfsim.quadtree{:row 32 :column 40 :tile-y 3 :tile-x 5 :dy 7.0 :dx 11.0})
                    quadtree/access-cube-tar (fn [tar-name] (fact tar-name => "data/globe/2/6/31.tar") "31.tar")
                    util/cube-tar (fn [prefix face level x]
                                    (fact prefix => "data/globe", face => 2, level => 6, x => 40)
                                    "data/globe/2/6/31.tar")
                    util/cube-file-name (fn [y suffix] (fact y => 32, suffix => ".surf.gz") "35.surf.gz")
                    util/slurp-floats-gz-tar (fn [tar file-name] (fact tar => "31.tar" file-name => "35.surf.gz") :surface-tile)
                    cubemap/tile-center (fn [face level row column radius]
                                          (facts face => 2, level => 6, row => 32, column => 40, radius => 6378000.0)
                                          (vec3 1 2 3))
                    quadtree/tile-triangle (fn [^double y ^double x ^Boolean first-diagonal]
                                             (facts y => 7.0, x => 11.0, first-diagonal => true)
                                             [[0 0] [0 1] [1 1]])
                    image/get-vector3 (fn [img ^long y ^long x]
                                        (facts (:sfsim.image/data img) => :surface-tile,
                                               y => #(contains? #{3 4} %), x => #(contains? #{5 6} %))
                                        ({[3 5] (vec3 0 0 0) [3 6] (vec3 1 0 0) [4 6] (vec3 1 1 0)} [y x]))
                    plane/points->plane (fn [p q r] (facts p => (vec3 0 0 0), q => (vec3 1 0 0), r => (vec3 1 1 0)) :plane)
                    plane/ray-plane-intersection-parameter (fn [plane ray]
                                                             (facts plane => :plane
                                                                    (:sfsim.ray/origin ray) => (vec3 -1 -2 -3)
                                                                    (:sfsim.ray/direction ray) => (vec3 2 3 5))
                                                             6378123.0)]
        ((distance-to-surface planet-config [[] [] [] [false false false false false true]]) (vec3 2 3 5))
        => (roughly (* 6378123.0 (mag (vec3 2 3 5))) 1e-6)))


(tabular "Get neighbouring tile face and coordinates"
         (let [extract (juxt :sfsim.quadtree/face :sfsim.quadtree/row :sfsim.quadtree/column
                             :sfsim.quadtree/tile-y :sfsim.quadtree/tile-x :sfsim.quadtree/rotation)]
           (fact (extract (neighbour-tile ?face 2 9 ?row ?column ?tile-y ?tile-x ?dy ?dx 0)) => ?neighbour))
         ?face ?row ?column ?tile-y ?tile-x ?dy ?dx ?neighbour
         face0 0    0         0.0     0.0    0   0  [face0 0 0   0.0 0.0   0]
         face0 1    2         5.0     7.0    0   0  [face0 1 2   5.0 7.0   0]
         face0 1    2         5.0     7.0    1   0  [face0 1 2   6.0 7.0   0]
         face0 1    2         5.0     6.0    0   1  [face0 1 2   5.0 7.0   0]
         face0 1    2         5.0     7.0    4   0  [face0 2 2   1.0 7.0   0]
         face0 1    2         5.0     7.0    0   2  [face0 1 3   5.0 1.0   0]
         face0 1    2         5.0     7.0   -6   0  [face0 0 2   7.0 7.0   0]
         face0 1    2         5.0     7.0    0  -8  [face0 1 1   5.0 7.0   0]
         face1 0    0         0.0     0.0   -1   0  [face0 3 0   7.0 0.0   0]
         face0 0    0         0.0     0.0   -1   0  [face4 0 0   1.0 0.0 180]
         face0 0    0         0.5     0.0    1   0  [face0 0 0   1.5 0.0   0]
         face0 0    0         0.0     0.5    0   1  [face0 0 0   0.0 1.5   0]
         face0 3    0         7.5     0.5    1   0  [face1 0 0   0.5 0.5   0]
         face0 3    3         2.0     0.0   -1  -1  [face0 3 2   1.0 7.0   0])


(tabular "Get neighbouring tile face and coordinates"
         (let [tile (neighbour-tile ?face 1 9 ?row ?column 4.0 4.0 ?dy ?dx 0)
               j    (/ (+ (/ (:sfsim.quadtree/tile-y tile) 8.0) (:sfsim.quadtree/row tile)) 2)
               i    (/ (+ (/ (:sfsim.quadtree/tile-x tile) 8.0) (:sfsim.quadtree/column tile)) 2)
               p    (cube-map (:sfsim.quadtree/face tile) j i)]
           p => (vec3 ?x ?y ?z))
         ?face ?row ?column ?dy ?dx  ?x   ?y   ?z
         ;; direct neighbours of face 0
         face0 0    0       0   0  -0.5  0.5  1.0
         face0 0    0      -8   0  -0.5  1.0  0.5
         face0 1    0       8   0  -0.5 -1.0  0.5
         face0 0    0       0  -8  -1.0  0.5  0.5
         face0 0    1       0   8   1.0  0.5  0.5
         ;; direct neighbours of face 1
         face1 0    0       0   0  -0.5 -1.0  0.5
         face1 0    0      -8   0  -0.5 -0.5  1.0
         face1 1    0       8   0  -0.5 -0.5 -1.0
         face1 0    0       0  -8  -1.0 -0.5  0.5
         face1 0    1       0   8   1.0 -0.5  0.5
         ;; direct neighbours of face 2
         face2 0    0       0   0   1.0 -0.5  0.5
         face2 0    0      -8   0   0.5 -0.5  1.0
         face2 1    0       8   0   0.5 -0.5 -1.0
         face2 0    0       0  -8   0.5 -1.0  0.5
         face2 0    1       0   8   0.5  1.0  0.5
         ;; direct neighbours of face 3
         face3 0    0       0   0   0.5  1.0  0.5
         face3 0    0      -8   0   0.5  0.5  1.0
         face3 1    0       8   0   0.5  0.5 -1.0
         face3 0    0       0  -8   1.0  0.5  0.5
         face3 0    1       0   8  -1.0  0.5  0.5
         ;; direct neighbours of face 4
         face4 0    0       0   0  -1.0  0.5  0.5
         face4 0    0      -8   0  -0.5  0.5  1.0
         face4 1    0       8   0  -0.5  0.5 -1.0
         face4 0    0       0  -8  -0.5  1.0  0.5
         face4 0    1       0   8  -0.5 -1.0  0.5
         ;; direct neighbours of face 5
         face5 0    0       0   0  -0.5 -0.5 -1.0
         face5 0    0      -8   0  -0.5 -1.0 -0.5
         face5 1    0       8   0  -0.5  1.0 -0.5
         face5 0    0       0  -8  -1.0 -0.5 -0.5
         face5 0    1       0   8   1.0 -0.5 -0.5
         ;; test tile coordinates get rotated as well
         face0 0    0      -4   0  -0.5  1.0  1.0
         face0 0    0      -6   0  -0.5  1.0  0.75
         face0 1    0       4   0  -0.5 -1.0  1.0
         face0 1    0       6   0  -0.5 -1.0  0.75
         ;; test rotation of dy and dx
         face0 0    0       0   0  -0.5  0.5  1.0
         face0 0    0      -8   0  -0.5  1.0  0.5
         face0 0    0       0   8   0.5  0.5  1.0
         face0 0    0      -8   8   0.5  1.0  0.5)


(facts "Get diagonal orientations of quad"
       (quad-split-orientation [[true]]         #:sfsim.quadtree{:tile-y 0.5 :tile-x 0.5 :rotation   0}) => true
       (quad-split-orientation [[false]]        #:sfsim.quadtree{:tile-y 0.5 :tile-x 0.5 :rotation   0}) => false
       (quad-split-orientation [[false true]]   #:sfsim.quadtree{:tile-y 0.5 :tile-x 1.5 :rotation   0}) => true
       (quad-split-orientation [[false] [true]] #:sfsim.quadtree{:tile-y 1.5 :tile-x 0.5 :rotation   0}) => true
       (quad-split-orientation [[true]]         #:sfsim.quadtree{:tile-y 0.5 :tile-x 0.5 :rotation  90}) => false
       (quad-split-orientation [[false]]        #:sfsim.quadtree{:tile-y 0.5 :tile-x 0.5 :rotation  90}) => true
       (quad-split-orientation [[true]]         #:sfsim.quadtree{:tile-y 0.5 :tile-x 0.5 :rotation 180}) => true)


(facts "Determine point indices of a pair of triangles for a quad in a 3x3 mesh of 4x4 points"
       (first  (indexed-triangles -1 -1  true {})) => [0 5  1]
       (second (indexed-triangles -1 -1  true {})) => [0 4  5]
       (first  (indexed-triangles -1 -1 false {})) => [0 4  1]
       (second (indexed-triangles -1 -1 false {})) => [1 4  5]
       (first  (indexed-triangles -1  0  true {})) => [1 6  2]
       (second (indexed-triangles -1  0  true {})) => [1 5  6]
       (first  (indexed-triangles -1  0 false {})) => [1 5  2]
       (second (indexed-triangles -1  0 false {})) => [2 5  6]
       (first  (indexed-triangles  0 -1  true {})) => [4 9  5]
       (second (indexed-triangles  0 -1  true {})) => [4 8  9]
       (first  (indexed-triangles  0 -1 false {})) => [4 8  5]
       (second (indexed-triangles  0 -1 false {})) => [5 8  9]
       (first  (indexed-triangles -1  0  true {1 4})) => [4 6  2]
       (second (indexed-triangles -1  0  true {1 4})) => [4 5  6]
       (first  (indexed-triangles  1  0 false {13 8})) => [9 8 10]
       (second (indexed-triangles  1  0 false {13 8})) => [10 8 14])


(facts "Determine relative neighbour coordinates and index map for cube corners with special cases for corners of face"
       (let [extract (juxt :sfsim.quadtree/coordinates :sfsim.quadtree/index-map)]
         (extract (identify-neighbours 2 9 0 0 1 1)) => [[[-1 -1] [-1 0] [-1 1] [0 -1] [0 0] [0 1] [1 -1] [1 0] [1 1]] {}]
         (extract (identify-neighbours 2 9 0 0 0 0)) => [[[-1 0] [-1 1] [0 -1] [0 0] [0 1] [1 -1] [1 0] [1 1]] {1  4}]
         (extract (identify-neighbours 2 9 0 3 0 7)) => [[[-1 -1] [-1 0]        [0 -1] [0 0] [0 1] [1 -1] [1 0] [1 1]] {2  7}]
         (extract (identify-neighbours 2 9 3 0 7 0)) => [[[-1 -1] [-1 0] [-1 1] [0 -1] [0 0] [0 1]        [1 0] [1 1]] {13  8}]
         (extract (identify-neighbours 2 9 3 3 7 7)) => [[[-1 -1] [-1 0] [-1 1] [0 -1] [0 0] [0 1] [1 -1] [1 0]] {14 11}]))


(facts "Create local mesh of 3x3x2 triangles using GPU tessellation information"
       (let [orientations (vec (repeat 4 (vec (repeat 4 true))))]
         (nth (create-local-triangles orientations face0 2 5 0 0 1 1) 0) => [0 5 1]
         (nth (create-local-triangles (assoc-in orientations [0 0] false) face0 2 5 0 0 1 1) 0) => [0 4 1]
         (nth (create-local-triangles orientations face2 2 5 0 0 0 1) 0) => [0 4 1]
         (nth (create-local-triangles (assoc-in orientations [3 0] false) face1 2 5 0 0 0 1) 0) => [0 4 1]
         (count (create-local-triangles orientations face0 2 5 0 0 1 1)) => (* 3 3 2)
         (count (create-local-triangles orientations face0 2 5 0 0 0 0)) => (- (* 3 3 2) 2)
         (count (create-local-triangles orientations face0 2 5 3 3 3 3)) => (- (* 3 3 2) 2)))


(fact "Get 4x4 points of local mesh of 3x3 quads"
      (with-redefs [quadtree/neighbour-tile
                    (fn [face level tilesize row column tile-y tile-x dy dx rotation]
                      (facts face => face2
                             level => 7
                             tilesize => 65
                             row => 1
                             column => 3
                             tile-y => 0.0
                             tile-x => 5.0
                             rotation => 0
                             (contains? #{-1 0 1 2} dy) => true
                             (contains? #{-1 0 1 2} dx) => true)
                      #:sfsim.quadtree{:face face :row (if (>= dy 0) 1 0) :column column
                                       :tile-y (mod dy 64.0) :tile-x (+ tile-x dx)})
                    quadtree/tile-center-to-surface
                    (fn [level tilesize face row column tile-y tile-x]
                      (facts face => face2
                             level => 7
                             tilesize => 65
                             (contains? #{0 1} row) => true
                             column => 3
                             (contains? #{63.0 0.0 1.0 2.0} tile-y) => true
                             (contains? #{4.0 5.0 6.0 7.0} tile-x) => true)
                      (vec3 tile-y (- tile-x 5) 0))
                    cubemap/tile-center
                    (fn [face level row column radius]
                      (facts face => face2
                             level => 7
                             (contains? #{0 1} row) => true
                             column => 3
                             radius => 100.0)
                      (vec3 (* 64 (dec row)) 0 radius))]
        (create-local-vertices face2 7 65 1 3 0 5 100.0 (vec3 0 0 30)) => (for [y [-1 0 1 2] x [-1 0 1 2]] (vec3 y x 70))))


(fact "Create local mesh"
      (let [orientations (vec (repeat 4 (vec (repeat 4 true))))
            point        (vec3 2 3 5)]
        (create-local-mesh orientations face2 7 65 2 3 7 5 6378000.0 (vec3 0 0 30))
        => #:sfsim.quadtree{:vertices [point] :triangles [[0 1 2]]}
        (provided
          (quadtree/create-local-triangles orientations face2 7 65 2 3 7 5) => [[0 1 2]]
          (quadtree/create-local-vertices face2 7 65 2 3 7 5 6378000.0 (vec3 0 0 30)) => [point])))
