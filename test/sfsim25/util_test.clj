(ns sfsim25.util-test
  (:require [clojure.test :refer :all]
            [sfsim25.rgb :refer (->RGB)]
            [sfsim25.util :refer :all])
  (:import [java.io File]))

(deftest slurp-bytes-test
  (testing "Load a set of bytes"
    (is (= [2 3 5 7] (seq (slurp-bytes "test/sfsim25/fixtures/bytes.raw"))))))

(deftest slurp-shorts-test
  (testing "Load a set of short integers"
    (is (= [2 3 5 7] (seq (slurp-shorts "test/sfsim25/fixtures/shorts.raw"))))))

(deftest slurp-floats-test
  (testing "Load a set of floating point numbers"
    (is (= [2.0 3.0 5.0 7.0] (seq (slurp-floats "test/sfsim25/fixtures/floats.raw"))))))

(deftest spit-bytes-test
  (testing "Save a set of bytes"
    (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
      (spit-bytes file-name (byte-array [2 3 5 7]))
      (is (= [2 3 5 7] (seq (slurp-bytes file-name)))))))

(deftest spit-shorts-test
  (testing "Save a set of short integers"
    (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
      (spit-shorts file-name (short-array [2 3 5 7]))
      (is (= [2 3 5 7] (seq (slurp-shorts file-name)))))))

(deftest spit-floats-test
  (testing "Save a set of floating point numbers"
    (let [file-name (.getPath (File/createTempFile "spit" ".tmp"))]
      (spit-floats file-name (float-array [2.0 3.0 5.0 7.0]))
      (is (= [2.0 3.0 5.0 7.0] (seq (slurp-floats file-name)))))))

(deftest tile-path-test
  (testing "Determine file path of map tile"
    (is (= "world/1/2/3.png" (tile-path "world" 1 3 2 ".png")))))

(deftest tile-dir-test
  (testing "Determine directory name of map tile"
    (is (= "world/1/2" (tile-dir "world" 1 2)))))

(deftest cube-path-test
  (testing "Determine file path of cube tile"
    (is (= "globe/5/2/1/3.png" (cube-path "globe" 5 2 3 1 ".png")))))

(deftest cube-dir-test
  (testing "Determine directory name of cube tile"
    (is (= "globe/5/2/1" (cube-dir "globe" 5 2 1)))))

(def pi Math/PI)

(deftest sinc-test
  (testing "Sinc function"
    (are [result x] (< (Math/abs (- result (sinc x))) 1e-6)
      0.0      pi
      (/ 2 pi) (/ pi 2)
      1.0      0.0)))

(deftest roundtrip-image-test
  (testing "Saving and loading of RGB image"
    (let [file-name (.getPath (File/createTempFile "spit" ".png"))]
      (spit-image file-name 4 2 (byte-array (take 24 (cycle [1 2 3]))))
      (let [[w h data] (slurp-image file-name)]
        (is (= 4 w))
        (is (= 2 h))
        (is (= '(1 2 3) (take 3 data)))))))

(deftest unsigned-byte-conversion-test
  (testing "Converting unsigned byte to byte and back"
    (is (= 0 (byte->ubyte 0)))
    (is (= 127 (byte->ubyte 127)))
    (is (= 128 (byte->ubyte -128)))
    (is (= 255 (byte->ubyte -1)))
    (is (= 0 (ubyte->byte 0)))
    (is (= 127 (ubyte->byte 127)))
    (is (= -128 (ubyte->byte 128)))
    (is (= -1 (ubyte->byte 255)))))

(deftest pixel-test
  (testing "Reading and writing image pixels"
    (let [img [4 2 (byte-array (range 24))]]
      (is (= (->RGB 18 19 20) (get-pixel img 1 2))))
    (let [img [1 1 (byte-array [253 254 255])]]
      (is (= (->RGB 253 254 255) (get-pixel img 0 0))))
    (let [img [4 2 (byte-array (range 24))]]
      (set-pixel! img 1 2 (->RGB 253 254 255))
      (is (= (->RGB 253 254 255) (get-pixel img 1 2))))))

(def elevation-test
  (testing "Reading and writing of elevation pixels"
    (let [elevation [4 2 (short-array (range 8))]]
      (is (= 6 (get-elevation elevation 1 2))))))
