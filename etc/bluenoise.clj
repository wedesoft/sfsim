; http://cv.ulichney.com/papers/1993-void-cluster.pdf
(require '[clojure.math :refer (exp)]
         '[sfsim25.util :refer :all]
         '[midje.sweet :refer :all])

(import '[ij.process ByteProcessor]
        '[ij ImagePlus])

(def M 16)
(def Ones 26)

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

(defn initial [M Ones]
  (let [result (boolean-array (* M M))]
    (doseq [index (pick-n (indices M) Ones)]
           (aset-boolean result index true))
    result))

(defn show-bools [M data]
  (let [processor (ByteProcessor. M M (byte-array (map #(if % 0 255) data)))
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))

(defn energy-filter [sigma] (fn [dx dy] (exp (- (/ (+ (* dx dx) (* dy dy)) (* 2 sigma sigma))))))
(def f (energy-filter 1.9))

(defn argmax [arr] (first (apply max-key second (map-indexed vector arr))))
