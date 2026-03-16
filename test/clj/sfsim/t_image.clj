;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-image
  (:require
    [fastmath.vector :refer (vec3 vec4)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.util :as util]
    [sfsim.image :refer :all])
  (:import
    (java.io
      File)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Create empty RGBA color image"
       (:sfsim.image/width (make-image 3 2)) => 3
       (:sfsim.image/height (make-image 3 2)) => 2
       (:sfsim.image/channels (make-image 3 2)) => 4
       (:sfsim.image/data (make-image 3 2)) => bytes?
       (count (:sfsim.image/data (make-image 3 2))) => (* 3 2 4))


(facts "Create empty byte image"
       (:sfsim.image/width (make-byte-image 3 2)) => 3
       (:sfsim.image/height (make-byte-image 3 2)) => 2
       (:sfsim.image/data (make-byte-image 3 2)) => bytes?
       (count (:sfsim.image/data (make-byte-image 3 2))) => (* 3 2))


(facts "Create empty vector image"
       (:sfsim.image/width (make-vector-image 3 2)) => 3
       (:sfsim.image/height (make-vector-image 3 2)) => 2
       (:sfsim.image/data (make-vector-image 3 2)) => seqable?
       (count (:sfsim.image/data (make-vector-image 3 2))) => (* 3 2 3))


(facts "Create empty RGBA color image"
       (:sfsim.image/width (make-image 3 2)) => 3
       (:sfsim.image/height (make-image 3 2)) => 2
       (:sfsim.image/channels (make-image 3 2)) => 4
       (count (:sfsim.image/data (make-image 3 2))) => (* 3 2 4))


(facts "Load PNG image"
       (let [image (slurp-image "test/clj/sfsim/fixtures/image/pattern.png")]
         (:sfsim.image/width image) => 2
         (:sfsim.image/height image) => 2
         (:sfsim.image/channels image) => 3
         (seq (:sfsim.image/data image)) => [-1 0 0 -1, 0 0 0 -1, 0 0 0 -1, 0 -1 0 -1]))


(facts "Saving and loading of PNG image"
       (let [file-name (.getPath (File/createTempFile "spit" ".png"))
             value     [1 2 3 -1]]
         (spit-png file-name #:sfsim.image{:width 4 :height 2 :data (byte-array (flatten (repeat 8 value))) :channels 4})
         (:sfsim.image/width  (slurp-image file-name)) => 4
         (:sfsim.image/height (slurp-image file-name)) => 2
         (take 4 (:sfsim.image/data (slurp-image file-name))) => value))


(facts "Saving and loading of JPEG image"
       (let [file-name (.getPath (File/createTempFile "spit" ".jpg"))
             value     [0 35 63 -1]]
         (spit-jpg file-name #:sfsim.image{:width 4 :height 2 :data (byte-array (flatten (repeat 8 value))) :channels 4})
         (:sfsim.image/width  (slurp-image file-name)) => 4
         (:sfsim.image/height (slurp-image file-name)) => 2
         (take 4 (:sfsim.image/data (slurp-image file-name))) => value))


(facts "Load PNG image from tar file"
       (let [image (util/with-tar tar "test/clj/sfsim/fixtures/image/image.tar" (slurp-image-tar tar "pattern.png"))]
         (:sfsim.image/width image) => 2
         (:sfsim.image/height image) => 2
         (:sfsim.image/channels image) => 3
         (seq (:sfsim.image/data image)) => [-1 0 0 -1, 0 0 0 -1, 0 0 0 -1, 0 -1 0 -1]))


(facts "Try flipping an image when loading"
       (let [file-name (.getPath (File/createTempFile "spit" ".png"))
             values    [1 2 3 -1 4 5 6 -1]]
         (spit-png file-name #:sfsim.image{:width 1 :height 2 :data (byte-array values) :channels 4})
         (seq (:sfsim.image/data (slurp-image file-name true))) => [4 5 6 -1 1 2 3 -1]))


(facts "Try flipping an image when saving as PNG"
       (let [file-name (.getPath (File/createTempFile "spit" ".png"))
             values    [1 2 3 -1 4 5 6 -1]]
         (spit-png file-name #:sfsim.image{:width 1 :height 2 :data (byte-array values) :channels 4} true)
         (seq (:sfsim.image/data (slurp-image file-name))) => [4 5 6 -1 1 2 3 -1]))


(fact "Save and load normal vectors PNG file"
      (let [file-name (.getPath (File/createTempFile "spit" ".png"))]
        (spit-normals file-name #:sfsim.image{:width 2 :height 1 :data (float-array [1.0 0.0 0.0 0.0 -1.0 0.0])}) => anything
        (let [normals (slurp-normals file-name)
              n1      (get-vector3 normals 0 0)
              n2      (get-vector3 normals 0 1)]
          (:sfsim.image/width normals)  => 2
          (:sfsim.image/height normals) => 1
          (n1 0)            => (roughly  1.0 1e-2)
          (n1 1)            => (roughly  0.0 1e-2)
          (n2 0)            => (roughly  0.0 1e-2)
          (n2 1)            => (roughly -1.0 1e-2))))


(fact "Load normal vectors from PNG in a tar file"
      (let [file-name (.getPath (File/createTempFile "normals" ".png"))
            tar-name  (.getPath (File/createTempFile "normals" ".tar"))]
        (spit-normals file-name #:sfsim.image{:width 2 :height 1 :data (float-array [1.0 0.0 0.0 0.0 -1.0 0.0])})
        (util/create-tar tar-name ["normals.png" file-name])
        (let [normals (util/with-tar tar tar-name (slurp-normals-tar tar "normals.png"))
              n1      (get-vector3 normals 0 0)
              n2      (get-vector3 normals 0 1)]
          (:sfsim.image/width normals)  => 2
          (:sfsim.image/height normals) => 1
          (n1 0)            => (roughly  1.0 1e-2)
          (n1 1)            => (roughly  0.0 1e-2)
          (n2 0)            => (roughly  0.0 1e-2)
          (n2 1)            => (roughly -1.0 1e-2))))


(facts "Reading and writing image pixels"
       (get-pixel (slurp-image "test/clj/sfsim/fixtures/util/red.png") 0 0) => (vec3 255 0 0)
       (get-pixel (slurp-image "test/clj/sfsim/fixtures/util/green.png") 0 0) => (vec3 0 255 0)
       (get-pixel (slurp-image "test/clj/sfsim/fixtures/util/blue.png") 0 0) => (vec3 0 0 255)
       (let [img #:sfsim.image{:width 4 :height 2 :channels 4
                               :data (byte-array (flatten (concat (repeat 7 [0 0 0 -1]) [[1 2 3 -1]])))}]
         (get-pixel img 1 3) => (vec3 1 2 3))
       (let [img #:sfsim.image{:width 1 :height 1 :channels 4 :data (byte-array [1 2 3 -1])}]
         (get-pixel img 0 0) => (vec3 1 2 3))
       (let [img #:sfsim.image{:width 4 :height 2 :channels 4 :data (byte-array (repeat 32 0))}]
         (set-pixel! img 1 2 (vec3 253 254 255)) => anything
         (get-pixel img 1 2) => (vec3 253 254 255)))


(facts "Reading and writing of short integer values in 2D array"
       (let [elevation #:sfsim.image{:width 4 :height 2 :data (short-array (range 8))}]
         (get-short elevation 1 2) => 6
         (set-short! elevation 1 2 8) => anything
         (get-short elevation 1 2) => 8))


(facts "Reading and writing of float values in 2D array"
       (let [scale #:sfsim.image{:width 4 :height 2 :data (float-array (range 8))}]
         (get-float scale 1 2) => 6.0
         (set-float! scale 1 2 8.5) => anything
         (get-float scale 1 2) => 8.5))


(facts "Reading and writing of float values in 3D array"
       (let [scale #:sfsim.image{:width 5 :height 3 :depth 2 :data (float-array (range 30))}]
         (get-float-3d scale 1 2 3) => 28.0
         (set-float-3d! scale 1 2 3 8.5) => anything
         (get-float-3d scale 1 2 3) => 8.5))


(facts "Reading and writing of bytes in 2D array"
       (let [water #:sfsim.image{:width 4 :height 2 :data (byte-array (range 8))}]
         (get-byte water 1 2) => 6
         (set-byte! water 1 2 136) => anything
         (get-byte water 1 2) => 136))


(facts "Reading and writing of RGB vectors"
       (let [vectors #:sfsim.image{:width 4 :height 2 :data (float-array (range 24))}]
         (get-vector3 vectors 1 2) => (vec3 18.0 19.0 20.0)
         (set-vector3! vectors 1 2 (vec3 24.5 25.5 26.5)) => anything
         (get-vector3 vectors 1 2) => (vec3 24.5 25.5 26.5)))


(facts "Reading and writing of RGBA vectors"
       (let [vectors #:sfsim.image{:width 4 :height 2 :data (float-array (range 32))}]
         (get-vector4 vectors 1 2) => (vec4 24.0 25.0 26.0 27.0)
         (set-vector4! vectors 1 2 (vec4 1.5 2.5 3.5 4.5)) => anything
         (get-vector4 vectors 1 2) => (vec4 1.5 2.5 3.5 4.5)))


(fact "Convert 4D to 2D texture by tiling"
      (convert-4d-to-2d [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]])
      => [[1 2 5 6] [3 4 7 8] [9 10 13 14] [11 12 15 16]]
      (convert-4d-to-2d [[[[1 2 3 4] [5 6 7 8] [9 10 11 12]] [[13 14 15 16] [17 18 19 20] [21 22 23 24]]]])
      => [[1 2 3 4 13 14 15 16] [5 6 7 8 17 18 19 20] [9 10 11 12 21 22 23 24]])


(fact "Convert 2D texture with tiles to 4D array"
      (convert-2d-to-4d [[1 2 5 6] [3 4 7 8] [9 10 13 14] [11 12 15 16]] 2 2 2 2)
      => [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]]
      (convert-2d-to-4d [[1 2 3 4 13 14 15 16] [5 6 7 8 17 18 19 20] [9 10 11 12 21 22 23 24]] 1 2 3 4)
      => [[[[1 2 3 4] [5 6 7 8] [9 10 11 12]] [[13 14 15 16] [17 18 19 20] [21 22 23 24]]]])


(facts "Convert floating point image to RGBA"
       (let [arr    #:sfsim.image{:width 3 :height 1 :data (float-array [0.25 0.5 0.75])}
             result (floats->image arr)]
         (:sfsim.image/width result) => 3
         (:sfsim.image/height result) => 1
         (:sfsim.image/channels result) => 4
         (get-pixel result 0 0) => (vec3 63 63 63)
         (get-pixel result 0 1) => (vec3 127 127 127)))


(facts "Convert alpha image to white image with alpha channel"
       (let [alpha  #:sfsim.image{:width 3 :height 1 :data (byte-array [0 127 -1]) :channels 1}
             result (white-image-with-alpha alpha)]
         (:sfsim.image/width result) => 3
         (:sfsim.image/height result) => 1
         (:sfsim.image/channels result) => 4
         (seq (:sfsim.image/data result)) => [-1 -1 -1 0 -1 -1 -1 127 -1 -1 -1 -1]))
