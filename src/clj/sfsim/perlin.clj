;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.perlin
  "Create improved Perlin noise"
  (:require
    [clojure.math :refer (floor)]
    [com.climate.claypoole :refer (pfor ncpus)]
    [fastmath.vector :refer (vec3 add sub dot mult)]
    [sfsim.matrix :refer (fvec3)]
    [sfsim.util :refer (make-progress-bar tick-and-print dimension-count N)])
  (:import
    [fastmath.vector
     Vec3]))


;; improved Perlin noise algorithm
;; https://adrianb.io/2014/08/09/perlinnoise.html

(set! *unchecked-math* :warn-on-boxed)


(defn random-gradient
  "Randomly pick one of twelve gradient vectors"
  {:malli/schema [:=> [:cat [:? fn?]] fvec3]}
  ([]
   (random-gradient rand-nth))
  ([selector]
   (apply vec3
          (selector [[1  1  0] [-1  1  0] [1 -1  0] [-1 -1  0]
                     [1  0  1] [-1  0  1] [1  0 -1] [-1  0 -1]
                     [0  1  1] [ 0 -1  1] [0  1 -1] [ 0 -1 -1]]))))


(defn random-gradient-grid
  "Create a 3D grid with random gradient vectors"
  {:malli/schema [:=> [:cat N [:? [:=> :cat fvec3]]] [:vector [:vector [:vector fvec3]]]]}
  ([divisions]
   (random-gradient-grid divisions random-gradient))
  ([divisions random-gradient]
   (vec (repeatedly divisions
                    (fn random-gradient-table
                      []
                      (vec (repeatedly divisions
                                       (fn random-gradient-row [] (vec (repeatedly divisions random-gradient))))))))))


(defn determine-division
  "Determine division point belongs to"
  {:malli/schema [:=> [:cat fvec3] [:tuple :int :int :int]]}
  [point]
  (mapv (comp int floor) point))


(defn corner-vectors
  "Get 3D vectors pointing to corners of division"
  {:malli/schema [:=> [:cat fvec3] [:vector {:min 8 :max 8} fvec3]]}
  [point]
  (let [division (apply vec3 (determine-division point))]
    (vec (for [z (range 2) y (range 2) x (range 2)]
           (sub point (add division (vec3 x y z)))))))


(defn corner-gradients
  "Get 3D gradient vectors from corners of division"
  {:malli/schema [:=> [:cat [:vector [:vector [:vector fvec3]]] fvec3] [:vector fvec3]]}
  [gradient-grid point]
  (let [[x  y  z] (determine-division point)
        [x+ y+ z+] (mapv #(mod (inc ^double %) (dimension-count gradient-grid 0)) [x y z])]
    (vec (for [zd [z z+] yd [y y+] xd [x x+]]
           (reduce nth gradient-grid [zd yd xd])))))


(defn influence-values
  "Determine influence values"
  {:malli/schema [:=> [:cat [:vector fvec3] [:vector fvec3]] [:vector :double]]}
  [corner-gradients corner-vectors]
  (mapv dot corner-gradients corner-vectors))


(defn ease-curve
  "Monotonous and point-symmetric ease curve"
  ^double [^double t]
  (-> t (* 6.0) (- 15.0) (* t) (+ 10.0) (* t t t)))


(defn interpolation-weights
  "Determine weights for interpolation"
  {:malli/schema [:=> [:cat [:=> [:cat :double] :double] fvec3] [:sequential :double]]}
  [ease-curve ^Vec3 index]
  (let [division   (apply vec3 (determine-division index))
        point      (sub index division)
        [bx by bz] point
        [ax ay az] (sub (vec3 1 1 1) point)]
    (for [z [az bz] y [ay by] x [ax bx]]
      (* ^double (ease-curve z) ^double (ease-curve y) ^double (ease-curve x)))))


(defn normalize-vector
  "Normalize the values of a vector to be between 0.0 and 1.0"
  {:malli/schema [:=> [:cat [:vector :double]] [:vector :double]]}
  [values]
  (let [minimum (apply min values)
        maximum (apply max values)]
    (vec (pmap #(/ (- ^double % ^double minimum) (- ^double maximum ^double minimum)) values))))


(defn perlin-noise-sample
  "Compute a single sample of Perlin noise"
  ^double [gradient-grid ^long divisions ^long size ^Vec3 cell]
  (let [point     (mult cell (/ divisions size))
        corners   (corner-vectors point)
        gradients (corner-gradients gradient-grid point)
        influence (influence-values gradients corners)
        weights   (interpolation-weights ease-curve point)]
    (apply + (mapv * weights influence))))


(defn perlin-noise
  "Create 3D Perlin noise"
  ([^long divisions ^long size]
   (perlin-noise divisions size false))
  ([^long divisions ^long size ^Boolean progress]
   (let [gradient-grid (random-gradient-grid divisions)
         bar           (if progress (agent (make-progress-bar (* size size size) size)) nil)]
     (normalize-vector
       (vec
         (pfor (+ 2 ^long (ncpus)) [^long k (range size) ^long j (range size) ^long i (range size)]
               (do
                 (when progress (send bar tick-and-print))
                 (perlin-noise-sample gradient-grid divisions size (vec3 (+ i 0.5) (+ j 0.5) (+ k 0.5))))))))))


(set! *unchecked-math* false)
