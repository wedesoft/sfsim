(ns sfsim25.cubemap-test
  (:require [clojure.test :refer :all]
            [sfsim25.cubemap :refer :all]))

(deftest cube-face-0-test
  (testing "First face of cube"
    (are [result c j i] (= result (c 0 j i))
       1.0 cube-map-y 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
      -1.0 cube-map-z 0 0
       1.0 cube-map-z 1 0)))

(deftest cube-face-1-test
  (testing "Second face of cube"
    (are [result c j i] (= result (c 1 j i))
       1.0 cube-map-z 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0)))

(deftest cube-face-2-test
  (testing "Third face of cube"
    (are [result c j i] (= result (c 2 j i))
       1.0 cube-map-x 0 0
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0
       1.0 cube-map-z 0 0
      -1.0 cube-map-z 0 1)))

(deftest cube-face-3-test
  (testing "Fourth face of cube"
    (are [result c j i] (= result (c 3 j i))
      -1.0 cube-map-z 0 0
       1.0 cube-map-x 0 0
      -1.0 cube-map-x 0 1
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0)))

(deftest cube-face-4-test
  (testing "Fourth face of cube"
    (are [result c j i] (= result (c 4 j i))
      -1.0 cube-map-x 0 0
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0
      -1.0 cube-map-z 0 0
       1.0 cube-map-z 0 1)))

(deftest cube-face-5-test
  (testing "Fourth face of cube"
    (are [result c j i] (= result (c 5 j i))
      -1.0 cube-map-y 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
       1.0 cube-map-z 0 0
      -1.0 cube-map-z 1 0)))

(def pi Math/PI)

(deftest longitude-test
  (testing "Longitude of 3D point"
    (are [result x y z] (< (Math/abs (- result (longitude x y z))) 1e-6)
      0        1 0 0
      (/ pi 2) 0 0 1)))

(deftest latitude-test
  (testing "Latitude of 3D point"
    (are [result x y z] (< (Math/abs (- result (latitude x y z))) 1e-6)
      0        0 0 1
      (/ pi 2) 0 1 0
      (/ pi 4) 1 1 0)))
