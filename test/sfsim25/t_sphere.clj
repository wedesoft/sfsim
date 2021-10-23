(ns sfsim25.t-sphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
            [sfsim25.sphere :refer :all]))

(facts "Compute intersection of line with sphere"
  (let [sphere #:sfsim25.sphere{:sphere-centre (matrix [0 0 3]) :sphere-radius 1}]
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
