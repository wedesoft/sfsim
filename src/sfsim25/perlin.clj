(ns sfsim25.perlin
    "Create improved Perlin noise"
    (:require [clojure.core.matrix :refer (matrix array sub add)]
              [clojure.math :refer (floor)]
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

(set! *unchecked-math* false)
