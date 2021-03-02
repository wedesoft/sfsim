(ns sfsim25.globe
  (:require [clojure.core.memoize :as m]
            [sfsim25.cubemap :refer (cube-map cube-coordinate color-for-point water-for-point elevated-point normal-for-point)]
            [sfsim25.util :refer (tile-path spit-image spit-bytes spit-floats set-pixel! set-water!
                                  set-vector! cube-dir cube-path ubyte->byte)]
            [sfsim25.rgb :as r]
            [sfsim25.vector3 :as v])
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
    (doseq [k (range 6) b (range n) a (range n)]
      (let [tile    {:width tilesize :height tilesize :data (byte-array (* 3 tilesize tilesize))}
            water   {:width tilesize :height tilesize :data (byte-array (* tilesize tilesize))}
            points  {:width tilesize :height tilesize :data (float-array (* 3 tilesize tilesize))}
            normals {:width tilesize :height tilesize :data (float-array (* 3 tilesize tilesize))}]
        (doseq [v (range tilesize) u (range tilesize)]
          (let [j      (cube-coordinate out-level tilesize b v)
                i      (cube-coordinate out-level tilesize a u)
                p      (v/normalize (cube-map k j i))
                color  (color-for-point in-level width p)
                point  (elevated-point in-level width p radius1 radius2)
                normal (normal-for-point p in-level out-level width tilesize radius1 radius2)
                wet    (water-for-point in-level width p)]
            (set-pixel! tile v u color)
            (set-water! water v u wet)
            (set-vector! points v u point)
            (set-vector! normals v u normal)))
        (println (cube-path "globe" k out-level b a ".*"))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tile)
        (spit-bytes (cube-path "globe" k out-level b a ".water") (:data water))
        (spit-floats (cube-path "globe" k out-level b a ".points") (:data points))
        (spit-floats (cube-path "globe" k out-level b a ".normals") (:data normals))))))

(set! *unchecked-math* false)
