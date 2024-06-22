(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [gloss.core :refer (compile-frame ordered-map string finite-block finite-frame repeated prefix)]
              [gloss.io :refer (decode)])
    (:import [java.nio ByteBuffer]
             [java.nio.file Paths StandardOpenOption]
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

(def daf-header-frame
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
                 :ftpstr       (finite-frame 28 (repeated :ubyte :prefix :none)))))

(def daf-comment-frame
  (compile-frame (string :us-ascii :length 1000)))

(defn daf-summary-frame
  [num-doubles num-integers]
  (let [summary-length (+ (* 8 num-doubles) (* 4 num-integers))
        padding        (mod (- summary-length) 8)]
    (compile-frame
      (ordered-map :doubles  (repeat num-doubles :float64-le)
                   :integers (repeat num-integers :int32-le)
                   :padding  (finite-block padding)))))

(defn daf-summaries-frame
  [num-doubles num-integers]
  (let [summary-frame (daf-summary-frame num-doubles num-integers)]
    (compile-frame
      (ordered-map :next-number     :float64-le
                   :previous-number :float64-le
                   :descriptors     (repeated summary-frame
                                              :prefix (prefix :float64-le long double))))))

(defn daf-source-names-frame
  [n]
  (compile-frame (repeat n (string :us-ascii :length 40))))

(defn decode-record
  "Decode a record using the specified frame"
  {:malli/schema [:=> [:cat :some :some :int] :some]}
  [buffer frame index]
  (let [record (byte-array record-size)]
    (.position ^ByteBuffer buffer ^long (* (dec index) record-size))
    (.get ^ByteBuffer buffer record)
    (decode frame record false)))

(defn read-daf-header
  "Read DAF header from byte buffer"
  {:malli/schema [:=> [:cat :some] :map]}
  [buffer]
  (decode-record buffer daf-header-frame 1))

(defn check-endianness
  "Check endianness of DAF file"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [header]
  (= (:locfmt header) "LTL-IEEE"))

(def ftp-str "FTPSTR:\r:\n:\r\n:\r\u0000:\u0081:\u0010\u00ce:ENDFTP")

(defn check-ftp-str
  "Check FTP string of DAF file"
  {:malli/schema [:=> [:cat :map] :boolean]}
  [header]
  (= (:ftpstr header) (map int ftp-str)))

(defn read-daf-comment
  "Read DAF comment"
  {:malli/schema [:=> [:cat :map :some] :some]}
  [header buffer]
  (let [comment-lines (mapv #(decode-record buffer daf-comment-frame %) (range 2 (:forward header)))
        joined-lines  (clojure.string/join comment-lines)
        delimited     (subs joined-lines 0 (clojure.string/index-of joined-lines \o004))
        with-newlines (clojure.string/replace delimited \o000 \newline)]
    with-newlines))

(defn read-daf-summaries
  "Read descriptors for following data"
  {:malli/schema [:=> [:cat :map :int :some] :map]}
  [header index buffer]
  (let [num-doubles  (:num-doubles header)
        num-integers (:num-integers header)]
    (decode-record buffer (daf-summaries-frame num-doubles num-integers) index)))

(defn read-source-names
  "Read source name data"
  {:malli/schema [:=> [:cat :int :int :some] [:sequential :string]]}
  [index n buffer]
  (mapv clojure.string/trim (decode-record buffer (daf-source-names-frame n) index)))

(defn read-daf-summaries-blocks
  [header index buffer]
  (let [summaries   (read-daf-summaries header index buffer)
        next-number (long (:next-number summaries))
        descriptors (:descriptors summaries)
        n           (count (:descriptors summaries))
        sources     (read-source-names (inc index) n buffer)
        results     (map (fn [source descriptor] (assoc descriptor :source source)) sources descriptors)]
    (if (zero? next-number)
      results
      (concat results (read-daf-summaries-blocks header next-number buffer)))))

(defn spk-segment
  "Convert DAF descriptor into SPK segment"
  {:malli/schema [:=> [:cat :map] :map]}
  [descriptor]
  (let [source                                        (:source descriptor)
        [start-second end-second]                     (:doubles descriptor)
        [target center frame data-type start-i end-i] (:integers descriptor)]
    {:source       source
     :start-second start-second
     :end-second   end-second
     :target       target
     :center       center
     :frame        frame
     :data-type    data-type
     :start-i      start-i
     :end-i        end-i}))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
