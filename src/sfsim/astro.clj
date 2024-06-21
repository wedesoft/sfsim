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

(def record-size 1024)

(def spk-header-frame
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

(def spk-comment-frame
  (compile-frame
    (ordered-map :comment (string :us-ascii :length 1000)
                 :padding (finite-block 24))))

(defn decode-record
  "Decode a record using the specified frame"
  {:malli/schema [:=> [:cat :some :some :int] :some]}
  [buffer frame index]
  (.position buffer (* (dec index) record-size))
  (decode frame [buffer] false))

(defn read-spk-header
  "Read SPK header from byte buffer"
  {:malli/schema [:=> [:cat :some] :map]}
  [buffer]
  (decode-record buffer spk-header-frame 1))

(defn check-endianness
  "Check endianness of SPK file"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [header]
  (= (:locfmt header) "LTL-IEEE"))

(def ftp-str "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")

(defn check-ftp-str
  "Check FTP string of SPK file"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [header]
  (= (:ftpstr header) (map int ftp-str)))

(defn read-spk-comment
  "Read SPK comment"
  {:malli/schema [:=> [:cat :map :some] :some]}
  [header buffer]
  (let [decoded       (map #(decode-record buffer spk-comment-frame %) (range 2 (:forward header)))
        comment-lines (map :comment decoded)
        joined-lines  (clojure.string/join comment-lines)
        delimited     (subs joined-lines 0 (clojure.string/index-of joined-lines \o004))
        with-newlines (clojure.string/replace delimited \o000 \newline)]
    with-newlines))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
