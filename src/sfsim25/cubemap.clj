(ns sfsim25.cubemap
  "Conversions from cube coordinates (face, j, i) to geodetic coordinates (longitude, latitude)."
  (:require [clojure.core.memoize :as z]
            [fastmath.vector :refer (vec3 add sub mult dot cross mag normalize)]
            [fastmath.matrix :refer (mulv)]
            [clojure.math :refer (cos sin sqrt floor atan2 round PI)]
            [sfsim25.matrix :refer :all]
            [sfsim25.util :refer (tile-path slurp-image slurp-shorts get-pixel get-short sqr)])
  (:import [fastmath.vector Vec3]))

(set! *unchecked-math* true)

(defn cube-map-x
  "x-coordinate of point on cube face"
  ^double [^long face ^double j ^double i]
  (case face
    0 (+ -1 (* 2 i))
    1 (+ -1 (* 2 i))
    2  1
    3 (-  1 (* 2 i))
    4 -1
    5 (+ -1 (* 2 i))))

(defn cube-map-y
  "y-coordinate of point on cube face"
  ^double [^long face ^double j ^double i]
  (case face
    0 (-  1 (* 2 j))
    1 -1
    2 (+ -1 (* 2 i))
    3  1
    4 (-  1 (* 2 i))
    5 (+ -1 (* 2 j))))

(defn cube-map-z
  "y-coordinate of point on cube face"
  ^double [^long face ^double j ^double i]
  (case face
    0  1
    1 (- 1 (* 2 j))
    2 (- 1 (* 2 j))
    3 (- 1 (* 2 j))
    4 (- 1 (* 2 j))
    5 -1))

(defn cube-map
  "Get 3D vector to point on cube face"
  ^Vec3 [^long face ^double j ^double i]
  (vec3 (cube-map-x face j i) (cube-map-y face j i) (cube-map-z face j i)))

(defn cube-coordinate
  "Determine coordinate of a pixel on a tile of a given level"
  ^double [^long level ^long tilesize ^long tile ^double pixel]
  (let [tiles (bit-shift-left 1 level)]
    (/ (+ tile (double (/ pixel (dec tilesize)))) tiles)))

(defn cube-map-corners
  "Get 3D vectors to corners of cube map tile"
  [^long face ^long level ^long b ^long a]
  [(cube-map face (cube-coordinate level 2 b 0) (cube-coordinate level 2 a 0))
   (cube-map face (cube-coordinate level 2 b 0) (cube-coordinate level 2 a 1))
   (cube-map face (cube-coordinate level 2 b 1) (cube-coordinate level 2 a 0))
   (cube-map face (cube-coordinate level 2 b 1) (cube-coordinate level 2 a 1))])

(defn longitude
  "Longitude of 3D point (East is positive)"
  ^double [^Vec3 p]
  (atan2 (p 1) (p 0)))

(defn latitude
  "Latitude of 3D point"
  ^double [^Vec3 point ^double radius1 ^double radius2]
  (let [e (/ (sqrt (- (sqr radius1) (sqr radius2))) radius1)
        p (sqrt (+ (sqr (point 0)) (sqr (point 1))))]
    (atan2 (point 2) (* p (- 1.0 (* e e))))))

; https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
(defn geodetic->cartesian
  "Convert latitude and longitude to cartesian coordinates"
  [longitude latitude height radius1 radius2]
  (let [radius1-sqr     (sqr radius1)
        radius2-sqr     (sqr radius2)
        cos-lat         (cos latitude)
        sin-lat         (sin latitude)
        vertical-radius (/ radius1-sqr (sqrt (+ (* radius1-sqr cos-lat cos-lat) (* radius2-sqr sin-lat sin-lat))))]
    (vec3 (* (+ vertical-radius height) cos-lat (cos longitude))
          (* (+ vertical-radius height) cos-lat (sin longitude))
          (* (+ (* (/ radius2-sqr radius1-sqr) vertical-radius) height) sin-lat))))

(defn project-onto-ellipsoid
  "Project a 3D vector onto an ellipsoid"
  ^Vec3 [^Vec3 point ^double radius1 ^double radius2]
  (let [radius           (mag point)
        xy-radius        (sqrt (+ (sqr (point 0)) (sqr (point 1))))
        cos-latitude     (/ xy-radius radius)
        sin-latitude     (/ (point 2) radius)
        cos-longitude    (if (zero? xy-radius) 1.0 (/ (point 0) xy-radius))
        sin-longitude    (if (zero? xy-radius) 0.0 (/ (point 1) xy-radius))
        projected-radius (/ (* radius1 radius2) (sqrt (+ (* radius1 radius1 sin-latitude sin-latitude)
                                                         (* radius2 radius2 cos-latitude cos-latitude))))]
    (vec3 (* cos-latitude cos-longitude projected-radius)
          (* cos-latitude sin-longitude projected-radius)
          (* sin-latitude projected-radius))))

(defn ellipsoid-normal
  "Get normal vector for point on ellipsoid's surface"
  ^Vec3 [^Vec3 point ^double radius1 ^double radius2]
  (let [radius1-sqr (sqr radius1)
        radius2-sqr (sqr radius2)
        x           (/ (point 0) radius1-sqr)
        y           (/ (point 1) radius1-sqr)
        z           (/ (point 2) radius2-sqr)]
    (normalize (vec3 x y z))))

(defn cartesian->geodetic
  "Convert cartesian coordinates to latitude, longitude and height"
  [^Vec3 point ^double radius1 ^double radius2]
  (let [e         (/ (sqrt (- (sqr radius1) (sqr radius2))) radius1)
        lon       (atan2 (point 1) (point 0))
        iteration (fn [^Vec3 reference-point iter]
                    (let [surface-point (project-onto-ellipsoid reference-point radius1 radius2)
                          normal        (ellipsoid-normal surface-point radius1 radius2)
                          height        (dot normal (sub point surface-point))
                          p             (sqrt (+ (sqr (surface-point 0)) (sqr (surface-point 1))))
                          lat           (atan2 (surface-point 2) (* p (- 1.0 (* e e))))
                          result        (add surface-point (mult normal height))
                          error         (sub result point)]
                      (if (or (< (mag error) 1e-6) (>= iter 10)) [lon lat height] (recur (sub surface-point error) (inc iter)))))]
    (iteration point 0)))

(defn map-x
  "Compute x-coordinate on raster map"
  ^double [^double longitude ^long tilesize ^long level]
  (let [n (bit-shift-left 1 level)]
    (* (+ PI longitude) (/ (* 4 n tilesize) (* 2 PI)))))

(defn map-y
  "Compute y-coordinate on raster map"
  ^double [^double latitude ^long tilesize ^long level]
  (let [n (bit-shift-left 1 level)]
    (* (- (/ PI 2) latitude) (/ (* 2 n tilesize) PI))))

(defn map-pixels-x
  "Determine x-coordinates and fractions for interpolation"
  [^double longitude ^long tilesize ^long level]
  (let [n     (bit-shift-left 1 level)
        size  (* 4 n tilesize)
        x     (map-x longitude tilesize level)
        x0    (int (floor x))
        x1    (inc x0)
        frac1 (- x x0)
        frac0 (- 1 frac1)]
    [(mod x0 size) (mod x1 size) frac0 frac1]))

(defn map-pixels-y
  "Determine y-coordinates and fractions for interpolation"
  [^double latitude ^long tilesize ^long level]
  (let [n    (bit-shift-left 1 level)
        size (* 2 n tilesize)
        y    (map-y latitude tilesize level)
        y0   (int (floor y))
        y1   (inc y0)
        frac1 (- y y0)
        frac0 (- 1 frac1)]
    [(min y0 (dec size)) (min y1 (dec size)) frac0 frac1]))

(defn offset-longitude
  "Determine longitudinal offset for computing normal vector"
  ^Vec3 [^Vec3 p ^long level ^long tilesize]
  (let [lon  (longitude p)
        norm (mag p)]
    (mulv (rotation-z lon) (vec3 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))) 0))))

(defn offset-latitude
  "Determine latitudinal offset for computing normal vector"
  ^Vec3 [p level tilesize radius1 radius2]
  (let [lon  (longitude p)
        lat  (latitude p radius1 radius2)
        norm (mag p)
        v    (vec3 0 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))))
        vs   (mulv (rotation-z lon) (mulv (rotation-y (- lat)) v))]
    (vec3 (vs 0) (vs 1) (/ (* (vs 2) radius2) radius1))))

(def world-map-tile
  "Load and cache map tiles"
  (z/lru
    (fn [^long in-level ^long ty ^long tx]
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 128))

(def elevation-tile
  "Load and cache elevation tiles"
  (z/lru
    (fn [in-level ty tx]
      (let [data (slurp-shorts (tile-path "elevation" in-level ty tx ".raw"))
            size (int (round (sqrt (alength data))))]
        {:width size :height size :data data}))
    :lru/threshold 128))

(defn world-map-pixel
  "Get world map RGB value for a given pixel coordinate"
  [^long dy ^long dx ^long in-level ^long width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (world-map-tile in-level ty tx)]
    (get-pixel img py px)))

(defn elevation-pixel
  "Get elevation value for given pixel coordinates"
  ^long [^long dy ^long dx ^long in-level ^long width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (elevation-tile in-level ty tx)]
    (get-short img py px)))

(defn map-interpolation
  "Interpolate world map values"
  [in-level width lon lat pixel p+ p*]
  (let [[dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        v0                      (pixel dy0 dx0 in-level width)
        v1                      (pixel dy0 dx1 in-level width)
        v2                      (pixel dy1 dx0 in-level width)
        v3                      (pixel dy1 dx1 in-level width)]
    (reduce p+ [(p* v0 (* yfrac0 xfrac0)) (p* v1 (* yfrac0 xfrac1)) (p* v2 (* yfrac1 xfrac0)) (p* v3 (* yfrac1 xfrac1))])))

(defn tile-center
  "Determine the 3D center of a cube map tile"
  [face level b a radius1 radius2]
  (let [j (cube-coordinate level 3 b 1)
        i (cube-coordinate level 3 a 1)]
    (project-onto-ellipsoid (cube-map face j i) radius1 radius2)))

(defn color-geodetic
  "Compute interpolated RGB value for a point on the world"
  [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat world-map-pixel add mult))

(defn elevation-geodetic
  "Compute interpolated elevation value for a point on the world (-500 for water)"
  [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat elevation-pixel + *))

(defn water-geodetic
  "Decide whether point is on land (0) or on water (255)"
  [^long in-level ^long width ^double lon ^double lat]
  (let [height (elevation-geodetic in-level width lon lat)]
    (if (< height 0) (int (/ (* height 255) -500)) 0)))

(defn project-onto-globe
  "Project point onto the globe with heightmap applied"
  [point in-level width radius1 radius2]
  (let [iteration (fn [scaled iter]
                    (let [[lon lat] (cartesian->geodetic scaled radius1 radius2)
                          height    (max (elevation-geodetic in-level width lon lat) 0)
                          elevated  (geodetic->cartesian lon lat height radius1 radius2)]
                      (if (or (< (mag (sub scaled elevated)) 1e-6) (>= iter 10))
                        scaled
                        (recur (mult point (/ (mag elevated) (mag point))) (inc iter)))))]
    (iteration (project-onto-ellipsoid point radius1 radius2) 0)))

(defn surrounding-points
  "Compute local point cloud consisting of nine points"
  [p in-level out-level width tilesize radius1 radius2]
  (let [d1 (offset-longitude p out-level tilesize)
        d2 (offset-latitude p out-level tilesize radius1 radius2)]
    (for [dj [-1 0 1] di [-1 0 1]]
         (let [ps (add p (add (mult d2 dj) (mult d1 di)))]
           (project-onto-globe ps in-level width radius1 radius2)))))

(defn normal-for-point
  "Estimate normal vector for a point on the world"
  [p in-level out-level width tilesize radius1 radius2]
  (let [pc (surrounding-points p in-level out-level width tilesize radius1 radius2)
        sx [-0.25  0    0.25, -0.5 0 0.5, -0.25 0   0.25]
        sy [-0.25 -0.5 -0.25,  0   0 0  ,  0.25 0.5 0.25]
        n1 (reduce add (map mult pc sx))
        n2 (reduce add (map mult pc sy))]
    (normalize (cross n1 n2))))

(set! *unchecked-math* false)
