(ns sfsim25.t-sphere
  (:require [midje.sweet :refer :all]
            [sfsim25.conftest :refer (roughly-matrix)]
            [clojure.core.matrix :refer (matrix mget)]
            [clojure.math :refer (PI)]
            [sfsim25.sphere :refer :all]))

(facts "Determine height above surface for given point"
  (height #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius 10} (matrix [10 0 0])) => 0.0
  (height #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius 10} (matrix [13 0 0])) => 3.0
  (height #:sfsim25.sphere{:centre (matrix [2 0 0]) :radius 10} (matrix [13 0 0])) => 1.0)

(facts "Check ray for intersection with sphere"
       (let [sphere #:sfsim25.sphere{:centre (matrix [0 0 3]) :radius 1}]
         (ray-intersects-sphere? sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [0 1 0])}) => false
         (ray-intersects-sphere? sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [1 0 0])}) => true))

(facts "Compute intersection of line with sphere or closest point with sphere"
       (let [sphere #:sfsim25.sphere{:centre (matrix [0 0 3]) :radius 1}]
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [0 1 0])})
         => {:sfsim25.intersection/distance 0.0 :sfsim25.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [1 0 0])})
         => {:sfsim25.intersection/distance 1.0 :sfsim25.intersection/length 2.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [ 0 0 3]) :direction (matrix [1 0 0])})
         => {:sfsim25.intersection/distance 0.0 :sfsim25.intersection/length 1.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [ 2 0 3]) :direction (matrix [1 0 0])})
         => {:sfsim25.intersection/distance 0.0 :sfsim25.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-2 0 3]) :direction (matrix [2 0 0])})
         => {:sfsim25.intersection/distance 0.5 :sfsim25.intersection/length 1.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [-5 0 0]) :direction (matrix [1 0 0])})
         => {:sfsim25.intersection/distance 5.0 :sfsim25.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim25.ray{:origin (matrix [ 5 0 0]) :direction (matrix [1 0 0])})
         => {:sfsim25.intersection/distance 0.0 :sfsim25.intersection/length 0.0}))

(facts "Check ray pointing downwards"
       (let [sphere #:sfsim25.sphere{:centre (matrix [3 2 1])}]
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [5 2 1]) :direction (matrix [-1 0 0])}) => true
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [5 2 1]) :direction (matrix [ 1 0 0])}) => false
         (ray-pointing-downwards sphere #:sfsim25.ray {:origin (matrix [2 2 1]) :direction (matrix [ 1 0 0])}) => true))

(facts "Integrate over a circle"
  (integrate-circle 64 (fn [x] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
  (integrate-circle 64 (fn [x] (matrix [1]))) => (roughly-matrix (matrix (* 2 PI)) 1e-6))

(facts "Integrate over half unit sphere"
  (let [left (matrix [1 0 0])
        up   (matrix [0 1 0])]
    (integral-half-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 2 PI)]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1 (mget v 1) (mget v 2)]))) => (roughly-matrix (matrix [(* 2 PI) 0 0]) 1e-6)
    (integral-half-sphere 64 up (fn [v] (matrix [(mget v 0) 1 (mget v 2)]))) => (roughly-matrix (matrix [0 (* 2 PI) 0]) 1e-6)))

(facts "Integrate over unit sphere"
  (let [left (matrix [1 0 0])]
    (integral-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 4 PI)]) 1e-6)))
