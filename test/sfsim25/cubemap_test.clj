(ns sfsim25.cubemap-test
  (:require [clojure.test :refer :all]
            [sfsim25.vector3 :as v]
            [sfsim25.cubemap :refer :all]))

(deftest cube-faces-test
  (testing "First face of cube"
    (are [result c j i] (= result (c 0 j i))
       1.0 cube-map-y 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
      -1.0 cube-map-z 0 0
       1.0 cube-map-z 1 0))
  (testing "Second face of cube"
    (are [result c j i] (= result (c 1 j i))
       1.0 cube-map-z 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0))
  (testing "Third face of cube"
    (are [result c j i] (= result (c 2 j i))
       1.0 cube-map-x 0 0
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0
       1.0 cube-map-z 0 0
      -1.0 cube-map-z 0 1))
  (testing "Fourth face of cube"
    (are [result c j i] (= result (c 3 j i))
      -1.0 cube-map-z 0 0
       1.0 cube-map-x 0 0
      -1.0 cube-map-x 0 1
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0))
  (testing "Fifth face of cube"
    (are [result c j i] (= result (c 4 j i))
      -1.0 cube-map-x 0 0
       1.0 cube-map-y 0 0
      -1.0 cube-map-y 1 0
      -1.0 cube-map-z 0 0
       1.0 cube-map-z 0 1))
  (testing "Sixt face of cube"
    (are [result c j i] (= result (c 5 j i))
      -1.0 cube-map-y 0 0
      -1.0 cube-map-x 0 0
       1.0 cube-map-x 0 1
       1.0 cube-map-z 0 0
      -1.0 cube-map-z 1 0)))

(deftest cube-map-test
  (testing "Get vector to cube face"
    (is (= (v/vector3 0.0 -1.0 1.0) (cube-map 5 0 0.5)))))

(deftest cube-coordinate-test
  (testing "Test cube coordinates"
    (is (= 0.0  (cube-coordinate 0 256 0 0)))
    (is (= 0.5  (cube-coordinate 0 256 0 127.5)))
    (is (= 0.75 (cube-coordinate 1 256 1 127.5)))))

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

(deftest map-x-test
  (testing "x-coordinate on raster map"
    (is (= 0.0 (map-x pi 675 3)))
    (is (= (* 675 2.0 8) (map-x 0 675 3)))))

(deftest map-y-test
  (testing "y-coordinate on raster map"
    (is (= 0.0 (map-y (/ pi 2) 675 3)))
    (is (= (* 675 8.0) (map-y 0 675 3)))))

(deftest map-pixels-x-test
  (testing "determine x-coordinates and fractions for interpolation")
    (is (= [(* 675 2 8)     (inc (* 675 2 8)) 1.0 0.0] (map-pixels-x 0                       675 3)))
    (is (= [0               1                 1.0 0.0] (map-pixels-x (- pi)                  675 3)))
    (is (= [(dec (* 256 4)) 0                 0.5 0.5] (map-pixels-x (- (/ pi (* 256 4)) pi) 256 0))))

(deftest map-pixels-y-test
  (testing "determine y-coordinates and fractions for interpolation"
    (is (= [(* 675 8)         (inc (* 675 8))   1.0 0.0] (map-pixels-y 0                675 3)))
    (is (= [(dec (* 675 2 8)) (dec (* 675 2 8)) 1.0 0.0] (map-pixels-y (- (/ pi 2))     675 3)))
    (is (= [(dec 256)         256               0.5 0.5] (map-pixels-y (/ pi (* 4 256)) 256 0)))))

(deftest scale-point-test
  (testing "Scale point up to given size of ellipsoid"
    (is (= [0.0  80.0 0.0] (scale-point 0 2 0 100 80)))
    (is (= [100.0 0.0 0.0] (scale-point 2 0 0 100 80)))
    (is (= [0.0 0.0 100.0] (scale-point 0 0 2 100 80)))))
