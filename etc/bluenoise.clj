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
  ([arr n] (pick-n arr n shuffle))
  ([arr n order] (take n (order arr))))

(facts "Pick n random values from array"
       (pick-n [0 1 2 3] 2 identity) => [0 1]
       (pick-n [0 1 2 3] 2 reverse) => [3 2]
       (count (pick-n [0 1 2 3] 2)) => 2)

(defn scatter-mask [arr m]
  (reduce #(assoc %1 %2 true) (vec (repeat (* m m) false)) arr))

(facts "Scatter given indices on boolean array"
       (scatter-mask [] 2) => [false false false false]
       (scatter-mask [2] 2) => [false false true false])

(defn density-function [sigma] (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(facts "Weighting function to determine clusters and voids"
       ((density-function 1) 0 0) => (roughly 1.0 1e-6)
       ((density-function 1) 1 0) => (roughly (exp -0.5))
       ((density-function 1) 0 1) => (roughly (exp -0.5))
       ((density-function 1) -1 0) => (roughly (exp -0.5))
       ((density-function 1) 0 -1) => (roughly (exp -0.5))
       ((density-function 2) 2 0) => (roughly (exp -0.5)))

(defn argmax-with-mask [arr mask]
  (first (apply max-key second (filter (fn [[idx value]] (nth mask idx)) (map-indexed vector arr)))))

(facts "Index of largest element"
       (argmax-with-mask [5 3 2] (repeat 3 true)) => 0
       (argmax-with-mask [3 5 2] (repeat 3 true)) => 1
       (argmax-with-mask [3 5 2] [true false true]) => 0)

(defn argmin-with-mask [arr mask]
  (first (apply min-key second (remove (fn [[idx value]] (nth mask idx)) (map-indexed vector arr)))))

(facts "Index of largest element"
       (argmin-with-mask [2 3 5] (repeat 3 false)) => 0
       (argmin-with-mask [3 2 5] (repeat 3 false)) => 1
       (argmin-with-mask [3 2 5] [false true false]) => 0)

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
         (if (nth mask index)
           (f (wrap (- x cx) m) (wrap (- y cy) m))
           0)))))

(facts "Compute sample of correlation of binary image with density function"
       (density-sample (repeat 4 false) 2 (fn [dx dy] 1) 0 0) => 0
       (density-sample [true false false false] 2 (fn [dx dy] 1) 0 0) => 1
       (density-sample [true false false false] 2 (fn [dx dy] 2) 0 0) => 2
       (density-sample (repeat 4 true) 2 (fn [dx dy] 1) 0 0) => 4
       (density-sample (repeat 9 true) 3 (fn [dx dy] dx) 1 1) => 0
       (density-sample (repeat 9 true) 3 (fn [dx dy] dy) 1 1) => 0
       (density-sample [false false true false] 2 (fn [dx dy] dx) 1 1) => -1
       (density-sample [false true false false] 2 (fn [dx dy] dy) 1 1) => -1
       (density-sample (repeat 9 true) 3 (fn [dx dy] dx) 2 2) => 0
       (density-sample (repeat 9 true) 3 (fn [dx dy] dy) 2 2) => 0)

(defn density-array [mask m f]
  (vec (pfor (+ 2 (ncpus)) [cy (range m) cx (range m)] (density-sample mask m f cx cy))))

(facts "Compute dither array for given boolean mask"
       (density-array [true false false false] 2 (fn [dx dy] dx)) => [0 -1 0 -1]
       (density-array [true false false false] 2 (fn [dx dy] dx)) => vector?)

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
  ([mask m f] (seed-pattern mask m f (density-array mask m f)))
  ([mask m f density]
   (let [cluster (argmax-with-mask density mask)
         mask    (assoc mask cluster false)]
     (let [density (density-change density m - f cluster)
           void    (argmin-with-mask density mask)
           mask    (assoc mask void true)]
       (if (= cluster void)
         mask
         (recur mask m f (density-change density m + f void)))))))

(facts "Initial binary pattern generator"
       (seed-pattern [true false false false] 2 (density-function 1.9)) => [false false false true]
       (seed-pattern [true true false false] 2 (density-function 1.9)) => [true false false true])

(defn dither-phase1
  ([mask m n f] (dither-phase1 mask m n f (density-array mask m f)))
  ([mask m n f density] (dither-phase1 mask m n f density (vec (repeat (* m m) 0))))
  ([mask m n f density dither]
   (if (zero? n)
     dither
     (let [cluster (argmax-with-mask density mask)
           density (density-change density m - f cluster)
           mask    (assoc mask cluster false)]
       (recur mask m (dec n) f density (assoc dither cluster (dec n)))))))

(facts "Phase 1 dithering"
       (dither-phase1 [true false false false] 2 1 (density-function 1.5)) => [0 0 0 0]
       (dither-phase1 [true false false true] 2 2 (density-function 1.5)) => [0 0 0 1])

(defn dither-phase2
  ([mask m n dither f] (dither-phase2 mask m n dither f (density-array mask m f)))
  ([mask m n dither f density]
   (if (>= n (quot (* m m) 2))
     dither
     (let [void    (argmin-with-mask density mask)
           density (density-change density m + f void)
           mask    (assoc mask void true)]
       (recur mask m (inc n) (assoc dither void n) f density)))))

(facts "Phase 2 dithering"
       (dither-phase2 [true false false true] 2 2 [0 0 0 1] (density-function 1.5)) => [0 0 0 1]
       (dither-phase2 [true false false false] 2 1 [0 0 0 0] (density-function 1.5)) => [0 0 0 1])

(defn dither-phase3
  ([mask m n dither f]
   (let [mask-not (mapv not mask)]
     (dither-phase3 mask-not m n dither f (density-array mask-not m f))))
  ([mask-not m n dither f density]
   (if (>= n (* m m))
     dither
     (let [cluster  (argmax-with-mask density mask-not)
           density  (density-change density m - f cluster)
           mask-not (assoc mask-not cluster false)]
       (recur mask-not m (inc n) (assoc dither cluster n) f density)))))

(fact "Phase 3 dithering"
      (dither-phase3 [true false false true] 2 2 [0 0 0 1] (density-function 1.5)) => [0 3 2 1])

(def m 64)
(def n (* 16 26))
(def mask (scatter-mask (pick-n (indices-2d m) n) m))
(def f (density-function 1.5))
(def density (density-array mask m f))
(def seed (seed-pattern mask m f density))
(show-bools m seed)
(def dither (dither-phase1 mask m n f density))
(def dither (dither-phase2 mask m n dither f density))
(def dither (dither-phase3 mask m (quot (* m m) 2) dither f))
(show-floats {:width m :height m :data (float-array (map #(* % (/ 255.0 64 64)) dither))})
(spit-floats "bluenoise.raw" (float-array (map #(/ % 64 64) dither)))
