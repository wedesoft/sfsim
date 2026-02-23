;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-util
  (:require
    [clojure.math :refer (PI)]
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.util :refer :all])
  (:import
    (java.io
      File)
    (org.lwjgl
      BufferUtils)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Get elements of a small list"
       (first  '(2 3 5 7)) => 2
       (second '(2 3 5 7)) => 3
       (third  '(2 3 5 7)) => 5
       (fourth '(2 3 5 7)) => 7)


(fact "Load a set of bytes"
      (seq (slurp-bytes "test/clj/sfsim/fixtures/util/bytes.raw")) => [2 3 5 7])


(fact "Uncompress bytes"
      (seq (slurp-bytes-gz "test/clj/sfsim/fixtures/util/bytes.gz")) => [2 3 5 7] )


(fact "Slurp bytes from file in tar"
      (seq (with-tar tar "test/clj/sfsim/fixtures/util/bytes.tar" (slurp-bytes-tar tar "bytes.raw"))) => [2 3 5 7])


(fact "Slurp compressed bytes from file in tar"
      (seq (with-tar tar "test/clj/sfsim/fixtures/util/bytes.tar" (slurp-bytes-gz-tar tar "bytes.gz"))) => [2 3 5 7])


(fact "Load a set of short integers"
      (seq (slurp-shorts "test/clj/sfsim/fixtures/util/shorts.raw")) => [2 3 5 7])


(fact "Load a set of floating point numbers"
      (seq (slurp-floats "test/clj/sfsim/fixtures/util/floats.raw")) => [2.0 3.0 5.0 7.0])


(fact "Load a set of compressed floating point numbers"
      (seq (slurp-floats-gz "test/clj/sfsim/fixtures/util/floats.gz")) => [2.0 3.0 5.0 7.0])


(fact "Load a set of floating point numbers from a file in a tar archive"
      (seq (with-tar tar "test/clj/sfsim/fixtures/util/bytes.tar" (slurp-floats-tar tar "floats.raw"))) => [2.0 3.0 5.0 7.0])


(fact "Load a set of compressed floating point numbers from a file in a tar archive"
      (seq (with-tar tar "test/clj/sfsim/fixtures/util/bytes.tar" (slurp-floats-gz-tar tar "floats.gz"))) => [2.0 3.0 5.0 7.0])


(fact "Save a set of bytes"
      (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
        (spit-bytes file-name (byte-array [2 3 5 7])) => anything
        (seq (slurp-bytes file-name)) => [2 3 5 7]))


(fact "Compress a set of bytes"
      (let [file-name (.getPath (File/createTempFile "spit" ".gz"))]
        (spit-bytes-gz file-name (byte-array [2 3 5 7])) => anything
        (seq (slurp-bytes-gz file-name)) => [2 3 5 7]))


(fact "Save a set of short integers"
      (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
        (spit-shorts file-name (short-array [2 3 5 7])) => anything
        (seq (slurp-shorts file-name)) => [2 3 5 7]))


(fact "Save a set of floating point numbers"
      (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
        (spit-floats file-name (float-array [2.0 3.0 5.0 7.0])) => anything
        (seq (slurp-floats file-name)) => [2.0 3.0 5.0 7.0]))


(fact "Compress a set of floats"
      (let [file-name (.getPath (File/createTempFile "spit" ".gz"))]
        (spit-floats-gz file-name (float-array [2.0 3.0 5.0 7.0])) => anything
        (seq (slurp-floats-gz file-name)) => [2.0 3.0 5.0 7.0]))



(facts "Slurp bytes into a Java byte buffer"
       (let [buffer (slurp-byte-buffer "test/clj/sfsim/fixtures/util/bytes.raw")]
         (.get buffer 0) => 2
         (.get buffer 1) => 3
         (.get buffer 2) => 5
         (.get buffer 3) => 7))


(fact "Create tar file from files"
      (let [file-name (.getPath (File/createTempFile "test" ".tar"))]
        (create-tar file-name ["bytes.raw" "test/clj/sfsim/fixtures/util/bytes.raw"])
        (seq (with-tar tar file-name (slurp-bytes-tar tar "bytes.raw"))) => [2 3 5 7]))


(fact "Determine file path of map tile"
      (tile-path "world" 1 3 2 ".png") => "world/1/2/3.png")


(fact "Determine directory name of map tile"
      (tile-dir "world" 1 2) => "world/1/2")


(fact "Determine file path of cube tile"
      (cube-path "globe" :sfsim.cubemap/face5 2 3 1 ".png") => "globe/5/2/1/3.png")


(fact "Determine directory name of cube tile"
      (cube-dir "globe" :sfsim.cubemap/face5 2 1) => "globe/5/2/1")


(fact "Determine tar file containing cube tile"
      (cube-tar "globe" :sfsim.cubemap/face5 2 1) => "globe/5/2/1.tar")


(fact "Determine file name of cube tile"
      (cube-file-name 3 ".png") => "3.png")


(tabular "Sinc function"
         (fact (sinc ?x) => (roughly ?result 1e-6))
         ?x       ?result
         PI       0.0
         (/ PI 2) (/ 2 PI)
         0.0      1.0)


(facts "Square values"
       (sqr 2.0) => 4.0
       (sqr 3.0) => 9.0)


(facts "Cube values"
       (cube 1.0) => 1.0
       (cube 2.0) => 8.0
       (cube 3.0) => 27.0)


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


(facts "Clamp value between minimum and maximum"
       (clamp 0.0 -3.0 5.0) => 0.0
       (clamp 10.0 -3.0 5.0) => 5.0
       (clamp -10.0 -3.0 5.0) => -3.0)


(facts "Compute quotient and limit it"
       (limit-quot 0.0 0.0 1.0) => 0.0
       (limit-quot 4.0 2.0 1.0) => 1.0
       (limit-quot -4.0 2.0 1.0) => -1.0
       (limit-quot 1.0 2.0 1.0) => 0.5
       (limit-quot -4.0 -2.0 1.0) => 1.0)


(facts "Count dimensions of nested vector"
       (dimension-count [1 2 3] 0) => 3
       (dimension-count [[1 2 3]] 0) => 1
       (dimension-count [[1 2 3]] 1) => 3
       (dimension-count [[[1 2]]] 2) => 2)


(facts "Create octaves summing to one"
       (octaves 4 1.0) => [0.25 0.25 0.25 0.25]
       (octaves 2 0.25) => [0.8 0.2])

(facts "Fetch first element matching a predicate"
       (find-if odd? [2 4 6]) => nil
       (find-if odd? [2 3 4]) => 3
       (find-if odd? [2 4 5]) => 5)


(def destruct (atom nil))
(def sqr-cache (make-lru-cache 2 sqr (fn [x] (reset! destruct x))))


(facts "LRU cache with destructor"
       (sqr-cache 2.0) => 4.0
       (sqr-cache 3.0) => 9.0
       (sqr-cache 2.0) => 4.0
       @destruct => nil
       (sqr-cache 5.0) => 25.0
       @destruct => 9.0)


(facts "Convert byte buffer to byte array"
       (let [buf (BufferUtils/createByteBuffer 3)]
         (.put buf (byte 2))
         (.put buf (byte 3))
         (.put buf (byte 5))
         (.flip buf)
         (seq (byte-buffer->byte-array buf)) => [2 3 5]
         (seq (byte-buffer->byte-array nil)) => nil))


(facts "Convert float buffer to float array"
       (let [buf (BufferUtils/createFloatBuffer 3)]
         (.put buf 2.0)
         (.put buf 3.0)
         (.put buf 5.0)
         (.flip buf)
         (seq (float-buffer->float-array buf)) => [2.0 3.0 5.0]
         (seq (float-buffer->float-array nil)) => nil))


(facts "Threading macro ignoring updates to nil"
       (ignore-nil-> 42 x) => 42
       (ignore-nil-> 42 x (inc x)) => 43
       (ignore-nil-> 42 x (inc x) (inc x)) => 44
       (ignore-nil-> 42 x nil) => 42
       (ignore-nil-> 42 x false) => false
       (ignore-nil-> 42 x (inc (inc x))) => 44
       (ignore-nil-> 42 x (inc x) (and x nil) (inc x)) => 44
       (let [x 0] (ignore-nil-> 42 x (inc x))) => 43
       (let [x 42] (ignore-nil-> x x (inc x))) => 43
       (let [y 0] (ignore-nil-> 42 y (inc y))) => 43)
