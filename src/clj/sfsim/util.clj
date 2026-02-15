;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.util
  "Various utility functions."
  (:require
    [clojure.core.cache :as cache]
    [clojure.java.io :as io]
    [clojure.math :refer (sin)]
    [clojure.set :refer (map-invert)]
    [malli.core :as m]
    [progrock.core :as p])
  (:import
    (clojure.core.cache
      LRUCache)
    (java.io
      File
      BufferedInputStream
      ByteArrayOutputStream)
    (java.util.zip
      GZIPInputStream
      GZIPOutputStream)
    (java.nio
      ByteBuffer
      ByteOrder)
    (java.nio.channels
      FileChannel)
    (java.nio.file
      Paths
      StandardOpenOption)
    (org.apache.commons.compress.archivers.tar
      TarFile
      TarArchiveOutputStream
      TarArchiveEntry
      TarFile$BoundedTarEntryInputStream)
    (org.lwjgl
      BufferUtils)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def face->index
  {:sfsim.cubemap/face0 0 :sfsim.cubemap/face1 1 :sfsim.cubemap/face2 2
   :sfsim.cubemap/face3 3 :sfsim.cubemap/face4 4 :sfsim.cubemap/face5 5})


(def index->face (map-invert face->index))


(defn third
  "Get third element of a list"
  {:malli/schema [:=> [:cat [:sequential {:min 3} any?]] any?]}
  [lst]
  (nth lst 2))


(defn fourth
  "Get fourth element of a list"
  {:malli/schema [:=> [:cat [:sequential {:min 4} any?]] any?]}
  [lst]
  (nth lst 3))


(def non-empty-string (m/schema [:string {:min 1}]))
(def tarfile (m/schema [:map [::tar :some] [::entries [:map-of :string :some]]]))


(defn untar
  "Open a tar file"
  {:malli/schema [:=> [:cat non-empty-string] tarfile]}
  [^String file-name]
  (let [tar-file (TarFile. (File. file-name))
        entries  (.getEntries tar-file)]
    {::tar tar-file
     ::entries (into {} (map (juxt #(.getName ^TarArchiveEntry %) identity) entries))}))


(defn tar-close
  "Close a tar file"
  {:malli/schema [:=> [:cat tarfile] :any]}
  [tar-file]
  (.close ^TarFile (::tar tar-file)))


(defmacro with-tar
  "Macro for opening and closing a tar file"
  [sym file-name & body]
  `(let [~sym    (untar ~file-name)
         result# (do ~@body)]
     (tar-close ~sym)
     result#))


(defn get-tar-entry
  "Open stream to read file from tar archive"
  {:malli/schema [:=> [:cat tarfile non-empty-string] :some]}
  ^TarFile$BoundedTarEntryInputStream [tar ^String file-name]
  (let [entry (get-in tar [::entries file-name])]
    (when-not entry
              (throw (Exception. (str "Entry " file-name " not found in tar file"))))
    (BufferedInputStream. (.getInputStream ^TarFile (::tar tar) ^TarArchiveEntry entry))))


(defn stream->bytes
  "Read bytes from a stream"
  {:malli/schema [:=> [:cat :some] bytes?]}
  [^java.io.InputStream in]
  (with-open [out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))


(defn slurp-bytes
  "Read bytes from a file"
  {:malli/schema [:=> [:cat non-empty-string] bytes?]}
  ^bytes [^String file-name]
  (with-open [in (io/input-stream file-name)]
    (stream->bytes in)))


(defn slurp-bytes-gz
  "Read bytes from a gzip compressed file"
  {:malli/schema [:=> [:cat non-empty-string] bytes?]}
  ^bytes [^String file-name]
  (with-open [in (-> file-name io/input-stream GZIPInputStream.)]
    (stream->bytes in)))


(defn slurp-bytes-tar
  "Read bytes from a file in a tar file"
  {:malli/schema [:=> [:cat tarfile non-empty-string] bytes?]}
  ^bytes [tar ^String file-name]
  (with-open [in (get-tar-entry tar file-name)]
    (stream->bytes in)))


(defn slurp-bytes-gz-tar
  "Read compressed bytes from a file in a tar file"
  {:malli/schema [:=> [:cat tarfile non-empty-string] bytes?]}
  ^bytes [tar ^String file-name]
  (with-open [in (GZIPInputStream. (get-tar-entry tar file-name))]
    (stream->bytes in)))


(defn spit-bytes
  "Write bytes to a file"
  {:malli/schema [:=> [:cat non-empty-string bytes?] :nil]}
  [^String file-name ^bytes byte-data]
  (with-open [out (io/output-stream file-name)]
    (.write out byte-data)))


(defn spit-bytes-gz
  "Compress bytes and write to a file"
  {:malli/schema [:=> [:cat non-empty-string bytes?] :nil]}
  [^String file-name ^bytes byte-data]
  (with-open [out (-> file-name io/output-stream GZIPOutputStream.)]
    (.write out byte-data)))


(defn slurp-shorts
  "Read short integers from a file"
  {:malli/schema [:=> [:cat non-empty-string] seqable?]}
  ^shorts [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        short-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asShortBuffer)
        result       (short-array (/ n 2))]
    (.get short-buffer result)
    result))


(defn spit-shorts
  "Write short integers to a file"
  {:malli/schema [:=> [:cat non-empty-string seqable?] :nil]}
  [^String file-name ^shorts short-data]
  (let [n           (count short-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 2)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asShortBuffer byte-buffer) short-data)
    (spit-bytes file-name (.array byte-buffer))))


(defn bytes->floats
  "Convert bytes to floating point numbers"
  {:malli/schema [:=> [:cat bytes?] seqable?]}
  ^floats [^bytes byte-data]
  (let [n            (.count (seq byte-data))
        float-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asFloatBuffer)
        result       (float-array (/ n 4))]
    (.get float-buffer result)
    result))


(defn slurp-floats
  "Read floating point numbers from a file"
  {:malli/schema [:=> [:cat non-empty-string] seqable?]}
  ^floats [^String file-name]
  (-> file-name slurp-bytes bytes->floats))


(defn slurp-floats-gz
  "Read floating point numbers from a compressed file"
  {:malli/schema [:=> [:cat non-empty-string] seqable?]}
  ^floats [^String file-name]
  (-> file-name slurp-bytes-gz bytes->floats))


(defn slurp-floats-tar
  "Read floating point numbers from a file in a tar archive"
  {:malli/schema [:=> [:cat tarfile non-empty-string] seqable?]}
  ^floats [tar ^String file-name]
  (->> file-name (slurp-bytes-tar tar) bytes->floats))


(defn slurp-floats-gz-tar
  "Read compressed floating point numbers from a file in a tar archive"
  {:malli/schema [:=> [:cat tarfile non-empty-string] seqable?]}
  ^floats [tar ^String file-name]
  (->> file-name (slurp-bytes-gz-tar tar) bytes->floats))


(defn floats->bytes
  "Convert float array to byte buffer"
  [^floats float-data]
  (let [n           (count float-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 4)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asFloatBuffer byte-buffer) float-data)
    (.array byte-buffer)))


(defn spit-floats
  "Write floating point numbers to a file"
  {:malli/schema [:=> [:cat non-empty-string seqable?] :nil]}
  [^String file-name ^floats float-data]
  (spit-bytes file-name (floats->bytes float-data)))


(defn spit-floats-gz
  "Write floating point numbers to a compressed file"
  {:malli/schema [:=> [:cat non-empty-string seqable?] :nil]}
  [^String file-name ^floats float-data]
  (spit-bytes-gz file-name (floats->bytes float-data)))


(defn create-tar
  "Create tar file from interleaved entry names and file names"
  {:malli/schema [:=> [:cat non-empty-string
                            [:and [:sequential non-empty-string]
                                  [:fn {:error/message "Vector needs to have even number of values"} #(even? (count %))]]]
                  :nil]}
  [tar-file-name destinations-and-sources]
  (let [tar (TarArchiveOutputStream. (io/output-stream tar-file-name))]
    (.setBigNumberMode ^TarArchiveOutputStream tar TarArchiveOutputStream/BIGNUMBER_STAR)
    (doseq [[dest src] (partition 2 destinations-and-sources)]
           (let [file  (File. ^String src)
                 entry (.createArchiveEntry ^TarArchiveOutputStream tar ^File file ^String dest)
                 in    (io/input-stream file)]
             (.putArchiveEntry tar entry)
             (io/copy in tar)
             (.closeArchiveEntry tar)
             (.close in)))
    (.close tar)))


(defn slurp-byte-buffer
  [filename]
  (let [option-read (into-array StandardOpenOption [StandardOpenOption/READ])
        path        (Paths/get filename (make-array String 0))
        channel     (FileChannel/open path option-read)
        len         (.size channel)
        result      (BufferUtils/createByteBuffer len)]
    (.read channel result)
    (.flip result)))


(def N0 (m/schema [:int {:min 0}]))
(def N (m/schema [:int {:min 1}]))
(def face (m/schema :keyword))


(defn tile-path
  "Determine file path of map tile"
  {:malli/schema [:=> [:cat :string N0 N0 N0 :string] :string]}
  [prefix level y x suffix]
  (str prefix \/ level \/ x \/ y suffix))


(defn tile-dir
  "Determine directory name of map tile"
  {:malli/schema [:=> [:cat :string N0 N0] :string]}
  [prefix level x]
  (str prefix \/ level \/ x))


(defn cube-path
  "Determine file path of cube tile"
  {:malli/schema [:=> [:cat :string face N0 N0 N0 :string] :string]}
  [prefix face level y x suffix]
  (str prefix \/ (face->index face) \/ level \/ x \/ y suffix))


(defn cube-dir
  "Determine directory name of cube tile"
  {:malli/schema [:=> [:cat :string face N0 N0] :string]}
  [prefix face level x]
  (str prefix \/ (face->index face) \/ level \/ x))


(defn cube-tar
  "Determine tar file containing cube tile"
  {:malli/schema [:=> [:cat :string face N0 N0] :string]}
  [prefix face level x]
  (str prefix \/ (face->index face) \/ level \/ x ".tar"))


(defn cube-file-name
  "Determine file name of cube tile"
  {:malli/schema [:=> [:cat N0 :string] :string]}
  [y suffix]
  (str y suffix))


(defn sinc
  "sin(x) / x function"
  ^double [^double x]
  (if (zero? x) 1.0 (/ (sin x) x)))


(defn sqr
  "Square of x"
  ^double [^double x]
  (* x x))


(defn cube
  "Cube of x"
  ^double [^double x]
  (* x x x))


(defn byte->ubyte
  "Convert byte to unsigned byte"
  ^long [^long b]
  (if (>= b 0) b (+ b 256)))


(defn ubyte->byte
  "Convert unsigned byte to byte"
  ^long [^long u]
  (if (<= u 127) u (- u 256)))


(defn dissoc-in
  "Return nested hash with path removed"
  {:malli/schema [:=> [:cat :map [:vector :some]] :map]}
  [m ks]
  (let [path (butlast ks)
        node (last ks)]
    (if (empty? path) (dissoc m node) (update-in m path dissoc node))))


(defn align-address
  "Function for aligning an address with specified alignment"
  ^long [^long address ^long alignment]
  (let [mask (dec alignment)]
    (bit-and (+ address mask) (bit-not mask))))


(defn dimensions
  "Return shape of nested vector"
  {:malli/schema [:=> [:cat :some] [:vector N0]]}
  [array]
  (if (instance? clojure.lang.PersistentVector array)
    (into [(count array)] (dimensions (first array)))
    []))


(defn comp*
  "Combine multiple-argument functions"
  {:malli/schema [:=> [:cat fn? fn?] fn?]}
  [f g]
  (fn comp* [& args] (apply f (apply g args))))


(defn pack-floats
  "Pack nested floating-point vector into float array"
  {:malli/schema [:=> [:cat [:vector :some]] seqable?]}
  [array]
  (float-array (flatten array)))


(defn size-of-shape
  "Determine size of given shape"
  {:malli/schema [:=> [:cat [:vector N0]] N0]}
  [shape]
  (apply * shape))


(defn clamp
  "Clamp value between minimum and maximum"
  ^double [^double x ^double lower ^double upper]
  (-> x (min upper) (max lower)))


(defn limit-quot
  "Compute quotient and limit it"
  (^double [^double a ^double b ^double limit]
   (limit-quot a b (- limit) limit))
  (^double [^double a ^double b ^double limit-lower ^double limit-upper]
   (if (zero? a)
     a
     (if (< b 0)
       (limit-quot (- a) (- b) limit-lower limit-upper)
       (if (< a (* b limit-upper))
         (if (> a (* b limit-lower))
           (/ a b)
           limit-lower)
         limit-upper)))))


(def progress (m/schema [:map [:progress :int] [:total :int] [:step :int]]))


(defn make-progress-bar
  "Create a progress bar"
  {:malli/schema [:=> [:cat N0 N] progress]}
  [size step]
  (let [result (assoc (p/progress-bar size) :step step)]
    (p/print result)
    result))


(defn tick-and-print
  "Increase progress and occasionally update progress bar"
  {:malli/schema [:=> [:cat progress] progress]}
  [bar]
  (let [done   (== (inc ^long (:progress bar)) ^long (:total bar))
        result (assoc (p/tick bar 1) :done? done)]
    (when (or (zero? ^long (mod ^long (:progress result) ^long (:step bar))) done) (p/print result))
    result))


(defn progress-wrap
  "Update progress bar when calling a function"
  {:malli/schema [:=> [:cat fn? N0 N] fn?]}
  [fun size step]
  (let [bar (agent (make-progress-bar size step))]
    (fn progress-wrap
      [& args]
      (send bar tick-and-print)
      (apply fun args))))


(defn dimension-count
  "Count dimensions of nested vector"
  {:malli/schema [:=> [:cat [:vector :some] N0] N0]}
  [array dimension]
  (count (nth (iterate first array) dimension)))


(defn octaves
  "Create octaves summing to one"
  [^long n ^double decay]
  (let [series (take n (iterate #(* ^double % decay) 1.0))
        sum    (apply + series)]
    (mapv #(/ ^double % ^double sum) series)))


(defn find-if
  "Fetch first element matching a predicate"
  {:malli/schema [:=> [:cat fn? [:sequential :some]] :any]}
  [pred coll]
  (first (filter pred coll)))


(defn make-lru-cache
  "Create LRU cache with destructor"
  {:malli/schema [:=> [:cat N0 fn? fn?] fn?]}
  [cache-size fun destructor]
  (let [state (atom (cache/lru-cache-factory {} :threshold cache-size))]
    (fn lru-wrapper
        [arg]
        (if-let [result (cache/lookup @state arg)]
          (do
            (swap! state (memfn ^LRUCache hit arg) arg)
            result)
          (let [result  (fun arg)
                old     @state
                updated (swap! state cache/miss arg result)
                evicted (remove (set (keys updated)) (keys old))]
            (doseq [x evicted] (destructor (cache/lookup old x)))
            result)))))


(defn byte-buffer->byte-array
  [buffer]
  (if buffer
    (let [array (byte-array (.limit ^java.nio.DirectByteBuffer buffer))]
      (.get ^java.nio.DirectByteBuffer buffer array)
      array)
    (byte-array [])))


(defn float-buffer->float-array
  [buffer]
  (if buffer
    (let [array (float-array (.limit ^java.nio.DirectFloatBufferU buffer))]
      (.get ^java.nio.DirectFloatBufferU buffer array)
      array)
    (float-array [])))


(defmacro ignore-nil->
  "Threading macro ignoring nil results"
  [value identifier & body]
  (if (empty? body)
    value
    `(let [~identifier ~value
           next-value# (or ~(first body) ~identifier)]
       (ignore-nil-> next-value# ~identifier ~@(rest body)))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
