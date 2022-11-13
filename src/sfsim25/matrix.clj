(ns sfsim25.matrix
  "Matrix and vector operations"
  (:require [clojure.core.matrix :refer (matrix mget mmul inverse eseq transpose div dot identity-matrix normalise cross add)]
            [clojure.math :refer (cos sin tan)]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.util :refer :all]
            [sfsim25.quaternion :refer (rotate-vector)])
  (:import [mikera.vectorz Vector]
           [mikera.matrixx Matrix]
           [sfsim25.quaternion Quaternion]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn normalize
  "Normalize the vector"
  ^Vector [^Vector v]
  (div v (norm v)))

(defn rotation-x
  "Rotation matrix around x-axis"
  ^Matrix [^double angle]
  (let [ca (cos angle) sa (sin angle)]
    (matrix [[1 0 0] [0 ca (- sa)] [0 sa ca]])))

(defn rotation-y
  "Rotation matrix around y-axis"
  ^Matrix [^double angle]
  (let [ca (cos angle) sa (sin angle)]
    (matrix [[ca 0 sa] [0 1 0] [(- sa) 0 ca]])))

(defn rotation-z
  "Rotation matrix around z-axis"
  ^Matrix [^double angle]
  (let [ca (cos angle) sa (sin angle)]
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
  (let [dx (/ 1.0 (tan (* 0.5 field-of-view)))
        dy (-> dx (* width) (/ height))
        a  (/ (* far near) (- far near))
        b  (/ near (- far near))]
    (matrix [[dx  0  0  0]
             [ 0 dy  0  0]
             [ 0  0  b  a]
             [ 0  0 -1  0]])))

(defn frustum-corners
  "Determine corners of OpenGL frustum"
  [projection-matrix]
  (let [minv (inverse projection-matrix)]
    (mapv #(mmul minv %)
          [(matrix [-1 -1 1 1])
           (matrix [ 1 -1 1 1])
           (matrix [-1  1 1 1])
           (matrix [ 1  1 1 1])
           (matrix [-1 -1 0 1])
           (matrix [ 1 -1 0 1])
           (matrix [-1  1 0 1])
           (matrix [ 1  1 0 1])])))

(defn bounding-box
  "Compute 3D bounding box for a set of points"
  [points]
  (let [x (map #(/ (mget % 0) (mget % 3)) points)
        y (map #(/ (mget % 1) (mget % 3)) points)
        z (map #(/ (mget % 2) (mget % 3)) points)]
    {:bottomleftnear (matrix [(apply min x) (apply min y) (apply max z)])
     :toprightfar (matrix [(apply max x) (apply max y) (apply min z)])}))

(defn expand-bounding-box-near
  "Enlarge bounding box towards positive z (near)"
  [bbox z-expand]
  (update bbox :bottomleftnear add (matrix [0 0 z-expand])))

(defn shadow-box-to-ndc
  "Scale and translate light box coordinates to normalised device coordinates"
  [{:keys [bottomleftnear toprightfar]}]
  (let [left   (mget bottomleftnear 0)
        right  (mget toprightfar 0)
        bottom (mget bottomleftnear 1)
        top    (mget toprightfar 1)
        near   (- (mget bottomleftnear 2))
        far    (- (mget toprightfar 2))]
    (matrix [[(/ 2 (- right left))                    0                  0   (- (/ (* 2 left) (- left right)) 1)]
             [                   0 (/ 2 (- top bottom))                  0 (- (/ (* 2 bottom) (- bottom top)) 1)]
             [                   0                    0 (/ 1 (- far near))                  (/ far (- far near))]
             [                   0                    0                  0                                     1]])))

(defn shadow-box-to-map
  "Scale and translate light box coordinates to shadow map texture coordinates"
  [bounding-box]
  (mmul (matrix [[0.5 0 0 0.5] [0 0.5 0 0.5] [0 0 1 0] [0 0 0 1]]) (shadow-box-to-ndc bounding-box)))

(defn orthogonal
  "Create orthogonal vector to specified 3D vector"
  [n]
  (let [b (first (sort-by #(abs (dot n %)) (identity-matrix 3)))]
    (normalise (cross n b))))

(defn oriented-matrix
  "Create a 3x3 isometry with given normal vector as first row"
  [n]
  (let [o1 (orthogonal n)
        o2 (cross n o1)]
    (matrix [n o1 o2])))

(defn orient-to-light
  "Return matrix to rotate points into coordinate system with z-axis pointing towards the light"
  [light-vector]
  (let [o (oriented-matrix light-vector)]
    (matrix [[(mget o 1 0) (mget o 1 1) (mget o 1 2) 0]
             [(mget o 2 0) (mget o 2 1) (mget o 2 2) 0]
             [(mget o 0 0) (mget o 0 1) (mget o 0 2) 0]
             [           0            0            0 1]])))

(defn shadow-matrices
  "Choose NDC and texture coordinate matrices for shadow mapping"
  [projection-matrix transform light-vector longest-shadow]
  (let [points       (map #(mmul transform %) (frustum-corners projection-matrix))
        light-matrix (orient-to-light light-vector)
        bbox         (expand-bounding-box-near (bounding-box (map #(mmul light-matrix %) points)) longest-shadow)
        shadow-ndc   (shadow-box-to-ndc bbox)
        shadow-map   (shadow-box-to-map bbox)
        depth        (- (mget (:bottomleftnear bbox) 2) (mget (:toprightfar bbox) 2))]
    {:shadow-ndc-matrix (mmul shadow-ndc light-matrix) :shadow-map-matrix (mmul shadow-map light-matrix) :depth depth}))

(defn pack-matrices
  "Pack nested vector of matrices into float array"
  [array]
  (float-array (flatten (map (comp reverse eseq) (flatten array)))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
