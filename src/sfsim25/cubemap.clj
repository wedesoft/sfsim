(ns sfsim25.cubemap
  (:require [clojure.core.memoize :as z]
            [sfsim25.vector3 :as v]
            [sfsim25.rgb :as r]
            [sfsim25.matrix3x3 :as m]
            [sfsim25.util :refer (tile-path slurp-image slurp-shorts get-pixel get-elevation)])
  (:import [sfsim25.vector3 Vector3]))

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
    0  1
    1 (- 1 (* 2 j))
    2 (- 1 (* 2 j))
    3 (- 1 (* 2 j))
    4 (- 1 (* 2 j))
    5 -1))

(defn cube-map-z
  "z-coordinate of point on cube face"
  ^double [^long face ^double j ^double i]
  (case face
    0 (+ -1 (* 2 j))
    1  1
    2 (-  1 (* 2 i))
    3 -1
    4 (+ -1 (* 2 i))
    5 (-  1 (* 2 j))))

(defn cube-map
  "Get 3D vector to point on cube face"
  ^Vector3 [^long face ^double j ^double i]
  (v/->Vector3 (cube-map-x face j i) (cube-map-y face j i) (cube-map-z face j i)))

(defn cube-coordinate
  "Determine coordinate of a pixel on a tile of a given level"
  ^double [^long level ^long tilesize ^long tile ^double pixel]
  (let [tiles (bit-shift-left 1 level)]
    (/ (+ tile (double (/ pixel (dec tilesize)))) tiles)))

(defn longitude
  "Longitude of 3D point"
  ^double [^Vector3 p]
  (Math/atan2 (.z p) (.x p)))

(defn latitude
  "Latitude of 3D point"
  ^double [^Vector3 p]
  (Math/atan2 (.y p) (Math/sqrt (+ (* (.x p) (.x p)) (* (.z p) (.z p))))))

(defn map-x
  "Compute x-coordinate on raster map"
  ^double [^double longitude ^long tilesize ^long level]
  (let [n (bit-shift-left 1 level)]
    (* (- Math/PI longitude) (/ (* 4 n tilesize) (* 2 Math/PI)))))

(defn map-y
  "Compute y-coordinate on raster map"
  ^double [^double latitude ^long tilesize ^long level]
  (let [n (bit-shift-left 1 level)]
    (* (- (/ Math/PI 2) latitude) (/ (* 2 n tilesize) Math/PI))))

(defn map-pixels-x
  "Determine x-coordinates and fractions for interpolation"
  [^double longitude ^long tilesize ^long level]
  (let [n     (bit-shift-left 1 level)
        size  (* 4 n tilesize)
        x     (map-x longitude tilesize level)
        x0    (int (Math/floor x))
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
        y0   (int (Math/floor y))
        y1   (inc y0)
        frac1 (- y y0)
        frac0 (- 1 frac1)]
    [(min y0 (dec size)) (min y1 (dec size)) frac0 frac1]))

(defn scale-point
  "Scale point coordinates to reach surface of ellipsoid"
  ^Vector3 [^Vector3 p ^double radius1 ^double radius2]
  (let [norm (v/norm p)]
    (v/->Vector3 (* radius1 (/ (.x p) norm)) (* radius2 (/ (.y p) norm)) (* radius1 (/ (.z p) norm)))))

(defn offset-longitude
  "Determine longitudinal offset for computing normal vector"
  ^Vector3 [^Vector3 p ^long level ^long tilesize]
  (let [lon  (longitude p)
        norm (v/norm p)]
    (m/* (m/rotation-y (- lon)) (v/->Vector3 0 0 (- (/ (* norm Math/PI) (* 2 tilesize (bit-shift-left 1 level))))))))

(defn offset-latitude
  "Determine latitudinal offset for computing normal vector"
  ^Vector3 [p level tilesize radius1 radius2]
  (let [lon  (longitude p)
        lat  (latitude p)
        norm (v/norm p)
        v    (v/->Vector3 0 (/ (* norm Math/PI) (* 2 tilesize (bit-shift-left 1 level))) 0)
        vs   (m/* (m/rotation-y (- lon)) (m/* (m/rotation-z lat) v))]
    (v/->Vector3 (.x vs) (/ (* (.y vs) radius2) radius1) (.z vs))))

(defn super-tile
  "Create lower resolution tile from four sub-tiles"
  [tilesize a b c d]
  (let [subsample (fn [a b] (vec (concat (map (vec a) (range 0 tilesize 2)) (map (vec b) (range 2 tilesize 2)))))]
    (vec (mapcat subsample (subsample (partition tilesize a) (partition tilesize c))
                           (subsample (partition tilesize b) (partition tilesize d))))))

(def world-map-tile
  "Load and cache map tiles"
  (z/lru
    (fn [^long in-level ^long ty ^long tx]
      (slurp-image (tile-path "world" in-level ty tx ".png")))
    :lru/threshold 128))

(def elevation-tile
  "Load and cache elevation tiles"
  (z/lru
    (fn ^shorts [^long in-level ^long ty ^long tx]
      (let [data (slurp-shorts (tile-path "elevation" in-level ty tx ".raw"))
            size (int (Math/round (Math/sqrt (alength data))))]
        [size size data]))
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
  [^long dy ^long dx ^long in-level ^long width]
  (let [ty  (quot dy width)
        tx  (quot dx width)
        py  (mod dy width)
        px  (mod dx width)
        img (elevation-tile in-level ty tx)]
    (get-elevation img py px)))

(defn interpolate-map
  "Interpolate map values"
  [in-level width point pixel p+ p*]
  (let [lon                     (longitude point)
        lat                     (latitude point)
        [dx0 dx1 xfrac0 xfrac1] (map-pixels-x lon width in-level)
        [dy0 dy1 yfrac0 yfrac1] (map-pixels-y lat width in-level)
        v0                      (pixel dy0 dx0 in-level width)
        v1                      (pixel dy0 dx1 in-level width)
        v2                      (pixel dy1 dx0 in-level width)
        v3                      (pixel dy1 dx1 in-level width)]
    (p+ (p* (* yfrac0 xfrac0) v0) (p* (* yfrac0 xfrac1) v1) (p* (* yfrac1 xfrac0) v2) (p* (* yfrac1 xfrac1) v3))))

(defn color-for-point
  "Compute interpolated RGB value for a point on the world"
  [^long in-level ^long width ^Vector3 point]
  (interpolate-map in-level width point world-map-pixel r/+ r/*))

(defn elevation-for-point
  "Compute interpolated elevation value for a point on the world (-500 for water)"
  [^long in-level ^long width ^Vector3 point]
  (interpolate-map in-level width point elevation-pixel + *))

(defn elevated-point
  "Get elevated 3D point for a point on the world"
  [in-level width p radius1 radius2]
  (let [height (max 0 (elevation-for-point in-level width p))]
    (scale-point p (+ radius1 height) (+ radius2 height))))

(defn surrounding-points
  "Compute local point cloud consisting of nine points"
  [p in-level out-level width tilesize radius1 radius2]
  (let [d1 (offset-longitude p out-level tilesize)
        d2 (offset-latitude p out-level tilesize radius1 radius2)]
    (for [dj [-1 0 1] di [-1 0 1]]
      (let [ps (v/+ p (v/* dj d2) (v/* di d1))]
        (elevated-point in-level width ps radius1 radius2)))))

(set! *unchecked-math* false)
