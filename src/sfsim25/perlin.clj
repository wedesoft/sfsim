(ns sfsim25.perlin
    "Create improved Perlin noise"
    (:require [clojure.core.matrix :refer (matrix array)]
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
  ([divisions selector]
   (array (repeatedly divisions (fn [] (repeatedly divisions (fn [] (repeatedly divisions selector))))))))

(set! *unchecked-math* false)
