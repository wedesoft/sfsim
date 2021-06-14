(ns sfsim25.map-tiles
  "Split up large Mercator image into smaller ones."
  (:import [java.io File]
           [ij ImagePlus]
           [ij.io Opener FileSaver])
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
        img         (.openImage (Opener.) input-image)
        [w h]       [(.getWidth img) (.getHeight img)]
        processor   (.getProcessor img)]
    (doseq [j (range (/ h tilesize)) i (range (/ w tilesize))]
      (.setRoi processor (* i tilesize) (* j tilesize) tilesize tilesize )
      (let [cropped   (.crop processor)
            tile      (ImagePlus.)
            dir       (tile-dir prefix level (+ i dx))
            path      (tile-path prefix level (+ j dy) (+ i dx) ".png")]
        (.setProcessor tile cropped)
        (.mkdirs (File. dir))
        (.saveAsPng (FileSaver. tile) path)))))
