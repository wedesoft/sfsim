(ns sfsim25.util
  "Various utility functions."
  (:require [clojure.java.io :as io]
            [clojure.core.matrix :refer :all]
            [sfsim25.rgb :refer (->RGB)])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io ByteArrayOutputStream]
           [ij ImagePlus]
           [ij.io Opener FileSaver]
           [ij.process ImageConverter ColorProcessor]
           [mikera.vectorz Vector]
           [sfsim25.rgb RGB]))

(defn slurp-bytes
  "Read bytes from a file"
  ^bytes [^String file-name]
  (with-open [in  (io/input-stream file-name)
              out (ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn slurp-shorts
  "Read short integers from a file"
  ^shorts [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        short-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asShortBuffer)
        result       (short-array (/ n 2))]
    (.get short-buffer result)
    result))

(defn slurp-floats
  "Read floating point numbers from a file"
  ^floats [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        float-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asFloatBuffer)
        result       (float-array (/ n 4))]
    (.get float-buffer result)
    result))

(defn spit-bytes
  "Write bytes to a file"
  [^String file-name ^bytes byte-data]
  (with-open [out (io/output-stream file-name)]
    (.write out byte-data)))

(defn spit-shorts
  "Write short integers to a file"
  [^String file-name ^shorts short-data]
  (let [n           (count short-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 2)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asShortBuffer byte-buffer) short-data)
    (spit-bytes file-name (.array byte-buffer))))

(defn spit-floats
  "Write floating point numbers to a file"
  [^String file-name ^floats float-data]
  (let [n           (count float-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 4)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asFloatBuffer byte-buffer) float-data)
    (spit-bytes file-name (.array byte-buffer))))

(defn tile-path
  "Determine file path of map tile"
  ^String [prefix level y x suffix]
  (str prefix \/ level \/ x \/ y suffix))

(defn tile-dir
  "Determine directory name of map tile"
  ^String [^String prefix ^long level ^long x]
  (str prefix \/ level \/ x))

(defn cube-path
  "Determine file path of cube tile"
  ^String [prefix face level y x suffix]
  (str prefix \/ face \/ level \/ x \/ y suffix))

(defn cube-dir
  "Determine directory name of cube tile"
  ^String [^String prefix ^long face ^long level ^long x]
  (str prefix \/ face \/ level \/ x))

(defn sinc
  "sin(x) / x function"
  ^double [^double x]
  (if (zero? x) 1.0 (/ (Math/sin x) x)))

(defn sqr
  "Square of x"
  ^double [^double x]
  (* x x))

(defn spit-image
  "Save RGB image as PNG file"
  [^String file-name {:keys [width height data]}]
  (let [processor (ColorProcessor. width height data)
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.saveAsPng (FileSaver. img) file-name)))

(defn slurp-image
  "Load an RGB image"
  [^String file-name]
  (let [img (.openImage (Opener.) file-name)]
    (.convertToRGB (ImageConverter. img))
    {:width (.getWidth img) :height (.getHeight img) :data (.getPixels (.getProcessor img))}))

(defn byte->ubyte
  "Convert byte to unsigned byte"
  ^long [^long b]
  (if (>= b 0) b (+ b 256)))

(defn ubyte->byte
  "Convert unsigned byte to byte"
  ^long [^long u]
  (if (<= u 127) u (- u 256)))

(defn get-pixel
  "Read color value from a pixel of an image"
  ^RGB [{:keys [width height data]} ^long y ^long x]
  (let [offset (+ (* width y) x)
        value  (aget data offset)]
    (->RGB (bit-shift-right (bit-and 0xff0000 value) 16) (bit-shift-right (bit-and 0xff00 value) 8) (bit-and 0xff value))))

(defn set-pixel!
  "Set color value of a pixel in an image"
  [{:keys [width height data]} ^long y ^long x ^RGB c]
  (let [offset (+ (* width y) x)]
    (aset-int data offset (bit-or (bit-shift-left -1 24)
                                  (bit-shift-left (Math/round (:r c)) 16)
                                  (bit-shift-left (Math/round (:g c)) 8)
                                  (Math/round (:b c))))))

(defn get-elevation
  "Read elevation value from an elevation tile"
  [{:keys [width height data]} ^long y ^long x]
  (aget data (+ (* width y) x)))

(defn set-elevation!
  "Write elevation value to an elevation tile"
  [{:keys [width height data]} ^long y ^long x ^long value]
  (aset-short data (+ (* width y) x) value))

(defn get-scale
  "Read scale value from a scale tile"
  ^double [{:keys [width height data]} ^long y ^long x]
  (aget data (+ (* width y) x)))

(defn set-scale!
  "Write scale value to a scale tile"
  [{:keys [width height data]} ^long y ^long x ^double value]
  (aset-float data (+ (* width y) x) value))

(defn get-water
  "Read water value from a water tile"
  ^long [{:keys [width height data]} ^long y ^long x]
  (byte->ubyte (aget data (+ (* width y) x))))

(defn set-water!
  "Write water value to a water tile"
  [{:keys [width height data]} ^long y ^long x ^long value]
  (aset-byte data (+ (* width y) x) (ubyte->byte value)))

(defn get-vector
  "Read vector from a vectors tile"
  ^Vector [{:keys [width height data]} ^long y ^long x]
  (let [offset (* 3 (+ (* width y) x))]
    (matrix [(aget data offset      )
             (aget data (+ offset 1))
             (aget data (+ offset 2))])))

(defn set-vector!
  "Write vector value to vectors tile"
  [{:keys [width height data]} ^long y ^long x ^Vector value]
  (let [offset (* 3 (+ (* width y) x))]
    (aset-float data offset       (mget value 0))
    (aset-float data (+ offset 1) (mget value 1))
    (aset-float data (+ offset 2) (mget value 2))))

(defn dissoc-in
  "Return nested hash with path removed"
  [m ks]
  (let [path (butlast ks)
        node (last ks)]
    (if (empty? path) (dissoc m node) (update-in m path dissoc node))))

(defmacro def-context-macro
  "Define context macro opening and closing a context object"
  [method open close]
  `(defmacro ~method [object# & body#]
     `(do
        (~~open ~object#)
        (let [~'result# (do ~@body#)]
          (~~close ~object#)
          ~'result#))))

(defmacro def-context-create-macro
  "Define context macro creating opening and closing a context object"
  [method constructor ctx-macro]
  `(defmacro ~method [object# & body#]
     `(let [~object# (~~constructor)]
        (~~ctx-macro ~object# ~@body#))))
