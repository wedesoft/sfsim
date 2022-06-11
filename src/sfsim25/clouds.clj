(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [clojure.core.matrix :refer (matrix add sub)]
              [clojure.core.matrix.linear :refer (norm)]
              [com.climate.claypoole :refer (pfor ncpus)]))

(defn random-points
  "Create a vector of random points"
  [n size]
  (vec (repeatedly n (fn [] (matrix (repeatedly 3 #(rand size)))))))

(defn repeat-points
  "Repeat point cloud in each direction"
  [size points]
  (let [offsets      [0 (- size) size]
        combinations (for [k offsets j offsets i offsets] (matrix [i j k]))]
    (vec (flatten (map (fn [point] (map #(add point %) combinations)) points)))))

(defn normalise-vector
  "Normalise the values of a vector"
  [values]
  (let [maximum (apply max values)]
    (vec (pmap #(/ % maximum) values))))

(defn invert-vector
  "Invert values of a vector"
  [values]
  (mapv #(- 1 %) values))

(defn worley-noise
  "Create 3D Worley noise"
  [n size]
  (let [points (repeat-points size (random-points n size))]
    (invert-vector
      (normalise-vector
        (pfor (+ 2 (ncpus)) [i (range size) j (range size) k (range size)]
              (apply min (map (fn [point] (norm (sub (add (matrix [i j k]) 0.5) point))) points)))))))

(def cloud-track
  "Shader for putting volumetric clouds into the atmosphere"
  (slurp "resources/shaders/clouds/cloud_track.glsl"))
