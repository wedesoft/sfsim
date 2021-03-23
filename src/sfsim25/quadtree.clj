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

(defn increase-level?
  "Decide whether next quad tree level is required"
  [face level tilesize y x radius1 radius2 width angle max-size position]
  (let [center   (tile-center face level y x radius1 radius2)
        distance (norm (v/- position center))
        size     (quad-size level tilesize radius1 width distance angle)]
    (> size max-size)))

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

(set! *unchecked-math* false)
