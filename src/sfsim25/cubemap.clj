(ns sfsim25.cubemap
  "Conversions from cube coordinates (face, j, i) to geodetic coordinates (longitude, latitude)."
  (:require [clojure.core.memoize :as z]
            [fastmath.vector :refer (vec3 add mult cross mag normalize)]
            [fastmath.matrix :refer (mulv)]
            [clojure.math :refer (cos sin sqrt floor atan2 round PI)]
            [sfsim25.matrix :refer (rotation-y rotation-z fvec3)]
            [sfsim25.image :refer (slurp-image get-pixel get-short)]
            [sfsim25.util :refer (slurp-shorts tile-path sqr)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn cube-map-x
  "x-coordinate of point on cube face"
  {:malli/schema [:=> [:cat :int :double :double] :double]}
  [face _j i]
  (case (long face)
    0 (+ -1.0 (* 2.0 i))
    1 (+ -1.0 (* 2.0 i))
    2  1.0
    3 (-  1.0 (* 2.0 i))
    4 -1.0
    5 (+ -1.0 (* 2.0 i))))

(defn cube-map-y
  "y-coordinate of point on cube face"
  {:malli/schema [:=> [:cat :int :double :double] :double]}
  [face j i]
  (case (long face)
    0 (-  1.0 (* 2.0 j))
    1 -1.0
    2 (+ -1.0 (* 2.0 i))
    3  1.0
    4 (-  1.0 (* 2.0 i))
    5 (+ -1.0 (* 2.0 j))))

(defn cube-map-z
  "z-coordinate of point on cube face"
  {:malli/schema [:=> [:cat :int :double :double] :double]}
  [face j _i]
  (case (long face)
    0  1.0
    1 (- 1.0 (* 2.0 j))
    2 (- 1.0 (* 2.0 j))
    3 (- 1.0 (* 2.0 j))
    4 (- 1.0 (* 2.0 j))
    5 -1.0))

(defn cube-map
  "Get 3D vector to point on cube face"
  {:malli/schema [:=> [:cat :int :double :double] fvec3]}
  [face j i]
  (vec3 (cube-map-x face j i) (cube-map-y face j i) (cube-map-z face j i)))

(defn cube-coordinate
  "Determine coordinate of a pixel on a tile of a given level"
  {:malli/schema [:=> [:cat :int :int :int :double] :double]}
  [level tilesize tile pixel]
  (let [tiles (bit-shift-left 1 level)]
    (/ (+ tile (double (/ pixel (dec tilesize)))) tiles)))

(defn cube-map-corners
  "Get 3D vectors to corners of cube map tile"
  {:malli/schema [:=> [:cat :int :int :int :int] [:tuple fvec3 fvec3 fvec3 fvec3]]}
  [face level b a]
  [(cube-map face (cube-coordinate level 2 b 0.0) (cube-coordinate level 2 a 0.0))
   (cube-map face (cube-coordinate level 2 b 0.0) (cube-coordinate level 2 a 1.0))
   (cube-map face (cube-coordinate level 2 b 1.0) (cube-coordinate level 2 a 0.0))
   (cube-map face (cube-coordinate level 2 b 1.0) (cube-coordinate level 2 a 1.0))])

(defn longitude
  "Longitude of 3D point (East is positive)"
  {:malli/schema [:=> [:cat fvec3] :double]}
  [point]
  (atan2 (point 1) (point 0)))

(defn latitude
  "Latitude of 3D point"
  {:malli/schema [:=> [:cat fvec3] :double]}
  [point]
  (let [p (sqrt (+ (sqr (point 0)) (sqr (point 1))))]
    (atan2 (point 2) p)))

; https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
(defn geodetic->cartesian
  "Convert latitude and longitude to cartesian coordinates assuming a spherical Earth"
  {:malli/schema [:=> [:cat :double :double :double :double] fvec3]}
  [longitude latitude height radius]
  (let [distance        (+ height radius)
        cos-lat         (cos latitude)
        sin-lat         (sin latitude)]
    (vec3 (* distance cos-lat (cos longitude)) (* distance cos-lat (sin longitude)) (* distance sin-lat))))

(defn project-onto-sphere
  "Project a 3D vector onto an ellipsoid"
  {:malli/schema [:=> [:cat fvec3 :double] fvec3]}
  [point radius]
  (mult (normalize point) radius))

(defn cartesian->geodetic
  "Convert cartesian coordinates to latitude, longitude and height assuming a spherical Earth"
  {:malli/schema [:=> [:cat fvec3 :double] [:tuple :double :double :double]]}
  [point radius]
  (let [height    (- (mag point) radius)
        longitude (atan2 (point 1) (point 0))
        p         (sqrt (+ (sqr (point 0)) (sqr (point 1))))
        latitude  (atan2 (point 2) p)]
    [longitude latitude height]))

(defn map-x
  "Compute x-coordinate on raster map"
  {:malli/schema [:=> [:cat :double :int :int] :double]}
  [longitude tilesize level]
  (let [n (bit-shift-left 1 level)]
    (* (+ PI longitude) (/ (* 4 n tilesize) (* 2 PI)))))

(defn map-y
  "Compute y-coordinate on raster map"
  {:malli/schema [:=> [:cat :double :int :int] :double]}
  [latitude tilesize level]
  (let [n (bit-shift-left 1 level)]
    (* (- (/ PI 2) latitude) (/ (* 2 n tilesize) PI))))

(defn map-pixels-x
  "Determine x-coordinates and fractions for interpolation"
  {:malli/schema [:=> [:cat :double :int :int] [:tuple :int :int :double :double]]}
  [longitude tilesize level]
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
  {:malli/schema [:=> [:cat :double :int :int] [:tuple :int :int :double :double]]}
  [latitude tilesize level]
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
  {:malli/schema [:=> [:cat fvec3 :int :int] fvec3]}
  [p level tilesize]
  (let [lon  (longitude p)
        norm (mag p)]
    (mulv (rotation-z lon) (vec3 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))) 0))))

(defn offset-latitude
  "Determine latitudinal offset for computing normal vector"
  {:malli/schema [:=> [:cat fvec3 :int :int] fvec3]}
  [p level tilesize]
  (let [lon  (longitude p)
        lat  (latitude p)
        norm (mag p)
        v    (vec3 0 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))))
        vs   (mulv (rotation-z lon) (mulv (rotation-y (- lat)) v))]
    vs))

(def world-map-tile
  "Load and cache map tiles"
  (z/lru
    (fn
      [prefix in-level ty tx]
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
  {:malli/schema [:=> [:cat :string] [:=> [:cat :int :int :int :int] fvec3]]}
  [prefix]
  (fn [dy dx in-level width]
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
  {:malli/schema [:=> [:cat :int :int :int :int] :int]}
  [dy dx in-level width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (elevation-tile in-level ty tx)]
    (get-short img py px)))

(defn map-interpolation
  "Interpolate world map values"
  {:malli/schema [:=> [:cat :int :int :double :double fn? fn? fn?] :any]}
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
  {:malli/schema [:=> [:cat :int :int :int :int :double] fvec3]}
  [face level b a radius]
  (let [j (cube-coordinate level 3 b 1.0)
        i (cube-coordinate level 3 a 1.0)]
    (project-onto-sphere (cube-map face j i) radius)))

(defn color-geodetic-day
  "Compute interpolated daytime RGB value for a point on the world"
  {:malli/schema [:=> [:cat :int :int :double :double] fvec3]}
  [in-level width lon lat]
  (map-interpolation in-level width lon lat world-map-pixel-day add mult))

(defn color-geodetic-night
  "Compute interpolated nighttime RGB value for a point on the world"
  {:malli/schema [:=> [:cat :int :int :double :double] fvec3]}
  [in-level width lon lat]
  (map-interpolation in-level width lon lat world-map-pixel-night add mult))

(defn elevation-geodetic
  "Compute interpolated elevation value for a point on the world (-500 for water)"
  {:malli/schema [:=> [:cat :int :int :double :double] :double]}
  [in-level width lon lat]
  (map-interpolation in-level width lon lat elevation-pixel + *))

(defn water-geodetic
  "Decide whether point is on land (0) or on water (255)"
  {:malli/schema [:=> [:cat :int :int :double :double] :int]}
  [in-level width lon lat]
  (let [height (elevation-geodetic in-level width lon lat)]
    (if (< height 0) (int (/ (* height 255) -500)) 0)))

(defn project-onto-globe
  "Project point onto the globe with heightmap applied"
  {:malli/schema [:=> [:cat fvec3 :int :int :double] fvec3]}
  [point in-level width radius]
  (let [surface-point (project-onto-sphere point radius)
        [lon lat _]   (cartesian->geodetic surface-point radius)
        height        (max (elevation-geodetic in-level width lon lat) 0.0)]
    (geodetic->cartesian lon lat height radius)))

(defn surrounding-points
  "Compute local point cloud consisting of nine points"
  {:malli/schema [:=> [:cat fvec3 :int :int :int :int :double] [:sequential fvec3]]}
  [p in-level out-level width tilesize radius]
  (let [d1 (offset-longitude p out-level tilesize)
        d2 (offset-latitude p out-level tilesize)]
    (for [dj [-1 0 1] di [-1 0 1]]
         (let [ps (add p (add (mult d2 dj) (mult d1 di)))]
           (project-onto-globe ps in-level width radius)))))

(defn normal-for-point
  "Estimate normal vector for a point on the world"
  {:malli/schema [:=> [:cat fvec3 :int :int :int :int :double] fvec3]}
  [p in-level out-level width tilesize radius]
  (let [pc (surrounding-points p in-level out-level width tilesize radius)
        sx [-0.25  0    0.25, -0.5 0 0.5, -0.25 0   0.25]
        sy [-0.25 -0.5 -0.25,  0   0 0  ,  0.25 0.5 0.25]
        n1 (reduce add (map mult pc sx))
        n2 (reduce add (map mult pc sy))]
    (normalize (cross n1 n2))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
