(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [clojure.core.matrix :refer (matrix)]))

(defn random-points
  "Create a vector of random points"
  [n size]
  (vec (repeatedly n (fn [] (matrix (repeatedly 3 #(rand size)))))))
