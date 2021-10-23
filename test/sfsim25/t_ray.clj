(ns sfsim25.t-ray
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix sub mget)]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.ray :refer :all]))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(facts "Integrate over a ray"
  (integral-ray #:sfsim25.ray{:origin (matrix [2 3 5]) :direction (matrix [1 0 0])} 10 0 (fn [x] (matrix [2])))
  => (roughly-matrix (matrix [0]) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (matrix [2 3 5]) :direction (matrix [1 0 0])} 10 3 (fn [x] (matrix [2])))
  => (roughly-matrix (matrix [6]) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (matrix [2 3 5]) :direction (matrix [1 0 0])} 10 3 (fn [x] (matrix [(mget x 0)])))
  => (roughly-matrix (matrix [10.5]) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (matrix [2 3 5]) :direction (matrix [2 0 0])} 10 1.5 (fn [x] (matrix [(mget x 0)])))
  => (roughly-matrix (matrix [10.5]) 1e-6))
