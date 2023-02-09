(ns sfsim25.map-tiles
  "Split up part of Mercator map into map tiles of specified size and save them in computed file names."
  (:import [java.io File]
           [ij ImagePlus]
           [ij.io Opener FileSaver])
  (:require [sfsim25.util :refer (tile-dir tile-path)]))

(defn make-map-tiles
  "Program to generate map tiles"
  [input-image tilesize level prefix y-offset x-offset]
  (let [dy          (bit-shift-left y-offset level)
        dx          (bit-shift-left x-offset level)
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
