(ns sfsim.astro
    "NASA JPL interpolation for pose of celestical objects (see https://rhodesmill.org/skyfield/ for original code and more)"
    (:require [fastmath.vector :refer (add mult sub)]
              [gloss.core :refer (compile-frame ordered-map string finite-block finite-frame repeated prefix sizeof)]
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

(defn daf-descriptor-frame
  [num-doubles num-integers]
  (let [summary-length (+ (* 8 num-doubles) (* 4 num-integers))
        padding        (mod (- summary-length) 8)]
    (compile-frame
      (ordered-map :doubles  (repeat num-doubles :float64-le)
                   :integers (repeat num-integers :int32-le)
                   :padding  (finite-block padding)))))

(defn daf-descriptors-frame
  [num-doubles num-integers]
  (let [descriptor-frame (daf-descriptor-frame num-doubles num-integers)]
    (compile-frame
      (ordered-map :next-number     :float64-le
                   :previous-number :float64-le
                   :descriptors     (repeated descriptor-frame
                                              :prefix (prefix :float64-le long double))))))

(defn daf-source-names-frame
  [n]
  (compile-frame (repeat n (string :us-ascii :length 40))))

(def coefficient-layout-frame
  (compile-frame
    (ordered-map :init :float64-le
                 :intlen :float64-le
                 :rsize :float64-le
                 :n :float64-le)))

(defn coefficient-frame
  [rsize component-count]
  (let [coefficient-count (/ (- rsize 2) component-count)]
    (compile-frame (concat [:float64-le :float64-le] (repeat component-count (repeat coefficient-count :float64-le))))))

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

(defn read-daf-descriptor
  "Read descriptors for following data"
  {:malli/schema [:=> [:cat :map :int :some] :map]}
  [header index buffer]
  (let [num-doubles  (:num-doubles header)
        num-integers (:num-integers header)]
    (decode-record buffer (daf-descriptors-frame num-doubles num-integers) index)))

(defn read-source-names
  "Read source name data"
  {:malli/schema [:=> [:cat :int :int :some] [:sequential :string]]}
  [index n buffer]
  (mapv clojure.string/trim (decode-record buffer (daf-source-names-frame n) index)))

(defn read-daf-summaries
  "Read sources and descriptors to get summaries"
  [header index buffer]
  (let [summaries   (read-daf-descriptor header index buffer)
        next-number (long (:next-number summaries))
        descriptors (:descriptors summaries)
        n           (count (:descriptors summaries))
        sources     (read-source-names (inc index) n buffer)
        results     (map (fn [source descriptor] (assoc descriptor :source source)) sources descriptors)]
    (if (zero? next-number)
      results
      (concat results (read-daf-summaries header next-number buffer)))))

(defn summary->spk-segment
  "Convert DAF summary to SPK segment"
  {:malli/schema [:=> [:cat :map] :map]}
  [summary]
  (let [source                                        (:source summary)
        [start-second end-second]                     (:doubles summary)
        [target center frame data-type start-i end-i] (:integers summary)]
    {:source       source
     :start-second start-second
     :end-second   end-second
     :target       target
     :center       center
     :frame        frame
     :data-type    data-type
     :start-i      start-i
     :end-i        end-i}))

(defn spk-segment-lookup-table
  "Make a lookup table for pairs of center and target to lookup SPK summaries"
  {:malli/schema [:=> [:cat :map :some] :map]}
  [header buffer]
  (let [summaries (read-daf-summaries header (:forward header) buffer)
        segments  (map summary->spk-segment summaries)]
    (reduce (fn [lookup segment] (assoc lookup [(:center segment) (:target segment)] segment)) {} segments)))

(defn convert-to-long
  "Convert values with specified keys to long integer"
  {:malli/schema [:=> [:cat :map [:vector :keyword]] :map]}
  [hashmap keys_]
  (reduce (fn [hashmap key_] (update hashmap key_ long)) hashmap keys_))

(defn read-coefficient-layout
  "Read layout information from end of segment"
  {:malli/schema [:=> [:cat :map :some] :map]}
  [segment buffer]
  (let [info (byte-array (sizeof coefficient-layout-frame))]
    (.position ^ByteBuffer buffer ^long (* 8 (- (:end-i segment) 4)))
    (.get ^ByteBuffer buffer info)
    (convert-to-long (decode coefficient-layout-frame info) [:rsize :n])))

(defn read-interval-coefficients
  "Read coefficient block with specified index from segment"
  {:malli/schema [:=> [:cat :map :map :int :some] [:sequential [:sequential :double]]]}
  [segment layout index buffer]
  (let [component-count ({2 3 3 6} (:data-type segment))
        frame           (coefficient-frame (:rsize layout) component-count)
        data            (byte-array (sizeof frame))]
    (.position ^ByteBuffer buffer ^long (+ (* 8 (dec (:start-i segment))) (* index (sizeof frame))))
    (.get ^ByteBuffer buffer data)
    (reverse (apply map vector (drop 2 (decode frame data))))))

(defn chebyshev-polynomials
  "Chebyshev polynomials"
  {:malli/schema [:=> [:cat [:sequential :some] :double :some] :some]}
  [coefficients s zero]
  (let [s2      (* 2.0 s)
        [w0 w1] (reduce (fn [[w0 w1] c] [(add c (sub (mult w0 s2) w1)) w0])
                        [zero zero]
                        (butlast coefficients))]
    (add (last coefficients) (sub (mult w0 s) w1))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
