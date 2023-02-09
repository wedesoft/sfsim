(ns sfsim25.scale-elevation
  "Convert large elevation image into lower resolution image with half the width and height."
  (:require [clojure.math :refer (sqrt round)]
            [sfsim25.util :refer (slurp-shorts spit-shorts)])
  (:gen-class))

(defn scale-elevation
  "Program to scale elevation images"
  [input-data output-data]
  (let [data          (slurp-shorts input-data)
        n             (alength data)
        w             (int (round (sqrt n)))
        size          (/ w 2)
        result        (short-array (* size size))]
    (doseq [^int j (range size) ^int i (range size)]
      (let [offset (+ (* j 2 w) (* i 2))
            x1 (aget ^shorts data offset)
            x2 (aget ^shorts data (inc offset))
            x3 (aget ^shorts data (+ offset w))
            x4 (aget ^shorts data (+ offset w 1))]
        (aset ^shorts result (+ (* j size) i) (short (/ (+ x1 x2 x3 x4) 4)))))
    (spit-shorts output-data result)))
