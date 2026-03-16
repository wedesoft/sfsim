;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-cubemap
  (:require
    [clojure.math :refer (sqrt PI)]
    [fastmath.vector :refer (vec3 add mult)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.cubemap :refer :all :as cubemap]
    [sfsim.image :as image]
    [sfsim.util :as util])
  (:import
    (fastmath.vector
      Vec3)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(def face0 :sfsim.cubemap/face0)
(def face1 :sfsim.cubemap/face1)
(def face2 :sfsim.cubemap/face2)
(def face3 :sfsim.cubemap/face3)
(def face4 :sfsim.cubemap/face4)
(def face5 :sfsim.cubemap/face5)


(tabular "First face of cube"
         (fact (?c face0 ?j ?i) => ?result)
         ?c         ?j  ?i  ?result
         cube-map-z 0.0 0.0  1.0
         cube-map-x 0.0 0.0 -1.0
         cube-map-x 0.0 1.0  1.0
         cube-map-y 0.0 0.0  1.0
         cube-map-y 1.0 0.0 -1.0)


(tabular "Second face of cube"
         (fact (?c face1 ?j ?i) => ?result)
         ?c         ?j  ?i  ?result
         cube-map-y 0.0 0.0 -1.0
         cube-map-x 0.0 0.0 -1.0
         cube-map-x 0.0 1.0  1.0
         cube-map-z 0.0 0.0  1.0
         cube-map-z 1.0 0.0 -1.0)


(tabular "Third face of cube"
         (fact (?c face2 ?j ?i) => ?result)
         ?c         ?j  ?i  ?result
         cube-map-x 0.0 0.0  1.0
         cube-map-z 0.0 0.0  1.0
         cube-map-z 1.0 0.0 -1.0
         cube-map-y 0.0 0.0 -1.0
         cube-map-y 0.0 1.0  1.0)


(tabular "Fourth face of cube"
         (fact (?c face3 ?j ?i) => ?result)
         ?c         ?j  ?i  ?result
         cube-map-y 0.0 0.0  1.0
         cube-map-x 0.0 0.0  1.0
         cube-map-x 0.0 1.0 -1.0
         cube-map-z 0.0 0.0  1.0
         cube-map-z 1.0 0.0 -1.0)


(tabular "Fifth face of cube"
         (fact (?c face4 ?j ?i) => ?result)
         ?c         ?j ?i   ?result
         cube-map-x 0.0 0.0 -1.0
         cube-map-z 0.0 0.0  1.0
         cube-map-z 1.0 0.0 -1.0
         cube-map-y 0.0 0.0  1.0
         cube-map-y 0.0 1.0 -1.0)


(tabular "Sixth face of cube"
         (fact (?c face5 ?j ?i) => ?result)
         ?c         ?j  ?i  ?result
         cube-map-z 0.0 0.0 -1.0
         cube-map-x 0.0 0.0 -1.0
         cube-map-x 0.0 1.0  1.0
         cube-map-y 0.0 0.0 -1.0
         cube-map-y 1.0 0.0  1.0)


(fact "Get vector to cube face"
      (cube-map face5 0.0 0.5) => (vec3 0.0 -1.0 -1.0))


(tabular "Round trip for conversion to face coordinates and back to coordinates on cube"
         (fact (let [point  (vec3 ?x ?y ?z)
                     face   (determine-face point)
                     i      (cube-i face point)
                     j      (cube-j face point)]
                 (cube-map face j i) => (roughly-vector point 1e-6)))
         ?x   ?y   ?z
         1.0  0.2  0.4
         -1.0  0.2  0.4
         0.2  1.0  0.4
         0.2 -1.0  0.4
         0.2  0.4  1.0
         0.2  0.4 -1.0)


(facts "Test cube coordinates"
       (cube-coordinate 0 256 0   0.0) => 0.0
       (cube-coordinate 0 256 0 127.5) => 0.5
       (cube-coordinate 1 256 1 127.5) => 0.75)


(tabular "Get corners of cube map tiles"
         (fact (nth (cube-map-corners ?face ?level ?row ?column) ?idx) => (vec3 ?x ?y ?z))
         ?face ?level ?row ?column ?idx ?x   ?y   ?z
         face0 0      0    0  0   -1    1    1
         face0 0      0    0  1    1    1    1
         face0 0      0    0  2   -1   -1    1
         face0 0      0    0  3    1   -1    1
         face5 2      3    1  0   -0.5  0.5 -1.0
         face5 2      3    1  1    0.0  0.5 -1.0
         face5 2      3    1  2   -0.5  1.0 -1.0
         face5 2      3    1  3    0.0  1.0 -1.0)


(facts "Longitude of 3D point"
       (longitude (vec3 1 0 0)) => (roughly 0        1e-6)
       (longitude (vec3 0 1 0)) => (roughly (/ PI 2) 1e-6))


(facts "Latitude of 3D point"
       (latitude (vec3 0 6378000 0)) => (roughly 0        1e-6)
       (latitude (vec3 0 0 6357000)) => (roughly (/ PI 2) 1e-6)
       (latitude (vec3 6378000 0 0)) => (roughly 0        1e-6))


(tabular "Conversion from geodetic to cartesian coordinates"
         (fact
           (geodetic->cartesian ?lon ?lat ?h 6378000.0) => (roughly-vector (vec3 ?x ?y ?z) 1e-6))
         ?lon     ?lat    ?h        ?x        ?y        ?z
         0.0      0.0    0.0 6378000.0       0.0       0.0
         (/ PI 2)      0.0    0.0       0.0 6378000.0       0.0
         0.0 (/ PI 2)    0.0       0.0       0.0 6378000.0
         0.0      0.0 1000.0 6379000.0       0.0       0.0
         (/ PI 2)      0.0 1000.0       0.0 6379000.0       0.0)


(tabular "Conversion from cartesian (surface) coordinates to latitude and longitude"
         (fact
           (cartesian->geodetic (vec3 ?x ?y ?z) 6378000.0) =>
           (just (roughly ?lon 1e-6) (roughly ?lat 1e-6) (roughly ?height 1e-6)))
         ?x        ?y         ?z           ?lon         ?lat ?height
         6378000.0       0.0        0.0              0            0       0
         0.0 6378000.0        0.0       (/ PI 2)            0       0
         0.0       0.0  6378000.0              0     (/ PI 2)       0
         0.0       0.0  6379000.0              0     (/ PI 2)    1000
         0.0       0.0 -6379000.0              0 (/ (- PI) 2)    1000
         6378000.0       0.0        0.0              0            0       0
         0.0 6378000.0        0.0       (/ PI 2)            0       0
         6379000.0       0.0        0.0              0            0    1000
         6377900.0       0.0        0.0              0            0    -100)


(tabular "Project a vector onto an ellipsoid"
         (fact
           (project-onto-sphere (vec3 ?x ?y ?z) 6378000.0) => (roughly-vector (vec3 ?xp ?yp ?zp) 1e-6))
         ?x ?y ?z       ?xp       ?yp       ?zp
         1  0  0  6378000.0       0.0       0.0
         0  1  0        0.0 6378000.0       0.0
         0  0  1        0.0       0.0 6378000.0)


(fact "Project point onto cube"
      (project-onto-cube (vec3  1  0  0)) => (vec3  1    0    0)
      (project-onto-cube (vec3  2  1  1)) => (vec3  1    0.5  0.5)
      (project-onto-cube (vec3 -2  1  1)) => (vec3 -1    0.5  0.5)
      (project-onto-cube (vec3  0  1  0)) => (vec3  0    1    0)
      (project-onto-cube (vec3  1  2 -1)) => (vec3  0.5  1   -0.5)
      (project-onto-cube (vec3  1 -2 -1)) => (vec3  0.5 -1   -0.5)
      (project-onto-cube (vec3  0  0  1)) => (vec3  0    0    1)
      (project-onto-cube (vec3 -1  1  2)) => (vec3 -0.5  0.5  1)
      (project-onto-cube (vec3  1 -1 -2)) => (vec3  0.5 -0.5 -1))


(facts "x-coordinate on raster map"
       (map-x (- PI) 675 3) => 0.0
       (map-x    0.0 675 3) => (* 675 2.0 8))


(facts "y-coordinate on raster map"
       (map-y (/ PI 2) 675 3) => 0.0
       (map-y      0.0 675 3) => (* 675 8.0))


(facts "determine x-coordinates and fractions for interpolation"
       (map-pixels-x 0.0                     675 3) => [(* 675 2 8)     (inc (* 675 2 8)) 1.0 0.0]
       (map-pixels-x (- PI)                  675 3) => [0               1                 1.0 0.0]
       (map-pixels-x (- PI (/ PI (* 256 4))) 256 0) => [(dec (* 256 4)) 0                 0.5 0.5])


(facts "determine y-coordinates and fractions for interpolation"
       (map-pixels-y 0.0              675 3) => [(* 675 8)         (inc (* 675 8))   1.0 0.0]
       (map-pixels-y (- (/ PI 2))     675 3) => [(dec (* 675 2 8)) (dec (* 675 2 8)) 1.0 0.0]
       (map-pixels-y (/ PI (* 4 256)) 256 0) => [(dec 256)         256               0.5 0.5])


(facts "Offset in longitudinal direction"
       (offset-longitude (vec3 1  0 0) 0 675) => (roughly-vector (vec3 0 (/ (* 2 PI) (* 4 675)) 0) 1e-6)
       (offset-longitude (vec3 0 -1 0) 0 675) => (roughly-vector (vec3 (/ (* 2 PI) (* 4 675)) 0 0) 1e-6)
       (offset-longitude (vec3 0 -2 0) 0 675) => (roughly-vector (vec3 (/ (* 4 PI) (* 4 675)) 0 0) 1e-6)
       (offset-longitude (vec3 0 -2 0) 1 675) => (roughly-vector (vec3 (/ (* 2 PI) (* 4 675)) 0 0) 1e-6))


(facts "Offset in latitudinal direction"
       (offset-latitude (vec3 1 0 0) 0 675)     => (roughly-vector (vec3 0 0 (/ (* 2 PI) (* 4 675))) 1e-6)
       (offset-latitude (vec3 0 0 1) 0 675)     => (roughly-vector (vec3 (/ (* -2 PI) (* 4 675)) 0 0) 1e-6)
       (offset-latitude (vec3 2 0 0) 0 675)     => (roughly-vector (vec3 0 0 (/ (* 4 PI) (* 4 675))) 1e-6)
       (offset-latitude (vec3 2 0 0) 1 675)     => (roughly-vector (vec3 0 0 (/ (* 2 PI) (* 4 675))) 1e-6)
       (offset-latitude (vec3 0 -1e-8 1) 0 675) => (roughly-vector (vec3 0 (/ (* 2 PI) (* 4 675)) 0) 1e-6))


(fact "Load (and cache) map tile"
      (world-map-tile "tmp/day" 2 3 5) => :map-tile
      (provided
        (image/slurp-image "tmp/day/2/3/5.png") => :map-tile :times irrelevant
        (util/tile-path "tmp/day" 2 3 5 ".png") => "tmp/day/2/3/5.png" :times irrelevant))


(facts "Load (and cache) elevation tile"
       (with-redefs [util/slurp-shorts (fn [file-name] ({"tmp/elevation235.raw" (short-array [2 3 5 7])} file-name))
                     util/tile-path    str]
         (:sfsim.image/width (elevation-tile 2 3 5)) => 2
         (:sfsim.image/height (elevation-tile 2 3 5)) => 2
         (seq (:sfsim.image/data (elevation-tile 2 3 5))) => [2 3 5 7]))


(facts "Read pixels from world map tile"
       (with-redefs [cubemap/world-map-tile list
                     image/get-pixel        (fn [img ^long y ^long x] (list 'get-pixel img y x))]
         ((world-map-pixel "tmp/day") 240 320 5 675) => '(get-pixel ("tmp/day" 5 0 0) 240 320)
         ((world-map-pixel "tmp/day") (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => '(get-pixel ("tmp/day" 5 2 1) 240 320)))


(facts "Read pixels from elevation tile"
       (let [args (atom nil)]
         (with-redefs [cubemap/elevation-tile list
                       image/get-short        (fn [img ^long y ^long x] (reset! args (list img y x)) 42)]
           (elevation-pixel 240 320 5 675) => 42
           @args => '((5 0 0) 240 320)
           (elevation-pixel (+ (* 2 675) 240) (+ (* 1 675) 320) 5 675) => 42
           @args => '((5 2 1) 240 320))))


(fact "Interpolation of map pixels"
      (let [x-info    [0 1 0.75 0.25]
            y-info    [8 9 0.5  0.5]
            get-pixel (fn [dy dx in-level width]
                        (fact "in-level argument to get-pixel" in-level => 5)
                        (fact "width argument to get-pixel" width => 675)
                        ({[8 0] 2, [8 1] 3, [9 0] 5, [9 1] 7} [dy dx]))]
        (with-redefs [cubemap/map-pixels-x (fn [^double lon ^long size ^long level] (fact [lon size level] => [135.0 675 5]) x-info)
                      cubemap/map-pixels-y (fn [^double lat ^long size ^long level] (fact [lat size level] => [45.0 675 5]) y-info)]
          (map-interpolation 5 675 135.0 45.0 get-pixel + *) => 3.875)))


(fact "Determine center of cube map tile"
      (with-redefs [cubemap/project-onto-sphere (fn [^Vec3 p ^double radius]
                                                  (fact [p radius] => [(vec3 1.0 -0.625 -0.875) 6378000.0])
                                                  (vec3 1000 -625 -875))]
        (tile-center face2 3 7 1 6378000.0) => (vec3 1000 -625 -875)))


(fact "Getting world map day color for given longitude and latitude"
      (color-geodetic-day 5 675 135.0 45.0) => (vec3 3 5 7)
      (provided
        (cubemap/map-interpolation 5 675 135.0 45.0 world-map-pixel-day add mult) => (vec3 3 5 7)))


(fact "Getting world map night color for given longitude and latitude"
      (color-geodetic-night 5 675 135.0 45.0) => (vec3 3 5 7)
      (provided
        (cubemap/map-interpolation 5 675 135.0 45.0 world-map-pixel-night add mult) => (vec3 3 5 7)))


(fact "Getting elevation value for given longitude and latitude"
      (elevation-geodetic 5 675 135.0 45.0) => 42.0
      (provided
        (cubemap/map-interpolation 5 675 135.0 45.0 elevation-pixel + *) => 42.0))


(defn elevation-geodetic-mock-1
  ^double [^long in-level ^long width ^double lon ^double lat]
  (fact [in-level width lon lat] => [5 675 135.0 45.0])
  0.0)


(fact "Test height zero maps to zero water"
      (with-redefs [elevation-geodetic elevation-geodetic-mock-1]
        (water-geodetic 5 675 135.0 45.0) => 0))

(defn elevation-geodetic-mock-2
  ^double [^long in-level ^long width ^double lon ^double lat]
  -500.0)


(fact "Test that height -500 maps to 255"
      (with-redefs [elevation-geodetic elevation-geodetic-mock-2]
        (water-geodetic 5 675 0.0 0.0) => 255))


(defn elevation-geodetic-mock-3
  ^double [^long in-level ^long width ^double lon ^double lat]
  100.0)


(fact "Test that positive height maps to 0"
      (with-redefs [elevation-geodetic elevation-geodetic-mock-3]
        (water-geodetic 5 675 0.0 0.0) => 0))


(defn elevation-geodetic-mock-4
  ^double [^long in-level ^long width ^double lon ^double lat]
  (fact [in-level width lon lat] => [4 675 0.0 (/ (- PI) 2)])
  2777.0)


(fact "Project point onto globe"
      (with-redefs [cubemap/elevation-geodetic elevation-geodetic-mock-4]
        (project-onto-globe (vec3 0 0 -1) 4 675 6378000.0) => (roughly-vector (vec3 0 0 -6380777.0) 1e-6)))


(defn elevation-geodetic-mock
  ^double [^long in-level ^long width ^double lon ^double lat]
  -500.0)


(fact "Clip negative height (water) to zero"
      (with-redefs [cubemap/elevation-geodetic elevation-geodetic-mock]
        (project-onto-globe (vec3 1 0 0) 4 675 6378000.0) => (roughly-vector (vec3 6378000 0 0) 1e-6)))


(facts "Determine surrounding points for a location on the globe"
       (let [ps (atom [])]
         (with-redefs [cubemap/offset-longitude (fn [^Vec3 p ^long level ^long tilesize]
                                                  (fact [p level tilesize] => [(vec3 1 0 0) 7 33])
                                                  (vec3 0 0 -0.1))
                       cubemap/offset-latitude  (fn [^Vec3 p ^long level ^long tilesize]
                                                  (fact [p level tilesize] => [(vec3 1 0 0) 7 33])
                                                  (vec3 0 0.1 0))
                       cubemap/project-onto-globe (fn [^Vec3 p ^long in-level ^long width ^double radius]
                                                    (fact [in-level width radius] => [5 675 6378000.0])
                                                    (swap! ps conj p)
                                                    (mult p 2))]
           (let [pts (surrounding-points (vec3 1 0 0) 5 7 675 33 6378000.0)]
             (doseq [j [-1 0 1] i [-1 0 1]]
               (let [k (+ (* 3 (inc j)) (inc i))]
                 (vec3 2 (* 0.2 j) (* -0.2 i)) => (roughly-vector (nth pts k) 1e-6)
                 (vec3 1 (* 0.1 j) (* -0.1 i)) => (roughly-vector (nth @ps k) 1e-6)))))))


(fact "Get normal vector for point on flat part of elevation map"
      (with-redefs [cubemap/surrounding-points (fn [& args]
                                                 (fact args => [(vec3 1 0 0) 5 7 675 33 6378000.0])
                                                 (for [j [-1 0 1] i [-1 0 1]] (vec3 6378000 j (- i))))]
        (normal-for-point (vec3 1 0 0) 5 7 675 33 6378000.0) => (vec3 1 0 0)))


(fact "Get normal vector for point on elevation map sloped in longitudinal direction"
      (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (vec3 (+ 6378000 i) j (- i))))]
        (normal-for-point (vec3 1 0 0) 5 7 675 33 6378000.0) =>
        (roughly-vector (vec3 (sqrt 0.5) 0 (sqrt 0.5)) 1e-6)))


(fact "Get normal vector for point on elevation map sloped in latitudinal direction"
      (with-redefs [cubemap/surrounding-points (fn [& args] (for [j [-1 0 1] i [-1 0 1]] (vec3 (+ 6378000 j) j (- i))))]
        (normal-for-point (vec3 1 0 0) 5 7 675 33 6378000.0) =>
        (roughly-vector (vec3 (sqrt 0.5) (- (sqrt 0.5)) 0) 1e-6)))
