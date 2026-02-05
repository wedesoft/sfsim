;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.quadtree
  "Manage quad tree of map tiles."
  (:require
    [clojure.core.memoize :as z]
    [clojure.math :refer (tan to-radians)]
    [fastmath.vector :refer (sub mag add)]
    [malli.core :as m]
    [sfsim.cubemap :refer (tile-center project-onto-cube determine-face cube-i cube-j)]
    [sfsim.image :refer (slurp-image-tar slurp-normals-tar get-vector3)]
    [sfsim.matrix :refer (fvec3)]
    [sfsim.plane :refer (points->plane ray-plane-intersection-parameter)]
    [sfsim.util :refer (cube-tar cube-file-name slurp-floats-gz-tar slurp-bytes-gz-tar dissoc-in untar tar-close
                        make-lru-cache N N0)])
  (:import
    [clojure.lang
     Keyword]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn quad-size
  "Determine screen size of a quad of the world map"
  {:malli/schema [:=> [:cat N0 N :double N :double :double] :double]}
  [level tilesize radius1 width distance angle]
  (let [cnt         (bit-shift-left 1 ^long level)
        real-size   (/ ^double (* 2.0 ^double radius1) ^long cnt ^long (dec ^long tilesize))
        f           (/ ^long width (* 2.0 ^double (-> ^double angle (* 0.5) to-radians tan)))
        screen-size (* ^double (/ ^double real-size ^double (max 1.0 ^double distance)) f)]
    screen-size))


(defn- quad-size-for-camera-position
  "Determine screen size of a quad given the camera position"
  {:malli/schema [:=> [:cat N :double N :double fvec3 :keyword N0 N0 N0] :double]}
  [tilesize radius width angle position face level y x]
  (let [center   (tile-center face level y x radius)
        distance (mag (sub position center))]
    (quad-size level tilesize radius width distance angle)))


(defn increase-level?
  "Decide whether next quad tree level is required"
  {:malli/schema [:=> [:cat N :double N :double N N fvec3 :keyword N0 N0 N0] :boolean]}
  [tilesize radius width angle max-size max-level position face level y x]
  (and (< ^long level ^long max-level)
       (> ^double (quad-size-for-camera-position tilesize radius width angle position face level y x) ^long max-size)))


(def tile
  (m/schema [:map [::face :keyword]
             [::level N0]
             [::y N0]
             [::x N0]
             [::center :any]
             [:sfsim.planet/day :any]
             [:sfsim.planet/night :any]
             [:sfsim.planet/surface :any]
             [:sfsim.planet/normals :any]
             [:sfsim.planet/water :any]]))


(def access-cube-tar (make-lru-cache 80 untar tar-close))


(defn load-tile-data
  "Load data associated with a cube map tile"
  {:malli/schema [:=> [:cat :keyword N0 N0 N0 :double] tile]}
  [face level y x radius]
  (let [tar (access-cube-tar (cube-tar "data/globe" face level x))]
    {::face                 face
     ::level                level
     ::y                    y
     ::x                    x
     ::center               (tile-center face level y x radius)
     :sfsim.planet/day     (slurp-image-tar     tar (cube-file-name y ".jpg"))
     :sfsim.planet/night   (slurp-image-tar     tar (cube-file-name y ".night.jpg"))
     :sfsim.planet/surface (slurp-floats-gz-tar tar (cube-file-name y ".surf.gz"))
     :sfsim.planet/normals (slurp-normals-tar   tar (cube-file-name y ".png"))
     :sfsim.planet/water   (slurp-bytes-gz-tar  tar (cube-file-name y ".water.gz"))}))


(def tile-info (m/schema [:map [::face :keyword] [::level N0] [::y N0] [::x N0]]))


(defn sub-tiles-info
  "Get metadata for sub tiles of cube map tile"
  [^Keyword face ^long level ^long y ^long x]
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
        (contains? node :sfsim.cubemap/face0)
        (contains? node :sfsim.cubemap/face1)
        (contains? node :sfsim.cubemap/face2)
        (contains? node :sfsim.cubemap/face3)
        (contains? node :sfsim.cubemap/face4)
        (contains? node :sfsim.cubemap/face5)
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
   (mapcat #(tiles-to-drop (% tree) increase-level-fun? [%])
           [:sfsim.cubemap/face0 :sfsim.cubemap/face1 :sfsim.cubemap/face2
            :sfsim.cubemap/face3 :sfsim.cubemap/face4 :sfsim.cubemap/face5]))
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
   (let [nodes       (filter (partial contains? tree) [:sfsim.cubemap/face0 :sfsim.cubemap/face1 :sfsim.cubemap/face2
                                                       :sfsim.cubemap/face3 :sfsim.cubemap/face4 :sfsim.cubemap/face5
                                                       ::quad0 ::quad1 ::quad2 ::quad3])
         child-paths (mapcat #(tiles-path-list (get tree %) (conj path %)) nodes)]
     (concat child-paths (mapv #(conj path %) nodes)))))


(defn tiles-to-load
  "Determine which tiles to load into the quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] fn? [:? [:vector :keyword]]] [:sequential [:vector :keyword]]]}
  ([tree increase-level-fun?]
   (if (empty? tree)
     [[:sfsim.cubemap/face0] [:sfsim.cubemap/face1] [:sfsim.cubemap/face2]
      [:sfsim.cubemap/face3] [:sfsim.cubemap/face4] [:sfsim.cubemap/face5]]
     (mapcat (fn load-tiles-for-face [k] (tiles-to-load (k tree) increase-level-fun? [k]))
             [:sfsim.cubemap/face0 :sfsim.cubemap/face1 :sfsim.cubemap/face2
              :sfsim.cubemap/face3 :sfsim.cubemap/face4 :sfsim.cubemap/face5])))
  ([tree increase-level-fun? path]
   (if (nil? tree) []
       (let [increase? (increase-level-fun? (::face tree) (::level tree) (::y tree) (::x tree))]
         (cond
           (and (is-leaf? tree) increase?) (sub-paths path)
           increase?                       (mapcat #(tiles-to-load (% tree) increase-level-fun? (conj path %))
                                                   [::quad0 ::quad1 ::quad2 ::quad3])
           :else                           [])))))


(defn tile-meta-data
  "Convert tile path to face, level, y and x"
  {:malli/schema [:=> [:cat [:vector :keyword]] tile-info]}
  [path]
  (let [[top & tree]    path
        dy              {::quad0 0 ::quad1 0 ::quad2 1 ::quad3 1}
        dx              {::quad0 0 ::quad1 1 ::quad2 0 ::quad3 1}
        combine-offsets #(bit-or (bit-shift-left ^long %1 1) ^long %2)]
    {::face  top
     ::level (count tree)
     ::y     (reduce combine-offsets 0 (mapv dy tree))
     ::x     (reduce combine-offsets 0 (mapv dx tree))}))


(defn tiles-meta-data
  "Convert multiple tile paths to face, level, y and x"
  {:malli/schema [:=> [:cat [:sequential [:vector :keyword]]] [:vector tile-info]]}
  [paths]
  (mapv tile-meta-data paths))


(defn quadtree-add
  "Add tiles to quad tree"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential [:vector :keyword]] [:sequential :map]] [:maybe :map]]}
  [tree paths tiles]
  (reduce (fn add-title-to-quadtree [tree [path tile]] (assoc-in tree path tile)) tree (mapv vector paths tiles)))


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
  (reduce (fn update-tile-in-quadtree
            [tree [path & args]]
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
                 :sfsim.cubemap/face0
                 (case (long dy)
                   -1 [:sfsim.cubemap/face3 c2],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face4 c1], 0 [:sfsim.cubemap/face0 c0], 1 [:sfsim.cubemap/face2 c3]),
                   +1 [:sfsim.cubemap/face1 c0])
                 :sfsim.cubemap/face1
                 (case (long dy)
                   -1 [:sfsim.cubemap/face0 c0],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face4 c0], 0 [:sfsim.cubemap/face1 c0], 1 [:sfsim.cubemap/face2 c0]),
                   +1 [:sfsim.cubemap/face5 c0])
                 :sfsim.cubemap/face2
                 (case (long dy)
                   -1 [:sfsim.cubemap/face0 c1],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face1 c0], 0 [:sfsim.cubemap/face2 c0], 1 [:sfsim.cubemap/face3 c0]),
                   +1 [:sfsim.cubemap/face5 c3])
                 :sfsim.cubemap/face3
                 (case (long dy)
                   -1 [:sfsim.cubemap/face0 c2],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face2 c0], 0 [:sfsim.cubemap/face3 c0], 1 [:sfsim.cubemap/face4 c0]),
                   +1 [:sfsim.cubemap/face5 c2])
                 :sfsim.cubemap/face4
                 (case (long dy)
                   -1 [:sfsim.cubemap/face0 c3],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face3 c0], 0 [:sfsim.cubemap/face4 c0], 1 [:sfsim.cubemap/face1 c0]),
                   +1 [:sfsim.cubemap/face5 c1])
                 :sfsim.cubemap/face5
                 (case (long dy)
                   -1 [:sfsim.cubemap/face1 c0],
                   +0 (case (long dx) -1 [:sfsim.cubemap/face4 c3], 0 [:sfsim.cubemap/face5 c0], 1 [:sfsim.cubemap/face2 c1]),
                   +1 [:sfsim.cubemap/face3 c2]))]
           (cons replacement (mapv rotation tail)))
         (let [[replacement propagate]
               (case tile
                 ::quad0 (case (long dy) -1 [::quad2 true],
                               0 (case (long dx) -1 [::quad1 true] 0 [::quad0 false] 1 [::quad1 false]),
                               1 [::quad2 false])
                 ::quad1 (case (long dy) -1 [::quad3 true],
                               0 (case (long dx) -1 [::quad0 false] 0 [::quad1 false] 1 [::quad0 true]),
                               1 [::quad3 false])
                 ::quad2 (case (long dy) -1 [::quad0 false],
                               0 (case (long dx) -1 [::quad3 true] 0 [::quad2 false] 1 [::quad3 false]),
                               1 [::quad0 true])
                 ::quad3 (case (long dy) -1 [::quad1 false],
                               0 (case (long dx) -1 [::quad2 false] 0 [::quad3 false] 1 [::quad2 true]),
                               1 [::quad1 true]))]
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
    (fn leaves-of-node
      [[k v]]
      (if (is-leaf? v)
        [(list k)]
        (mapv #(cons k %) (leaf-paths v))))
    (select-keys tree #{:sfsim.cubemap/face0 :sfsim.cubemap/face1 :sfsim.cubemap/face2
                        :sfsim.cubemap/face3 :sfsim.cubemap/face4 :sfsim.cubemap/face5
                        ::quad0 ::quad1 ::quad2 ::quad3})))


(defn- check-neighbours-for-tile
  "Update neighbourhood information for a single tile"
  {:malli/schema [:=> [:cat [:maybe :map] [:sequential :keyword]] [:maybe :map]]}
  [tree path]
  (reduce
    (fn check-neighbour-resolution
      [updated-tree [direction neighbour-path]]
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


(def coords
  (m/schema [:map [::row :int]
             [::column :int]
             [::tile-y :int]
             [::tile-x :int]
             [::dy :double]
             [::dx :double]]))


(defn tile-coordinates
  "Get tile indices, coordinates of pixel within tile and position in pixel"
  [^double j ^double i ^long level ^long tilesize]
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
    {::row row ::column column ::tile-y tile-y ::tile-x tile-x ::dy dy ::dx dx}))


(defn tile-triangle
  "Determine triangle of quad the specified coordinate is in"
  [^double y ^double x ^Boolean first-diagonal]
  (if first-diagonal
    (if (>= x y) [[0 0] [0 1] [1 1]] [[0 0] [1 1] [1 0]])
    (if (<= (+ x y) 1) [[0 0] [0 1] [1 0]] [[1 0] [0 1] [1 1]])))


(def load-surface-tile
  "Load tile with specified face and tile position"
  (z/lru
    (fn [level tilesize face row column]
      (let [tar       (access-cube-tar (cube-tar "data/globe" face level column))
            file-name (cube-file-name row ".surf.gz")]
        #:sfsim.image{:width tilesize :height tilesize :data (slurp-floats-gz-tar tar file-name)}))
    :lru/threshold 4))


(defn tile-center-to-surface
  "Get vector from tile center to surface mesh point"
  {:malli/schema [:=> [:cat :int :int :keyword :int :int :double :double] fvec3]}
  [level tilesize face row column tile-y tile-x]
  (let [terrain (load-surface-tile level tilesize face row column)]
    (get-vector3 terrain tile-y tile-x)))


(def planet-config
  (m/schema [:map [:sfsim.planet/level N] [:sfsim.planet/tilesize N] [:sfsim.planet/radius :double]]))


(defn distance-to-surface
  "Get distance of surface to planet center for given radial vector"
  {:malli/schema [:=> [:cat fvec3 planet-config [:vector [:vector :boolean]]] :double]}
  [point {:sfsim.planet/keys [level tilesize radius]} split-orientations]
  (let [p                      (project-onto-cube point)
        face                   (determine-face p)
        j                      (cube-j face p)
        i                      (cube-i face p)
        {::keys [^long row ^long column ^long tile-y ^long tile-x ^double dy ^double dx]}
                               (tile-coordinates j i level tilesize)
        terrain                (load-surface-tile level tilesize face row column)
        center                 (tile-center face level row column radius)
        orientation            (nth (nth split-orientations tile-y) tile-x)
        triangle               (mapv (fn [[^long y ^long x]] (get-vector3 terrain (+ y ^long tile-y) (+ x ^long tile-x)))
                                     (tile-triangle dy dx orientation))
        plane                  (apply points->plane triangle)
        ray                    #:sfsim.ray{:origin (sub center) :direction point}
        multiplier             (ray-plane-intersection-parameter plane ray)
        magnitude-point        (mag point)]
    (* ^double multiplier magnitude-point)))


(defn rotate-b-long
  [^long angle ^long size ^long row ^long column]
  (case angle
    0   row
    90  (- size column 1)
    180 (- size row 1)
    270 column))


(defn rotate-a-long
  [^long angle ^long size ^long row ^long column]
  (case angle
    0   column
    90  row
    180 (- size column 1)
    270 (- size row 1)))


(defn rotate-b-double
  [^long angle ^long size ^double tile-y ^double tile-x]
  (case angle
    0   tile-y
    90  (- size tile-x 1)
    180 (- size tile-y 1)
    270 tile-x))


(defn rotate-a-double
  [^long angle ^long size ^double tile-y ^double tile-x]
  (case angle
    0   tile-x
    90  tile-y
    180 (- size tile-x 1)
    270 (- size tile-y 1)))


(declare neighbour-tile)


(def neighbour
  (m/schema [:map [::face :keyword]
             [::row :int]
             [::column :int]
             [::tile-y :double]
             [::tile-x :double]
             [::rotation :int]]))


(defn build-neighbour
  "Create vector with coordinates in neighbouring face"
  {:malli/schema [:=> [:cat :keyword :int :int :int :int :int :double :double :int :int :int] neighbour]}
  [face drotation level tilesize row column tile-y tile-x dy dx rotation]
  (let [gridsize  (bit-shift-left 1 ^long level)
        row'      (rotate-b-long drotation gridsize row column)
        column'   (rotate-a-long drotation gridsize row column)
        tile-y'   (rotate-b-double drotation tilesize tile-y tile-x)
        tile-x'   (rotate-a-double drotation tilesize tile-y tile-x)
        dy'       (rotate-b-long drotation 1 dy dx)
        dx'       (rotate-a-long drotation 1 dy dx)
        rotation' (mod (+ ^long rotation ^long drotation) 360)]
    (neighbour-tile face level tilesize row' column' tile-y' tile-x' dy' dx' rotation')))


(defn neighbour-tile
  "Get neighbouring tile face and coordinates"
  {:malli/schema [:=> [:cat :keyword :int :int :int :int :double :double :int :int :int] neighbour]}
  [face level tilesize row column tile-y tile-x dy dx rotation]
  (let [gridsize (bit-shift-left 1 ^long level)]
    (cond
      (< ^long row 0)
      (let [row (+ ^long row gridsize)]
        (case face
          :sfsim.cubemap/face0 (build-neighbour :sfsim.cubemap/face3 180 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face1 (build-neighbour :sfsim.cubemap/face0   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face2 (build-neighbour :sfsim.cubemap/face0  90 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face3 (build-neighbour :sfsim.cubemap/face0 180 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face4 (build-neighbour :sfsim.cubemap/face0 270 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face5 (build-neighbour :sfsim.cubemap/face1   0 level tilesize row column tile-y tile-x dy dx rotation)))
      (>= ^long row gridsize)
      (let [row (- ^long row gridsize)]
        (case face
          :sfsim.cubemap/face0 (build-neighbour :sfsim.cubemap/face1   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face1 (build-neighbour :sfsim.cubemap/face5   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face2 (build-neighbour :sfsim.cubemap/face5 270 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face3 (build-neighbour :sfsim.cubemap/face5 180 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face4 (build-neighbour :sfsim.cubemap/face5  90 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face5 (build-neighbour :sfsim.cubemap/face3 180 level tilesize row column tile-y tile-x dy dx rotation)))
      (< ^long column 0)
      (let [column (+ ^long column gridsize)]
        (case face
          :sfsim.cubemap/face0 (build-neighbour :sfsim.cubemap/face4  90 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face1 (build-neighbour :sfsim.cubemap/face4   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face2 (build-neighbour :sfsim.cubemap/face1   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face3 (build-neighbour :sfsim.cubemap/face2   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face4 (build-neighbour :sfsim.cubemap/face3   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face5 (build-neighbour :sfsim.cubemap/face4 270 level tilesize row column tile-y tile-x dy dx rotation)))
      (>= ^long column gridsize)
      (let [column (- ^long column gridsize)]
        (case face
          :sfsim.cubemap/face0 (build-neighbour :sfsim.cubemap/face2 270 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face1 (build-neighbour :sfsim.cubemap/face2   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face2 (build-neighbour :sfsim.cubemap/face3   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face3 (build-neighbour :sfsim.cubemap/face4   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face4 (build-neighbour :sfsim.cubemap/face1   0 level tilesize row column tile-y tile-x dy dx rotation)
          :sfsim.cubemap/face5 (build-neighbour :sfsim.cubemap/face2  90 level tilesize row column tile-y tile-x dy dx rotation)))
      :else
      (let [tile-y (+ ^double tile-y ^long dy)]
        (cond
          (>= ^double tile-y (dec ^long tilesize))
          (recur face level tilesize (inc ^long row) column (- ^double tile-y ^long (dec ^long tilesize)) tile-x 0 dx rotation)
          (< ^double tile-y 0)
          (recur face level tilesize (dec ^long row) column (+ ^double tile-y ^long (dec ^long tilesize)) tile-x 0 dx rotation)
          :else
          (let [tile-x (+ ^double tile-x ^long dx)]
            (cond (>= ^double tile-x ^long (dec ^long tilesize))
                  (recur face level tilesize row (inc ^long column) ^double tile-y (- ^double tile-x ^long (dec ^long tilesize))
                         0 0 rotation)
                  (< ^double tile-x 0)
                  (recur face level tilesize row (dec ^long column) tile-y (+ ^double tile-x ^long (dec ^long tilesize))
                         0 0 rotation)
                  :else
                  {::face face ::row row ::column column ::tile-y tile-y ::tile-x tile-x ::rotation rotation})))))))


(defn translate-indices
  "Apply lookup table with index replacements"
  {:malli/schema [:=> [:cat [:map-of :int :int] [:vector :int]] [:vector :int]]}
  [index-map indices]
  (mapv #(get index-map % %) indices))


(defn generate-triangle-pair
  "Make two triangles for a quad in a 3x3 mesh of 4x4 points"
  [^long dy ^long dx ^Boolean orientation]
  (let [offset (+ (* 4 dy) dx)]
    (mapv (partial mapv #(+ ^long offset ^long %)) (if orientation [[5 10 6] [5 9 10]] [[5 9 6] [6 9 10]]))))


(defn indexed-triangles
  "Determine point indices of a pair of triangles and apply index map"
  {:malli/schema [:=> [:cat :int :int :boolean [:map-of :int :int]] [:vector [:tuple :int :int :int]]]}
  [dy dx orientation index-map]
  (mapv (partial translate-indices index-map) (generate-triangle-pair dy dx orientation)))


(defn identify-neighbours
  "Determine relative neighbour coordinates and index map for cube corners with special cases for corners of face"
  {:malli/schema [:=> [:cat :int :int :int :int :int :int]
                  [:map [::coordinates [:vector [:vector :int]]] [::index-map [:map-of :int :int]]]]}
  [level tilesize row column tile-y tile-x]
  (let [gridsize    (bit-shift-left 1 ^long level)
        top         (and (<= ^long row 0) (<= ^long tile-y 0))
        bottom      (and (>= ^long row ^long (dec gridsize)) (>= ^long tile-y (- ^long tilesize 2)))
        left        (and (<= ^long column 0) (<= ^long tile-x 0))
        right       (and (>= ^long column ^long (dec gridsize)) (>= ^long tile-x (- ^long tilesize 2)))
        coordinates (for [dy [-1 0 1] dx [-1 0 1]] [dy dx])]
    (cond
      (and top    left ) {::coordinates (vec (remove #{[-1 -1]} coordinates)) ::index-map {1  4}}
      (and top    right) {::coordinates (vec (remove #{[-1  1]} coordinates)) ::index-map {2  7}}
      (and bottom left ) {::coordinates (vec (remove #{[1 -1]} coordinates)) ::index-map {13  8}}
      (and bottom right) {::coordinates (vec (remove #{[1  1]} coordinates)) ::index-map {14 11}}
      :else              {::coordinates (vec coordinates)                     ::index-map {}})))


(defn quad-split-orientation
  "Perform lookup and rotation for split orientation of quad"
  {:malli/schema [:=> [:cat [:vector [:vector :boolean]] [:map [::tile-y :double] [::tile-x :double] [::rotation :int]]]
                  :boolean]}
  [orientations {::keys [tile-y tile-x rotation]}]
  (let [original-orientation (nth (nth orientations (long tile-y)) (long tile-x))]
    (= original-orientation (= (mod rotation 180) 0))))


(defn create-local-triangles
  "Create local mesh of 3x3x2 triangles using GPU tessellation information"
  {:malli/schema [:=> [:cat [:vector [:vector :boolean]] :keyword :int :int :int :int :int :int] [:vector [:vector :int]]]}
  [orientations face level tilesize row column tile-y tile-x]
  (let [{::keys [coordinates index-map]} (identify-neighbours level tilesize row column tile-y tile-x)]
    (vec
      (mapcat
        (fn [[^long dy ^long dx]]
          (let [neighbour   (neighbour-tile face level tilesize row column (+ ^long tile-y 0.5) (+ ^long tile-x 0.5) dy dx 0)
                orientation (quad-split-orientation orientations neighbour)]
            (indexed-triangles dy dx orientation index-map)))
        coordinates))))


(defn create-local-vertices
  "Get 4x4 points of local mesh of 3x3 quads"
  {:malli/schema [:=> [:cat :keyword :int :int :int :int :int :int :double fvec3] [:vector fvec3]]}
  [face level tilesize row column tile-y tile-x radius center]
  (vec
    (for [dy [-1 0 1 2] dx [-1 0 1 2]]
      (let [{::keys [face row column tile-y tile-x]} (neighbour-tile face level tilesize row column (double tile-y) (double tile-x)
                                                                     dy dx 0)
            surface-vector                           (tile-center-to-surface level tilesize face row column tile-y tile-x)
            center-of-current-tile                   (tile-center face level row column radius)]
        (sub (add center-of-current-tile surface-vector) center)))))


(defn create-local-mesh
  "Return local mesh consisting of vertices and triangles"
  {:malli/schema [:=> [:cat [:vector [:vector :boolean]] :keyword :int :int :int :int :int :int :double :any]
                  [:map [::vertices [:vector :any]] [::triangles [:vector [:vector :int]]]]]}
  [orientations face level tilesize row column tile-y tile-x radius center]
  {::vertices (create-local-vertices face level tilesize row column tile-y tile-x radius center)
   ::triangles (create-local-triangles orientations face level tilesize row column tile-y tile-x)})


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
