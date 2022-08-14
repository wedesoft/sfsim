(require '[clojure.math :refer (log pow ceil)])

(defn stepping0 [a b offset samples]
  {:multiplier (pow (/ (+ b offset) (+ a offset)) (/ 1 samples)) :offset offset :samples samples :a a :b b})

(defn offset0 [a b max-samples max-scale]
  (let [overall-scale (pow max-scale max-samples)]
    (/ (- (* a overall-scale) b) (- 1 overall-scale))))

(defn stepping [a b max-samples max-scale]
  (let [s0      (stepping0 a b 0 max-samples)
        offset  (if (<= (:multiplier s0) max-scale) 0 (offset0 a b max-samples max-scale))
        samples (if (<= (:multiplier s0) max-scale) (int (ceil (/ (log (/ b a)) (log max-scale)))) max-samples)]
    (stepping0 a b offset samples)))

(defn next-point [obj p]
  (- (* (+ p (:offset obj)) (:multiplier obj)) (:offset obj)))

(def o (stepping 1 100000 64 1.1))
(take (inc (:samples o)) (iterate #(next-point o %1) (:a o)))
o
