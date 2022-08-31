(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [clojure.core.matrix :refer (matrix add sub array slice-view dimension-count)]
              [clojure.core.matrix.linear :refer (norm)]
              [com.climate.claypoole :refer (pfor ncpus)]
              [sfsim25.util :refer (make-progress-bar tick-and-print)]))

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

(defn random-point-grid
  "Create a 3D grid with a random point in each cell"
  ([divisions size]
   (random-point-grid divisions size rand))
  ([divisions size random]
   (let [cellsize (/ size divisions)]
     (array
       (map (fn [k]
                (map (fn [j]
                         (map (fn [i]
                                  (add (matrix [(* i cellsize) (* j cellsize) (* k cellsize)])
                                       (matrix (repeatedly 3 #(random cellsize)))))
                              (range divisions)))
                     (range divisions)))
            (range divisions))))))

(defn- clipped-index-and-offset
  [grid size dimension index]
  (let [divisions     (dimension-count grid dimension)
        clipped-index (if (< index divisions) index (- index divisions))
        offset        (if (< index divisions) 0 size)]
    [clipped-index offset]))

(defn extract-point-from-grid
  "Extract the random point from the given grid cell"
  [grid size k j i]
  (let [[i-clip x-offset] (clipped-index-and-offset grid size 2 i)
        [j-clip y-offset] (clipped-index-and-offset grid size 1 j)
        [k-clip z-offset] (clipped-index-and-offset grid size 0 k)]
    (add (slice-view (slice-view (slice-view grid k-clip) j-clip) i-clip) (matrix [x-offset y-offset z-offset]))))

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
  ([n size] (worley-noise n size false))
  ([n size progress]
   (let [points (repeat-points size (random-points n size))
         bar    (if progress (agent (make-progress-bar (* size size size) 1)))]
     (invert-vector
       (normalise-vector
         (pfor (+ 2 (ncpus)) [i (range size) j (range size) k (range size)]
               (let [result (apply min (map (fn [point] (norm (sub (add (matrix [i j k]) 0.5) point))) points))]
                 (if progress (send bar tick-and-print))
                 result)))))))

(def cloud-track
  "Shader for putting volumetric clouds into the atmosphere"
  (slurp "resources/shaders/clouds/cloud_track.glsl"))

(def cloud-track-base
  "Shader for determining shadowing (or lack of shadowing) by clouds"
  (slurp "resources/shaders/clouds/cloud_track_base.glsl"))

(def sky-outer
  "Shader for determining lighting of atmosphere including clouds coming from space"
  (slurp "resources/shaders/clouds/sky_outer.glsl"))

(def sky-track
  "Shader for determining lighting of atmosphere including clouds between to points"
  (slurp "resources/shaders/clouds/sky_track.glsl"))

(def cloud-shadow
  "Shader for determining illumination of clouds"
  (slurp "resources/shaders/clouds/cloud_shadow.glsl"))

(def cloud-density
  "Shader for determining cloud density at specified point"
  (slurp "resources/shaders/clouds/cloud_density.glsl"))

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear_sampling.glsl"))

(def exponential-sampling
  "Shader functions for defining exponential sampling"
  (slurp "resources/shaders/clouds/exponential_sampling.glsl"))
