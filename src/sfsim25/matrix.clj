(ns sfsim25.matrix
  "Matrix and vector operations"
  (:require [clojure.core.matrix :refer :all]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.quaternion :refer (rotate-vector)])
  (:import [mikera.vectorz Vector]
           [mikera.matrixx Matrix]
           [sfsim25.quaternion Quaternion]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(set-current-implementation :vectorz)

(defn normalize
  "Normalize the vector"
  ^Vector [^Vector v]
  (div v (norm v)))

(defn rotation-x
  "Rotation matrix around x-axis"
  ^Matrix [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (matrix [[1 0 0] [0 ca (- sa)] [0 sa ca]])))

(defn rotation-y
  "Rotation matrix around y-axis"
  ^Matrix [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (matrix [[ca 0 sa] [0 1 0] [(- sa) 0 ca]])))

(defn rotation-z
  "Rotation matrix around z-axis"
  ^Matrix [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (matrix [[ca (- sa) 0] [sa ca 0] [0 0 1]])))

(defn quaternion->matrix
  "Convert rotation quaternion to rotation matrix"
  ^Matrix [^Quaternion q]
  (let [a (rotate-vector q (matrix [1 0 0]))
        b (rotate-vector q (matrix [0 1 0]))
        c (rotate-vector q (matrix [0 0 1]))]
    (transpose (matrix [a b c]))))

(defn project
  "Project homogeneous coordinate to cartesian"
  ^Vector [^Vector v]
  (div (matrix [(mget v 0) (mget v 1) (mget v 2)]) (mget v 3)))

(defn transformation-matrix
  "Create homogeneous 4x4 transformation matrix from 3x3 rotation matrix and translation vector"
  ^Matrix [^Matrix m ^Vector v]
  (matrix [[(mget m 0 0) (mget m 0 1) (mget m 0 2) (mget v 0)]
           [(mget m 1 0) (mget m 1 1) (mget m 1 2) (mget v 1)]
           [(mget m 2 0) (mget m 2 1) (mget m 2 2) (mget v 2)]
           [           0            0            0          1]]))

(defn projection-matrix
  "Compute OpenGL projection matrix (frustum)"
  [width height near far field-of-view]
  (let [dx (/ 1.0 (Math/tan (/ field-of-view 2.0)))
        dy (-> dx (* width) (/ height))
        a  (/ (* far near) (- far near))
        b  (/ near (- far near))]
    (matrix [[dx  0  0  0]
             [ 0 dy  0  0]
             [ 0  0  b  a]
             [ 0  0 -1  0]])))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
