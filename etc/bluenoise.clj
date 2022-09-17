; http://cv.ulichney.com/papers/1993-void-cluster.pdf
(require '[clojure.math :refer (exp)]
         '[sfsim25.util :refer :all]
         '[midje.sweet :refer :all])

(import '[ij.process ByteProcessor]
        '[ij ImagePlus])

(defn show-bools [M data]
  (let [processor (ByteProcessor. M M (byte-array (map #(if % 0 255) data)))
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))

(def m 16)
(def ones 26)

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

(defn density [sigma] (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))

(facts "Weighting function to determine clusters and voids"
       ((density 1) 0 0) => (roughly 1.0 1e-6)
       ((density 1) 1 0) => (roughly (exp -0.5))
       ((density 1) 0 1) => (roughly (exp -0.5))
       ((density 1) -1 0) => (roughly (exp -0.5))
       ((density 1) 0 -1) => (roughly (exp -0.5))
       ((density 2) 2 0) => (roughly (exp -0.5)))

(def f (density 1.9))

(defn argmax [arr] (first (apply max-key second (map-indexed vector arr))))

(facts "Index of largest element"
       (argmax [5 3 2]) => 0
       (argmax [3 5 2]) => 1)

(facts "Wrap coordinate ")

(defn dither-sample [mask m f cx cy]
  (reduce +
    (for [y (range m) x (range m)]
       (let [index (+ (* y m) x)]
         (if (aget mask index)
           (f (- x cx) (- y cy))
           0)))))

(facts "Compute sample of correlation of binary image with density function"
       (dither-sample (boolean-array (repeat 4 false)) 2 (fn [dx dy] 1) 0 0) => 0
       (dither-sample (boolean-array [true false false false]) 2 (fn [dx dy] 1) 0 0) => 1
       (dither-sample (boolean-array [true false false false]) 2 (fn [dx dy] 2) 0 0) => 2
       (dither-sample (boolean-array (repeat 4 true)) 2 (fn [dx dy] 1) 0 0) => 4
       (dither-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dx) 1 1) => 0
       (dither-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dy) 1 1) => 0
       (dither-sample (boolean-array [false true false false]) 2 (fn [dx dy] dx) 0 0) => 1
       (dither-sample (boolean-array [false false true false]) 2 (fn [dx dy] dy) 0 0) => 1
       (dither-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dx) 2 2) => 0
       (dither-sample (boolean-array (repeat 9 true)) 3 (fn [dx dy] dy) 2 2) => 0)
