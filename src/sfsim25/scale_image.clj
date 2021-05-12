(ns sfsim25.scale-image
  "Convert large map image into smaller image."
  (:import [magick MagickImage ImageInfo])
  (:gen-class))

(defn -main
  "Program to scale image"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-scale-image [input image] [output image]")
    (System/exit 1))
  (let [[input-image
         output-image] args
        info           (ImageInfo. input-image)
        img            (MagickImage. info)
        dimension      (.getDimension img)
        [w h]          [(.width dimension) (.height dimension)]
        scaled         (.scaleImage img (/ w 2) (/ h 2))]
    (.setFileName scaled output-image)
    (.writeImage scaled info)))
