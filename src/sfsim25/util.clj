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
        short-buffer (.asShortBuffer (.order (ByteBuffer/wrap byte-data) ByteOrder/LITTLE_ENDIAN))
        result       (short-array (/ n 2))]
    (.get short-buffer result)
    result))
