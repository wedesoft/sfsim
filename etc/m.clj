(require '[clojure.math :refer (sqrt exp sin cos)])
(require '[clojure.core.matrix :refer (matrix dot)])
(require '[clojure.core.matrix.linear :refer (norm)])
(require '[gnuplot.core :as g])
(require '[sfsim25.atmosphere :refer (horizon-angle)])


(def Rg 6360000)
(def Rt 6420000)
(def H (sqrt (- (* Rt Rt) (* Rg Rg))))

(defn sqr [v] (* v v))
(defn r [x] (norm x))
(defn rho [r] (sqrt (- (sqr r) (sqr Rg))))
(defn mu [x v] (/ (dot v x) (r x)))
(defn mu-s [x s] (/ (dot s x) (r x)))
(defn nu [v s] (dot v s))
(defn Delta [r mu] (- (* (sqr r) (sqr mu)) (sqr (rho r))))

(defn u-r [r] (/ (rho r) H))
(defn u-mu [r mu]
  (let [D (Delta r mu)]
    (if (and (< mu 0) (> D 0))
      (+ 0.5 (/ (+ (* r mu) (sqrt D)) (* 2 (rho r))))
      (- 0.5 (/ (- (* r mu) (sqrt (+ D (sqr H)))) (+ (* 2 (rho r)) (* 2 H)))))))
(defn u-mu-s [mu-s] (/ (- 1 (exp (- 0 (* 3 mu-s) 0.6))) (- 1 (exp -3.6))))
(defn u-nu [nu] (/ (+ 1 nu) 2))

(g/raw-plot! [[:plot (g/list ["-" :title "u-mu-s" :with :lines]
                             ["-" :title "u-r" :with :lines]
                             ["-" :title "u-mu" :with :lines])]]
             [(mapv (fn [mu-s] [mu-s (u-mu-s mu-s)]) (range -0.2 1.0 0.001))
              (mapv (fn [r] [(- (* 2.0 (/ (- r Rg) (- Rt Rg))) 1) (u-r r)]) (range Rg Rt 100))
              (mapv (fn [mu] [mu (u-mu 6361000 mu)]) (range -1.0 1.0 0.001))])

(defn rho [u-r] (* H u-r))
(defn r [u-r] (sqrt (+ (sqr (rho u-r)) (sqr Rg))))
(defn x [u-r] (matrix [(r u-r) 0 0]))
(defn mu [u-r u-mu]
  (let [r (r u-r)]
    (if (< u-mu 0.5)
      (let [h (* (rho u-r) (- 1 (* 2 u-mu)))]
        (/ (- (sqr Rg) (sqr r) (sqr h)) (* 2 r h)))
      (let [h (* (+ (rho u-r) H) (- (* 2 u-mu) 1))]
        (/ (- (sqr Rt) (sqr r) (sqr h)) (* 2 r h))))))
(defn v [u-r u-mu]
  (let [mu (mu u-r u-mu)]
    (matrix [mu (sqrt (- 1 (sqr mu)))])))
