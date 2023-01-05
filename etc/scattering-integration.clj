(require '[clojure.math :refer (exp pow PI)])

(defn sqr
  "Square of x"
  ^double [^double x]
  (* x x))

(defn phase
  "Mie scattering phase function by Henyey-Greenstein depending on assymetry g and mu = cos(theta)"
  [scatter-g mu]
  (let [scatter-g-sqr (sqr scatter-g)]
    (/ (* 3 (- 1 scatter-g-sqr) (+ 1 (sqr mu)))
       (* 8 PI (+ 2 scatter-g-sqr) (pow (- (+ 1 scatter-g-sqr) (* 2 scatter-g mu)) 1.5)))))

(def anisotropic 0.3)
(def cloud-scatter-amount 1.0)
(defn scatter-amount [mu] (* (+ (* anisotropic (phase 0.76 mu)) (- 1 anisotropic)) cloud-scatter-amount))
(def cloud-multiplier 0.001)
(def density cloud-multiplier)

(defn testshadow [n stepsize]
  (pow (exp (* (- (scatter-amount -1) 1) density stepsize)) n))

(testshadow 1 200.0)
(testshadow 2 100.0)
(testshadow 10 20.0)

(defn testcloud [n stepsize mu]
  (if (zero? n)
    0.0
    (let [scatter       (scatter-amount mu)
          transmittance (exp (- (* density stepsize)))
          intensity     1.0
          cloud         (* (- 1 transmittance) scatter intensity)]
      (+ (* (testcloud (dec n) stepsize mu) transmittance) cloud))))

(testcloud 1 200.0 0.3)
(testcloud 2 100.0 0.3)
(testcloud 10 20.0 0.3)
