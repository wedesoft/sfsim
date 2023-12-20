(ns sfsim25.util
  "Various utility functions."
  (:require [clojure.java.io :as io]
            [clojure.math :refer (sin round)]
            [malli.core :as m]
            [fastmath.vector :refer (vec3 vec4)]
            [progrock.core :as p])
  (:import [java.io ByteArrayOutputStream]
           [java.nio ByteBuffer ByteOrder]
           [org.lwjgl BufferUtils]
           [org.lwjgl.stb STBImage STBImageWrite]
           [fastmath.vector Vec3 Vec4]))

(set! *unchecked-math* true)

(defn third
  "Get third element of a list"
  {:malli/schema [:=> [:cat [:sequentual {:min 3} :some]]]}
  [lst]
  (nth lst 2))

(defn fourth
  "Get fourth element of a list"
  [lst]
  {:malli/schema [:=> [:cat [:sequentual {:min 4} :some]]]}
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
  {:malli/schema [:=> [:cat non-empty-string bytes?] bytes?]}
  [^String file-name ^bytes byte-data]
  (with-open [out (io/output-stream file-name)]
    (.write out byte-data)))

(defn slurp-shorts
  "Read short integers from a file"
  ^shorts [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        short-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asShortBuffer)
        result       (short-array (/ n 2))]
    (.get short-buffer result)
    result))

(defn spit-shorts
  "Write short integers to a file"
  [^String file-name ^shorts short-data]
  (let [n           (count short-data)
        byte-buffer (.order (ByteBuffer/allocate (* n 2)) ByteOrder/LITTLE_ENDIAN)]
    (.put (.asShortBuffer byte-buffer) short-data)
    (spit-bytes file-name (.array byte-buffer))))

(defn slurp-floats
  "Read floating point numbers from a file"
  ^floats [^String file-name]
  (let [byte-data    (slurp-bytes file-name)
        n            (.count (seq byte-data))
        float-buffer (-> byte-data ByteBuffer/wrap (.order ByteOrder/LITTLE_ENDIAN) .asFloatBuffer)
        result       (float-array (/ n 4))]
    (.get float-buffer result)
    result))

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

(defn slurp-image
  "Load an RGB image"
  [path]
  (let [width (int-array 1)
        height (int-array 1)
        channels (int-array 1)
        buffer (STBImage/stbi_load path width height channels 4)
        width  (aget width 0)
        height (aget height 0)
        data   (byte-array (* width height 4))]
    (.get buffer data)
    (.flip buffer)
    (STBImage/stbi_image_free buffer)
    {:data data :width width :height height :channels (aget channels 0)}))

(defn spit-png
  "Save RGB image as PNG file"
  [path {:keys [width height data] :as img}]
  (let [buffer (BufferUtils/createByteBuffer (* 4 (count data)))]
    (-> buffer (.put data) (.flip))
    (STBImageWrite/stbi_write_png path width height 4 buffer (* 4 width))
    img))

(defn spit-jpg
  "Save RGB image as JPEG file"
  [path {:keys [width height data] :as img}]
  (let [buffer (BufferUtils/createByteBuffer (* 4 (count data)))]
    (-> buffer (.put data) (.flip))
    (STBImageWrite/stbi_write_jpg path width height 4 buffer (* 4 width))
    img))

(defn spit-normals
  "Compress normals to PNG and save"
  [path {:keys [width height data] :as img}]
  (let [buffer    (BufferUtils/createByteBuffer (count data))
        byte-data (byte-array (count data))]
    (doseq [i (range (count data))]
           (aset-byte ^bytes byte-data ^long i ^byte (round (- (* (aget ^floats data ^long i) 127.5) 0.5))))
    (-> buffer (.put byte-data) (.flip))
    (STBImageWrite/stbi_write_png path width height 3 buffer (* 3 width))
    img))

(defn slurp-normals
  "Convert PNG to normal vectors"
  [path]
  (let [width     (int-array 1)
        height    (int-array 1)
        channels  (int-array 1)
        buffer    (STBImage/stbi_load path width height channels 3)
        width     (aget width 0)
        height    (aget height 0)
        byte-data (byte-array (* width height 3))
        data      (float-array (* width height 3))]
    (.get buffer byte-data)
    (.flip buffer)
    (STBImage/stbi_image_free buffer)
    (doseq [i (range (count data))]
           (aset-float ^floats data ^long i ^float (/ (+ (aget ^bytes byte-data ^long i) 0.5) 127.5)))
    {:width width :height height :data data}))

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
  ^Vec3 [{:keys [width data]} ^long y ^long x]
  (let [offset (* 4 (+ (* width y) x))]
    (vec3 (byte->ubyte (aget data offset))
          (byte->ubyte (aget data (inc offset)))
          (byte->ubyte (aget data (inc (inc offset)))))))

(defn set-pixel!
  "Set color value of a pixel in an image"
  [{:keys [width data]} ^long y ^long x ^Vec3 c]
  (let [offset (* 4 (+ (* width y) x))]
    (aset-byte data offset (ubyte->byte (c 0)))
    (aset-byte data (inc offset) (ubyte->byte (c 1)))
    (aset-byte data (inc (inc offset)) (ubyte->byte (c 2)))
    (aset-byte data (inc (inc (inc offset))) -1)))

(defn get-short
  "Read value from a short integer tile"
  [{:keys [width data]} ^long y ^long x]
  (aget data (+ (* width y) x)))

(defn set-short!
  "Write value to a short integer tile"
  [{:keys [width data]} ^long y ^long x ^long value]
  (aset-short data (+ (* width y) x) value))

(defn get-float
  "Read value from a floating-point tile"
  ^double [{:keys [width data]} ^long y ^long x]
  (aget data (+ (* width y) x)))

(defn set-float!
  "Write value to a floating-point tile"
  [{:keys [width data]} ^long y ^long x ^double value]
  (aset-float data (+ (* width y) x) value))

(defn get-float-3d
  "Read floating-point value from a 3D cube"
  ^double [{:keys [width height data]} ^long z ^long y ^long x]
  (aget data (+ (* width (+ (* height z) y)) x)))

(defn set-float-3d!
  "Write floating-point value to a 3D cube"
  [{:keys [width height data]} z y x value]
  (aset-float data (+ (* width (+ (* height z) y)) x) value))

(defn get-byte
  "Read byte value from a tile"
  ^long [{:keys [width data]} ^long y ^long x]
  (byte->ubyte (aget data (+ (* width y) x))))

(defn set-byte!
  "Write byte value to a tile"
  [{:keys [width data]} ^long y ^long x ^long value]
  (aset-byte data (+ (* width y) x) (ubyte->byte value)))

(defn get-vector3
  "Read RGB vector from a vectors tile"
  ^Vec3 [{:keys [width data]} ^long y ^long x]
  (let [offset (* 3 (+ (* width y) x))]
    (vec3 (aget data (+ offset 0)) (aget data (+ offset 1)) (aget data (+ offset 2)))))

(defn set-vector3!
  "Write RGB vector value to vectors tile"
  [{:keys [width data]} ^long y ^long x ^Vec3 value]
  (let [offset (* 3 (+ (* width y) x))]
    (aset-float data (+ offset 0) (value 0))
    (aset-float data (+ offset 1) (value 1))
    (aset-float data (+ offset 2) (value 2))))

(defn get-vector4
  "read RGBA vector from a vectors tile"
  ^Vec4 [{:keys [width data]} ^long y ^long x]
  (let [offset (* 4 (+ (* width y) x))]
    (vec4 (aget data (+ offset 0)) (aget data (+ offset 1)) (aget data (+ offset 2)) (aget data (+ offset 3)))))

(defn set-vector4!
  "Write RGBA vector value to vectors tile"
  [{:keys [width data]} ^long y ^long x ^Vec4 value]
  (let [offset (* 4 (+ (* width y) x))]
    (aset-float data (+ offset 0) (value 0))
    (aset-float data (+ offset 1) (value 1))
    (aset-float data (+ offset 2) (value 2))
    (aset-float data (+ offset 3) (value 3))))

(defn dissoc-in
  "Return nested hash with path removed"
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
  [array]
  (if (instance? clojure.lang.PersistentVector array)
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
  [array d c b a]
  (mapv (fn [y]
            (mapv (fn [x]
                      (mapv (fn [v]
                                (mapv (fn [u] (get-in array [(+ v (* y b)) (+ u (* x a))]))
                                      (range a)))
                            (range b)))
                  (range c)))
        (range d)))

(defn size-of-shape
  "Determine size of given shape"
  [shape]
  (apply * shape))

(defn limit-quot
  "Compute quotient and limit it"
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

(defn make-progress-bar
  "Create a progress bar"
  [size step]
  (let [result (assoc (p/progress-bar size) :step step)]
    (p/print result)
    result))

(defn tick-and-print
  "Increase progress and occasionally update progress bar"
  [bar]
  (let [done   (== (inc (:progress bar)) (:total bar))
        result (assoc (p/tick bar 1) :done? done)]
    (when (or (zero? (mod (:progress result) (:step bar))) done) (p/print result))
    result))

(defn progress-wrap
  "Update progress bar when calling a function"
  [fun size step]
  (let [bar (agent (make-progress-bar size step))]
    (fn [& args]
        (send bar tick-and-print)
        (apply fun args))))

(defn dimension-count
  "Count dimensions of nested vector"
  [array dimension]
  (count (nth (iterate first array) dimension)))

(set! *unchecked-math* false)
