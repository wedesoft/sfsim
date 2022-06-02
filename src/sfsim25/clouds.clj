(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [clojure.core.matrix :refer (matrix add sub)]))

(defn random-points
  "Create a vector of random points"
  [n size]
  (vec (repeatedly n (fn [] (matrix (repeatedly 3 #(rand size)))))))

(defn repeat-points
  "Repeat point cloud in each direction"
  [size points]
  (vec (flatten (map (fn [point] [point
                                  (sub point (matrix [size 0 0]))
                                  (add point (matrix [size 0 0]))
                                  (sub point (matrix [0 size 0]))
                                  (add point (matrix [0 size 0]))
                                  (sub point (matrix [0 0 size]))
                                  (add point (matrix [0 0 size]))])
                     points))))

(defn normalise-vector
  "Normalise the values of an array"
  [values]
  (let [maximum (apply max values)]
    (mapv #(/ % maximum) values)))
