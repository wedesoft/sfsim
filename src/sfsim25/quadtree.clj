(ns sfsim25.quadtree
  "Manage quad tree of map tiles."
  (:require [clojure.core.matrix :refer (sub)]
            [clojure.core.matrix.linear :refer (norm)]
            [clojure.math :refer (tan)]
            [sfsim25.cubemap :refer (tile-center)]
            [sfsim25.util :refer (cube-path slurp-image slurp-floats slurp-bytes dissoc-in)]))

(set! *unchecked-math* true)

(defn quad-size
  "Determine screen size of a quad of the world map"
  [level tilesize radius1 width distance angle]
  (let [cnt         (bit-shift-left 1 level)
        real-size   (/ (* 2 radius1) cnt (dec tilesize))
        f           (/ width 2 (-> angle (/ 2) Math/toRadians tan))
        screen-size (* (/ real-size (max 1 distance)) f)]
    screen-size))

(defn- quad-size-for-camera-position
  "Determine screen size of a quad given the camera position"
  [tilesize radius1 radius2 width angle position face level y x]
  (let [center   (tile-center face level y x radius1 radius2)
        distance (norm (sub position center))]
    (quad-size level tilesize radius1 width distance angle)))

(defn increase-level?
  "Decide whether next quad tree level is required"
  [tilesize radius1 radius2 width angle max-size max-level position face level y x]
  (and (< level max-level)
       (> (quad-size-for-camera-position tilesize radius1 radius2 width angle position face level y x) max-size)))

(defn load-tile-data
  "Load data associated with a cube map tile"
  [face level y x]
  {:face    face
   :level   level
   :y       y
   :x       x
   :colors  (slurp-image  (cube-path "globe" face level y x ".png"))
   :scales  (slurp-floats (cube-path "globe" face level y x ".scale"))
   :normals (slurp-floats  (cube-path "globe" face level y x ".normals"))
   :water   (slurp-bytes  (cube-path "globe" face level y x ".water"))})

(defn sub-tiles-info
  "Get metadata for sub tiles of cube map tile"
  [face level y x]
  [{:face face :level (inc level) :y (* 2 y)       :x (* 2 x)}
   {:face face :level (inc level) :y (* 2 y)       :x (inc (* 2 x))}
   {:face face :level (inc level) :y (inc (* 2 y)) :x (* 2 x)}
   {:face face :level (inc level) :y (inc (* 2 y)) :x (inc (* 2 x))}])

(defn load-tiles-data
  "Load a set of tiles"
  [metadata]
  (map (fn [{:keys [face level y x]}] (load-tile-data face level y x)) metadata))

(defn is-leaf?
  "Check whether specified tree node is a leaf"
  [node]
  (not (or (nil? node) (contains? node :0) (contains? node :1) (contains? node :2) (contains? node :3))))

(defn- is-flat?
  "Check whether node has four leafs"
  [node]
  (and (is-leaf? (:0 node)) (is-leaf? (:1 node)) (is-leaf? (:2 node)) (is-leaf? (:3 node))))

(defn- sub-paths
  "Get path for each leaf"
  [path]
  [(conj path :0) (conj path :1) (conj path :2) (conj path :3)])

(defn tiles-to-drop
  "Determine tiles to remove from the quad tree"
  ([tree increase-level-fun?]
   (mapcat #(tiles-to-drop (% tree) increase-level-fun? [%]) [:0 :1 :2 :3 :4 :5]))
  ([tree increase-level-fun? path]
   (cond
     (nil? tree) []
     (and (is-flat? tree) (not (increase-level-fun? (:face tree) (:level tree) (:y tree) (:x tree)))) (sub-paths path)
     :else (mapcat #(tiles-to-drop (% tree) increase-level-fun? (conj path %)) [:0 :1 :2 :3]))))

(defn tiles-to-load
  "Determine which tiles to load into the quad tree"
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
  [paths]
  (map tile-meta-data paths))

(defn quadtree-add
  "Add tiles to quad tree"
  [tree paths tiles]
  (reduce (fn [tree [path tile]] (assoc-in tree path tile)) tree (map vector paths tiles)))

(defn quadtree-extract
  "Extract a list of tiles from quad tree"
  [tree paths]
  (map (partial get-in tree) paths))

(defn quadtree-drop
  "Drop tiles specified by path list from quad tree"
  [tree paths]
  (reduce dissoc-in tree paths))

(defn quadtree-update
  "Update tiles with specified paths using a function with optional arguments from lists"
  [tree paths fun & arglists]
  (reduce (fn [tree [path & args]] (apply update-in tree path fun args)) tree (apply map list paths arglists)))

(defn neighbour-path
  "Determine path of neighbouring tile at same level"
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
                   :0 (case dy -1 [:3 c2], 0 (case dx -1 [:4 c1], 0 [:0 c0], 1 [:2 c3]), 1 [:1 c0])
                   :1 (case dy -1 [:0 c0], 0 (case dx -1 [:4 c0], 0 [:1 c0], 1 [:2 c0]), 1 [:5 c0])
                   :2 (case dy -1 [:0 c1], 0 (case dx -1 [:1 c0], 0 [:2 c0], 1 [:3 c0]), 1 [:5 c3])
                   :3 (case dy -1 [:0 c2], 0 (case dx -1 [:2 c0], 0 [:3 c0], 1 [:4 c0]), 1 [:5 c2])
                   :4 (case dy -1 [:0 c3], 0 (case dx -1 [:3 c0], 0 [:4 c0], 1 [:1 c0]), 1 [:5 c1])
                   :5 (case dy -1 [:1 c0], 0 (case dx -1 [:4 c3], 0 [:5 c0], 1 [:2 c1]), 1 [:3 c2]))]
           (cons replacement (map rotation tail)))
         (let [[replacement propagate]
                 (case tile
                   :0 (case dy -1 [:2 true ], 0 (case dx -1 [:1 true ] 0 [:0 false] 1 [:1 false]), 1 [:2 false])
                   :1 (case dy -1 [:3 true ], 0 (case dx -1 [:0 false] 0 [:1 false] 1 [:0 true ]), 1 [:3 false])
                   :2 (case dy -1 [:0 false], 0 (case dx -1 [:3 true ] 0 [:2 false] 1 [:3 false]), 1 [:0 true ])
                   :3 (case dy -1 [:1 false], 0 (case dx -1 [:2 false] 0 [:3 false] 1 [:2 true ]), 1 [:1 true ]))]
             [(cons replacement tail) (if propagate dy 0) (if propagate dx 0)])))))
  ([path dy dx]
   (neighbour-path path dy dx true)))

(defn neighbour-paths
  "Get the paths for the four neighbours of a tile"
  [path]
  {::up    (neighbour-path path -1  0)
   ::left  (neighbour-path path  0 -1)
   ::down  (neighbour-path path  1  0)
   ::right (neighbour-path path  0  1)})

(defn leaf-paths
  "Get a path to every leaf in the tree"
  [tree]
  (mapcat (fn [[k v]] (if (is-leaf? v) [(list k)] (map #(cons k %) (leaf-paths v))))
          (select-keys tree #{:0 :1 :2 :3 :4 :5})))

(defn- check-neighbours-for-tile
  "Update neighbourhood information for a single tile"
  [tree path]
  (reduce
    (fn [updated-tree [direction neighbour-path]]
      (assoc-in updated-tree (conj (vec path) direction) (boolean (get-in tree neighbour-path))))
    tree
    (neighbour-paths path)))

(defn check-neighbours
  "Populate quad tree with neighbourhood information"
  [tree]
  (reduce check-neighbours-for-tile tree (leaf-paths tree)))

(defn update-level-of-detail
  "Return tree with updated level of detail (LOD), a list of dropped tiles and a list of new paths"
  [tree increase-level-fun? neighbours?]
  (let [drop-list (tiles-to-drop tree increase-level-fun?)
        load-list (tiles-to-load tree increase-level-fun?)
        new-tiles (load-tiles-data (tiles-meta-data load-list))
        check     (if neighbours? check-neighbours identity)]
    {:tree (check (quadtree-add (quadtree-drop tree drop-list) load-list new-tiles))
     :drop (quadtree-extract tree drop-list)
     :load load-list}))

(defn parent-path
  "Get path of parent for specified path"
  [path]
  (subvec path 0 (dec (count path))))

(defn add-parent-info
  "Add parent data to a tile"
  [tile parent]
  (assoc tile :parent (select-keys parent [:level :face :x :y :heightfield :colors :normals])))

(defn update-tree-parents
  "Add parent info to specified paths in tree"
  [tree paths]
  (quadtree-update tree paths add-parent-info (quadtree-extract tree (map parent-path paths))))

(set! *unchecked-math* false)
