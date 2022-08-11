(require '[clojure.math :refer (log pow)])

(defn stepping0 [a b offset max-samples]
  {:multiplier (pow (/ (+ b offset) (+ a offset)) (/ 1 max-samples)) :offset offset})

(defn offset0 [a b max-samples max-scale]
  (let [overall-scale (pow max-scale max-samples)]
    (/ (- (* a overall-scale) b) (- 1 overall-scale))))

(defn stepping [a b max-samples max-scale]
  (let [s0     (stepping0 a b 0 max-samples)
        offset (if (<= (:multiplier s0) max-scale) 0 (offset0 a b max-samples max-scale))]
    (stepping0 a b offset max-samples)))

(defn next-point [obj p]
  (- (* (+ p (:offset obj)) (:multiplier obj)) (:offset obj)))

(def o (stepping 1 4 2 2))
(take 3 (iterate #(next-point o %1) 1))

(def o (stepping 1 4 2 1.5))
(take 3 (iterate #(next-point o %1) 1))
