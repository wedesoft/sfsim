(ns sfsim25.worley
    "Create Worley noise"
    (:require [malli.core :as m]
              [fastmath.vector :refer (vec3 add sub mag)]
              [sfsim25.util :refer (make-progress-bar tick-and-print dimension-count N N0)]
              [sfsim25.matrix :refer (fvec3)]
              [com.climate.claypoole :refer (pfor ncpus)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def worley-size 64)

(def grid (m/schema [:vector [:vector [:vector fvec3]]]))

(defn random-point-grid
  "Create a 3D grid with a random point in each cell"
  {:malli/schema [:=> [:cat N N [:? [:=> :cat :double]]] grid]}
  ([divisions size]
   (random-point-grid divisions size rand))
  ([divisions size random]
   (let [cellsize (/ size divisions)]
     (mapv (fn [k]
               (mapv (fn [j]
                         (mapv (fn [i]
                                   (add (vec3 (* i cellsize) (* j cellsize) (* k cellsize))
                                        (apply vec3 (repeatedly 3 #(random cellsize)))))
                               (range divisions)))
                     (range divisions)))
           (range divisions)))))

(defn- clipped-index-and-offset
  "Return index modulo dimension of grid and offset for corresponding coordinate"
  {:malli/schema [:=> [:cat grid N N N0] [:tuple N0 :double]]}
  [grid size dimension index]
  (let [divisions     (dimension-count grid dimension)
        clipped-index (if (< index divisions) (if (>= index 0) index (+ index divisions)) (- index divisions))
        offset        (if (< index divisions) (if (>= index 0) 0 (- size)) size)]
    [clipped-index offset]))

(defn extract-point-from-grid
  "Extract the random point from the given grid cell"
  {:malli/schema [:=> [:cat grid N :int :int :int] fvec3]}
  [grid size k j i]
  (let [[i-clip x-offset] (clipped-index-and-offset grid size 2 i)
        [j-clip y-offset] (clipped-index-and-offset grid size 1 j)
        [k-clip z-offset] (clipped-index-and-offset grid size 0 k)]
    (add (get-in grid [k-clip j-clip i-clip]) (vec3 x-offset y-offset z-offset))))

(defn closest-distance-to-point-in-grid
  "Return distance to closest point in 3D grid"
  {:malli/schema [:=> [:cat grid N N fvec3] :double]}
  [grid divisions size point]
  (let [cellsize (/ size divisions)
        [x y z]  point
        [i j k]  [(int (quot x cellsize)) (int (quot y cellsize)) (int (quot z cellsize))]
        points   (for [dk [-1 0 1] dj [-1 0 1] di [-1 0 1]] (extract-point-from-grid grid size (+ k dk) (+ j dj) (+ i di)))]
    (apply min (map #(mag (sub point %)) points))))

(defn normalize-vector
  "Normalize the values of a vector by scaling the maximum down to 1.0"
  {:malli/schema [:=> [:cat [:sequential :double]] [:vector :double]]}
  [values]
  (let [maximum (apply max values)]
    (vec (pmap #(/ % maximum) values))))

(defn invert-vector
  "Invert values of a vector"
  {:malli/schema [:=> [:cat [:sequential :double]] [:vector :double]]}
  [values]
  (mapv #(- 1 %) values))

(defn worley-noise
  "Create 3D Worley noise"
  {:malli/schema [:=> [:cat N N [:? :boolean]] [:vector :double]]}
  ([divisions size]
   (worley-noise divisions size false))
  ([divisions size progress]
   (let [grid (random-point-grid divisions size)
         bar  (if progress (agent (make-progress-bar (* size size size) size)) nil)]
     (invert-vector
       (normalize-vector
         (pfor (+ 2 (ncpus)) [k (range size) j (range size) i (range size)]
               (do
                 (when progress (send bar tick-and-print))
                 (closest-distance-to-point-in-grid grid divisions size (vec3 (+ k 0.5) (+ j 0.5) (+ i 0.5))))))))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
