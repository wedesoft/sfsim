(ns sfsim25.map-tiles
  (:import [magick MagickImage ImageInfo]
           [java.awt Rectangle]
           [java.io File])
  (:require [sfsim25.util :refer (tile-dir tile-path)])
  (:gen-class))

(defn -main
  "Program to generate map tiles"
  [& args]
  (when-not (= (count args) 6)
    (.println *err* "Syntax: lein run-map-tiles [image] [tilesize] [level] [prefix] [y offset] [x offset]")
    (System/exit 1))
  (let [input-image  (nth args 0)
        tilesize    (Integer/parseInt (nth args 1))
        level       (Integer/parseInt (nth args 2))
        prefix      (nth args 3)
        dy          (bit-shift-left (Integer/parseInt (nth args 4)) level)
        dx          (bit-shift-left (Integer/parseInt (nth args 5)) level)
        info        (ImageInfo. input-image)
        image       (MagickImage. info)
        dimension   (.getDimension image)
        [w h]       [(.width dimension) (.height dimension)]]
    (doseq [j (range (/ h tilesize)) i (range (/ w tilesize))]
      (let [rectangle (Rectangle. (* i tilesize) (* j tilesize) tilesize tilesize)
            tile      (.cropImage image rectangle)
            dir       (tile-dir prefix level (+ i dx))
            path      (tile-path prefix level (+ j dy) (+ i dx) ".png")]
        (.mkdirs (File. dir))
        (.setFileName tile path)
        (.writeImage tile info)))))
