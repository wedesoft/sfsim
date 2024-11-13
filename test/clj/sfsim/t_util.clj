(ns sfsim.t-util
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [clojure.math :refer (PI)]
            [fastmath.vector :refer (vec3 vec4)]
            [sfsim.util :refer :all])
  (:import [java.io File]))

(mi/collect! {:ns ['sfsim.util]})
(mi/instrument! {:report (pretty/thrower)})

(facts "Get elements of a small list"
       (first  '(2 3 5 7)) => 2
       (second '(2 3 5 7)) => 3
       (third  '(2 3 5 7)) => 5
       (fourth '(2 3 5 7)) => 7)

(fact "Load a set of bytes"
  (seq (slurp-bytes "test/clj/sfsim/fixtures/util/bytes.raw")) => [2 3 5 7])

(fact "Load a set of short integers"
  (seq (slurp-shorts "test/clj/sfsim/fixtures/util/shorts.raw")) => [2 3 5 7])

(fact "Load a set of floating point numbers"
  (seq (slurp-floats "test/clj/sfsim/fixtures/util/floats.raw")) => [2.0 3.0 5.0 7.0])

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

(facts "Slurp bytes into a Java byte buffer"
  (let [buffer (slurp-byte-buffer "test/clj/sfsim/fixtures/util/bytes.raw")]
    (.get buffer 0) => 2
    (.get buffer 1) => 3
    (.get buffer 2) => 5
    (.get buffer 3) => 7))

(fact "Determine file path of map tile"
  (tile-path "world" 1 3 2 ".png") => "world/1/2/3.png")

(fact "Determine directory name of map tile"
  (tile-dir "world" 1 2) => "world/1/2")

(fact "Determine file path of cube tile"
  (cube-path "globe" :sfsim.cubemap/face5 2 3 1 ".png") => "globe/5/2/1/3.png")

(fact "Determine directory name of cube tile"
  (cube-dir "globe" :sfsim.cubemap/face5 2 1) => "globe/5/2/1")

(tabular "Sinc function"
  (fact (sinc ?x) => (roughly ?result 1e-6))
  ?x       ?result
  PI       0.0
  (/ PI 2) (/ 2 PI)
  0.0      1.0)

(facts "Square values"
  (sqr 2) => 4
  (sqr 3) => 9)

(facts "Converting unsigned byte to byte and back"
  (byte->ubyte    0) =>    0
  (byte->ubyte  127) =>  127
  (byte->ubyte -128) =>  128
  (byte->ubyte   -1) =>  255
  (ubyte->byte    0) =>    0
  (ubyte->byte  127) =>  127
  (ubyte->byte  128) => -128
  (ubyte->byte  255) =>   -1)

(facts "Removal of entry in nested hash"
  (dissoc-in {:a 42} [:a]) => {}
  (dissoc-in {:a 42 :b 51} [:b]) => {:a 42}
  (dissoc-in {:a {:b 42 :c 20}} [:a :b]) => {:a {:c 20}})

(facts "Alignment function"
       (align-address 0 4) => 0
       (align-address 8 4) => 8
       (align-address 7 4) => 8
       (align-address 6 4) => 8
       (align-address 6 2) => 6)

(facts "Shape of nested vector"
       (dimensions [1 2 3]) => [3]
       (dimensions [[1 2 3] [4 5 6]]) => [2 3]
       (dimensions [(vec3 1 2 3) (vec3 4 5 6)]) => [2])

(facts "Combine multiple-argument functions"
       ((comp* + vector) 1 2 3) => 6)

(facts "Pack nested floating-point vector into float array"
       (seq (pack-floats [2 3 5 7])) => [2.0 3.0 5.0 7.0]
       (seq (pack-floats [[2 3 5] [7 11 13]])) => [2.0 3.0 5.0 7.0 11.0 13.0])

(fact "Determine size of given shape"
      (size-of-shape [2 3 5]) => 30)

(facts "Compute quotient and limit it"
       (limit-quot 0 0 1) => 0
       (limit-quot 4 2 1) => 1
       (limit-quot -4 2 1) => -1
       (limit-quot 1 2 1) => 1/2
       (limit-quot -4 -2 1) => 1)

(facts "Count dimensions of nested vector"
       (dimension-count [1 2 3] 0) => 3
       (dimension-count [[1 2 3]] 0) => 1
       (dimension-count [[1 2 3]] 1) => 3
       (dimension-count [[[1 2]]] 2) => 2)

(facts "Create octaves summing to one"
       (octaves 4 1.0) => [0.25 0.25 0.25 0.25]
       (octaves 2 0.25) => [0.8 0.2])
