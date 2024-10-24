(ns sfsim.quadtree
  "Manage quad tree of map tiles."
  (:require [fastmath.vector :refer (sub mag)]
            [clojure.math :refer (tan to-radians)]
            [malli.core :as m]
            [sfsim.cubemap :refer (tile-center project-onto-cube determine-face cube-i cube-j)]
            [sfsim.matrix :refer (fvec3)]
            [sfsim.plane :refer (points->plane ray-plane-intersection-parameter)]
            [sfsim.image :refer (slurp-image slurp-normals get-vector3)]
            [sfsim.util :refer (cube-path slurp-floats slurp-bytes dissoc-in N N0)]))

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

(def tile (m/schema [:map [::face N0]
                          [::level N0]
                          [::y N0]
                          [::x N0]
                          [::center :any]
                          [:sfsim.planet/day :any]
                          [:sfsim.planet/night :any]
                          [:sfsim.planet/surface :any]
                          [:sfsim.planet/normals :any]
                          [:sfsim.planet/water :any]]))

(defn load-tile-data
  "Load data associated with a cube map tile"
  {:malli/schema [:=> [:cat N0 N0 N0 N0 :double] tile]}
  [face level y x radius]
  {::face                 face
   ::level                level
   ::y                    y
   ::x                    x
   ::center               (tile-center face level y x radius)
   :sfsim.planet/day     (slurp-image   (cube-path "data/globe" face level y x ".jpg"))
   :sfsim.planet/night   (slurp-image   (cube-path "data/globe" face level y x ".night.jpg"))
   :sfsim.planet/surface (slurp-floats  (cube-path "data/globe" face level y x ".surf"))
   :sfsim.planet/normals (slurp-normals (cube-path "data/globe" face level y x ".png"))
   :sfsim.planet/water   (slurp-bytes   (cube-path "data/globe" face level y x ".water"))})

(def tile-info (m/schema [:map [::face N0] [::level N0] [::y N0] [::x N0]]))

(defn sub-tiles-info
  "Get metadata for sub tiles of cube map tile"
  {:malli/schema [:=> [:cat N0 N0 N0 N0] [:vector tile-info]]}
  [face level y x]
  [{::face face ::level (inc level) ::y (* 2 y)       ::x (* 2 x)}
   {::face face ::level (inc level) ::y (* 2 y)       ::x (inc (* 2 x))}
   {::face face ::level (inc level) ::y (inc (* 2 y)) ::x (* 2 x)}
   {::face face ::level (inc level) ::y (inc (* 2 y)) ::x (inc (* 2 x))}])

(defn load-tiles-data
  "Load a set of tiles"
  {:malli/schema [:=> [:cat [:sequential tile-info] :double] [:vector :any]]}
  [metadata radius]
  (mapv (fn load-tile-using-info [{::keys [face level y x]}] (load-tile-data face level y x radius)) metadata))

(defn is-leaf?
  "Check whether specified tree node is a leaf"
  {:malli/schema [:=> [:cat [:maybe :map]] :boolean]}
  [node]
  (not
    (or (nil? node)
        (contains? node ::face0)
        (contains? node ::face1)
        (contains? node ::face2)
        (contains? node ::face3)
        (contains? node ::face4)
        (contains? node ::face5)
        (contains? node ::quad0)
        (contains? node ::quad1)
        (contains? node ::quad2)
        (contains? node ::quad3))))

(defn- is-flat?
  "Check whether node has four leafs"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [node]
  (and (is-leaf? (::quad0 node)) (is-leaf? (::quad1 node)) (is-leaf? (::quad2 node)) (is-leaf? (::quad3 node))))

(defn- sub-paths
  "Get path for each leaf"
  [path]
  [(conj path ::quad0) (conj path ::quad1) (conj path ::quad2) (conj path ::quad3)])

(defn tiles-to-drop
  "Determine tiles to remove from the quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] fn? [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree increase-level-fun?]
   (mapcat #(tiles-to-drop (% tree) increase-level-fun? [%]) [::face0 ::face1 ::face2 ::face3 ::face4 ::face5]))
  ([tree increase-level-fun? path]
   (cond
     (nil? tree) []
     (and (is-flat? tree) (not (increase-level-fun? (::face tree) (::level tree) (::y tree) (::x tree)))) (sub-paths path)
     :else (mapcat #(tiles-to-drop (% tree) increase-level-fun? (conj path %)) [::quad0 ::quad1 ::quad2 ::quad3]))))

(defn tiles-path-list
  "Get a list with a path to every quad tree tile"
  {:malli/schema [:=> [:cat [:maybe :map] [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree] (tiles-path-list tree []))
  ([tree path]
   (let [nodes       (filter (partial contains? tree) [::face0 ::face1 ::face2 ::face3 ::face4 ::face5
                                                       ::quad0 ::quad1 ::quad2 ::quad3])
         child-paths (mapcat #(tiles-path-list (get tree %) (conj path %)) nodes)]
     (concat child-paths (map #(conj path %) nodes)))))

(defn tiles-to-load
  "Determine which tiles to load into the quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] fn? [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree increase-level-fun?]
   (if (empty? tree)
     [[::face0] [::face1] [::face2] [::face3] [::face4] [::face5]]
     (mapcat (fn load-tiles-for-face [k] (tiles-to-load (k tree) increase-level-fun? [k]))
             [::face0 ::face1 ::face2 ::face3 ::face4 ::face5])))
  ([tree increase-level-fun? path]
   (if (nil? tree) []
     (let [increase? (increase-level-fun? (::face tree) (::level tree) (::y tree) (::x tree))]
       (cond
         (and (is-leaf? tree) increase?) (sub-paths path)
         increase?                       (mapcat #(tiles-to-load (% tree) increase-level-fun? (conj path %))
                                                 [::quad0 ::quad1 ::quad2 ::quad3])
         :else                           [])))))

(def face->index {::face0 0 ::face1 1 ::face2 2 ::face3 3 ::face4 4 ::face5 5})

(defn tile-meta-data
  "Convert tile path to face, level, y and x"
  {:malli/schema [:=> [:cat [:vector :keyword]] tile-info]}
  [path]
  (let [[top & tree]    path
        dy              {::quad0 0 ::quad1 0 ::quad2 1 ::quad3 1}
        dx              {::quad0 0 ::quad1 1 ::quad2 0 ::quad3 1}
        combine-offsets #(bit-or (bit-shift-left %1 1) %2)]
    {::face  (face->index top)
     ::level (count tree)
     ::y     (reduce combine-offsets 0 (map dy tree))
     ::x     (reduce combine-offsets 0 (map dx tree))}))

(defn tiles-meta-data
  "Convert multiple tile paths to face, level, y and x"
  {:malli/schema [:=> [:cat [:sequential [:vector :keyword]]] [:vector tile-info]]}
  [paths]
  (mapv tile-meta-data paths))

(defn quadtree-add
  "Add tiles to quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]] [:sequential :map]] [:maybe :map]]}
  [tree paths tiles]
  (reduce (fn add-title-to-quadtree [tree [path tile]] (assoc-in tree path tile)) tree (map vector paths tiles)))

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
  (reduce (fn update-tile-in-quadtree [tree [path & args]]
              (apply update-in tree path fun args)) tree (apply map list paths arglists)))

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
         (let [c0           {::quad0 ::quad0, ::quad1 ::quad1, ::quad3 ::quad3, ::quad2 ::quad2}
               c1           {::quad0 ::quad2, ::quad1 ::quad0, ::quad3 ::quad1, ::quad2 ::quad3}
               c2           {::quad0 ::quad3, ::quad1 ::quad2, ::quad3 ::quad0, ::quad2 ::quad1}
               c3           {::quad0 ::quad1, ::quad1 ::quad3, ::quad3 ::quad2, ::quad2 ::quad0}
               [replacement rotation]
               (case (first path)
                 ::face0 (case (long dy) -1 [::face3 c2],
                                          0 (case (long dx) -1 [::face4 c1], 0 [::face0 c0], 1 [::face2 c3]),
                                          1 [::face1 c0])
                 ::face1 (case (long dy) -1 [::face0 c0],
                                          0 (case (long dx) -1 [::face4 c0], 0 [::face1 c0], 1 [::face2 c0]),
                                          1 [::face5 c0])
                 ::face2 (case (long dy) -1 [::face0 c1],
                                          0 (case (long dx) -1 [::face1 c0], 0 [::face2 c0], 1 [::face3 c0]),
                                          1 [::face5 c3])
                 ::face3 (case (long dy) -1 [::face0 c2],
                                          0 (case (long dx) -1 [::face2 c0], 0 [::face3 c0], 1 [::face4 c0]),
                                          1 [::face5 c2])
                 ::face4 (case (long dy) -1 [::face0 c3],
                                          0 (case (long dx) -1 [::face3 c0], 0 [::face4 c0], 1 [::face1 c0]),
                                          1 [::face5 c1])
                 ::face5 (case (long dy) -1 [::face1 c0],
                                          0 (case (long dx) -1 [::face4 c3], 0 [::face5 c0], 1 [::face2 c1]),
                                          1 [::face3 c2]))]
           (cons replacement (map rotation tail)))
         (let [[replacement propagate]
               (case tile
                 ::quad0 (case (long dy) -1 [::quad2 true ],
                                          0 (case (long dx) -1 [::quad1 true ] 0 [::quad0 false] 1 [::quad1 false]),
                                          1 [::quad2 false])
                 ::quad1 (case (long dy) -1 [::quad3 true ],
                                          0 (case (long dx) -1 [::quad0 false] 0 [::quad1 false] 1 [::quad0 true ]),
                                          1 [::quad3 false])
                 ::quad2 (case (long dy) -1 [::quad0 false],
                                          0 (case (long dx) -1 [::quad3 true ] 0 [::quad2 false] 1 [::quad3 false]),
                                          1 [::quad0 true ])
                 ::quad3 (case (long dy) -1 [::quad1 false],
                                          0 (case (long dx) -1 [::quad2 false] 0 [::quad3 false] 1 [::quad2 true ]),
                                          1 [::quad1 true ]))]
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
    (fn leaves-of-node [[k v]]
      (if (is-leaf? v)
        [(list k)]
        (map #(cons k %) (leaf-paths v))))
    (select-keys tree #{::face0 ::face1 ::face2 ::face3 ::face4 ::face5 ::quad0 ::quad1 ::quad2 ::quad3})))

(defn- check-neighbours-for-tile
  "Update neighbourhood information for a single tile"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential :keyword]] [:maybe :map]]}
  [tree path]
  (reduce
    (fn check-neighbour-resolution [updated-tree [direction neighbour-path]]
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

(defn tile-coordinates
  "Get tile indices, coordinates of pixel within tile and position in pixel"
  {:malli/schema [:=> [:cat :double :double :int :int] [:tuple :int :int :int :int :double :double]]}
  [j i level tilesize]
  (let [n      (bit-shift-left 1 level)
        jj     (* j n)
        ii     (* i n)
        row    (min (long jj) (dec n))
        column (min (long ii) (dec n))
        dj     (- jj row)
        di     (- ii column)
        y      (* dj (dec tilesize))
        x      (* di (dec tilesize))
        tile-y (min (long y) (- tilesize 2))
        tile-x (min (long x) (- tilesize 2))
        dy     (- y tile-y)
        dx     (- x tile-x)]
    [row column tile-y tile-x dy dx]))

(defn tile-triangle
  "Determine triangle of quad the specified coordinate is in"
  {:malli/schema [:=> [:cat :double :double :boolean] [:vector [:tuple :int :int]]]}
  [y x first-diagonal]
  (if first-diagonal
    (if (>= x y) [[0 0] [0 1] [1 1]] [[0 0] [1 1] [1 0]])
    (if (<= (+ x y) 1) [[0 0] [0 1] [1 0]] [[1 0] [0 1] [1 1]])))

(defn distance-to-surface
  "Get distance of surface to planet center for given radial vector"
  {:malli/schema [:=> [:cat fvec3 :int :int :double [:vector [:vector :boolean]]] :double]}
  [point level tilesize radius split-orientations]
  (let [p                         (project-onto-cube point)
        face                      (determine-face p)
        j                         (cube-j face p)
        i                         (cube-i face p)
        [b a tile-y tile-x dy dx] (tile-coordinates j i level tilesize)
        path                      (cube-path "data/globe" face level b a ".surf")
        terrain                   #:sfsim.image{:width tilesize :height tilesize :data (slurp-floats path)}
        center                    (tile-center face level b a radius)
        orientation               (nth (nth split-orientations tile-y) tile-x)
        triangle                  (mapv (fn [[y x]] (get-vector3 terrain (+ y tile-y) (+ x tile-x)))
                                        (tile-triangle dy dx orientation))
        plane                     (apply points->plane triangle)
        ray                       #:sfsim.ray{:origin (sub center) :direction point}
        multiplier                (ray-plane-intersection-parameter plane ray)
        magnitude-point           (mag point)]
    (* multiplier magnitude-point)))

(defn neighbour-tile
  "Get neighbouring tile face and coordinates"
  {:malli/schema [:=> [:cat :int :int :int :int :int :int :int :int :int] [:tuple :int :int :int :int :int]]}
  [face level tilesize b a tile-y tile-x dy dx]
  (let [tile-y (+ tile-y dy)]
    (if (>= tile-y tilesize)
      (recur face level tilesize (inc b) a (- tile-y tilesize) tile-x 0 dx)
      (if (< tile-y 0)
        (recur face level tilesize (dec b) a (+ tile-y tilesize) tile-x 0 dx)
        (let [tile-x (+ tile-x dx)]
          (if (>= tile-x tilesize)
            (recur face level tilesize b (inc a) tile-y (- tile-x tilesize) dy 0)
            (if (< tile-x 0)
              (recur face level tilesize b (dec a) tile-y (+ tile-x tilesize) dy 0)
              [face b a tile-y tile-x])))))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
