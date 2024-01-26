(ns sfsim25.t-image
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [clojure.math :refer (PI)]
            [fastmath.vector :refer (vec3 vec4)]
            [sfsim25.image :refer :all])
  (:import [java.io File]))

(mi/collect! {:ns ['sfsim25.util]})
(mi/instrument! {:report (pretty/thrower)})

(facts "Saving and loading of PNG image"
  (let [file-name (.getPath (File/createTempFile "spit" ".png"))
        value     [1 2 3 -1]]
      (spit-png file-name {:width 4 :height 2 :data (byte-array (flatten (repeat 8 value))) :channels 4})
      (:width  (slurp-image file-name)) => 4
      (:height (slurp-image file-name)) => 2
      (take 4 (:data (slurp-image file-name))) => value))

(facts "Saving and loading of JPEG image"
  (let [file-name (.getPath (File/createTempFile "spit" ".jpg"))
        value     [0 35 63 -1]]
      (spit-jpg file-name {:width 4 :height 2 :data (byte-array (flatten (repeat 8 value))) :channels 4})
      (:width  (slurp-image file-name)) => 4
      (:height (slurp-image file-name)) => 2
      (take 4 (:data (slurp-image file-name))) => value))

(fact "Save normal vectors"
      (let [file-name (.getPath (File/createTempFile "spit" ".png"))]
        (spit-normals file-name {:width 2 :height 1 :data (float-array [1.0 0.0 0.0 0.0 -1.0 0.0])}) => anything
        (let [normals (slurp-normals file-name)
              n1      (get-vector3 normals 0 0)
              n2      (get-vector3 normals 0 1)]
          (:width normals)  => 2
          (:height normals) => 1
          (n1 0)            => (roughly  1.0 1e-2)
          (n1 1)            => (roughly  0.0 1e-2)
          (n2 0)            => (roughly  0.0 1e-2)
          (n2 1)            => (roughly -1.0 1e-2))))

(facts "Reading and writing image pixels"
  (get-pixel (slurp-image "test/sfsim25/fixtures/util/red.png") 0 0) => (vec3 255 0 0)
  (get-pixel (slurp-image "test/sfsim25/fixtures/util/green.png") 0 0) => (vec3 0 255 0)
  (get-pixel (slurp-image "test/sfsim25/fixtures/util/blue.png") 0 0) => (vec3 0 0 255)
  (let [img {:width 4 :height 2 :channels 4 :data (byte-array (flatten (concat (repeat 7 [0 0 0 -1]) [[1 2 3 -1]])))}]
    (get-pixel img 1 3) => (vec3 1 2 3))
  (let [img {:width 1 :height 1 :channels 4 :data (byte-array [1 2 3 -1])}]
    (get-pixel img 0 0) => (vec3 1 2 3))
  (let [img {:width 4 :height 2 :channels 4 :data (byte-array (repeat 32 0))}]
    (set-pixel! img 1 2 (vec3 253 254 255)) => anything
    (get-pixel img 1 2) => (vec3 253 254 255)))

(facts "Reading and writing of short integer values in 2D array"
  (let [elevation {:width 4 :height 2 :data (short-array (range 8))}]
    (get-short elevation 1 2) => 6
    (set-short! elevation 1 2 8) => anything
    (get-short elevation 1 2) => 8))

(facts "Reading and writing of float values in 2D array"
  (let [scale {:width 4 :height 2 :data (float-array (range 8))}]
    (get-float scale 1 2) => 6.0
    (set-float! scale 1 2 8.5) => anything
    (get-float scale 1 2) => 8.5))

(facts "Reading and writing of float values in 3D array"
  (let [scale {:width 5 :height 3 :depth 2 :data (float-array (range 30))}]
    (get-float-3d scale 1 2 3) => 28.0
    (set-float-3d! scale 1 2 3 8.5) => anything
    (get-float-3d scale 1 2 3) => 8.5))

(facts "Reading and writing of bytes in 2D array"
  (let [water {:width 4 :height 2 :data (byte-array (range 8))}]
    (get-byte water 1 2) => 6
    (set-byte! water 1 2 136) => anything
    (get-byte water 1 2) => 136))

(facts "Reading and writing of RGB vectors"
  (let [vectors {:width 4 :height 2 :data (float-array (range 24))}]
    (get-vector3 vectors 1 2) => (vec3 18.0 19.0 20.0)
    (set-vector3! vectors 1 2 (vec3 24.5 25.5 26.5)) => anything
    (get-vector3 vectors 1 2) => (vec3 24.5 25.5 26.5)))

(facts "Reading and writing of RGBA vectors"
  (let [vectors {:width 4 :height 2 :data (float-array (range 32))}]
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


