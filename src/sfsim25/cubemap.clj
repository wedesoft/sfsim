(ns sfsim25.cubemap
  (:require [sfsim25.vector3 :as v]
            [sfsim25.matrix3x3 :as m])
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
  (v/vector3 (cube-map-x face j i) (cube-map-y face j i) (cube-map-z face j i)))

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
    (v/vector3 (* radius1 (/ (.x p) norm)) (* radius2 (/ (.y p) norm)) (* radius1 (/ (.z p) norm)))))

(defn offset-longitude
  "Determine longitudinal offset for computing normal vector"
  ^Vector3 [^Vector3 p ^long level ^long tilesize]
  (let [lon  (longitude p)
        norm (v/norm p)]
    (m/* (m/rotation-y (- lon)) (v/vector3 0 0 (- (/ (* norm Math/PI) (* 2 tilesize (bit-shift-left 1 level))))))))

(defn offset-latitude
  "Determine latitudinal offset for computing normal vector"
  ^Vector3 [p level tilesize radius1 radius2]
  (let [lon  (longitude p)
        lat  (latitude p)
        norm (v/norm p)
        v    (v/vector3 0 (/ (* norm Math/PI) (* 2 tilesize (bit-shift-left 1 level))) 0)
        vs   (m/* (m/rotation-y (- lon)) (m/* (m/rotation-z lat) v))]
    (v/vector3 (.x vs) (/ (* (.y vs) radius2) radius1) (.z vs))))

(set! *unchecked-math* false)
