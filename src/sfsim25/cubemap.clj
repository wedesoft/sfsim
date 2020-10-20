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

(set! *unchecked-math* false)
