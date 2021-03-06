(ns sfsim25.cubemap-test
  (:require [clojure.test :refer :all]
            [sfsim25.vector3 :refer (->Vector3 norm) :as v]
            [sfsim25.rgb :refer (->RGB) :as r]
            [sfsim25.util :as util]
            [sfsim25.cubemap :refer :all :as cubemap])
  (:import [sfsim25.vector3 Vector3]))

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
    (is (= (->Vector3 0.0 -1.0 1.0) (cube-map 5 0 0.5)))))

(deftest cube-coordinate-test
  (testing "Test cube coordinates"
    (is (= 0.0  (cube-coordinate 0 256 0 0)))
    (is (= 0.5  (cube-coordinate 0 256 0 127.5)))
    (is (= 0.75 (cube-coordinate 1 256 1 127.5)))))

(def pi Math/PI)

(deftest longitude-test
  (testing "Longitude of 3D point"
    (are [result x y z] (< (Math/abs (- result (longitude (->Vector3 x y z)))) 1e-6)
      0        1 0 0
      (/ pi 2) 0 0 1)))

(deftest latitude-test
  (testing "Latitude of 3D point"
    (are [result x y z] (< (Math/abs (- result (latitude (->Vector3 x y z)))) 1e-6)
      0        0 0 1
      (/ pi 2) 0 1 0
      (/ pi 4) 1 1 0)))

(deftest geodetic->cartesian-test
  (testing "Conversion from geodetic to cartesian coordinates"
    (are [x y z lon lat h] (< (norm (v/- (->Vector3 x y z) (geodetic->cartesian lon lat h 6378000.0 6357000.0))) 1e-6)
      6378000.0       0.0       0.0        0        0    0
            0.0       0.0 6378000.0 (/ pi 2)        0    0
            0.0 6357000.0       0.0        0 (/ pi 2)    0
      6379000.0       0.0       0.0        0        0 1000
            0.0       0.0 6379000.0 (/ pi 2)        0 1000)))

(deftest cartesian->geodetic-test
  (testing "Conversion from cartesian (surface) coordinates to latitude and longitude"
    (are [lon lat x y z] (let [result (cartesian->geodetic (->Vector3 x y z) 6378000.0 6357000.0)]
                           (and (< (Math/abs (- lon (first result))) 1e-6) (< (Math/abs (- lat (second result))))))
             0        0 6378000.0       0.0       0.0
      (/ pi 2)        0       0.0       0.0 6378000.0
             0 (/ pi 2)       0.0 6357000.0       0.0
             0        0 6379000.0       0.0       0.0
      (/ pi 2)        0       0.0       0.0 6379000.0)))

(deftest project-onto-ellipsoid-test
  (testing "Project a vector onto an ellipsoid"
    (are [xp yp zp x y z] (< (norm (v/- (->Vector3 xp yp zp) (project-onto-ellipsoid (->Vector3 x y z) 6378000.0 6357000.0))) 1e-6)
      6378000.0       0.0       0.0 1 0 0
            0.0 6357000.0       0.0 0 1 0
            0.0       0.0 6378000.0 0 0 1)))

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
    (is (= (->Vector3 0.0  80.0 0.0) (scale-point (->Vector3 0 2 0) 100 80)))
    (is (= (->Vector3 100.0 0.0 0.0) (scale-point (->Vector3 2 0 0) 100 80)))
    (is (= (->Vector3 0.0 0.0 100.0) (scale-point (->Vector3 0 0 2) 100 80)))))

(deftest offset-longitude-test
  (testing "Offset in longitudinal direction"
    (is (< (norm (v/- (->Vector3 0 0 (/ (* -2 pi) (* 4 675))) (offset-longitude (->Vector3 1 0 0) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 2 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 0 1) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 4 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 0 2) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 2 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 0 2) 1 675))) 1e-6))))

(deftest offset-latitude-test
  (testing "Offset in latitudinal direction"
    (is (< (norm (v/- (->Vector3 0 (/ (* 2 pi) (* 4 675)) 0) (offset-latitude (->Vector3 1 0 0) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* -2 pi) (* 4 675)) 0 0) (offset-latitude (->Vector3 0 1 0) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 (/ (* 4 pi) (* 4 675)) 0) (offset-latitude (->Vector3 2 0 0) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 (/ (* 2 pi) (* 4 675)) 0) (offset-latitude (->Vector3 2 0 0) 1 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 0 (/ (* -2 pi) (* 4 675))) (offset-latitude (->Vector3 0 1 1e-8) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 (/ pi (* 4 675)) 0) (offset-latitude (->Vector3 1 0 0) 0 675 1 0.5))) 1e-6))))

(deftest world-map-tile-test
  (testing "Load (and cache) map tile"
    (with-redefs [util/slurp-image list
                  util/tile-path   str]
      (is (= '("world235.png") (world-map-tile 2 3 5))))))

(deftest elevation-tile-test
  (testing "Load (and cache) elevation tile"
    (let [path (atom nil)]
      (with-redefs [util/slurp-shorts (fn [arg] (reset! path arg) (short-array [2 3 5 7]))
                    util/tile-path    str]
        (is (= 2 (first (elevation-tile 2 3 5))))
        (is (= 2 (second (elevation-tile 2 3 5))))
        (is (= [2 3 5 7] (seq (nth (elevation-tile 2 3 5) 2))))
        (is (= @path "elevation235.raw"))))))

(deftest world-map-pixel-test
  (testing "Read pixels from world map tile"
    (with-redefs [cubemap/world-map-tile list
                  util/get-pixel         (fn [img ^long y ^long x] (list 'get-pixel img y x))]
      (is (= '(get-pixel (5 0 0) 240 320) (world-map-pixel 240 320 5 675)))
      (is (= '(get-pixel (5 2 1) 240 320) (world-map-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675))))))

(deftest elevation-pixel-test
  (testing "Read pixels from elevation tile"
    (let [args (atom nil)]
      (with-redefs [cubemap/elevation-tile list
                    util/get-elevation     (fn ^long [img ^long y ^long x] (reset! args (list img y x)) 42)]
        (is (= 42 (elevation-pixel 240 320 5 675)))
        (is (= '((5 0 0) 240 320) @args))
        (is (= (elevation-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) 42))
        (is (= '((5 2 1) 240 320) @args))))))

(deftest interpolate-map-test
  (testing "Interpolation of map pixels"
    (let [x-info    [0 1 0.75 0.25]
          y-info    [8 9 0.5  0.5 ]
          get-pixel (fn [dy dx in-level width]
                      (is (= in-level 5))
                      (is (= width 675))
                      ({[8 0] 2, [8 1] 3, [9 0] 5, [9 1] 7} [dy dx]))]
      (with-redefs [cubemap/longitude (fn ^double [^Vector3 p] (is (= p (->Vector3 2 3 5))) 135.0)
                    cubemap/latitude  (fn ^double [^Vector3 p] (is (= p (->Vector3 2 3 5)))  45.0)
                    cubemap/map-pixels-x (fn [^double lon ^long size ^long level] (is (= [lon size level] [135.0 675 5])) x-info)
                    cubemap/map-pixels-y (fn [^double lat ^long size ^long level] (is (= [lat size level] [ 45.0 675 5])) y-info)]
        (is (= 3.875 (interpolate-map 5 675 (->Vector3 2 3 5) get-pixel + *)))))))

(deftest tile-center-test
  (testing "Determine center of cube map tile"
    (with-redefs [cubemap/scale-point (fn [^Vector3 p ^double radius1 ^double radius2]
                                        (is (= [p radius1 radius2] [(->Vector3 1.0 -0.875 0.625) 6378000.0 6357000.0]))
                                        (->Vector3 1000 -875 625))]
      (is (= (tile-center 2 3 7 1 6378000.0 6357000.0) (->Vector3 1000 -875 625))))))

(deftest color-for-point-test
  (testing "Getting world map color for a 3D point"
    (with-redefs [cubemap/interpolate-map (fn [& args]
                                            (is (= args [5 675 (->Vector3 2 3 5) world-map-pixel r/+ r/*]))
                                            (->RGB 3 5 7))]
      (is (= (->RGB 3 5 7) (color-for-point 5 675 (->Vector3 2 3 5)))))))

(deftest elevation-for-point-test
  (testing "Getting elevation value for a 3D point"
    (with-redefs [cubemap/interpolate-map (fn [& args]
                                            (is (= args [5 675 (->Vector3 2 3 5) elevation-pixel + *]))
                                            42.0)]
      (is (= 42.0 (elevation-for-point 5 675 (->Vector3 2 3 5)))))))

(deftest elevated-point-test
  (testing "Compute point with elevation at equator"
    (with-redefs [cubemap/elevation-for-point (fn [^long in-level ^long width ^Vector3 point]
                                                 (is (= [in-level width point] [5 675 (->Vector3 1 0 0)]))
                                                 123.0)]
      (is (= (->Vector3 6378123.0 0.0 0.0) (elevated-point 5 675 (->Vector3 1 0 0) 6378000.0 6357000.0)))))
  (testing "Compute point with elevation at pole"
    (with-redefs [cubemap/elevation-for-point (fn [^long in-level ^long width ^Vector3 point] 123.0)]
      (is (= (->Vector3 0.0 6357123.0 0.0) (elevated-point 5 675 (->Vector3 0 1 0) 6378000.0 6357000.0)))))
  (testing "Clip height to at least zero at equator"
    (with-redefs [cubemap/elevation-for-point (fn [^long in-level ^long width ^Vector3 point] -123.0)]
      (is (= (->Vector3 6378000.0 0.0 0.0) (elevated-point 5 675 (->Vector3 1 0 0) 6378000.0 6357000.0)))))
  (testing "Clip height to at least zero at pole"
    (with-redefs [cubemap/elevation-for-point (fn [^long in-level ^long width ^Vector3 point] -123.0)]
      (is (= (->Vector3 0.0 6357000 0.0) (elevated-point 5 675 (->Vector3 0 1 0) 6378000.0 6357000.0))))))

(deftest surrounding-points-test
  (testing "Determine surrounding points for a location on the globe"
    (let [ps (atom [])]
      (with-redefs [cubemap/offset-longitude (fn [^Vector3 p ^long level ^long tilesize]
                                               (is (= [p level tilesize] [(->Vector3 1 0 0) 7 33]))
                                               (->Vector3 0 0 -0.1))
                    cubemap/offset-latitude  (fn [p level tilesize radius1 radius2]
                                               (is (= [p level tilesize radius1 radius2] [(->Vector3 1 0 0) 7 33 6378000 6357000]))
                                               (->Vector3 0 0.1 0))
                    cubemap/elevated-point (fn [in-level width p radius1 radius2]
                                             (is (= [in-level width radius1 radius2] [5 675 6378000 6357000]))
                                             (swap! ps conj p)
                                             (->Vector3 (* 2 (.x p)) (* 2 (.y p)) (* 2 (.z p))))]
        (let [pts (surrounding-points (->Vector3 1 0 0) 5 7 675 33 6378000 6357000)]
          (doseq [j [-1 0 1] i [-1 0 1]]
            (let [k (+ (* 3 (inc j)) (inc i))]
              (is (= (->Vector3 2 (* 0.2 j) (* -0.2 i)) (nth pts k)))
              (is (= (->Vector3 1 (* 0.1 j) (* -0.1 i)) (nth @ps k))))))))))

(deftest normal-for-point-test
  (testing "Get normal vector for point on flat part of elevation map"
    (with-redefs [cubemap/surrounding-points (fn [& args]
                                               (is (= args [(->Vector3 1 0 0) 5 7 675 33 6378000 6357000]))
                                               (for [j [-1 0 1] i [-1 0 1]] (->Vector3 6378000 j (- i))))]
      (is (< (norm (v/- (->Vector3 1 0 0) (normal-for-point (->Vector3 1 0 0) 5 7 675 33 6378000 6357000))) 1e-6))))
  (testing "Get normal vector for point on elevation map sloped in longitudinal direction"
    (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (->Vector3 (+ 6378000 i) j (- i))))]
      (is (< (norm (v/- (->Vector3 (Math/sqrt 0.5) 0 (Math/sqrt 0.5))
                        (normal-for-point (->Vector3 1 0 0) 5 7 675 33 6378000 6357000))) 1e-6))))
  (testing "Get normal vector for point on elevation map sloped in latitudinal direction"
    (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (->Vector3 (+ 6378000 j) j (- i))))]
      (is (< (norm (v/- (->Vector3 (Math/sqrt 0.5) (- (Math/sqrt 0.5)) 0)
                        (normal-for-point (->Vector3 1 0 0) 5 7 675 33 6378000 6357000))) 1e-6)))))

(deftest water-for-point-test
  (testing "Test height zero maps to zero water"
    (with-redefs [elevation-for-point (fn [^long in-level ^long width ^Vector3 point]
                                        (is (= [in-level width point] [5 675 (->Vector3 1 0 0)]))
                                        0)]
      (is (= (water-for-point 5 675 (->Vector3 1 0 0)) 0))))
  (testing "Test that height -500 maps to 255"
    (with-redefs [elevation-for-point (fn [^long in-level ^long width ^Vector3 point] -500)]
      (is (= (water-for-point 5 675 (->Vector3 1 0 0)) 255))))
  (testing "Test that positive height maps to 0"
    (with-redefs [elevation-for-point (fn [^long in-level ^long width ^Vector3 point] 100)]
      (is (= (water-for-point 5 675 (->Vector3 1 0 0)) 0)))))
