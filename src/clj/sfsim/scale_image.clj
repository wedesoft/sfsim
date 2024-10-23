(ns sfsim.scale-image
  "Convert large map image into a smaller image with half the width and height."
  (:require [clojure.java.io :as io]
            [sfsim.util :refer (non-empty-string)])
  (:import [javax.imageio ImageIO]
           [java.awt.image BufferedImage AffineTransformOp]
           [java.awt.geom AffineTransform]))

(defn scale-image-file
  "Program to load, scale, and save image"
  {:malli/schema [:=> [:cat non-empty-string non-empty-string] :boolean]}
  [input-path output-path]
  (let [input-image  (with-open [input-file (io/input-stream input-path)] (ImageIO/read input-file))
        width        (.getWidth input-image)
        height       (.getHeight input-image)
        output-image (BufferedImage. (quot width 2) (quot height 2) (.getType input-image))
        transform    (let [transform (AffineTransform.)] (.scale transform 0.5 0.5) transform)
        op           (AffineTransformOp. transform AffineTransformOp/TYPE_BILINEAR)]
    (.filter op input-image output-image)
    (with-open [output-file (io/output-stream output-path)]
      (ImageIO/write output-image "png" output-file))))
