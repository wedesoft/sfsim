(ns sfsim25.cubemap
  "Conversions from cube coordinates (face, j, i) to geodetic coordinates (longitude, latitude)."
  (:require [clojure.core.memoize :as z]
            [fastmath.vector :refer (vec3 add mult cross mag normalize)]
            [fastmath.matrix :refer (mulv)]
            [clojure.math :refer (cos sin sqrt floor atan2 round PI)]
            [sfsim25.matrix :refer (rotation-y rotation-z)]
            [sfsim25.util :refer (tile-path slurp-image slurp-shorts get-pixel get-short sqr)])
  (:import [fastmath.vector Vec3]))

(set! *unchecked-math* true)

(defn cube-map-x
  "x-coordinate of point on cube face"
  ^double [^long face ^double _j ^double i]
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
  ^double [^long face ^double j ^double _i]
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
  ^double [^Vec3 point]
  (atan2 (point 1) (point 0)))

(defn latitude
  "Latitude of 3D point"
  ^double [^Vec3 point]
  (let [p (sqrt (+ (sqr (point 0)) (sqr (point 1))))]
    (atan2 (point 2) p)))

; https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
(defn geodetic->cartesian
  "Convert latitude and longitude to cartesian coordinates assuming a spherical Earth"
  [longitude latitude height radius]
  (let [distance        (+ height radius)
        cos-lat         (cos latitude)
        sin-lat         (sin latitude)]
    (vec3 (* distance cos-lat (cos longitude)) (* distance cos-lat (sin longitude)) (* distance sin-lat))))

(defn project-onto-sphere
  "Project a 3D vector onto an ellipsoid"
  ^Vec3 [^Vec3 point ^double radius]
  (mult (normalize point) radius))

(defn cartesian->geodetic
  "Convert cartesian coordinates to latitude, longitude and height assuming a spherical Earth"
  [^Vec3 point ^double radius]
  (let [height    (- (mag point) radius)
        longitude (atan2 (point 1) (point 0))
        p         (sqrt (+ (sqr (point 0)) (sqr (point 1))))
        latitude  (atan2 (point 2) p)]
    [longitude latitude height]))

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
  ^Vec3 [^Vec3 p ^long level ^long tilesize]
  (let [lon  (longitude p)
        lat  (latitude p)
        norm (mag p)
        v    (vec3 0 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))))
        vs   (mulv (rotation-z lon) (mulv (rotation-y (- lat)) v))]
    vs))

(def world-map-tile
  "Load and cache map tiles"
  (z/lru
    (fn [^String prefix ^long in-level ^long ty ^long tx]
      (slurp-image (tile-path prefix in-level ty tx ".png")))
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
  [prefix]
  (fn [^long dy ^long dx ^long in-level ^long width]
      (let [ty  (quot dy width)
            tx  (quot dx width)
            py  (mod dy width)
            px  (mod dx width)
            img (world-map-tile prefix in-level ty tx)]
        (get-pixel img py px))))

(def world-map-pixel-day (world-map-pixel "tmp/day"))

(def world-map-pixel-night (world-map-pixel "tmp/night"))

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
  [face level b a radius]
  (let [j (cube-coordinate level 3 b 1)
        i (cube-coordinate level 3 a 1)]
    (project-onto-sphere (cube-map face j i) radius)))

(defn color-geodetic-day
  "Compute interpolated daytime RGB value for a point on the world"
  [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat world-map-pixel-day add mult))

(defn color-geodetic-night
  "Compute interpolated nighttime RGB value for a point on the world"
  [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat world-map-pixel-night add mult))

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
  [^Vec3 point ^long in-level ^long width ^double radius]
  (let [surface-point (project-onto-sphere point radius)
        [lon lat _]   (cartesian->geodetic surface-point radius)
        height        (max (elevation-geodetic in-level width lon lat) 0)]
    (geodetic->cartesian lon lat height radius)))

(defn surrounding-points
  "Compute local point cloud consisting of nine points"
  [p in-level out-level width tilesize radius]
  (let [d1 (offset-longitude p out-level tilesize)
        d2 (offset-latitude p out-level tilesize)]
    (for [dj [-1 0 1] di [-1 0 1]]
         (let [ps (add p (add (mult d2 dj) (mult d1 di)))]
           (project-onto-globe ps in-level width radius)))))

(defn normal-for-point
  "Estimate normal vector for a point on the world"
  [p in-level out-level width tilesize radius]
  (let [pc (surrounding-points p in-level out-level width tilesize radius)
        sx [-0.25  0    0.25, -0.5 0 0.5, -0.25 0   0.25]
        sy [-0.25 -0.5 -0.25,  0   0 0  ,  0.25 0.5 0.25]
        n1 (reduce add (map mult pc sx))
        n2 (reduce add (map mult pc sy))]
    (normalize (cross n1 n2))))

(set! *unchecked-math* false)
