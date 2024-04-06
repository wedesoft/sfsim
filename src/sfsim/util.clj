(ns sfsim.util
  "Various utility functions."
  (:require [clojure.java.io :as io]
            [clojure.math :refer (sin)]
            [malli.core :as m]
            [progrock.core :as p])
  (:import [java.io ByteArrayOutputStream]
           [java.nio ByteBuffer ByteOrder]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

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

(defn slurp-bytes
  "Read bytes from a file"
  {:malli/schema [:=> [:cat non-empty-string] bytes?]}
  ^bytes [^String file-name]
  (with-open [in  (io/input-stream file-name)
              out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn spit-bytes
  "Write bytes to a file"
  {:malli/schema [:=> [:cat non-empty-string bytes?] :nil]}
  [^String file-name ^bytes byte-data]
  (with-open [out (io/output-stream file-name)]
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

(defn slurp-floats
  "Read floating point numbers from a file"
  {:malli/schema [:=> [:cat non-empty-string] seqable?]}
  ^floats [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        float-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asFloatBuffer)
        result       (float-array (/ n 4))]
    (.get float-buffer result)
    result))

(defn spit-floats
  "Write floating point numbers to a file"
  {:malli/schema [:=> [:cat non-empty-string seqable?] :nil]}
  [^String file-name ^floats float-data]
  (let [n           (count float-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 4)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asFloatBuffer byte-buffer) float-data)
    (spit-bytes file-name (.array byte-buffer))))

(def N0 (m/schema [:int {:min 0}]))
(def N (m/schema [:int {:min 1}]))
(def face (m/schema [:int {:min 0 :max 5}]))

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
  (str prefix \/ face \/ level \/ x \/ y suffix))

(defn cube-dir
  "Determine directory name of cube tile"
  {:malli/schema [:=> [:cat :string face N0 N0] :string]}
  [prefix face level x]
  (str prefix \/ face \/ level \/ x))

(defn sinc
  "sin(x) / x function"
  {:malli/schema [:=> [:cat :double] :double]}
  [x]
  (if (zero? x) 1.0 (/ (sin x) x)))

(defn sqr
  "Square of x"
  {:malli/schema [:=> [:cat number?] number?]}
  [x]
  (* x x))

(defn byte->ubyte
  "Convert byte to unsigned byte"
  {:malli/schema [:=> [:cat :int] N0]}
  [b]
  (if (>= b 0) b (+ b 256)))

(defn ubyte->byte
  "Convert unsigned byte to byte"
  {:malli/schema [:=> [:cat N0] :int]}
  [u]
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
  {:malli/schema [:=> [:cat N0 N] N0]}
  [address alignment]
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

(defn limit-quot
  "Compute quotient and limit it"
  {:malli/schema [:function [:=> [:cat number? number? number? [:? number?]] number?]]}
  ([a b limit]
   (limit-quot a b (- limit) limit))
  ([a b limit-lower limit-upper]
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
  (let [done   (== (inc (:progress bar)) (:total bar))
        result (assoc (p/tick bar 1) :done? done)]
    (when (or (zero? (mod (:progress result) (:step bar))) done) (p/print result))
    result))

(defn progress-wrap
  "Update progress bar when calling a function"
  {:malli/schema [:=> [:cat fn? N0 N] fn?]}
  [fun size step]
  (let [bar (agent (make-progress-bar size step))]
    (fn progress-wrap [& args]
        (send bar tick-and-print)
        (apply fun args))))

(defn dimension-count
  "Count dimensions of nested vector"
  {:malli/schema [:=> [:cat [:vector :some] N0] N0]}
  [array dimension]
  (count (nth (iterate first array) dimension)))

(defn octaves
  "Creat eoctaves summing to one"
  {:malli/schema [:=> [:cat N :double] [:vector :double]]}
  [n decay]
  (let [series (take n (iterate #(* % decay) 1.0))
        sum    (apply + series)]
    (mapv #(/ % sum) series)))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
