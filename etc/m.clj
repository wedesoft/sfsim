(require '[clojure.math :refer (sqrt exp sin cos log)]
         '[clojure.core.matrix :refer (matrix dot)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[midje.sweet :refer :all]
         '[gnuplot.core :as g]
         '[sfsim25.atmosphere :refer (horizon-angle)]
         '[sfsim25.util :refer :all])

(defn horizon-distance [planet radius]
  "Distance from point with specified radius to horizon of planet"
  (sqrt (- (sqr radius) (sqr (:sfsim25.sphere/radius planet)))))

(facts "Distance from point with radius to horizon of planet"
       (horizon-distance #:sfsim25.sphere{:radius 4.0} 4.0) => 0.0
       (horizon-distance #:sfsim25.sphere{:radius 4.0} 5.0) => 3.0)

(defn height-to-index
  "Convert height of point to index"
  [planet size point]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)]
    (* (dec size) (/ (horizon-distance planet (norm point)) (horizon-distance planet (+ radius max-height))))))

(facts "Convert height of point to index"
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4 0 0])) => 0.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [5 0 0])) => 1.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4.5 0 0])) => (roughly 0.687 1e-3)
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 17 (matrix [5 0 0])) => 16.0)

(defn sun-elevation-to-index
  "Convert sun elevation to index"
  [size point light-direction]
  (let [sin-elevation (/ (dot point light-direction) (norm point))]
    (* (dec size) (max 0.0 (/ (- 1 (exp (- 0 (* 3 sin-elevation) 0.6))) (- 1 (exp -3.6)))))))

(facts "Convert sun elevation to index"
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [1 0 0])) => 1.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [0 1 0])) => (roughly 0.464 1e-3)
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-0.2 0.980 0])) => 0.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-1 0 0])) => 0.0
       (sun-elevation-to-index 17 (matrix [4 0 0]) (matrix [1 0 0])) => 16.0)

(defn heading-to-index
  "Convert sun and viewing direction angle to index"
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(facts "Convert sun and viewing direction angle to index"
       (heading-to-index 2 (matrix [0 1 0]) (matrix [0 1 0])) => 1.0
       (heading-to-index 2 (matrix [0 1 0]) (matrix [0 -1 0])) => 0.0
       (heading-to-index 17 (matrix [0 1 0]) (matrix [1 0 0])) => 8.0)

;(def Rg 6360000)
;(def Rt 6420000)
(def Rg 4)
(def Rt 5)
(def H (sqrt (- (* Rt Rt) (* Rg Rg))))

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
    (matrix [mu (sqrt (- 1 (sqr mu))) 0])))
(defn mu-s [u-mu-s] (- (+ (/ (log (- 1 (* u-mu-s (- 1 (exp -3.6))))) 3) 0.2)))
(defn s [u-mu-s u-nu u-r u-mu]
  (let [nu   (- (* 2 u-nu) 1)
        mu   (mu u-r u-mu)
        mu-s (mu-s u-mu-s)
        s1   mu-s
        s2   (/ (- nu (* mu mu-s)) (sqrt (- 1 (sqr mu))))
        s3   (sqrt (- 1 (sqr s1) (sqr s2)))]
    (matrix [s1 s2 s3])))

(def u-r 0.5)
(def u-mu 0.55)
(def u-mu-s 0.6)
(def u-nu 0.2)

(def x (x u-r))
(def v (v u-r u-mu))
(def s (s u-mu-s u-nu u-r u-mu))

(def mu (mu x v))
(def mu-s (mu-s x s))
(def nu (nu v s))
(def r (r x))

(u-r r)
(u-mu r mu)
(u-mu-s mu-s)
(u-nu nu)

; [v1 v2 0]
; [s1 s2? s3?]

; mu-s = s.x / |x|
; nu = v.s
;
; s1 = mu-s
; s2 = (nu - v1 mu-s) / v2
; s3 = sqrt(1 - s1^2 + s2^2)