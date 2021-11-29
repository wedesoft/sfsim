(ns sfsim25.scale-image
  "Convert large map image into a smaller image with half the width and height."
  (:import [ij ImagePlus]
           [ij.io Opener FileSaver])
  (:gen-class))

(defn -main
  "Program to scale image"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-scale-image [input image] [output image]")
    (System/exit 1))
  (let [[input-image output-image] args
        img                        (.openImage (Opener.) input-image)
        [w h]                      [(.getWidth img) (.getHeight img)]
        processor                  (.getProcessor img)
        resized                    (.resize processor (/ w 2) (/ h 2))
        output                     (ImagePlus.)]
    (.setProcessor output resized)
    (.saveAsPng (FileSaver. output) output-image)))
