(ns sfsim25.quadtree
  "Manage quad tree of map tiles."
  (:require [fastmath.vector :refer (sub mag)]
            [clojure.math :refer (tan to-radians)]
            [malli.core :as m]
            [sfsim25.cubemap :refer (tile-center)]
            [sfsim25.matrix :refer (fvec3)]
            [sfsim25.util :refer (cube-path slurp-image slurp-floats slurp-bytes slurp-normals dissoc-in N N0)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn quad-size
  "Determine screen size of a quad of the world map"
  {:malli/schema [:=> [:cat N0 N :double N :double :double] :double]}
  [level tilesize radius1 width distance angle]
  (let [cnt         (bit-shift-left 1 level)
        real-size   (/ (* 2 radius1) cnt (dec tilesize))
        f           (/ width 2 (-> angle (/ 2) to-radians tan))
        screen-size (* (/ real-size (max 1 distance)) f)]
    screen-size))

(defn- quad-size-for-camera-position
  "Determine screen size of a quad given the camera position"
  {:malli/schema [:=> [:cat N :double N :double fvec3 N0 N0 N0 N0]]}
  [tilesize radius width angle position face level y x]
  (let [center   (tile-center face level y x radius)
        distance (mag (sub position center))]
    (quad-size level tilesize radius width distance angle)))

(defn increase-level?
  "Decide whether next quad tree level is required"
  {:malli/schema [:=> [:cat N :double N :double N N fvec3 N0 N0 N0 N0] :boolean]}
  [tilesize radius width angle max-size max-level position face level y x]
  (and (< level max-level)
       (> (quad-size-for-camera-position tilesize radius width angle position face level y x) max-size)))

(def tile (m/schema [:map [:face N0]
                          [:level N0]
                          [:y N0]
                          [:x N0]
                          [:center :any]
                          [:day :any]
                          [:night :any]
                          [:surface :any]
                          [:normals :any]
                          [:water :any]]))

(defn load-tile-data
  "Load data associated with a cube map tile"
  {:malli/schema [:=> [:cat N0 N0 N0 N0 :double] tile]}
  [face level y x radius]
  {:face    face
   :level   level
   :y       y
   :x       x
   :center  (tile-center face level y x radius)
   :day     (slurp-image   (cube-path "data/globe" face level y x ".jpg"))
   :night   (slurp-image   (cube-path "data/globe" face level y x ".night.jpg"))
   :surface (slurp-floats  (cube-path "data/globe" face level y x ".surf"))
   :normals (slurp-normals (cube-path "data/globe" face level y x ".png"))
   :water   (slurp-bytes   (cube-path "data/globe" face level y x ".water"))})

(def tile-info (m/schema [:map [:face N0] [:level N0] [:y N0] [:x N0]]))

(defn sub-tiles-info
  "Get metadata for sub tiles of cube map tile"
  {:malli/schema [:=> [:cat N0 N0 N0 N0] [:vector tile-info]]}
  [face level y x]
  [{:face face :level (inc level) :y (* 2 y)       :x (* 2 x)}
   {:face face :level (inc level) :y (* 2 y)       :x (inc (* 2 x))}
   {:face face :level (inc level) :y (inc (* 2 y)) :x (* 2 x)}
   {:face face :level (inc level) :y (inc (* 2 y)) :x (inc (* 2 x))}])

(defn load-tiles-data
  "Load a set of tiles"
  {:malli/schema [:=> [:cat [:sequential tile-info] :double] [:vector :any]]}
  [metadata radius]
  (mapv (fn [{:keys [face level y x]}] (load-tile-data face level y x radius)) metadata))

(defn is-leaf?
  "Check whether specified tree node is a leaf"
  {:malli/schema [:=> [:cat [:maybe :map]] :boolean]}
  [node]
  (not
    (or (nil? node)
        (contains? node :0)
        (contains? node :1)
        (contains? node :2)
        (contains? node :3)
        (contains? node :4)
        (contains? node :5))))

(defn- is-flat?
  "Check whether node has four leafs"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [node]
  (and (is-leaf? (:0 node)) (is-leaf? (:1 node)) (is-leaf? (:2 node)) (is-leaf? (:3 node))))

(defn- sub-paths
  "Get path for each leaf"
  [path]
  [(conj path :0) (conj path :1) (conj path :2) (conj path :3)])

(defn tiles-to-drop
  "Determine tiles to remove from the quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] fn? [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree increase-level-fun?]
   (mapcat #(tiles-to-drop (% tree) increase-level-fun? [%]) [:0 :1 :2 :3 :4 :5]))
  ([tree increase-level-fun? path]
   (cond
     (nil? tree) []
     (and (is-flat? tree) (not (increase-level-fun? (:face tree) (:level tree) (:y tree) (:x tree)))) (sub-paths path)
     :else (mapcat #(tiles-to-drop (% tree) increase-level-fun? (conj path %)) [:0 :1 :2 :3]))))

(defn tiles-to-load
  "Determine which tiles to load into the quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] fn? [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree increase-level-fun?]
   (if (empty? tree)
     [[:0] [:1] [:2] [:3] [:4] [:5]]
     (mapcat (fn [k] (tiles-to-load (k tree) increase-level-fun? [k])) [:0 :1 :2 :3 :4 :5])))
  ([tree increase-level-fun? path]
   (if (nil? tree) []
     (let [increase? (increase-level-fun? (:face tree) (:level tree) (:y tree) (:x tree))]
       (cond
         (and (is-leaf? tree) increase?) (sub-paths path)
         increase? (mapcat #(tiles-to-load (% tree) increase-level-fun? (conj path %)) [:0 :1 :2 :3])
         :else [])))))

(defn tile-meta-data
  "Convert tile path to face, level, y and x"
  {:malli/schema [:=> [:cat [:vector :keyword]] tile-info]}
  [path]
  (let [[top & tree]    path
        dy              {:0 0 :1 0 :2 1 :3 1}
        dx              {:0 0 :1 1 :2 0 :3 1}
        combine-offsets #(bit-or (bit-shift-left %1 1) %2)]
    {:face (Integer/parseInt (name top))
     :level (count tree)
     :y (reduce combine-offsets 0 (map dy tree))
     :x (reduce combine-offsets 0 (map dx tree))}))

(defn tiles-meta-data
  "Convert multiple tile paths to face, level, y and x"
  {:malli/schema [:=> [:cat [:sequential [:vector :keyword]]] [:vector tile-info]]}
  [paths]
  (mapv tile-meta-data paths))

(defn quadtree-add
  "Add tiles to quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]] [:sequential :map]] [:maybe :map]]}
  [tree paths tiles]
  (reduce (fn [tree [path tile]] (assoc-in tree path tile)) tree (map vector paths tiles)))

(defn quadtree-extract
  "Extract a list of tiles from quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]]] [:vector :map]]}
  [tree paths]
  (mapv (partial get-in tree) paths))

(defn quadtree-drop
  "Drop tiles specified by path list from quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]]] [:maybe :map]]}
  [tree paths]
  (reduce dissoc-in tree paths))

(defn quadtree-update
  "Update tiles with specified paths using a function with optional arguments from lists"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]] fn? [:* :any]] [:maybe :map]]}
  [tree paths fun & arglists]
  (reduce (fn [tree [path & args]] (apply update-in tree path fun args)) tree (apply map list paths arglists)))

(defn neighbour-path
  "Determine path of neighbouring tile at same level"
  {:malli/schema [:=> [:cat [:sequential :keyword] :int :int [:? :boolean]]
                      [:or [:sequential :keyword] [:tuple [:sequential :keyword] :int :int]]]}
  ([path dy dx top-level]
   (if (empty? path)
     [() dy dx]
     (let [[tail dy dx] (neighbour-path (rest path) dy dx false)
           tile         (first path)]
       (if top-level
         (let [c0           {:0 :0, :1 :1, :3 :3, :2 :2}
               c1           {:0 :2, :1 :0, :3 :1, :2 :3}
               c2           {:0 :3, :1 :2, :3 :0, :2 :1}
               c3           {:0 :1, :1 :3, :3 :2, :2 :0}
               [replacement rotation]
               (case (first path)
                 :0 (case (long dy) -1 [:3 c2], 0 (case (long dx) -1 [:4 c1], 0 [:0 c0], 1 [:2 c3]), 1 [:1 c0])
                 :1 (case (long dy) -1 [:0 c0], 0 (case (long dx) -1 [:4 c0], 0 [:1 c0], 1 [:2 c0]), 1 [:5 c0])
                 :2 (case (long dy) -1 [:0 c1], 0 (case (long dx) -1 [:1 c0], 0 [:2 c0], 1 [:3 c0]), 1 [:5 c3])
                 :3 (case (long dy) -1 [:0 c2], 0 (case (long dx) -1 [:2 c0], 0 [:3 c0], 1 [:4 c0]), 1 [:5 c2])
                 :4 (case (long dy) -1 [:0 c3], 0 (case (long dx) -1 [:3 c0], 0 [:4 c0], 1 [:1 c0]), 1 [:5 c1])
                 :5 (case (long dy) -1 [:1 c0], 0 (case (long dx) -1 [:4 c3], 0 [:5 c0], 1 [:2 c1]), 1 [:3 c2]))]
           (cons replacement (map rotation tail)))
         (let [[replacement propagate]
               (case tile
                 :0 (case (long dy) -1 [:2 true ], 0 (case (long dx) -1 [:1 true ] 0 [:0 false] 1 [:1 false]), 1 [:2 false])
                 :1 (case (long dy) -1 [:3 true ], 0 (case (long dx) -1 [:0 false] 0 [:1 false] 1 [:0 true ]), 1 [:3 false])
                 :2 (case (long dy) -1 [:0 false], 0 (case (long dx) -1 [:3 true ] 0 [:2 false] 1 [:3 false]), 1 [:0 true ])
                 :3 (case (long dy) -1 [:1 false], 0 (case (long dx) -1 [:2 false] 0 [:3 false] 1 [:2 true ]), 1 [:1 true ]))]
           [(cons replacement tail) (if propagate dy 0) (if propagate dx 0)])))))
  ([path dy dx]
   (neighbour-path path dy dx true)))

(defn neighbour-paths
  "Get the paths for the four neighbours of a tile"
  {:malli/schema [:=> [:cat [:sequential :keyword]] [:map-of :keyword [:sequential :keyword]]]}
  [path]
  {::up    (neighbour-path path -1  0)
   ::left  (neighbour-path path  0 -1)
   ::down  (neighbour-path path  1  0)
   ::right (neighbour-path path  0  1)})

(defn leaf-paths
  "Get a path to every leaf in the tree"
  {:malli/schema [:=> [:cat [:maybe :map]] [:sequential [:sequential :keyword]]]}
  [tree]
  (mapcat
    (fn [[k v]]
      (if (is-leaf? v)
        [(list k)]
        (map #(cons k %) (leaf-paths v))))
    (select-keys tree #{:0 :1 :2 :3 :4 :5})))

(defn- check-neighbours-for-tile
  "Update neighbourhood information for a single tile"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential :keyword]] [:maybe :map]]}
  [tree path]
  (reduce
    (fn [updated-tree [direction neighbour-path]]
      (assoc-in updated-tree (conj (vec path) direction) (boolean (get-in tree neighbour-path))))
    tree
    (neighbour-paths path)))

(defn check-neighbours
  "Populate quad tree with neighbourhood information"
  {:malli/schema [:=> [:cat [:maybe :map]] [:maybe :map]]}
  [tree]
  (reduce check-neighbours-for-tile tree (leaf-paths tree)))

(defn update-level-of-detail
  "Return tree with updated level of detail (LOD), a list of dropped tiles and a list of new paths"
  {:malli/schema [:=> [:cat [:maybe :map] :double fn? :boolean]
                      [:map [:tree [:maybe :map]]
                            [:drop [:vector :map]]
                            [:load [:sequential [:vector :keyword]]]]]}
  [tree radius increase-level-fun? neighbours?]
  (let [drop-list (tiles-to-drop tree increase-level-fun?)
        load-list (tiles-to-load tree increase-level-fun?)
        new-tiles (load-tiles-data (tiles-meta-data load-list) radius)
        check     (if neighbours? check-neighbours identity)]
    {:tree (check (quadtree-add (quadtree-drop tree drop-list) load-list new-tiles))
     :drop (quadtree-extract tree drop-list)
     :load load-list}))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
