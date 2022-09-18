; http://cv.ulichney.com/papers/1993-void-cluster.pdf
(require '[clojure.math :refer (exp)]
         '[com.climate.claypoole :refer (pfor ncpus)]
         '[midje.sweet :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij.process ByteProcessor]
        '[ij ImagePlus])

(defn show-bools [m data]
  (let [processor (ByteProcessor. m m (byte-array (map #(if % 0 255) data)))
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))

(defn indices-2d [m] (range (* m m)))

(fact "Generate indices for 2D array"
      (indices-2d 3) => [0 1 2 3 4 5 6 7 8])

(defn pick-n
  ([arr n order] (take n (order arr)))
  ([arr n] (pick-n arr n shuffle)))

(facts "Pick n random values from array"
       (pick-n [0 1 2 3] 2 identity) => [0 1]
       (pick-n [0 1 2 3] 2 reverse) => [3 2]
       (count (pick-n [0 1 2 3] 2)) => 2)

(defn scatter-mask [arr m]
  (let [result (boolean-array (* m m))]
    (doseq [index arr]
           (aset-boolean result index true))
    result))

(facts "Scatter given indices on boolean array"
       (seq (scatter-mask [] 2)) => [false false false false]
       (seq (scatter-mask [2] 2)) => [false false true false])

(defn density-function [sigma] (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(facts "Weighting function to determine clusters and voids"
       ((density 1) 0 0) => (roughly 1.0 1e-6)
       ((density 1) 1 0) => (roughly (exp -0.5))
       ((density 1) 0 1) => (roughly (exp -0.5))
       ((density 1) -1 0) => (roughly (exp -0.5))
       ((density 1) 0 -1) => (roughly (exp -0.5))
       ((density 2) 2 0) => (roughly (exp -0.5)))

(defn argmax-with-mask [arr mask]
  (first (apply max-key second (filter (fn [[idx value]] (aget mask idx)) (map-indexed vector arr)))))

(facts "Index of largest element"
       (argmax-with-mask [5 3 2] (boolean-array (repeat 3 true))) => 0
       (argmax-with-mask [3 5 2] (boolean-array (repeat 3 true))) => 1
       (argmax-with-mask [3 5 2] (boolean-array [true false true])) => 0)

(defn argmin-with-mask [arr mask]
  (first (apply min-key second (remove (fn [[idx value]] (aget mask idx)) (map-indexed vector arr)))))

(facts "Index of largest element"
       (argmin-with-mask [2 3 5] (boolean-array (repeat 3 false))) => 0
       (argmin-with-mask [3 2 5] (boolean-array (repeat 3 false))) => 1
       (argmin-with-mask [3 2 5] (boolean-array [false true false])) => 0)

(defn wrap [x m]
  (let [offset (quot m 2)]
    (- (mod (+ x offset) m) offset)))

(facts "Wrap coordinate"
       (wrap 0 5) => 0
       (wrap 2 5) => 2
       (wrap 5 5) => 0
       (wrap 3 5) => -2
       (wrap 2 4) => -2)

(defn density-sample [mask m f cx cy]
  (reduce +
    (for [y (range m) x (range m)]
       (let [index (+ (* y m) x)]
         (if (aget mask index)
           (f (wrap (- x cx) m) (wrap (- y cy) m))
           0)))))

(facts "Compute sample of correlation of binary image with density function"
       (density-sample (boolean-array (repeat 4 false)) 2 (fn [dx dy] 1) 0 0) => 0
       (density-sample (boolean-array [true false false false]) 2 (fn [dx dy] 1) 0 0) => 1
       (density-sample (boolean-array [true false false false]) 2 (fn [dx dy] 2) 0 0) => 2
       (density-sample (boolean-array (repeat 4 true)) 2 (fn [dx dy] 1) 0 0) => 4
       (density-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dx) 1 1) => 0
       (density-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dy) 1 1) => 0
       (density-sample (boolean-array [false false true false]) 2 (fn [dx dy] dx) 1 1) => -1
       (density-sample (boolean-array [false true false false]) 2 (fn [dx dy] dy) 1 1) => -1
       (density-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dx) 2 2) => 0
       (density-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dy) 2 2) => 0)

(defn density-array [mask m f]
  (vec (pfor (+ 2 (ncpus)) [cy (range m) cx (range m)] (density-sample mask m f cx cy))))

(facts "Compute dither array for given boolean mask"
       (density-array (boolean-array [true false false false]) 2 (fn [dx dy] dx)) => [0 -1 0 -1]
       (density-array (boolean-array [true false false false]) 2 (fn [dx dy] dx)) => vector?)

(defn density-change [density m op f index]
  (let [cy (quot index m)
        cx (mod index m)]
    (pfor (+ 2 (ncpus)) [y (range m) x (range m)]
          (let [index (+ (* y m) x)]
            (op (nth density index) (f (wrap (- x cx) m) (wrap (- y cy) m)))))))

(facts "Add/subtract sample from dither array"
       (density-change [0 -1 0 -1] 2 + (fn [dx dy] dx) 0) => [0 -2 0 -2]
       (density-change [0 -1 0 -1] 2 - (fn [dx dy] dx) 0) => [0 0 0 0])

(defn seed-pattern
  ([mask m f] (seed-pattern mask m f (density-array (aclone mask) m f)))
  ([mask m f density]
   (let [cluster (argmax-with-mask density mask)]
     (aset-boolean mask cluster false)
     (let [density (density-change density m - f cluster)
           void    (argmin-with-mask density mask)]
       (aset-boolean mask void true)
       (if (= cluster void)
         mask
         (recur mask m f (density-change density m + f void)))))))

(facts "Initial binary pattern generator"
       (seq (seed-pattern (boolean-array [true false false false]) 2 (density 1.9))) => [false false false true]
       (seq (seed-pattern (boolean-array [true true false false]) 2 (density 1.9))) => [true false false true])

(defn dither-phase1
  ([mask m n f] (dither-phase1 (aclone mask) m n f (density-array mask m f) (int-array (* m m))))
  ([mask m n f density dither]
   (if (zero? n)
     dither
     (let [cluster (argmax-with-mask density mask)]
       (aset-boolean mask cluster false)
       (aset-int dither cluster (dec n))
       (let [density (density-remove density m f cluster)]
         (recur mask m (dec n) f density dither))))))

(facts "Phase 1 dithering"
       (seq (dither-phase1 (boolean-array [true false false false]) 2 1 (density 1.5))) => [0 0 0 0]
       (seq (dither-phase1 (boolean-array [true false false true]) 2 2 (density 1.5))) => [0 0 0 1])

(def m 64)
(def n (* 16 26))
(def mask (scatter-mask (pick-n (indices-2d m) n) m))
(def f (density-function 1.5))
(def density (density-array mask m f))
(def seed (seed-pattern mask m f density))
(def dither (dither-phase1 mask m n f))
;(show-bools m seed)
