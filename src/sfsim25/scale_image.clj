(ns sfsim25.scale-image
  "Convert large map image into a smaller image with half the width and height."
  (:import [ij ImagePlus]
           [ij.io Opener FileSaver]))

(defn scale-image
  "Program to scale image"
  [input-image output-image]
  (let [img                        (.openImage (Opener.) input-image)
        [w h]                      [(.getWidth img) (.getHeight img)]
        processor                  (.getProcessor img)
        resized                    (.resize processor (/ w 2) (/ h 2))
        output                     (ImagePlus.)]
    (.setProcessor output resized)
    (.saveAsPng (FileSaver. output) output-image)))
