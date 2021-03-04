(ns sfsim25.quadtree
  (:require [sfsim25.vector3 :refer (norm) :as v]
            [sfsim25.cubemap :refer (tile-center)]))

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
  [face level tilesize b a radius1 radius2 width angle max-size position]
  (let [center   (tile-center face level b a radius1 radius2)
        distance (norm (v/- position center))
        size     (quad-size level tilesize radius1 width distance angle)]
    (> size max-size)))

(set! *unchecked-math* false)
