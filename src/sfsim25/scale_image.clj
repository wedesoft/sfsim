(ns sfsim25.scale-image
  (:import [magick MagickImage ImageInfo])
  (:gen-class))

(defn -main
  "Program to scale image"
  [input-image output-image]
  (let [info      (ImageInfo. input-image)
        img       (MagickImage. info)
        dimension (.getDimension img)
        [w h]     [(.width dimension) (.height dimension)]
        scaled    (.scaleImage img (/ w 2) (/ h 2))]
    (.setFileName scaled output-image)
    (.writeImage scaled info)))
