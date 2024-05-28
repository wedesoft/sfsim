(ns sfsim.image
  "Various utility functions."
  (:require [clojure.math :refer (round)]
            [malli.core :as m]
            [fastmath.vector :refer (vec3 vec4)]
            [sfsim.util :refer (byte->ubyte ubyte->byte dimensions N N0 non-empty-string)])
  (:import [java.nio DirectByteBuffer]
           [org.lwjgl BufferUtils]
           [org.lwjgl.stb STBImage STBImageWrite]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def image (m/schema [:map [::width N] [::height N] [::channels N] [::data bytes?]]))
(def byte-image (m/schema [:map [::width N] [::height N] [::data bytes?]]))
(def float-image-2d (m/schema [:map [::width N] [::height N] [::data seqable?]]))
(def float-image-3d (m/schema [:map [::width N] [::height N] [::depth N] [::data seqable?]]))
(def float-image-4d (m/schema [:map [::width N] [::height N] [::depth N] [::hyperdepth N] [::data seqable?]]))
(def field-2d (m/schema [:map [::width N] [::height N] [::data seqable?]]))

(defn make-image
  "Create an empty RGBA image"
  {:malli/schema [:=> [:cat N N] image]}
  [width height]
  {::width width ::height height ::channels 4 ::data (byte-array (* width height 4))})

(defn make-byte-image
  "Create an empty byte image"
  {:malli/schema [:=> [:cat N N] byte-image]}
  [width height]
  {::width width ::height height ::data (byte-array (* width height))})

(defn make-vector-image
  "Create an empty vector image"
  {:malli/schema [:=> [:cat N N] field-2d]}
  [width height]
  {::width width ::height height ::data (float-array (* width height 3))})

(defn slurp-image
  "Load an RGBA image"
  {:malli/schema [:=> [:cat non-empty-string [:? :boolean]] image]}
  ([path] (slurp-image path false))
  ([path flip]
   (STBImage/stbi_set_flip_vertically_on_load flip)
   (let [width (int-array 1)
         height (int-array 1)
         channels (int-array 1)
         buffer (STBImage/stbi_load ^String path width height channels 4)
         width  (aget width 0)
         height (aget height 0)
         data   (byte-array (* width height 4))]
     (.get ^DirectByteBuffer buffer ^bytes data)
     (.flip ^DirectByteBuffer buffer)
     (STBImage/stbi_image_free ^DirectByteBuffer buffer)
     (STBImage/stbi_set_flip_vertically_on_load false)
     {::data data ::width width ::height height ::channels (aget channels 0)})))

(defn spit-png
  "Save RGBA image as PNG file"
  {:malli/schema [:=> [:cat non-empty-string [:and image [:map [::channels [:= 4]]]] [:? :boolean]] image]}
  ([path img] (spit-png path img false))
  ([path {::keys [width height data] :as img} flip]
   (let [buffer (BufferUtils/createByteBuffer (count data))]
     (.put ^DirectByteBuffer buffer ^bytes data)
     (.flip buffer)
     (STBImageWrite/stbi_flip_vertically_on_write flip)
     (STBImageWrite/stbi_write_png ^String path ^long width ^long height 4 ^DirectByteBuffer buffer ^long (* 4 width))
     (STBImageWrite/stbi_flip_vertically_on_write false)
     img)))

(defn spit-jpg
  "Save RGBA image as JPEG file"
  {:malli/schema [:=> [:cat non-empty-string [:and image [:map [::channels [:= 4]]]] [:? :boolean]] image]}
  ([path img] (spit-jpg path img false))
  ([path {::keys [width height data] :as img} flip]
   (let [buffer (BufferUtils/createByteBuffer (count data))]
     (.put ^DirectByteBuffer buffer ^bytes data)
     (.flip buffer)
     (STBImageWrite/stbi_flip_vertically_on_write flip)
     (STBImageWrite/stbi_write_jpg ^String path ^long width ^long height 4 ^DirectByteBuffer buffer ^long (* 4 width))
     (STBImageWrite/stbi_flip_vertically_on_write false)
     img)))

(def normals (m/schema [:map [::width N] [::height N] [::data seqable?]]))

(defn spit-normals
  "Compress normals to PNG and save"
  {:malli/schema [:=> [:cat non-empty-string normals] normals]}
  [path {::keys [width height data] :as img}]
  (let [buffer    (BufferUtils/createByteBuffer (count data))
        byte-data (byte-array (count data))]
    (doseq [i (range (count data))]
           (aset-byte ^bytes byte-data ^long i ^byte (round (- (* (aget ^floats data ^long i) 127.5) 0.5))))
    (-> buffer (.put byte-data) (.flip))
    (STBImageWrite/stbi_write_png ^String path ^long width ^long height 3 ^DirectByteBuffer buffer ^long (* 3 width))
    img))

(defn slurp-normals
  "Convert PNG to normal vectors"
  {:malli/schema [:=> [:cat non-empty-string] normals]}
  [path]
  (let [width     (int-array 1)
        height    (int-array 1)
        channels  (int-array 1)
        buffer    (STBImage/stbi_load ^String path width height channels 3)
        width     (aget width 0)
        height    (aget height 0)
        byte-data (byte-array (* width height 3))
        data      (float-array (* width height 3))]
    (.get ^DirectByteBuffer buffer ^bytes byte-data)
    (.flip ^DirectByteBuffer buffer)
    (STBImage/stbi_image_free ^DirectByteBuffer buffer)
    (doseq [i (range (count data))]
           (aset-float ^floats data ^long i ^float (/ (+ (aget ^bytes byte-data ^long i) 0.5) 127.5)))
    {::width width ::height height ::data data}))

(defn get-pixel
  "Read color value from a pixel of an image"
  {:malli/schema [:=> [:cat image N0 N0] [:tuple :double :double :double]]}
  [{::keys [width data]} y x]
  (let [offset (* 4 (+ (* width y) x))]
    (vec3 (byte->ubyte (aget ^bytes data ^long offset))
          (byte->ubyte (aget ^bytes data ^long (inc offset)))
          (byte->ubyte (aget ^bytes data ^long (inc (inc offset)))))))

(defn set-pixel!
  "Set color value of a pixel in an image"
  {:malli/schema [:=> [:cat image N0 N0 [:vector :double]] [:vector :double]]}
  [{::keys [width data]} y x c]
  (let [offset (* 4 (+ (* width y) x))]
    (aset-byte data offset (ubyte->byte (long (c 0))))
    (aset-byte data (inc offset) (ubyte->byte (long (c 1))))
    (aset-byte data (inc (inc offset)) (ubyte->byte (long (c 2))))
    (aset-byte data (inc (inc (inc offset))) -1)
    c))

(defn get-short
  "Read value from a short integer tile"
  {:malli/schema [:=> [:cat field-2d N0 N0] :int]}
  [{::keys [width data]} y x]
  (aget ^shorts data ^long (+ (* width y) x)))

(defn set-short!
  "Write value to a short integer tile"
  {:malli/schema [:=> [:cat field-2d N0 N0 :int] :int]}
  [{::keys [width data]} y x value]
  (aset-short data (+ (* width y) x) value))

(defn get-float
  "Read value from a floating-point tile"
  {:malli/schema [:=> [:cat field-2d N0 N0] number?]}
  [{::keys [width data]} y x]
  (aget ^floats data ^long (+ (* width y) x)))

(defn set-float!
  "Write value to a floating-point tile"
  {:malli/schema [:=> [:cat field-2d N0 N0 number?] number?]}
  [{::keys [width data]} y x value]
  (aset-float data (+ (* width y) x) value))

(def field-3d (m/schema [:map [::width N] [::height N] [::depth N] [::data seqable?]]))

(defn get-float-3d
  "Read floating-point value from a 3D cube"
  {:malli/schema [:=> [:cat field-3d N0 N0 N0] number?]}
  [{::keys [width height data]} z y x]
  (aget ^floats data ^long (+ (* width (+ (* height z) y)) x)))

(defn set-float-3d!
  "Write floating-point value to a 3D cube"
  {:malli/schema [:=> [:cat field-3d N0 N0 N0 number?] number?]}
  [{::keys [width height data]} z y x value]
  (aset-float data (+ (* width (+ (* height z) y)) x) value))

(def byte-field-2d (m/schema [:map [::width N] [::height N] [::data bytes?]]))

(defn get-byte
  "Read byte value from a tile"
  {:malli/schema [:=> [:cat byte-field-2d N0 N0] :int]}
  [{::keys [width data]} y x]
  (byte->ubyte (aget ^bytes data ^long (+ (* width y) x))))

(defn set-byte!
  "Write byte value to a tile"
  {:malli/schema [:=> [:cat byte-field-2d N0 N0 :int] :int]}
  [{::keys [width data]} y x value]
  (aset-byte data (+ (* width y) x) (ubyte->byte value)))

(defn get-vector3
  "Read RGB vector from a vectors tile"
  {:malli/schema [:=> [:cat field-2d N0 N0] [:tuple :double :double :double]]}
  [{::keys [width data]} y x]
  (let [offset (* 3 (+ (* width y) x))]
    (vec3 (aget ^floats data ^long (+ offset 0))
          (aget ^floats data ^long (+ offset 1))
          (aget ^floats data ^long (+ offset 2)))))

(defn set-vector3!
  "Write RGB vector value to vectors tile"
  {:malli/schema [:=> [:cat field-2d N0 N0 [:vector :double]] [:vector :double]]}
  [{::keys [width data]} y x value]
  (let [offset (* 3 (+ (* width y) x))]
    (aset-float data (+ offset 0) (value 0))
    (aset-float data (+ offset 1) (value 1))
    (aset-float data (+ offset 2) (value 2))
    value))

(defn get-vector4
  "read RGBA vector from a vectors tile"
  {:malli/schema [:=> [:cat field-2d N0 N0] [:vector :double]]}
  [{::keys [width data]} y x]
  (let [offset (* 4 (+ (* width y) x))]
    (vec4 (aget ^floats data ^long (+ offset 0))
          (aget ^floats data ^long (+ offset 1))
          (aget ^floats data ^long (+ offset 2))
          (aget ^floats data ^long (+ offset 3)))))

(defn set-vector4!
  "Write RGBA vector value to vectors tile"
  {:malli/schema [:=> [:cat field-2d N0 N0 [:vector :double]] [:vector :double]]}
  [{::keys [width data]} y x value]
  (let [offset (* 4 (+ (* width y) x))]
    (aset-float data (+ offset 0) (value 0))
    (aset-float data (+ offset 1) (value 1))
    (aset-float data (+ offset 2) (value 2))
    (aset-float data (+ offset 3) (value 3))
    value))

(def array-2d [:vector [:vector :any]])
(def array-4d [:vector [:vector [:vector [:vector :any]]]])

(defn convert-4d-to-2d
  "Take tiles from 4D array and arrange in a 2D array"
  {:malli/schema [:=> [:cat array-4d] array-2d]}
  [array]
  (let [[d c b a] (dimensions array)
        h         (* d b)
        w         (* c a)]
    (mapv (fn row-2d [y] (mapv (fn pixel-2d [x] (get-in array [(quot y b) (quot x a) (mod y b) (mod x a)])) (range w))) (range h))))

(defn convert-2d-to-4d
  "Convert 2D array with tiles to 4D array (assuming that each two dimensions are the same)"
  {:malli/schema [:=> [:cat array-2d N N N N] array-4d]}
  [array d c b a]
  (mapv (fn cube-4d [y]
            (mapv (fn plane-4d [x]
                      (mapv (fn row-4d [v]
                                (mapv (fn pixel-4d [u] (get-in array [(+ v (* y b)) (+ u (* x a))]))
                                      (range a)))
                            (range b)))
                  (range c)))
        (range d)))

(defn floats->image
  "Convert floating point image to RGBA"
  {:malli/schema [:=> [:cat float-image-2d] image]}
  [{::keys [width height data]}]
  {::width width
   ::height height
   ::channels 4
   ::data (byte-array (mapcat (fn [scalar] (let [b (int (* 255.0 scalar))] [b b b -1])) data))})

(defn white-image-with-alpha
  "Convert alpha image to white image with alpha channel"
  {:malli/schema [:=> [:cat [:and image [:map [::channels [:= 1]]]]] [:and image [:map [::channels [:= 4]]]]]}
  [{::keys [width height data]}]
  (let [result (byte-array (mapcat (fn [alpha] [-1 -1 -1 alpha]) data))]
    {::width width ::height height ::data result ::channels 4}))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
