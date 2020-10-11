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
