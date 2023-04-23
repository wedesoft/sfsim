(ns sfsim25.perlin
    "Create improved Perlin noise"
    (:require [clojure.core.matrix :refer (matrix array sub add div mul slice-view dimension-count dot eseq)]
              [clojure.math :refer (floor)]
              [com.climate.claypoole :refer (pfor ncpus)]
              [sfsim25.util :refer (make-progress-bar tick-and-print spit-floats)]))

; improved Perlin noise algorithm
; https://adrianb.io/2014/08/09/perlinnoise.html

(set! *unchecked-math* true)

(defn random-gradient
  "Randomly pick one of twelve gradient vectors"
  ([]
   (random-gradient rand-nth))
  ([selector]
   (matrix
     (selector [[ 1  1  0] [-1  1  0] [ 1 -1  0] [-1 -1  0]
                [ 1  0  1] [-1  0  1] [ 1  0 -1] [-1  0 -1]
                [ 0  1  1] [ 0 -1  1] [ 0  1 -1] [ 0 -1 -1]]))))

(defn random-gradient-grid
  "Create a 3D grid with random gradient vectors"
  ([divisions]
   (random-gradient-grid divisions random-gradient))
  ([divisions random-gradient]
   (array (repeatedly divisions (fn [] (repeatedly divisions (fn [] (repeatedly divisions random-gradient))))))))

(defn determine-division
  "Determine division point belongs to"
  [point]
  (mapv (comp int floor) point))

(defn corner-vectors
  "Get 3D vectors pointing to corners of division"
  [point]
  (let [division (matrix (determine-division point))]
    (vec (for [z (range 2) y (range 2) x (range 2)]
              (sub point (add division (matrix [x y z])))))))

(defn corner-gradients
  "Get 3D gradient vectors from corners of division"
  [gradient-grid point]
  (let [[x  y  z ] (determine-division point)
        [x+ y+ z+] (map #(mod (inc %) (dimension-count gradient-grid 0)) [x y z])]
    (vec (for [zd [z z+] yd [y y+] xd [x x+]]
              (reduce slice-view gradient-grid [zd yd xd])))))

(defn influence-values
  "Determine influence values"
  [corner-gradients corner-vectors]
  (mapv dot corner-gradients corner-vectors))

(defn ease-curve
  "Monotonous and point-symmetric ease curve"
  [t]
  (-> t (* 6) (- 15) (* t) (+ 10) (* t t t)))

(defn interpolation-weights
  "Determine weights for interpolation"
  [ease-curve point]
  (let [[bx by bz] (eseq point)
        [ax ay az] (eseq (sub 1.0 point))]
    (for [z [az bz] y [ay by] x [ax bx]]
         (* (ease-curve z) (ease-curve y) (ease-curve x)))))

(defn normalize-vector
  "Normalize the values of a vector to be between 0.0 and 1.0"
  [values]
  (let [minimum (apply min values)
        maximum (apply max values)]
    (vec (pmap #(/ (- % minimum) (- maximum minimum)) values))))

(defn perlin-noise-sample
  "Compute a single sample of Perlin noise"
  [gradient-grid divisions size cell]
  (let [point     (mul cell (/ divisions size))
        corners   (corner-vectors point)
        gradients (corner-gradients gradient-grid point)
        influence (influence-values gradients corners)
        weights   (interpolation-weights ease-curve point)]
    (apply + (map * weights influence))))

(defn perlin-noise
  "Create 3D Perlin noise"
  ([divisions size]
   (perlin-noise divisions size false))
  ([divisions size progress]
   (let [gradient-grid (random-gradient-grid divisions)
         bar           (if progress (agent (make-progress-bar (* size size size) size)))]
     (normalize-vector
       (pfor (+ 2 (ncpus)) [k (range size) j (range size) i (range size)]
             (do
               (if progress (send bar tick-and-print))
               (perlin-noise-sample gradient-grid divisions size (matrix [(+ i 0.5) (+ j 0.5) (+ k 0.5) ]))))))))

(set! *unchecked-math* false)
