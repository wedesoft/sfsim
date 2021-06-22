(ns sfsim25.globe
  "Convert Mercator image and elevation data into cube map tiles."
  (:require [clojure.core.memoize :as m]
            [com.climate.claypoole :as cp]
            [clojure.core.matrix :refer :all]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.cubemap :refer :all]
            [sfsim25.rgb :refer (->RGB)]
            [sfsim25.util :refer :all])
  (:import [java.io File])
  (:gen-class))

(set! *unchecked-math* true)

(defn -main
  "Program to generate tiles for cube map"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-globe [input level] [output level]")
    (System/exit 1))
  (let [in-level           (Integer/parseInt (nth args 0))
        out-level          (Integer/parseInt (nth args 1))
        n                  (bit-shift-left 1 out-level)
        width              675
        elevation-tilesize 33
        sublevel           2
        subsample          (bit-shift-left 1 sublevel)
        color-tilesize     (inc (* subsample (dec elevation-tilesize)))
        radius1            6378000.0
        radius2            6357000.0]
    (cp/pdoseq (+ (cp/ncpus) 2) [k (range 6) b (range n) a (range n)]
      (let [tile    {:width color-tilesize :height color-tilesize :data (int-array (sqr color-tilesize))}
            water   {:width elevation-tilesize :height elevation-tilesize :data (byte-array (sqr elevation-tilesize))}
            scale   {:width elevation-tilesize :height elevation-tilesize :data (float-array (sqr elevation-tilesize))}
            normals {:width color-tilesize :height color-tilesize :data (float-array (* 3 (sqr color-tilesize)))}]
        (doseq [v (range elevation-tilesize) u (range elevation-tilesize)]
          (let [j                (cube-coordinate out-level elevation-tilesize b v)
                i                (cube-coordinate out-level elevation-tilesize a u)
                p                (cube-map k j i)
                point            (project-onto-globe p in-level width radius1 radius2)
                [lon lat height] (cartesian->geodetic point radius1 radius2)
                wet              (water-geodetic in-level width lon lat)]
            (set-water! water v u wet)
            (set-scale! scale v u (/ (norm point) (norm p) 6388000.0))))
        (doseq [v (range color-tilesize) u (range color-tilesize)]
          (let [j                (cube-coordinate out-level color-tilesize b v)
                i                (cube-coordinate out-level color-tilesize a u)
                p                (cube-map k j i)
                point            (project-onto-globe p in-level width radius1 radius2)
                [lon lat height] (cartesian->geodetic point radius1 radius2)
                normal           (normal-for-point point in-level out-level width color-tilesize radius1 radius2)
                color            (color-geodetic (min 5 (+ in-level sublevel)) width lon lat)]
            (set-vector! normals v u normal)
            (set-pixel! tile v u color)))
        (locking *out* (println (cube-path "globe" k out-level b a ".*")))
        (.mkdirs (File. (cube-dir "globe" k out-level a)))
        (spit-image (cube-path "globe" k out-level b a ".png") tile)
        (spit-bytes (cube-path "globe" k out-level b a ".water") (:data water))
        (spit-floats (cube-path "globe" k out-level b a ".scale") (:data scale))
        (spit-floats (cube-path "globe" k out-level b a ".normals") (:data normals))))
    (System/exit 0)))

(set! *unchecked-math* false)
