(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [fastmath.vector :refer (vec3)]
              [gloss.core :refer (compile-frame ordered-map string)]
              [gloss.io :refer (decode)])
    (:import [java.nio.file Paths StandardOpenOption]
             [java.nio.channels FileChannel FileChannel$MapMode]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn map-file-to-buffer
  "Map file to read-only byte buffer"
  {:malli/schema [:=> [:cat :string] :some]}
  [filename]
  (let [path        (Paths/get filename (make-array String 0))
        option-read (into-array StandardOpenOption [StandardOpenOption/READ])
        channel     (FileChannel/open path option-read)
        size        (.size channel)
        buffer      (.map channel FileChannel$MapMode/READ_ONLY 0 size)]
    buffer))

(def spk-header
  (compile-frame
    (ordered-map :magic        (string :us-ascii :length 8)
                 :num-doubles  :int32-le
                 :num-integers :int32-le)))

(defn read-spk-header
  "Read SPK header from byte buffer"
  [buffer]
  (decode spk-header [buffer] false))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
