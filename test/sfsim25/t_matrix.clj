(ns sfsim25.t-matrix
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix mmul sub det dot identity-matrix transpose)]
            [clojure.core.matrix.linear :refer (norm)]
            [clojure.math :refer (sqrt)]
            [sfsim25.matrix :refer :all]
            [sfsim25.quaternion :refer (->Quaternion rotation)]))

(fact "Normalize 3D vector"
  (normalize (matrix [3 0 0])) => (matrix [1 0 0]))

(def pi Math/PI)
(def ca (/ (sqrt 3) 2))
(def sa 0.5)
(def -sa -0.5)

(defn roughly-matrix [m] (fn [x] (< (norm (sub m x)) 1e-6)))

(facts "Rotation matrices"
  (rotation-x (/ pi 6)) => (roughly-matrix (matrix [[1 0 0] [0 ca -sa] [0 sa ca]]))
  (rotation-y (/ pi 6)) => (roughly-matrix (matrix [[ca 0 sa] [0 1 0] [-sa 0 ca]]))
  (rotation-z (/ pi 6)) => (roughly-matrix (matrix [[ca -sa 0] [sa ca 0] [0 0 1]])))

(facts "Comvert rotation quaternion to rotation matrix"
  (quaternion->matrix (->Quaternion 1 0 0 0))               => (roughly-matrix (identity-matrix 3))
  (quaternion->matrix (rotation (/ pi 6) (matrix [1 0 0]))) => (roughly-matrix (rotation-x (/ pi 6)))
  (quaternion->matrix (rotation (/ pi 6) (matrix [0 1 0]))) => (roughly-matrix (rotation-y (/ pi 6)))
  (quaternion->matrix (rotation (/ pi 6) (matrix [0 0 1]))) => (roughly-matrix (rotation-z (/ pi 6))))

(fact "Project homogeneous coordinate to cartesian"
  (project (matrix [4 6 10 2])) => (matrix [2 3 5]))

(fact "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
  (transformation-matrix (matrix [[1 2 3] [5 6 7] [9 10 11]]) (matrix [4 8 12])) => (matrix [[1 2 3 4] [5 6 7 8] [9 10 11 12] [0 0 0 1]]))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(fact "OpenGL projection matrix"
  (let [m (projection-matrix 640 480 5.0 1000.0 (* 0.5 pi))]
    (project (mmul m (matrix [   0    0    -5 1]))) => (roughly-matrix (matrix [0 0 1]) 1e-6)
    (project (mmul m (matrix [   0    0 -1000 1]))) => (roughly-matrix (matrix [0 0 0]) 1e-6)
    (project (mmul m (matrix [1000    0 -1000 1]))) => (roughly-matrix (matrix [1 0 0]) 1e-6)
    (project (mmul m (matrix [   5    0    -5 1]))) => (roughly-matrix (matrix [1 0 1]) 1e-6)
    (project (mmul m (matrix [   0 3.75    -5 1]))) => (roughly-matrix (matrix [0 1 1]) 1e-6)))

(facts "Generate orthogonal vector"
  (dot (orthogonal (matrix [1 0 0])) (matrix [1 0 0])) => 0.0
  (norm (orthogonal (matrix [1 0 0]))) => 1.0
  (dot (orthogonal (matrix [0 1 0])) (matrix [0 1 0])) => 0.0
  (norm (orthogonal (matrix [0 1 0]))) => 1.0
  (dot (orthogonal (matrix [0 0 1])) (matrix [0 0 1])) => 0.0)

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(facts "Generate isometry with given normal vector as first column"
  (let [n (matrix [0.36 0.48 0.8])
        m (oriented-matrix n)]
    (mmul m n) => (roughly-matrix (matrix [1 0 0]) 1e-6)
    (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3) 1e-6)
    (det m) => (roughly 1.0 1e-6)))

(fact "Pack nested vector of matrices into float array"
      (seq (pack-matrices [[(matrix [1 2 3])] [(matrix [4 5 6])]])) => [3.0 2.0 1.0 6.0 5.0 4.0])
