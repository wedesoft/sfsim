(ns sfsim25.globe
  "Convert Mercator image and elevation data into cube map tiles."
  (:require [clojure.core.memoize :as m]
            [com.climate.claypoole :as cp]
            [sfsim25.cubemap :refer (cube-map cube-coordinate color-geodetic water-geodetic project-onto-globe normal-for-point
                                     cartesian->geodetic)]
            [sfsim25.util :refer (tile-path spit-image spit-bytes spit-floats set-pixel! set-water! set-vector! set-scale! cube-dir
                                  cube-path ubyte->byte)]
            [sfsim25.vector3 :refer (norm)])
  (:import [java.io File]
           [sfsim25.vector3 Vector3])
  (:gen-class))

(set! *unchecked-math* true)

(defn -main
  "Program to generate tiles for cube map"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-globe [input level] [output level]")
    (System/exit 1))
  (let [in-level  (Integer/parseInt (nth args 0))
        out-level (Integer/parseInt (nth args 1))
        n         (bit-shift-left 1 out-level)
        width     675
        tilesize  33
        radius1   6378000.0
        radius2   6357000.0]
    (cp/pdoseq (+ (cp/ncpus) 2) [k (range 6) b (range n) a (range n)]
      (let [tile    {:width tilesize :height tilesize :data (byte-array (* 4 tilesize tilesize))}
            water   {:width tilesize :height tilesize :data (byte-array (* tilesize tilesize))}
            scale   {:width tilesize :height tilesize :data (float-array (* tilesize tilesize))}
            normals {:width tilesize :height tilesize :data (float-array (* 3 tilesize tilesize))}]
        (doseq [v (range tilesize) u (range tilesize)]
          (let [j                (cube-coordinate out-level tilesize b v)
                i                (cube-coordinate out-level tilesize a u)
                p                (cube-map k j i)
                point            (project-onto-globe p in-level width radius1 radius2)
                [lon lat height] (cartesian->geodetic point radius1 radius2)
                color            (color-geodetic in-level width lon lat)
                normal           (normal-for-point point in-level out-level width tilesize radius1 radius2)
                wet              (water-geodetic in-level width lon lat)]
            (set-pixel! tile v u color)
            (set-water! water v u wet)
            (set-scale! scale v u (/ (norm point) (norm p) 6388000.0))
            (set-vector! normals v u normal)))
        (locking *out* (println (cube-path "globe" k out-level b a ".*")))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tile)
        (spit-bytes (cube-path "globe" k out-level b a ".water") (:data water))
        (spit-floats (cube-path "globe" k out-level b a ".scale") (:data scale))
        (spit-floats (cube-path "globe" k out-level b a ".normals") (:data normals))))
    (System/exit 0)))

(set! *unchecked-math* false)
