(ns sfsim25.t-util
  (:require [midje.sweet :refer :all]
            [clojure.math :refer (PI)]
            [clojure.core.matrix :refer (matrix)]
            [sfsim25.util :refer :all])
  (:import [java.io File]))

(facts "Get elements of a small list"
       (first  '(2 3 5 7)) => 2
       (second '(2 3 5 7)) => 3
       (third  '(2 3 5 7)) => 5
       (fourth '(2 3 5 7)) => 7)

(fact "Load a set of bytes"
  (seq (slurp-bytes "test/sfsim25/fixtures/util/bytes.raw")) => [2 3 5 7])

(fact "Load a set of short integers"
  (seq (slurp-shorts "test/sfsim25/fixtures/util/shorts.raw")) => [2 3 5 7])

(fact "Load a set of floating point numbers"
  (seq (slurp-floats "test/sfsim25/fixtures/util/floats.raw")) => [2.0 3.0 5.0 7.0])

(fact "Save a set of bytes"
  (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
    (spit-bytes file-name (byte-array [2 3 5 7])) => anything
    (seq (slurp-bytes file-name)) => [2 3 5 7]))

(fact "Save a set of short integers"
  (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
    (spit-shorts file-name (short-array [2 3 5 7])) => anything
    (seq (slurp-shorts file-name)) => [2 3 5 7]))

(fact "Save a set of floating point numbers"
  (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
    (spit-floats file-name (float-array [2.0 3.0 5.0 7.0])) => anything
    (seq (slurp-floats file-name)) => [2.0 3.0 5.0 7.0]))

(fact "Determine file path of map tile"
  (tile-path "world" 1 3 2 ".png") => "world/1/2/3.png")

(fact "Determine directory name of map tile"
  (tile-dir "world" 1 2) => "world/1/2")

(fact "Determine file path of cube tile"
  (cube-path "globe" 5 2 3 1 ".png") => "globe/5/2/1/3.png")

(fact "Determine directory name of cube tile"
  (cube-dir "globe" 5 2 1) => "globe/5/2/1")

(tabular "Sinc function"
  (fact (sinc ?x) => (roughly ?result 1e-6))
  ?x       ?result
  PI       0.0
  (/ PI 2) (/ 2 PI)
  0.0      1.0)

(facts "Square values"
  (sqr 2) => 4.0
  (sqr 3) => 9.0)

(facts "Saving and loading of RGB image"
  (let [file-name (.getPath (File/createTempFile "spit" ".png"))
        value     (bit-or (bit-shift-left -1 24) (bit-shift-left 1 16) (bit-shift-left 2 8) 3)]
      (spit-image file-name {:width 4 :height 2 :data (int-array (repeat 8 value))})
      (:width  (slurp-image file-name)) => 4
      (:height (slurp-image file-name)) => 2
      (first (:data (slurp-image file-name))) => value))

(facts "Converting unsigned byte to byte and back"
  (byte->ubyte    0) =>    0
  (byte->ubyte  127) =>  127
  (byte->ubyte -128) =>  128
  (byte->ubyte   -1) =>  255
  (ubyte->byte    0) =>    0
  (ubyte->byte  127) =>  127
  (ubyte->byte  128) => -128
  (ubyte->byte  255) =>   -1)

(facts "Reading and writing image pixels"
  (let [img {:width 4 :height 2 :data (int-array [0 0 0 0 0 0 0 (bit-or (bit-shift-left 1 16) (bit-shift-left 2 8) 3)])}]
    (get-pixel img 1 3) => (matrix [1 2 3]))
  (let [img {:width 1 :height 1 :data (int-array [(bit-or (bit-shift-left 1 16) (bit-shift-left 2 8) 3)])}]
    (get-pixel img 0 0) => (matrix [1 2 3]))
  (let [img {:width 4 :height 2 :data (int-array (repeat 8 0))}]
    (set-pixel! img 1 2 (matrix [253 254 255])) => anything
    (get-pixel img 1 2) => (matrix [253 254 255])))

(facts "Reading and writing of elevation pixels"
  (let [elevation {:width 4 :height 2 :data (short-array (range 8))}]
    (get-elevation elevation 1 2) => 6
    (set-elevation! elevation 1 2 8) => anything
    (get-elevation elevation 1 2) => 8))

(facts "Reading and writing of scale factors"
  (let [scale {:width 4 :height 2 :data (float-array (range 8))}]
    (get-scale scale 1 2) => 6.0
    (set-scale! scale 1 2 8.5) => anything
    (get-scale scale 1 2) => 8.5))

(facts "Reading and writing of water values"
  (let [water {:width 4 :height 2 :data (byte-array (range 8))}]
    (get-water water 1 2) => 6
    (set-water! water 1 2 136) => anything
    (get-water water 1 2) => 136))

(facts "Reading and writing of vectors"
  (let [vectors {:width 4 :height 2 :data (float-array (range 24))}]
    (get-vector vectors 1 2) => (matrix [20.0 19.0 18.0])
    (set-vector! vectors 1 2 (matrix [24.5 25.5 26.5])) => anything
    (get-vector vectors 1 2) => (matrix [24.5 25.5 26.5])))

(facts "Removal of entry in nested hash"
  (dissoc-in {:a 42} [:a]) => {}
  (dissoc-in {:a 42 :b 51} [:b]) => {:a 42}
  (dissoc-in {:a {:b 42 :c 20}} [:a :b]) => {:a {:c 20}})

(def context-test (atom nil))
(def-context-macro with-test-ctx
  (fn [x] (reset! context-test (if (= x 123)  42 :error)))
  (fn [x] (reset! context-test (if (= x 123) nil :error))))

(facts "Definition of context macro"
  (with-test-ctx 123 @context-test) => 42
  @context-test => nil)

(def-context-create-macro create-test-ctx (fn [] 123) 'with-test-ctx)

(facts "Definition of context creating macro"
       (create-test-ctx ctx (+ ctx @context-test)) => (+ 123 42)
       @context-test => nil)

(facts "Alignment function"
       (align-address 0 4) => 0
       (align-address 8 4) => 8
       (align-address 7 4) => 8
       (align-address 6 4) => 8
       (align-address 6 2) => 6)

(facts "Shape of nested vector"
       (dimensions [1 2 3]) => [3]
       (dimensions [[1 2 3] [4 5 6]]) => [2 3]
       (dimensions [(matrix [1 2 3]) (matrix [4 5 6])]) => [2])

(facts "Combine multiple-argument functions"
       ((comp* + vector) 1 2 3) => 6)

(facts "Pack nested floating-point vector into float array"
       (seq (pack-floats [2 3 5 7])) => [2.0 3.0 5.0 7.0]
       (seq (pack-floats [[2 3 5] [7 11 13]])) => [2.0 3.0 5.0 7.0 11.0 13.0])

(fact "Convert 4D to 2D texture by tiling"
      (convert-4d-to-2d [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]])
      => [[1 2 5 6] [3 4 7 8] [9 10 13 14] [11 12 15 16]])

(fact "Convert 2D texture with tiles to 4D array"
      (convert-2d-to-4d [[1 2 5 6] [3 4 7 8] [9 10 13 14] [11 12 15 16]])
      => [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]])
