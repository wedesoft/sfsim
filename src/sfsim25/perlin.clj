(ns sfsim25.perlin
    "Create improved Perlin noise"
    (:require [clojure.core.matrix :refer (matrix array sub add slice-view dimension-count dot eseq)]
              [clojure.math :refer (floor pow)]
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
  (-> (* 6 (pow t 5)) (- (* 15 (pow t 4))) (+ (* 10 (pow t 3)))))

(defn interpolation-weights
  "Determine weights for interpolation"
  [ease-curve point]
  (let [[bx by bz] (eseq point)
        [ax ay az] (eseq (sub 1.0 point))]
    (for [z [az bz] y [ay by] x [ax bx]]
         (* (ease-curve z) (ease-curve y) (ease-curve x)))))

(set! *unchecked-math* false)
