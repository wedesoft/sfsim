(ns sfsim25.cubemap-test
  (:require [midje.sweet :refer :all]
            [clojure.test :refer :all]
            [sfsim25.vector3 :refer (->Vector3 norm) :as v]
            [sfsim25.rgb :refer (->RGB) :as r]
            [sfsim25.util :as util]
            [sfsim25.cubemap :refer :all :as cubemap])
  (:import [sfsim25.vector3 Vector3]))

(tabular
  (fact "First face of cube"
    (c? 0 j? i?) => result?)
  c?         j? i? result?
  cube-map-z 0  0   1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-y 0  0   1.0
  cube-map-y 1  0  -1.0)

(tabular
  (fact "Second face of cube"
    (c? 1 j? i?) => result?)
  c?         j? i? result?
  cube-map-y 0  0  -1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0)

(tabular
  (fact "Third face of cube"
    (c? 2 j? i?) => result?)
  c?         j? i? result?
  cube-map-x 0  0   1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0
  cube-map-y 0  0  -1.0
  cube-map-y 0  1   1.0)

(tabular
  (fact "Fourth face of cube"
    (c? 3 j? i?) => result?)
  c?         j? i? result?
  cube-map-y 0  0   1.0
  cube-map-x 0  0   1.0
  cube-map-x 0  1  -1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0)

(tabular
  (fact "Fifth face of cube"
    (c? 4 j? i?) => result?)
  c?         j? i? result?
  cube-map-x 0  0  -1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0
  cube-map-y 0  0   1.0
  cube-map-y 0  1  -1.0)

(tabular
  (fact "Sixth face of cube"
    (c? 5 j? i?) => result?)
  c?         j? i? result?
  cube-map-z 0  0  -1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-y 0  0  -1.0
  cube-map-y 1  0   1.0)

(fact "Get vector to cube face"
  (cube-map 5 0 0.5) => (->Vector3 0.0 -1.0 -1.0))

(facts "Test cube coordinates"
  (cube-coordinate 0 256 0     0) => 0.0
  (cube-coordinate 0 256 0 127.5) => 0.5
  (cube-coordinate 1 256 1 127.5) => 0.75)

(tabular
  (fact "Get corners of cube map tiles"
    (nth (cube-map-corners face? level? b? a?) idx?) => (->Vector3 x? y? z?))
  face? level? b? a? idx? x? y? z?
  0 0 0 0 0 -1    1    1
  0 0 0 0 1  1    1    1
  0 0 0 0 2 -1   -1    1
  0 0 0 0 3  1   -1    1
  5 2 3 1 0 -0.5  0.5 -1.0
  5 2 3 1 1  0.0  0.5 -1.0
  5 2 3 1 2 -0.5  1.0 -1.0
  5 2 3 1 3  0.0  1.0 -1.0)

(def pi Math/PI)

(facts "Longitude of 3D point"
  (longitude (->Vector3 1 0 0)) => (roughly 0        1e-6)
  (longitude (->Vector3 0 1 0)) => (roughly (/ pi 2) 1e-6))

(facts "Latitude of 3D point"
  (latitude (->Vector3 0 6378000 0) 6378000 6357000) => (roughly 0        1e-6)
  (latitude (->Vector3 0 0 6357000) 6378000 6357000) => (roughly (/ pi 2) 1e-6)
  (latitude (->Vector3 6378000 0 0) 6378000 6357000) => (roughly 0        1e-6))

(defn roughly-vector [y] (fn [x] (< (v/norm (v/- y x)) 1e-6)))

(tabular
  (fact "Conversion from geodetic to cartesian coordinates"
    (geodetic->cartesian lon? lat? h? 6378000.0 6357000.0) => (roughly-vector (->Vector3 x? y? z?)))
      lon?     lat?   h?        x?        y?        z?
         0        0    0 6378000.0       0.0       0.0
  (/ pi 2)        0    0       0.0 6378000.0       0.0
         0 (/ pi 2)    0       0.0       0.0 6357000.0
         0        0 1000 6379000.0       0.0       0.0
  (/ pi 2)        0 1000       0.0 6379000.0       0.0)

(tabular
  (fact "Conversion from cartesian (surface) coordinates to latitude and longitude"
    (cartesian->geodetic (->Vector3 x? y? z?) 6378000.0 6357000.0) =>
      (fn [[lon lat height]] (and ((roughly lon?) lon) ((roughly lat?) lat) ((roughly height?) height))))
         x?        y?         z?           lon?         lat? height?
  6378000.0       0.0        0.0              0            0       0
        0.0 6378000.0        0.0       (/ pi 2)            0       0
        0.0       0.0  6357000.0              0     (/ pi 2)       0
        0.0       0.0  6358000.0              0     (/ pi 2)    1000
        0.0       0.0 -6358000.0              0 (/ (- pi) 2)    1000
  6378000.0       0.0        0.0              0            0       0
        0.0 6378000.0        0.0       (/ pi 2)            0       0
  6379000.0       0.0        0.0              0            0    1000
  6377900.0       0.0        0.0              0            0    -100)

(deftest project-onto-ellipsoid-test
  (testing "Project a vector onto an ellipsoid"
    (are [xp yp zp x y z] (< (norm (v/- (->Vector3 xp yp zp) (project-onto-ellipsoid (->Vector3 x y z) 6378000.0 6357000.0))) 1e-6)
      6378000.0       0.0       0.0 1 0 0
            0.0 6378000.0       0.0 0 1 0
            0.0       0.0 6357000.0 0 0 1)))

(deftest map-x-test
  (testing "x-coordinate on raster map"
    (is (= 0.0 (map-x (- pi) 675 3)))
    (is (= (* 675 2.0 8) (map-x 0 675 3)))))

(deftest map-y-test
  (testing "y-coordinate on raster map"
    (is (= 0.0 (map-y (/ pi 2) 675 3)))
    (is (= (* 675 8.0) (map-y 0 675 3)))))

(deftest map-pixels-x-test
  (testing "determine x-coordinates and fractions for interpolation")
    (is (= [(* 675 2 8)     (inc (* 675 2 8)) 1.0 0.0] (map-pixels-x 0                       675 3)))
    (is (= [0               1                 1.0 0.0] (map-pixels-x (- pi)                  675 3)))
    (is (= [(dec (* 256 4)) 0                 0.5 0.5] (map-pixels-x (- pi (/ pi (* 256 4))) 256 0))))

(deftest map-pixels-y-test
  (testing "determine y-coordinates and fractions for interpolation"
    (is (= [(* 675 8)         (inc (* 675 8))   1.0 0.0] (map-pixels-y 0                675 3)))
    (is (= [(dec (* 675 2 8)) (dec (* 675 2 8)) 1.0 0.0] (map-pixels-y (- (/ pi 2))     675 3)))
    (is (= [(dec 256)         256               0.5 0.5] (map-pixels-y (/ pi (* 4 256)) 256 0)))))

(deftest offset-longitude-test
  (testing "Offset in longitudinal direction"
    (is (< (norm (v/- (->Vector3 0 (/ (* 2 pi) (* 4 675)) 0) (offset-longitude (->Vector3 1 0 0) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 2 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 -1 0) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 4 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 -2 0) 0 675))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* 2 pi) (* 4 675)) 0 0) (offset-longitude (->Vector3 0 -2 0) 1 675))) 1e-6))))

(deftest offset-latitude-test
  (testing "Offset in latitudinal direction"
    (is (< (norm (v/- (->Vector3 0 0 (/ (* 2 pi) (* 4 675))) (offset-latitude (->Vector3 1 0 0) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 (/ (* -2 pi) (* 4 675)) 0 0) (offset-latitude (->Vector3 0 0 1) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 0 (/ (* 4 pi) (* 4 675))) (offset-latitude (->Vector3 2 0 0) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 0 (/ (* 2 pi) (* 4 675))) (offset-latitude (->Vector3 2 0 0) 1 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 (/ (* 2 pi) (* 4 675)) 0) (offset-latitude (->Vector3 0 -1e-8 1) 0 675 1 1))) 1e-6))
    (is (< (norm (v/- (->Vector3 0 0 (/ pi (* 4 675))) (offset-latitude (->Vector3 1 0 0) 0 675 1 0.5))) 1e-6))))

(fact "Load (and cache) map tile"
  (world-map-tile 2 3 5) => :map-tile
  (provided
    (util/slurp-image "world235.png") => :map-tile
    (util/tile-path "world" 2 3 5 ".png") => "world235.png"))

(with-redefs [util/slurp-shorts (fn [file-name] ({"elevation235.raw" (short-array [2 3 5 7])} file-name))
              util/tile-path    str]
  (facts "Load (and cache) elevation tile"
    (:width (elevation-tile 2 3 5)) => 2
    (:height (elevation-tile 2 3 5)) => 2
    (seq (:data (elevation-tile 2 3 5))) => [2 3 5 7]))

(with-redefs [cubemap/world-map-tile list
              util/get-pixel         (fn [img ^long y ^long x] (list 'get-pixel img y x))]
  (facts "Read pixels from world map tile"
    (world-map-pixel 240 320 5 675) => '(get-pixel (5 0 0) 240 320)
    (world-map-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => '(get-pixel (5 2 1) 240 320)))

(let [args (atom nil)]
  (with-redefs [cubemap/elevation-tile list
                util/get-elevation     (fn [img ^long y ^long x] (reset! args (list img y x)) 42)]
    (facts "Read pixels from elevation tile"
      (elevation-pixel 240 320 5 675) => 42
      @args => '((5 0 0) 240 320)
      (elevation-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => 42
      @args => '((5 2 1) 240 320))))


(let [x-info    [0 1 0.75 0.25]
      y-info    [8 9 0.5  0.5 ]
      get-pixel (fn [dy dx in-level width]
                  (fact in-level => 5)
                  (fact width => 675)
                  ({[8 0] 2, [8 1] 3, [9 0] 5, [9 1] 7} [dy dx]))]
  (with-redefs [cubemap/map-pixels-x (fn [^double lon ^long size ^long level] (fact [lon size level] => [135.0 675 5]) x-info)
                cubemap/map-pixels-y (fn [^double lat ^long size ^long level] (fact [lat size level] => [ 45.0 675 5]) y-info)]
    (fact (map-interpolation 5 675 135.0 45.0 get-pixel + *) => 3.875)))

(deftest tile-center-test
  (testing "Determine center of cube map tile"
    (with-redefs [cubemap/project-onto-ellipsoid (fn [^Vector3 p ^double radius1 ^double radius2]
                                                   (is (= [p radius1 radius2] [(->Vector3 1.0 -0.625 -0.875) 6378000.0 6357000.0]))
                                                   (->Vector3 1000 -625 -875))]
      (is (= (tile-center 2 3 7 1 6378000.0 6357000.0) (->Vector3 1000 -625 -875))))))

(deftest color-geodetic-test
  (testing "Getting world map color for given longitude and latitude"
    (with-redefs [cubemap/map-interpolation (fn [& args]
                                              (is (= args [5 675 135.0 45.0 world-map-pixel r/+ r/*]))
                                              (->RGB 3 5 7))]
      (is (= (->RGB 3 5 7) (color-geodetic 5 675 135.0 45.0))))))

(deftest elevation-geodetic-test
  (testing "Getting elevation value for given longitude and latitude"
    (with-redefs [cubemap/map-interpolation (fn [& args]
                                              (is (= args [5 675 135.0 45.0 elevation-pixel + *]))
                                              42.0)]
      (is (= 42.0 (elevation-geodetic 5 675 135.0 45.0))))))

(deftest water-geodetic-test
  (testing "Test height zero maps to zero water"
    (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat]
                                       (is (= [in-level width lon lat] [5 675 135.0 45.0]))
                                       0)]
      (is (= (water-geodetic 5 675 135.0 45.0) 0))))
  (testing "Test that height -500 maps to 255"
    (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] -500)]
      (is (= (water-geodetic 5 675 0.0 0.0) 255))))
  (testing "Test that positive height maps to 0"
    (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] 100)]
      (is (= (water-geodetic 5 675 0.0 0.0) 0)))))

(deftest project-onto-globe-test
  (testing "Project point onto globe"
    (with-redefs [cubemap/elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat]
                                               (is (= [in-level width lon lat] [4 675 0.0 (/ (- pi) 2)]))
                                               2777.0)]
      (is (< (norm (v/- (project-onto-globe (->Vector3 0 0 -1) 4 675 6378000 6357000) (->Vector3 0 0 -6359777.0))) 1e-6))))
  (testing "Clip negative height (water) to zero"
    (with-redefs [cubemap/elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] -500)]
      (is (< (norm (v/- (project-onto-globe (->Vector3 1 0 0) 4 675 6378000 6357000) (->Vector3 6378000 0 0))) 1e-6)))))

(deftest surrounding-points-test
  (testing "Determine surrounding points for a location on the globe"
    (let [ps (atom [])]
      (with-redefs [cubemap/offset-longitude (fn [^Vector3 p ^long level ^long tilesize]
                                               (is (= [p level tilesize] [(->Vector3 1 0 0) 7 33]))
                                               (->Vector3 0 0 -0.1))
                    cubemap/offset-latitude  (fn [p level tilesize radius1 radius2]
                                               (is (= [p level tilesize radius1 radius2] [(->Vector3 1 0 0) 7 33 6378000 6357000]))
                                               (->Vector3 0 0.1 0))
                    cubemap/project-onto-globe (fn [p in-level width radius1 radius2]
                                                 (is (= [in-level width radius1 radius2] [5 675 6378000 6357000]))
                                                 (swap! ps conj p)
                                                 (->Vector3 (* 2 (:x p)) (* 2 (:y p)) (* 2 (:z p))))]
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
