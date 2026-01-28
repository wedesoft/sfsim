;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.cubemap
  "Conversions from cube coordinates (face, j, i) to geodetic coordinates (longitude, latitude)."
  (:require
    [clojure.core.memoize :as z]
    [clojure.math :refer (cos sin sqrt floor atan2 round PI)]
    [fastmath.matrix :refer (mulv rotation-matrix-3d-y rotation-matrix-3d-z)]
    [fastmath.vector :refer (vec3 add mult div cross mag normalize)]
    [sfsim.image :refer (slurp-image get-pixel get-short)]
    [sfsim.matrix :refer (fvec3)]
    [sfsim.util :refer (slurp-shorts tile-path sqr)])
  (:import
    [clojure.lang
     Keyword]
    [fastmath.vector
     Vec3]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn cube-map-x
  "x-coordinate of point on cube face"
  ^double [^Keyword face ^double _j ^double i]
  (case face
    ::face0 (+ -1.0 (* 2.0 i))
    ::face1 (+ -1.0 (* 2.0 i))
    ::face2  1.0
    ::face3 (-  1.0 (* 2.0 i))
    ::face4 -1.0
    ::face5 (+ -1.0 (* 2.0 i))))


(defn cube-map-y
  "y-coordinate of point on cube face"
  ^double [^Keyword face ^double j ^double i]
  (case face
    ::face0 (-  1.0 (* 2.0 j))
    ::face1 -1.0
    ::face2 (+ -1.0 (* 2.0 i))
    ::face3  1.0
    ::face4 (-  1.0 (* 2.0 i))
    ::face5 (+ -1.0 (* 2.0 j))))


(defn cube-map-z
  "z-coordinate of point on cube face"
  ^double [^Keyword face ^double j ^double _i]
  (case face
    ::face0  1.0
    ::face1 (- 1.0 (* 2.0 j))
    ::face2 (- 1.0 (* 2.0 j))
    ::face3 (- 1.0 (* 2.0 j))
    ::face4 (- 1.0 (* 2.0 j))
    ::face5 -1.0))


(defn cube-map
  "Get 3D vector to point on cube face"
  ^Vec3 [^Keyword face ^double j ^double i]
  (vec3 (cube-map-x face j i) (cube-map-y face j i) (cube-map-z face j i)))


(defn determine-face
  "Determine which face a point gets projected on when projecting onto a cube"
  {:malli/schema [:=> [:cat fvec3] :keyword]}
  ^Keyword [^Vec3 point]
  (let [[^double x ^double y ^double z] point]
    (cond
      (>= (abs x) (max (abs y) (abs z))) (if (>= x 0) ::face2 ::face4)
      (>= (abs y) (max (abs x) (abs z))) (if (>= y 0) ::face3 ::face1)
      :else                              (if (>= z 0) ::face0 ::face5))))


(defn cube-i
  "Determine cube face coordinate i given face and a point on the cube surface"
  ^double [^Keyword face ^Vec3 point]
  (case face
    ::face0 (* 0.5 (+ ^double (point 0) 1.0))
    ::face1 (* 0.5 (+ ^double (point 0) 1.0))
    ::face2 (* 0.5 (+ ^double (point 1) 1.0))
    ::face3 (* 0.5 (- 1.0 ^double (point 0)))
    ::face4 (* 0.5 (- 1.0 ^double (point 1)))
    ::face5 (* 0.5 (+ ^double (point 0) 1.0))))


(defn cube-j
  "Determine cube face coordinate j given face and a point on the cube surface"
  ^double [^Keyword face ^Vec3 point]
  (case face
    ::face0 (* 0.5 (- 1.0 ^double (point 1)))
    ::face1 (* 0.5 (- 1.0 ^double (point 2)))
    ::face2 (* 0.5 (- 1.0 ^double (point 2)))
    ::face3 (* 0.5 (- 1.0 ^double (point 2)))
    ::face4 (* 0.5 (- 1.0 ^double (point 2)))
    ::face5 (* 0.5 (+ ^double (point 1) 1.0))))


(defn cube-coordinate
  "Determine coordinate of a pixel on a tile of a given level"
  ^double [^long level ^long tilesize ^long tile ^double pixel]
  (let [tiles (bit-shift-left 1 level)]
    (/ (+ tile (double (/ pixel (dec tilesize)))) tiles)))


(defn cube-map-corners
  "Get 3D vectors to corners of cube map tile"
  [^Keyword face ^long level ^long row ^long column]
  [(cube-map face (cube-coordinate level 2 row 0.0) (cube-coordinate level 2 column 0.0))
   (cube-map face (cube-coordinate level 2 row 0.0) (cube-coordinate level 2 column 1.0))
   (cube-map face (cube-coordinate level 2 row 1.0) (cube-coordinate level 2 column 0.0))
   (cube-map face (cube-coordinate level 2 row 1.0) (cube-coordinate level 2 column 1.0))])


(defn longitude
  "Longitude of 3D point (East is positive)"
  ^double [^Vec3 point]
  (atan2 (point 1) (point 0)))


(defn latitude
  "Latitude of 3D point"
  ^double [^Vec3 point]
  (let [p (sqrt (+ (sqr (point 0)) (sqr (point 1))))]
    (atan2 (point 2) p)))


;; https://en.wikipedia.org/wiki/Geographic_coordinate_conversion
(defn geodetic->cartesian
  "Convert latitude and longitude to cartesian coordinates assuming a spherical Earth"
  ^Vec3 [^double longitude ^double latitude ^double height ^double radius]
  (let [distance        (+ height radius)
        cos-lat         (cos latitude)
        sin-lat         (sin latitude)]
    (vec3 (* distance cos-lat (cos longitude)) (* distance cos-lat (sin longitude)) (* distance sin-lat))))


(defn project-onto-sphere
  "Project a 3D vector onto a sphere"
  ^Vec3 [^Vec3 point ^double radius]
  (mult (normalize point) radius))


(defn project-onto-cube
  "Project 3D vector onto cube"
  {:malli/schema [:=> [:cat fvec3] fvec3]}
  ^Vec3 [^Vec3 point]
  (let [[^double |x| ^double |y| ^double |z|] (mapv abs point)]
    (cond
      (>= |x| (max |y| |z|)) (div point |x|)
      (>= |y| (max |x| |z|)) (div point |y|)
      :else                  (div point |z|))))


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
    (mulv (rotation-matrix-3d-z lon) (vec3 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))) 0))))


(defn offset-latitude
  "Determine latitudinal offset for computing normal vector"
  ^Vec3 [^Vec3 p ^long level ^long tilesize]
  (let [lon  (longitude p)
        lat  (latitude p)
        norm (mag p)
        v    (vec3 0 0 (/ (* norm PI) (* 2 tilesize (bit-shift-left 1 level))))
        vs   (mulv (rotation-matrix-3d-z lon) (mulv (rotation-matrix-3d-y (- lat)) v))]
    vs))


(def world-map-tile
  "Load and cache map tiles"
  (z/lru
    (fn load-world-map-tile [prefix in-level ty tx]
      (slurp-image (tile-path prefix in-level ty tx ".png")))
    :lru/threshold 128))


(def elevation-tile
  "Load and cache elevation tiles"
  (z/lru
    (fn elevation-tile
      [in-level ty tx]
      (let [data (slurp-shorts (tile-path "tmp/elevation" in-level ty tx ".raw"))
            size (int (round (sqrt (alength data))))]
        {:sfsim.image/width size :sfsim.image/height size :sfsim.image/data data}))
    :lru/threshold 128))


(defn world-map-pixel
  "Get world map RGB value for a given pixel coordinate"
  {:malli/schema [:=> [:cat :string] [:=> [:cat :int :int :int :int] fvec3]]}
  [prefix]
  (fn world-map-pixel
    ^Vec3 [^long dy ^long dx ^long in-level ^long width]
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
  {:malli/schema [:=> [:cat :int :int :double :double fn? fn? fn?] :any]}
  [in-level width lon lat pixel p+ p*]
  (let [[^long dx0 ^long dx1 ^double xfrac0 ^double xfrac1] (map-pixels-x lon width in-level)
        [^long dy0 ^long dy1 ^double yfrac0 ^double yfrac1] (map-pixels-y lat width in-level)
        v0                                                  (pixel dy0 dx0 in-level width)
        v1                                                  (pixel dy0 dx1 in-level width)
        v2                                                  (pixel dy1 dx0 in-level width)
        v3                                                  (pixel dy1 dx1 in-level width)]
    (reduce p+ [(p* v0 (* yfrac0 xfrac0)) (p* v1 (* yfrac0 xfrac1)) (p* v2 (* yfrac1 xfrac0)) (p* v3 (* yfrac1 xfrac1))])))


(defn tile-center
  "Determine the 3D center of a cube map tile"
  {:malli/schema [:=> [:cat :keyword :int :int :int :double] fvec3]}
  [face level row column radius]
  (let [j (cube-coordinate level 3 row 1.0)
        i (cube-coordinate level 3 column 1.0)]
    (project-onto-sphere (cube-map face j i) radius)))


(defn color-geodetic-day
  "Compute interpolated daytime RGB value for a point on the world"
  ^Vec3 [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat world-map-pixel-day add mult))


(defn color-geodetic-night
  "Compute interpolated nighttime RGB value for a point on the world"
  ^Vec3 [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat world-map-pixel-night add mult))


(defn elevation-geodetic
  "Compute interpolated elevation value for a point on the world (-500 for water)"
  ^double [^long in-level ^long width ^double lon ^double lat]
  (map-interpolation in-level width lon lat elevation-pixel + *))


(defn water-geodetic
  "Decide whether point is on land (0) or on water (255)"
  ^long [^long in-level ^long width ^double lon ^double lat]
  (let [height (elevation-geodetic in-level width lon lat)]
    (if (< height 0) (int (/ (* height 255) -500)) 0)))


(defn project-onto-globe
  "Project point onto the globe with heightmap applied"
  ^Vec3 [^Vec3 point ^long in-level ^long width ^double radius]
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
        n1 (reduce add (mapv mult pc sx))
        n2 (reduce add (mapv mult pc sy))]
    (normalize (cross n1 n2))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
