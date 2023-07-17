(ns sfsim25.globe
  "Use Mercator color and elevation map tiles to generate cube map tiles for color, water, elevation, and normals."
  (:require [com.climate.claypoole :as cp]
            [fastmath.vector :refer (mag)]
            [sfsim25.cubemap :refer (cartesian->geodetic color-geodetic-day color-geodetic-night cube-coordinate cube-map
                                     normal-for-point project-onto-globe water-geodetic)]
            [sfsim25.util :refer (align-address cube-dir cube-path make-progress-bar set-byte! set-float! set-pixel!
                                  set-vector3! spit-bytes spit-floats spit-jpg sqr tick-and-print spit-normals)])
  (:import [java.io File])
  (:gen-class))

(set! *unchecked-math* true)

(defn make-cube-map
  "Program to generate tiles for cube map"
  [in-level out-level]
  (let [n                  (bit-shift-left 1 out-level)
        width              675
        elevation-tilesize 33
        sublevel           2
        subsample          (bit-shift-left 1 sublevel)
        color-tilesize     (inc (* subsample (dec elevation-tilesize)))
        radius             6378000.0
        bar                (agent (make-progress-bar (* 6 n n) 1))]
    (cp/pdoseq (+ (cp/ncpus) 2) [k (range 6) b (range n) a (range n)]
      (let [tile-night {:width color-tilesize :height color-tilesize :data (byte-array (* 4 (sqr color-tilesize)))}]
        (doseq [v (range color-tilesize) u (range color-tilesize)]
          (let [j                 (cube-coordinate out-level color-tilesize b v)
                i                 (cube-coordinate out-level color-tilesize a u)
                p                 (cube-map k j i)
                point             (project-onto-globe p (min 4 in-level) width radius)
                [lon lat _height] (cartesian->geodetic point radius)
                color-night       (color-geodetic-night (min 5 (+ in-level sublevel)) width lon lat)]
            (set-pixel! tile-night v u color-night)))
        (spit-jpg (cube-path "data/globe" k out-level b a ".night.jpg") tile-night)
        (send bar tick-and-print)))))

(set! *unchecked-math* false)
