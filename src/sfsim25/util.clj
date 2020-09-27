(ns sfsim25.util
  (:require [clojure.java.io :as io :refer [input-stream]])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io ByteArrayOutputStream]))

(defn slurp-bytes
  "Read bytes from a file"
  [file-name]
  (with-open [in  (io/input-stream file-name)
              out (ByteArrayOutputStream.)]
    (clojure.java.io/copy in out)
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
