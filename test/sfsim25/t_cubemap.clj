(ns sfsim25.t-cubemap
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.rgb :refer (->RGB) :as r]
            [sfsim25.util :as util]
            [sfsim25.cubemap :refer :all :as cubemap])
  (:import [mikera.vectorz Vector]))

(tabular "First face of cube"
  (fact (?c 0 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-z 0  0   1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-y 0  0   1.0
  cube-map-y 1  0  -1.0)

(tabular "Second face of cube"
  (fact (?c 1 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-y 0  0  -1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0)

(tabular "Third face of cube"
  (fact (?c 2 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-x 0  0   1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0
  cube-map-y 0  0  -1.0
  cube-map-y 0  1   1.0)

(tabular "Fourth face of cube"
  (fact (?c 3 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-y 0  0   1.0
  cube-map-x 0  0   1.0
  cube-map-x 0  1  -1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0)

(tabular "Fifth face of cube"
  (fact (?c 4 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-x 0  0  -1.0
  cube-map-z 0  0   1.0
  cube-map-z 1  0  -1.0
  cube-map-y 0  0   1.0
  cube-map-y 0  1  -1.0)

(tabular "Sixth face of cube"
  (fact (?c 5 ?j ?i) => ?result)
  ?c         ?j ?i ?result
  cube-map-z 0  0  -1.0
  cube-map-x 0  0  -1.0
  cube-map-x 0  1   1.0
  cube-map-y 0  0  -1.0
  cube-map-y 1  0   1.0)

(fact "Get vector to cube face"
  (cube-map 5 0 0.5) => (matrix [0.0 -1.0 -1.0]))

(facts "Test cube coordinates"
  (cube-coordinate 0 256 0     0) => 0.0
  (cube-coordinate 0 256 0 127.5) => 0.5
  (cube-coordinate 1 256 1 127.5) => 0.75)

(tabular "Get corners of cube map tiles"
  (fact (nth (cube-map-corners ?face ?level ?b ?a) ?idx) => (matrix [?x ?y ?z]))
  ?face ?level ?b ?a ?idx ?x ?y ?z
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
  (longitude (matrix [1 0 0])) => (roughly 0        1e-6)
  (longitude (matrix [0 1 0])) => (roughly (/ pi 2) 1e-6))

(facts "Latitude of 3D point"
  (latitude (matrix [0 6378000 0]) 6378000 6357000) => (roughly 0        1e-6)
  (latitude (matrix [0 0 6357000]) 6378000 6357000) => (roughly (/ pi 2) 1e-6)
  (latitude (matrix [6378000 0 0]) 6378000 6357000) => (roughly 0        1e-6))

(defn roughly-vector [y error] (fn [x] (<= (norm (sub y x)) error)))

(tabular "Conversion from geodetic to cartesian coordinates"
  (fact
    (geodetic->cartesian ?lon ?lat ?h 6378000.0 6357000.0) => (roughly-vector (matrix [?x ?y ?z]) 1e-6))
      ?lon     ?lat   ?h        ?x        ?y        ?z
         0        0    0 6378000.0       0.0       0.0
  (/ pi 2)        0    0       0.0 6378000.0       0.0
         0 (/ pi 2)    0       0.0       0.0 6357000.0
         0        0 1000 6379000.0       0.0       0.0
  (/ pi 2)        0 1000       0.0 6379000.0       0.0)

(tabular "Conversion from cartesian (surface) coordinates to latitude and longitude"
  (fact
    (cartesian->geodetic (matrix [?x ?y ?z]) 6378000.0 6357000.0) =>
      (just (roughly ?lon 1e-6) (roughly ?lat 1e-6) (roughly ?height 1e-6)))
         ?x        ?y         ?z           ?lon         ?lat ?height
  6378000.0       0.0        0.0              0            0       0
        0.0 6378000.0        0.0       (/ pi 2)            0       0
        0.0       0.0  6357000.0              0     (/ pi 2)       0
        0.0       0.0  6358000.0              0     (/ pi 2)    1000
        0.0       0.0 -6358000.0              0 (/ (- pi) 2)    1000
  6378000.0       0.0        0.0              0            0       0
        0.0 6378000.0        0.0       (/ pi 2)            0       0
  6379000.0       0.0        0.0              0            0    1000
  6377900.0       0.0        0.0              0            0    -100)

(tabular "Project a vector onto an ellipsoid"
  (fact
    (project-onto-ellipsoid (matrix [?x ?y ?z]) 6378000.0 6357000.0) => (roughly-vector (matrix [?xp ?yp ?zp]) 1e-6))
   ?x ?y ?z       ?xp       ?yp       ?zp
   1  0  0  6378000.0       0.0       0.0
   0  1  0        0.0 6378000.0       0.0
   0  0  1        0.0       0.0 6357000.0)

(facts "x-coordinate on raster map"
  (map-x (- pi) 675 3) => 0.0
  (map-x 0 675 3) => (* 675 2.0 8))

(facts "y-coordinate on raster map"
  (map-y (/ pi 2) 675 3) => 0.0
  (map-y 0 675 3) => (* 675 8.0))

(facts "determine x-coordinates and fractions for interpolation"
  (map-pixels-x 0                       675 3) => [(* 675 2 8)     (inc (* 675 2 8)) 1.0 0.0]
  (map-pixels-x (- pi)                  675 3) => [0               1                 1.0 0.0]
  (map-pixels-x (- pi (/ pi (* 256 4))) 256 0) => [(dec (* 256 4)) 0                 0.5 0.5])

(facts "determine y-coordinates and fractions for interpolation"
  (map-pixels-y 0                675 3) => [(* 675 8)         (inc (* 675 8))   1.0 0.0]
  (map-pixels-y (- (/ pi 2))     675 3) => [(dec (* 675 2 8)) (dec (* 675 2 8)) 1.0 0.0]
  (map-pixels-y (/ pi (* 4 256)) 256 0) => [(dec 256)         256               0.5 0.5])

(facts "Offset in longitudinal direction"
  (offset-longitude (matrix [1  0 0]) 0 675) => (roughly-vector (matrix [0 (/ (* 2 pi) (* 4 675)) 0]) 1e-6)
  (offset-longitude (matrix [0 -1 0]) 0 675) => (roughly-vector (matrix [(/ (* 2 pi) (* 4 675)) 0 0]) 1e-6)
  (offset-longitude (matrix [0 -2 0]) 0 675) => (roughly-vector (matrix [(/ (* 4 pi) (* 4 675)) 0 0]) 1e-6)
  (offset-longitude (matrix [0 -2 0]) 1 675) => (roughly-vector (matrix [(/ (* 2 pi) (* 4 675)) 0 0]) 1e-6))

(facts "Offset in latitudinal direction"
  (offset-latitude (matrix [1 0 0]) 0 675 1 1)     => (roughly-vector (matrix [0 0 (/ (* 2 pi) (* 4 675))]) 1e-6)
  (offset-latitude (matrix [0 0 1]) 0 675 1 1)     => (roughly-vector (matrix [(/ (* -2 pi) (* 4 675)) 0 0]) 1e-6)
  (offset-latitude (matrix [2 0 0]) 0 675 1 1)     => (roughly-vector (matrix [0 0 (/ (* 4 pi) (* 4 675))]) 1e-6)
  (offset-latitude (matrix [2 0 0]) 1 675 1 1)     => (roughly-vector (matrix [0 0 (/ (* 2 pi) (* 4 675))]) 1e-6)
  (offset-latitude (matrix [0 -1e-8 1]) 0 675 1 1) => (roughly-vector (matrix [0 (/ (* 2 pi) (* 4 675)) 0]) 1e-6)
  (offset-latitude (matrix [1 0 0]) 0 675 1 0.5)   => (roughly-vector (matrix [0 0 (/ pi (* 4 675))]) 1e-6))

(fact "Load (and cache) map tile"
  (world-map-tile 2 3 5) => :map-tile
    (provided
      (util/slurp-image "world235.png") => :map-tile :times irrelevant
      (util/tile-path "world" 2 3 5 ".png") => "world235.png" :times irrelevant))

(facts "Load (and cache) elevation tile"
  (with-redefs [util/slurp-shorts (fn [file-name] ({"elevation235.raw" (short-array [2 3 5 7])} file-name))
                util/tile-path    str]
    (:width (elevation-tile 2 3 5)) => 2
    (:height (elevation-tile 2 3 5)) => 2
    (seq (:data (elevation-tile 2 3 5))) => [2 3 5 7]))

(facts "Read pixels from world map tile"
  (with-redefs [cubemap/world-map-tile list
                util/get-pixel         (fn [img ^long y ^long x] (list 'get-pixel img y x))]
    (world-map-pixel 240 320 5 675) => '(get-pixel (5 0 0) 240 320)
    (world-map-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => '(get-pixel (5 2 1) 240 320)))

(facts "Read pixels from elevation tile"
  (let [args (atom nil)]
    (with-redefs [cubemap/elevation-tile list
                  util/get-elevation     (fn [img ^long y ^long x] (reset! args (list img y x)) 42)]
      (elevation-pixel 240 320 5 675) => 42
      @args => '((5 0 0) 240 320)
      (elevation-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => 42
      @args => '((5 2 1) 240 320))))

(fact "Interpolation of map pixels"
  (let [x-info    [0 1 0.75 0.25]
        y-info    [8 9 0.5  0.5 ]
        get-pixel (fn [dy dx in-level width]
                    (fact "in-level argument to get-pixel" in-level => 5)
                    (fact "width argument to get-pixel" width => 675)
                    ({[8 0] 2, [8 1] 3, [9 0] 5, [9 1] 7} [dy dx]))]
    (with-redefs [cubemap/map-pixels-x (fn [^double lon ^long size ^long level] (fact [lon size level] => [135.0 675 5]) x-info)
                  cubemap/map-pixels-y (fn [^double lat ^long size ^long level] (fact [lat size level] => [ 45.0 675 5]) y-info)]
      (map-interpolation 5 675 135.0 45.0 get-pixel + *) => 3.875)))

(fact "Determine center of cube map tile"
  (with-redefs [cubemap/project-onto-ellipsoid (fn [^Vector p ^double radius1 ^double radius2]
                                                 (fact [p radius1 radius2] => [(matrix [1.0 -0.625 -0.875]) 6378000.0 6357000.0])
                                                 (matrix [1000 -625 -875]))]
    (tile-center 2 3 7 1 6378000.0 6357000.0) => (matrix [1000 -625 -875])))

(fact "Getting world map color for given longitude and latitude"
  (color-geodetic 5 675 135.0 45.0) => (->RGB 3 5 7)
    (provided
      (cubemap/map-interpolation 5 675 135.0 45.0 world-map-pixel r/+ r/*) => (->RGB 3 5 7)))

(fact "Getting elevation value for given longitude and latitude"
  (elevation-geodetic 5 675 135.0 45.0) => 42.0
    (provided
      (cubemap/map-interpolation 5 675 135.0 45.0 elevation-pixel + *) => 42.0))

(fact "Test height zero maps to zero water"
  (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat]
                                     (fact [in-level width lon lat] => [5 675 135.0 45.0])
                                     0)]
    (water-geodetic 5 675 135.0 45.0) => 0))

(fact "Test that height -500 maps to 255"
  (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] -500)]
    (water-geodetic 5 675 0.0 0.0) => 255))

(fact "Test that positive height maps to 0"
  (with-redefs [elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] 100)]
    (water-geodetic 5 675 0.0 0.0) => 0))

(fact "Project point onto globe"
  (with-redefs [cubemap/elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat]
                                             (fact [in-level width lon lat] => [4 675 0.0 (/ (- pi) 2)])
                                             2777.0)]
    (project-onto-globe (matrix [0 0 -1]) 4 675 6378000 6357000) => (roughly-vector (matrix [0 0 -6359777.0]) 1e-6)))

(fact "Clip negative height (water) to zero"
  (with-redefs [cubemap/elevation-geodetic (fn [^long in-level ^long width ^double lon ^double lat] -500)]
    (project-onto-globe (matrix [1 0 0]) 4 675 6378000 6357000) => (roughly-vector (matrix [6378000 0 0]) 1e-6)))

(facts "Determine surrounding points for a location on the globe"
  (let [ps (atom [])]
    (with-redefs [cubemap/offset-longitude (fn [^Vector p ^long level ^long tilesize]
                                             (fact [p level tilesize] => [(matrix [1 0 0]) 7 33])
                                             (matrix [0 0 -0.1]))
                  cubemap/offset-latitude  (fn [p level tilesize radius1 radius2]
                                             (fact [p level tilesize radius1 radius2] => [(matrix [1 0 0]) 7 33 6378000 6357000])
                                             (matrix [0 0.1 0]))
                  cubemap/project-onto-globe (fn [p in-level width radius1 radius2]
                                               (fact [in-level width radius1 radius2] => [5 675 6378000 6357000])
                                               (swap! ps conj p)
                                               (mul 2 p))]
      (let [pts (surrounding-points (matrix [1 0 0]) 5 7 675 33 6378000 6357000)]
        (doseq [j [-1 0 1] i [-1 0 1]]
          (let [k (+ (* 3 (inc j)) (inc i))]
            (matrix [2 (* 0.2 j) (* -0.2 i)]) => (roughly-vector (nth pts k) 1e-6)
            (matrix [1 (* 0.1 j) (* -0.1 i)]) => (roughly-vector (nth @ps k) 1e-6)))))))

(fact "Get normal vector for point on flat part of elevation map"
  (with-redefs [cubemap/surrounding-points (fn [& args]
                                             (fact args => [(matrix [1 0 0]) 5 7 675 33 6378000 6357000])
                                             (for [j [-1 0 1] i [-1 0 1]] (matrix [6378000 j (- i)])))]
    (normal-for-point (matrix [1 0 0]) 5 7 675 33 6378000 6357000) => (matrix [1 0 0])))

(fact "Get normal vector for point on elevation map sloped in longitudinal direction"
  (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (matrix [(+ 6378000 i) j (- i)])))]
    (normal-for-point (matrix [1 0 0]) 5 7 675 33 6378000 6357000) =>
    (roughly-vector (matrix [(Math/sqrt 0.5) 0 (Math/sqrt 0.5)]) 1e-6)))

(fact "Get normal vector for point on elevation map sloped in latitudinal direction"
  (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (matrix [(+ 6378000 j) j (- i)])))]
    (normal-for-point (matrix [1 0 0]) 5 7 675 33 6378000 6357000) =>
    (roughly-vector (matrix [(Math/sqrt 0.5) (- (Math/sqrt 0.5)) 0]) 1e-6)))
