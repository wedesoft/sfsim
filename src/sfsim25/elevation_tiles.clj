(ns sfsim25.elevation-tiles
  "Split part of Mercator elevation map into small map tiles of specified size and save them in computed file names."
  (:import [java.io File])
  (:require [clojure.math :refer (sqrt)]
            [sfsim25.util :refer (slurp-shorts spit-shorts tile-dir tile-path)])
  (:gen-class))

(defn make-elevation-tiles
  "Program to generate elevation tiles"
  [input-data tilesize level prefix y-offset x-offset]
  (let [dy         (bit-shift-left y-offset level)
        dx         (bit-shift-left x-offset level)
        data       (slurp-shorts input-data)
        n          (count data)
        w          (int (sqrt n))
        h          w]
    (doseq [j (range (/ h tilesize)) i (range (/ w tilesize))]
      (let [dir  (tile-dir prefix level (+ i dx))
            path (tile-path prefix level (+ j dy) (+ i dx) ".raw")
            tile (short-array (* tilesize tilesize))]
        (doseq [y (range tilesize) x (range tilesize)]
          (aset ^shorts tile (+ (* y tilesize) x) (aget ^shorts data (+ (* (+ y (* j tilesize)) w) (* i tilesize) x))))
        (.mkdirs (File. dir))
        (spit-shorts path tile)))))
