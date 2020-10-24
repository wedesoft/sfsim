(ns sfsim25.cubemap)

(set! *unchecked-math* true)

(defn cube-map-x ^double [^long face ^double j ^double i]
  "x-coordinate of point on cube face"
  (case face
    0 (+ -1 (* 2 i))
    1 (+ -1 (* 2 i))
    2  1
    3 (-  1 (* 2 i))
    4 -1
    5 (+ -1 (* 2 i))))

(defn cube-map-y ^double [^long face ^double j ^double i]
  "y-coordinate of point on cube face"
  (case face
    0  1
    1 (- 1 (* 2 j))
    2 (- 1 (* 2 j))
    3 (- 1 (* 2 j))
    4 (- 1 (* 2 j))
    5 -1))

(defn cube-map-z ^double [^long face ^double j ^double i]
  "z-coordinate of point on cube face"
  (case face
    0 (+ -1 (* 2 j))
    1  1
    2 (-  1 (* 2 i))
    3 -1
    4 (+ -1 (* 2 i))
    5 (-  1 (* 2 j))))

(defn longitude ^double [^double x ^double y ^double z]
  "Longitude of 3D point"
  (Math/atan2 z x))

(defn latitude ^double [^double x ^double y ^double z]
  "Latitude of 3D point"
  (Math/atan2 y (Math/sqrt (+ (* x x) (* z z)))))

(defn map-x ^double [^double longitude ^long tilesize ^long level]
  "Compute x-coordinate on raster map"
  (let [n (bit-shift-left 1 level)]
    (* (- Math/PI longitude) (/ (* 4 n tilesize) (* 2 Math/PI)))))

(defn map-y ^double [^double latitude ^long tilesize ^long level]
  "Compute y-coordinate on raster map"
  (let [n (bit-shift-left 1 level)]
    (* (- (/ Math/PI 2) latitude) (/ (* 2 n tilesize) Math/PI))))

(defn map-pixels-x [^double longitude ^long tilesize ^long level]
  "Determine x-coordinates and fractions for interpolation"
  (let [n     (bit-shift-left 1 level)
        size  (* 4 n tilesize)
        x     (map-x longitude tilesize level)
        x0    (int (Math/floor x))
        x1    (inc x0)
        frac1 (- x x0)
        frac0 (- 1 frac1)]
    [(mod x0 size) (mod x1 size) frac0 frac1]))

(defn map-pixels-y [^double latitude ^long tilesize ^long level]
  "Determine y-coordinates and fractions for interpolation"
  (let [n    (bit-shift-left 1 level)
        size (* 2 n tilesize)
        y    (map-y latitude tilesize level)
        y0   (int (Math/floor y))
        y1   (inc y0)
        frac1 (- y y0)
        frac0 (- 1 frac1)]
    [(min y0 (dec size)) (min y1 (dec size)) frac0 frac1]))

(set! *unchecked-math* false)
