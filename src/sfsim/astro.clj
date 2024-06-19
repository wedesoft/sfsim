(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [fastmath.vector :refer (vec3)])
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

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
