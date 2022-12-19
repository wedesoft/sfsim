(ns sfsim25.t-matrix
  (:require [midje.sweet :refer :all]
            [sfsim25.conftest :refer (roughly-matrix)]
            [clojure.core.matrix :refer (matrix mmul mget det dot identity-matrix transpose)]
            [clojure.core.matrix.linear :refer (norm)]
            [clojure.math :refer (sqrt PI)]
            [sfsim25.matrix :refer :all]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(fact "Normalize 3D vector"
  (normalize (matrix [3 0 0])) => (matrix [1 0 0]))

(def ca (/ (sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(facts "Rotation matrices"
  (rotation-x (/ PI 6)) => (roughly-matrix (matrix [[1 0 0] [0 ca -sa] [0 sa ca]]) 1e-6)
  (rotation-y (/ PI 6)) => (roughly-matrix (matrix [[ca 0 sa] [0 1 0] [-sa 0 ca]]) 1e-6)
  (rotation-z (/ PI 6)) => (roughly-matrix (matrix [[ca -sa 0] [sa ca 0] [0 0 1]]) 1e-6))

(facts "Comvert rotation quaternion to rotation matrix"
  (quaternion->matrix (->Quaternion 1 0 0 0))               => (roughly-matrix (identity-matrix 3) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (matrix [1 0 0]))) => (roughly-matrix (rotation-x (/ PI 6)) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (matrix [0 1 0]))) => (roughly-matrix (rotation-y (/ PI 6)) 1e-6)
  (quaternion->matrix (rotation (/ PI 6) (matrix [0 0 1]))) => (roughly-matrix (rotation-z (/ PI 6)) 1e-6))

(fact "Project homogeneous coordinate to cartesian"
  (project (matrix [4 6 10 2])) => (matrix [2 3 5]))

(fact "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
  (transformation-matrix (matrix [[1 2 3] [5 6 7] [9 10 11]]) (matrix [4 8 12])) => (matrix [[1 2 3 4] [5 6 7 8] [9 10 11 12] [0 0 0 1]]))

(facts "OpenGL projection matrix"
  (let [m (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))]
    (project (mmul m (matrix [   0    0    -5 1]))) => (roughly-matrix (matrix [0 0 1]) 1e-6)
    (project (mmul m (matrix [   0    0 -1000 1]))) => (roughly-matrix (matrix [0 0 0]) 1e-6)
    (project (mmul m (matrix [1000    0 -1000 1]))) => (roughly-matrix (matrix [1 0 0]) 1e-6)
    (project (mmul m (matrix [   5    0    -5 1]))) => (roughly-matrix (matrix [1 0 1]) 1e-6)
    (project (mmul m (matrix [   0 3.75    -5 1]))) => (roughly-matrix (matrix [0 1 1]) 1e-6)))

(facts "Corners of OpenGL frustum"
       (let [m       (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             corners (frustum-corners m)]
         (project (mmul m (nth corners 0))) => (roughly-matrix (matrix [-1 -1 1]) 1e-6)
         (project (mmul m (nth corners 1))) => (roughly-matrix (matrix [ 1 -1 1]) 1e-6)
         (project (mmul m (nth corners 2))) => (roughly-matrix (matrix [-1  1 1]) 1e-6)
         (project (mmul m (nth corners 3))) => (roughly-matrix (matrix [ 1  1 1]) 1e-6)
         (project (mmul m (nth corners 4))) => (roughly-matrix (matrix [-1 -1 0]) 1e-6)
         (project (mmul m (nth corners 5))) => (roughly-matrix (matrix [ 1 -1 0]) 1e-6)
         (project (mmul m (nth corners 6))) => (roughly-matrix (matrix [-1  1 0]) 1e-6)
         (project (mmul m (nth corners 7))) => (roughly-matrix (matrix [ 1  1 0]) 1e-6)))

(facts "3D bounding box for set of points"
       (:bottomleftnear (bounding-box [(matrix [2 3 -5 1])])) => (matrix [2 3 -5])
       (:toprightfar (bounding-box [(matrix [2 3 -5 1])])) => (matrix [2 3 -5])
       (:bottomleftnear (bounding-box [(matrix [2 3 -5 1]) (matrix [1 2 -3 1])])) => (matrix [1 2 -3])
       (:toprightfar (bounding-box [(matrix [2 3 -5 1]) (matrix [5 7 -11 1])])) => (matrix [5 7 -11])
       (:bottomleftnear (bounding-box [(matrix [4 6 -10 2])])) => (matrix [2 3 -5]))

(facts "Expand near portion of bounding box"
       (let [bbox {:bottomleftnear (matrix [2 3 -5]) :toprightfar (matrix [7 11 -13])}]
         (expand-bounding-box-near bbox 0) => bbox
         (expand-bounding-box-near bbox 1) => {:bottomleftnear (matrix [2 3 -4]) :toprightfar (matrix [7 11 -13])}))

(facts "Scale and translate light box coordinates to normalised device coordinates"
       (let [m (shadow-box-to-ndc {:bottomleftnear (matrix [2 3 -5]) :toprightfar (matrix [7 11 -13])})]
         (project (mmul m (matrix [2  3  -5 1]))) => (roughly-matrix (matrix [-1 -1 1]) 1e-6)
         (project (mmul m (matrix [7  3  -5 1]))) => (roughly-matrix (matrix [ 1 -1 1]) 1e-6)
         (project (mmul m (matrix [2 11  -5 1]))) => (roughly-matrix (matrix [-1  1 1]) 1e-6)
         (project (mmul m (matrix [2  3 -13 1]))) => (roughly-matrix (matrix [-1 -1 0]) 1e-6)))

(facts "Scale and translate light box coordinates to shadow map coordinates"
       (let [m (shadow-box-to-map {:bottomleftnear (matrix [2 3 -5]) :toprightfar (matrix [7 11 -13])})]
         (project (mmul m (matrix [2  3  -5 1]))) => (roughly-matrix (matrix [ 0  0 1]) 1e-6)
         (project (mmul m (matrix [7  3  -5 1]))) => (roughly-matrix (matrix [ 1  0 1]) 1e-6)
         (project (mmul m (matrix [2 11  -5 1]))) => (roughly-matrix (matrix [ 0  1 1]) 1e-6)
         (project (mmul m (matrix [2  3 -13 1]))) => (roughly-matrix (matrix [ 0  0 0]) 1e-6)))

(facts "Generate orthogonal vector"
  (dot (orthogonal (matrix [1 0 0])) (matrix [1 0 0])) => 0.0
  (norm (orthogonal (matrix [1 0 0]))) => 1.0
  (dot (orthogonal (matrix [0 1 0])) (matrix [0 1 0])) => 0.0
  (norm (orthogonal (matrix [0 1 0]))) => 1.0
  (dot (orthogonal (matrix [0 0 1])) (matrix [0 0 1])) => 0.0)

(facts "Generate isometry with given normal vector as first row"
  (let [n (matrix [0.36 0.48 0.8])
        m (oriented-matrix n)]
    (mmul m n) => (roughly-matrix (matrix [1 0 0]) 1e-6)
    (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3) 1e-6)
    (det m) => (roughly 1.0 1e-6)))

(facts "Rotate points into coordinate system with z-axis pointing towards the light"
       (let [m (orient-to-light (matrix [0.64 0.48 0.6]))]
         (mmul m (matrix [0 0 0 1])) => (roughly-matrix (matrix [0 0 0 1]) 1e-6)
         (mmul m (matrix [0.64 0.48 0.6 1])) => (roughly-matrix (matrix [0 0 1 1]) 1e-6)
         (mmul m (transpose m)) => (roughly-matrix (identity-matrix 4) 1e-6)
         (det m) => (roughly 1.0 1e-6)))

(facts "Choose NDC and texture coordinate matrices for shadow mapping"
       (let [projection (projection-matrix 640 480 5.0 1000.0 (* 0.5 PI))
             transform1 (identity-matrix 4)
             transform2 (transformation-matrix (rotation-x (/ PI 2)) (matrix [0 0 0]))
             light_direction (matrix [0 1 0])]
         (mmul (:shadow-ndc-matrix (shadow-matrices 512 512 projection transform1 light_direction 0)) (matrix [0 750 -1000 1]))
         => (roughly-matrix (matrix [1 0 1 1]) 1e-6)
         (mmul (:shadow-ndc-matrix (shadow-matrices 512 512 projection transform1 light_direction 0)) (matrix [0 -750 -1000 1]))
         => (roughly-matrix (matrix [1 0 0 1]) 1e-6)
         (mmul (:shadow-ndc-matrix (shadow-matrices 512 512 projection transform2 light_direction 0)) (matrix [0 1000 0 1]))
         => (roughly-matrix (matrix [0 0 1 1]) 1e-6)
         (mmul (:shadow-ndc-matrix (shadow-matrices 512 512 projection transform2 light_direction 0)) (matrix [0 5 0 1]))
         => (roughly-matrix (matrix [0 0 0 1]) 1e-6)
         (mmul (:shadow-map-matrix (shadow-matrices 512 512 projection transform1 light_direction 0)) (matrix [0 -750 -1000 1]))
         => (roughly-matrix (matrix [1 0.5 0 1]) 1e-6)
         (mmul (:shadow-map-matrix (shadow-matrices 512 512 projection transform1 light_direction 0)) (matrix [0 750 -1000 1]))
         => (roughly-matrix (matrix [1 0.5 1 1]) 1e-6)
         (mmul (:shadow-map-matrix (shadow-matrices 512 512 projection transform2 light_direction 0)) (matrix [0 1000 0 1]))
         => (roughly-matrix (matrix [0.5 0.5 1 1]) 1e-6)
         (mmul (:shadow-map-matrix (shadow-matrices 512 512 projection transform2 light_direction 500)) (matrix [0 1500 0 1]))
         => (roughly-matrix (matrix [0.5 0.5 1 1]) 1e-6)
         (:depth (shadow-matrices 512 512 projection transform1 light_direction 0))
         => (roughly 1500 1e-6)))

(fact "Pack nested vector of matrices into float array"
      (seq (pack-matrices [[(matrix [1 2 3])] [(matrix [4 5 6])]])) => [3.0 2.0 1.0 6.0 5.0 4.0])
