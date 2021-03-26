(ns sfsim25.quadtree
  (:require [sfsim25.vector3 :refer (norm) :as v]
            [sfsim25.cubemap :refer (tile-center)]
            [sfsim25.util :refer (cube-path slurp-image slurp-floats slurp-bytes)]))

(set! *unchecked-math* true)

(defn quad-size
  "Determine screen size of a quad of the world map"
  [level tilesize radius1 width distance angle]
  (let [cnt         (bit-shift-left 1 level)
        real-size   (/ (* 2 radius1) cnt (dec tilesize))
        f           (/ width 2 (-> angle (/ 2) Math/toRadians Math/tan))
        screen-size (* (/ real-size distance) f)]
    screen-size))

(defn- quad-size-for-camera-position
  "Determine screen size of a quad given the camera position"
  [tilesize radius1 radius2 width angle position face level y x]
  (let [center   (tile-center face level y x radius1 radius2)
        distance (norm (v/- position center))]
    (quad-size level tilesize radius1 width distance angle)))

(defn increase-level?
  "Decide whether next quad tree level is required"
  [tilesize radius1 radius2 width angle max-size position face level y x]
  (> (quad-size-for-camera-position tilesize radius1 radius2 width angle position face level y x) max-size))

(defn load-tile-data
  "Load data associated with a cube map tile"
  [face level y x]
  {:face    face
   :level   level
   :y       y
   :x       x
   :colors  (slurp-image  (cube-path "globe" face level y x ".png"))
   :scales  (slurp-floats (cube-path "globe" face level y x ".scale"))
   :normals (slurp-floats (cube-path "globe" face level y x ".normals"))
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

(defn- is-leaf?
  "Check whether quad tree node is a leaf"
  [node]
  (not (or (:0 node) (:1 node) (:2 node) (:3 node))))

(defn- tiles-to-remove-from-face
  "Determine tiles to remove for a face of the cube"
  [node increase-level? path]
  (cond
    (nil? node) []
    (and (> (:level node) 0) (is-leaf? node) (not (increase-level? (:face node) (:level node) (:y node) (:x node)))) [path]
    :else (mapcat #(tiles-to-remove-from-face (node %) increase-level? (conj path %)) [:0 :1 :2 :3])))

(defn tiles-to-remove
  "Determine tiles to remove"
  [tree increase-level?]
  (mapcat #(tiles-to-remove-from-face (tree %) increase-level? [%]) [:0 :1 :2 :3 :4 :5]))

(defn tiles-to-add
  "Determine tiles to load"
  [tree increase-level?]
  (mapcat #(if (% tree) [] [[%]]) [:0 :1 :2 :3 :4 :5]))

(set! *unchecked-math* false)
