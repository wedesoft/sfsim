(ns sfsim25.t-sphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.sphere :refer :all]))

(facts "Determine height above surface for given point"
  (height #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius 10} (matrix [10 0 0])) => 0.0
  (height #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius 10} (matrix [13 0 0])) => 3.0
  (height #:sfsim25.sphere{:centre (matrix [2 0 0]) :radius 10} (matrix [13 0 0])) => 1.0)

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

(facts "Check ray pointing downwards"
       (let [sphere #:sfsim25.sphere{:centre (matrix [3 2 1])}]
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [5 2 1]) :direction (matrix [-1 0 0])}) => true
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [5 2 1]) :direction (matrix [ 1 0 0])}) => false
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [2 2 1]) :direction (matrix [ 1 0 0])}) => true))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

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
