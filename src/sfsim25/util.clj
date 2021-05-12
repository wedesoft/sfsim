(ns sfsim25.util
  "Various utility functions."
  (:require [clojure.java.io :as io]
            [sfsim25.rgb :refer (->RGB)]
            [sfsim25.vector3 :refer (->Vector3)])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io ByteArrayOutputStream]
           [magick MagickImage ImageInfo ColorspaceType]
           [sfsim25.rgb RGB]
           [sfsim25.vector3 Vector3]))

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
  "Save an RGB image"
  [^String file-name {:keys [width height data]}]
  (let [info  (ImageInfo.)
        image (MagickImage.)]
    (.constituteImage image width height "RGBA" data)
    (.setSize info (str width \x height))
    (.setDepth info 8)
    (.setColorspace info ColorspaceType/RGBColorspace)
    (.setFileName image file-name)
    (.writeImage image info)
    image))

(defn slurp-image
  "Load an RGB image"
  [^String file-name]
  (let [info      (ImageInfo. file-name)
        image     (MagickImage. info)
        dimension (.getDimension image)]
    (doto info
      (.setDepth 8)
      (.setColorspace ColorspaceType/RGBColorspace)
      (.setMagick "RGBA"))
    {:width (.width dimension) :height (.height dimension) :data (.imageToBlob image info)}))

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
  (let [offset (* 4 (+ (* width y) x))]
    (->RGB (byte->ubyte (aget data offset)) (byte->ubyte (aget data (inc offset))) (byte->ubyte (aget data (inc (inc offset)))))))

(defn set-pixel!
  "Set color value of a pixel in an image"
  [{:keys [width height data]} ^long y ^long x ^RGB c]
  (let [offset (* 4 (+ (* width y) x))]
    (aset-byte data offset       (ubyte->byte (:r c)))
    (aset-byte data (+ offset 1) (ubyte->byte (:g c)))
    (aset-byte data (+ offset 2) (ubyte->byte (:b c)))
    (aset-byte data (+ offset 3) (ubyte->byte 255))))

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
  [{:keys [width height data]} ^long y ^long x ^long value]
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
  ^Vector3 [{:keys [width height data]} ^long y ^long x]
  (let [offset (* 3 (+ (* width y) x))]
    (->Vector3 (aget data offset      )
               (aget data (+ offset 1))
               (aget data (+ offset 2)))))

(defn set-vector!
  "Write vector value to vectors tile"
  [{:keys [width height data]} ^long y ^long x ^Vector3 value]
  (let [offset (* 3 (+ (* width y) x))]
    (aset-float data offset       (:x value))
    (aset-float data (+ offset 1) (:y value))
    (aset-float data (+ offset 2) (:z value))))

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
