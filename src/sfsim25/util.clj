(ns sfsim25.util
  "Various utility functions."
  (:require [clojure.java.io :as io]
            [clojure.math :refer (sin sqrt round)]
            [clojure.core.matrix :refer (matrix mget set-current-implementation)]
            [progrock.core :as p])
  (:import [java.nio ByteBuffer ByteOrder]
           [java.io ByteArrayOutputStream]
           [ij ImagePlus]
           [ij.io Opener FileSaver]
           [ij.process ImageConverter ColorProcessor]
           [mikera.vectorz Vector]))

(set! *unchecked-math* true)

(set-current-implementation :vectorz)

(defn third
  "Get third element of a list"
  [lst]
  (nth lst 2))

(defn fourth
  "Get fourth element of a list"
  [lst]
  (nth lst 3))

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
  (if (zero? x) 1.0 (/ (sin x) x)))

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

(defn show-image
  "Open a window displaying the image"
  [{:keys [width height data]}]
  (let [processor (ColorProcessor. width height data)
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))

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
  ^Vector [{:keys [width height data]} ^long y ^long x]
  (let [offset (+ (* width y) x)
        value  (aget data offset)]
    (matrix [(bit-shift-right (bit-and 0xff0000 value) 16) (bit-shift-right (bit-and 0xff00 value) 8) (bit-and 0xff value)])))

(defn set-pixel!
  "Set color value of a pixel in an image"
  [{:keys [width height data]} ^long y ^long x ^Vector c]
  (let [offset (+ (* width y) x)]
    (aset-int data offset (bit-or (bit-shift-left -1 24)
                                  (bit-shift-left (round (mget c 0)) 16)
                                  (bit-shift-left (round (mget c 1)) 8)
                                  (round (mget c 2))))))

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
    (matrix [(aget data (+ offset 2))
             (aget data (+ offset 1))
             (aget data (+ offset 0))])))

(defn set-vector!
  "Write vector value to vectors tile"
  [{:keys [width height data]} ^long y ^long x ^Vector value]
  (let [offset (* 3 (+ (* width y) x))]
    (aset-float data (+ offset 2) (mget value 0))
    (aset-float data (+ offset 1) (mget value 1))
    (aset-float data (+ offset 0) (mget value 2))))

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
  "Define context macro creating, opening and closing a context object"
  [method constructor ctx-macro]
  `(defmacro ~method [object# & body#]
     `(let [~object# (~~constructor)]
        (~~ctx-macro ~object# ~@body#))))

(defn align-address
  "Function for aligning an address with specified alignment"
  ^long [^long address ^long alignment]
  (let [mask (dec alignment)]
    (bit-and (+ address mask) (bit-not mask))))

(defn dimensions
  "Return shape of nested vector"
  [array]
  (if (vector? array)
    (into [(count array)] (dimensions (first array)))
    []))

(defn comp*
  "Combine multiple-argument functions"
  [f g]
  (fn [& args] (apply f (apply g args))))

(defn pack-floats
  "Pack nested floating-point vector into float array"
  [array]
  (float-array (flatten array)))

(defn convert-4d-to-2d
  "Take tiles from 4D array and arrange in a 2D array"
  [array]
  (let [[d c b a] (dimensions array)
        h         (* d b)
        w         (* c a)]
    (mapv (fn [y] (mapv (fn [x] (get-in array [(quot y b) (quot x a) (mod y b) (mod x a)])) (range w))) (range h))))

(defn convert-2d-to-4d
  "Convert 2D array with tiles to 4D array (assuming that each two dimensions are the same)"
  [array]
  (let [[h w] (dimensions array)
        b     (int (sqrt h))
        a     (int (sqrt w))]
    (mapv (fn [y]
              (mapv (fn [x]
                        (mapv (fn [v]
                                  (mapv (fn [u] (get-in array [(+ v (* y b)) (+ u (* x a))]))
                                        (range a)))
                              (range b)))
                    (range a)))
          (range b))))

(defn size-of-shape
  "Determine size of given shape"
  [shape]
  (apply * shape))

(defn progress
  "Update progress bar when calling a function"
  [fun size step]
  (let [bar       (agent (p/progress-bar size))
        increase  (fn [bar]
                      (let [result (assoc (p/tick bar 1)
                                          :done? (= (inc (:progress bar)) (:total bar)))]
                           (when (zero? (mod (:progress result) step))
                                 (p/print result))
                           result))]
    (p/print @bar)
    (fn [& args]
        (send bar increase)
        (apply fun args))))

(set! *unchecked-math* false)
