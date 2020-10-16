(ns sfsim25.util-test
  (:require [clojure.test :refer :all]
            [sfsim25.util :refer :all])
  (:import [java.io File]))

(deftest slurp-bytes-test
  (testing "Load a set of bytes"
    (is (= [2 3 5 7] (seq (slurp-bytes "test/sfsim25/fixtures/bytes.raw"))))))

(deftest slurp-shorts-test
  (testing "Load a set of short integers"
    (is (= [2 3 5 7] (seq (slurp-shorts "test/sfsim25/fixtures/shorts.raw"))))))

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

(deftest tile-path-test
  (testing "Determine file path of map tile"
    (is (= "world/1/2/3.png" (tile-path "world" 1 3 2 ".png")))))

(deftest tile-dir-test
  (testing "Determine directory name of map tile"
    (is (= "world/1/2" (tile-dir "world" 1 2)))))

(def pi Math/PI)

(deftest sinc-test
  (testing "Sinc function"
    (are [result x] (< (Math/abs (- result (sinc x))) 1e-6)
      0.0      pi
      (/ 2 pi) (/ pi 2)
      1.0      0.0)))

(deftest cache-create-test
  (testing "Creating an empty LRU cache"
    (is (= {:stack [] :data {} :size 10} (cache-create 10)))))

(deftest cache-update-test
  (testing "Add elements to an LRU cache"
    (is (= {:stack [:a] :data {:a 42} :size 10} (cache-update (cache-create 10) :a 42)))
    (is (= {:stack [:a :b] :data {:a 2 :b 3} :size 10} (cache-update (cache-update (cache-create 10) :a 2) :b 3)))
    (is (= {:stack [:b] :data {:b 3} :size 1} (cache-update (cache-update (cache-create 1) :a 2) :b 3)))))

(deftest cache-test
  (testing "Wrap function with LRU cache"
    (let [f (cache identity)]
      (is (= [1 2 1] (map f [1 2 1]))))))

(deftest cache-multi-arg-test
  (testing "Wrap multiple argument function with LRU cache"
    (is (= 3 ((cache +) 1 2)))))
