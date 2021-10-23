(ns sfsim25.t-sphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.sphere :refer :all]))

(facts "Compute intersection of line with sphere"
  (let [sphere #:sfsim25.sphere{:centre (matrix [0 0 3]) :radius 1}]
    (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [0 1 0])})
    => {:distance 0.0 :length 0.0}
    (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [1 0 0])})
    => {:distance 1.0 :length 2.0}
    (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [ 0 0 3]) :direction (matrix [1 0 0])})
    => {:distance 0.0 :length 1.0}
    (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [ 2 0 3]) :direction (matrix [1 0 0])})
    => {:distance 0.0 :length 0.0}
    (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [2 0 0])})
    => {:distance 0.5 :length 1.0}))

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
    (slice m 1 0) => (roughly-matrix n 1e-6)
    (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3) 1e-6)
    (det m) => (roughly 1.0 1e-6)))

(facts "Integrate over a circle"
  (integrate-circle 64 (fn [x] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
  (integrate-circle 64 (fn [x] (matrix [1]))) => (roughly-matrix (matrix (* 2 Math/PI)) 1e-6))

(facts "Integrate over half unit sphere"
  (let [left (matrix [1 0 0])
        up   (matrix [0 1 0])]
    (integral-half-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 2 Math/PI)]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1 (mget v 1) (mget v 2)]))) => (roughly-matrix (matrix [(* 2 Math/PI) 0 0]) 1e-6)
    (integral-half-sphere 64 up (fn [v] (matrix [(mget v 0) 1 (mget v 2)]))) => (roughly-matrix (matrix [0 (* 2 Math/PI) 0]) 1e-6)))

(facts "Integrate over unit sphere"
  (let [left (matrix [1 0 0])]
    (integral-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 4 Math/PI)]) 1e-6)))
