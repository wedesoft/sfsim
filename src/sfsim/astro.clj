(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [gloss.core :refer (compile-frame ordered-map string finite-block finite-frame repeated)]
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
    (ordered-map :locidw       (string :us-ascii :length 8)
                 :num-doubles  :int32-le
                 :num-integers :int32-le
                 :locifn       (string :us-ascii :length 60)
                 :forward      :int32-le
                 :backward     :int32-le
                 :free         :int32-le
                 :locfmt       (string :us-ascii :length 8)
                 :prenul       (finite-block 603)
                 :ftpstr       (finite-frame 28 (repeated :ubyte :prefix :none))
                 :pstnul       (finite-block 297))))

(defn read-spk-header
  "Read SPK header from byte buffer"
  [buffer]
  (decode spk-header [buffer] false))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
