(ns sfsim25.map-tiles
  "Split up part of Mercator map into map tiles of specified size and save them in computed file names."
  (:require [clojure.java.io :as io]
            [sfsim25.util :refer (tile-dir tile-path non-empty-string N N0)])
  (:import [javax.imageio ImageIO]
           [java.io File]))

(defn make-map-tiles
  "Program to generate map tiles"
  {:malli/schema [:=> [:cat non-empty-string N N0 :string N0 N0] :nil]}
  [input-path tilesize level prefix y-offset x-offset]
  (let [dy          (bit-shift-left y-offset level)
        dx          (bit-shift-left x-offset level)
        img         (with-open [input-file (io/input-stream input-path)] (ImageIO/read input-file))
        [w h]       [(.getWidth img) (.getHeight img)]]
    (doseq [j (range (quot h tilesize)) i (range (quot w tilesize))]
           (let [dir  (tile-dir prefix level (+ i dx))
                 path (tile-path prefix level (+ j dy) (+ i dx) ".png")
                 tile (.getSubimage img (* i tilesize) (* j tilesize) tilesize tilesize)]
             (.mkdirs (File. dir))
             (with-open [output-file (io/output-stream path)]
               (ImageIO/write tile "png" output-file))))))
