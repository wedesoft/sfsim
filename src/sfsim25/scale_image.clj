(ns sfsim25.scale-image
  "Convert large map image into a smaller image with half the width and height."
  (:require [sfsim25.util :refer (slurp-image spit-image)])
  (:import [org.lwjgl.stb STBImageResize]
           [org.lwjgl BufferUtils]))

(defn scale-image
  "Scale image down to 50%"
  [input-image]
  (let [width (:width input-image)
        height (:height input-image)
        data (:data input-image)
        buffer (BufferUtils/createByteBuffer (* 4 (count data)))
        output-width (quot width 2)
        output-height (quot height 2)
        output-buffer (BufferUtils/createByteBuffer (* 4 output-width output-height))
        output-data (int-array (* output-width output-height))]
    (-> buffer (.asIntBuffer) (.put data) (.flip))
    (STBImageResize/stbir_resize_uint8 buffer width height 0 output-buffer output-width output-height 0 4)
    (.get (.asIntBuffer output-buffer) output-data)
    {:width output-width :height output-height :data output-data}))

(defn scale-image-file
  "Program to load, scale, and save image"
  [input-path output-path]
  (->> input-path slurp-image scale-image (spit-image output-path)))
