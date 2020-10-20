(ns sfsim25.util
  (:require [clojure.java.io :as io])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io ByteArrayOutputStream]))

(defn slurp-bytes
  "Read bytes from a file"
  [file-name]
  (with-open [in  (io/input-stream file-name)
              out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn slurp-shorts
  "Read short integers from a file"
  [file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        short-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asShortBuffer)
        result       (short-array (/ n 2))]
    (.get short-buffer result)
    result))

(defn spit-bytes
  "Write bytes to a file"
  [file-name byte-data]
  (with-open [out (io/output-stream file-name)]
    (.write out byte-data)))

(defn spit-shorts
  "Write short integers to a file"
  [file-name short-data]
  (let [n           (count short-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 2)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asShortBuffer byte-buffer) short-data)
    (spit-bytes file-name (.array byte-buffer))))

(defn tile-path
  "Determine file path of map tile"
  [prefix level y x suffix]
  (str prefix \/ level \/ x \/ y suffix))

(defn tile-dir
  "Determine directory name of map tile"
  [prefix level x]
  (str prefix \/ level \/ x))

(defn sinc ^double [^double x]
  "sin(x) / x function"
  (if (zero? x) 1.0 (/ (Math/sin x) x)))

(defn cache-create [n]
  "Create an empty LRU cache"
  {:stack [] :data {} :size n})

(defn cache-update [cache k v]
  "Add data to LRU cache"
  (if (= k (last (:stack cache))); optimization for repeated queries of the same key
    cache
    (let [remove-k    (remove #(= k %) (:stack cache))
          overflow?   (>= (count remove-k) (:size cache))
          limit-stack (if overflow? (rest remove-k) remove-k)
          limit-data  (if overflow? (dissoc (:data cache) (first remove-k)) (:data cache))]
      {:stack (conj (vec limit-stack) k) :data (assoc limit-data k v) :size (:size cache)})))

(defn cache [f]
  "Wrap function with LRU cache"
  (let [c (atom (cache-create 16))]
    (fn [& k]
      (let [v (or ((:data @c) k) (apply f k))]
        (swap! c cache-update k v)
        v))))
