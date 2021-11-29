(ns sfsim25.elevation-tiles
  "Split part of Mercator elevation map into small map tiles of specified size and save them in computed file names."
  (:import [java.io File])
  (:require [sfsim25.util :refer (slurp-shorts spit-shorts tile-dir tile-path)])
  (:gen-class))

(defn -main
  "Program to generate elevation tiles"
  [& args]
  (when-not (= (count args) 6)
    (.println *err* "Syntax: lein run-elevation-tiles [data] [tilesize] [level] [prefix] [y offset] [x offset]")
    (System/exit 1))
  (let [input-data (nth args 0)
        tilesize   (Integer/parseInt (nth args 1))
        level      (Integer/parseInt (nth args 2))
        prefix     (nth args 3)
        dy         (bit-shift-left (Integer/parseInt (nth args 4)) level)
        dx         (bit-shift-left (Integer/parseInt (nth args 5)) level)
        data       (slurp-shorts input-data)
        n          (count data)
        w          (int (Math/sqrt n))
        h          w]
    (doseq [j (range (/ h tilesize)) i (range (/ w tilesize))]
      (let [dir  (tile-dir prefix level (+ i dx))
            path (tile-path prefix level (+ j dy) (+ i dx) ".raw")
            tile (short-array (* tilesize tilesize))]
        (doseq [y (range tilesize) x (range tilesize)]
          (aset ^shorts tile (+ (* y tilesize) x) (aget ^shorts data (+ (* (+ y (* j tilesize)) w) (* i tilesize) x))))
        (.mkdirs (File. dir))
        (spit-shorts path tile)))))
