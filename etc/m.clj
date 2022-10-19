(require '[clojure.math :refer (sqrt exp sin cos log acos)]
         '[clojure.core.matrix :refer (matrix dot sub normalise mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.conftest :refer (roughly-matrix)]
         '[sfsim25.atmosphere :refer (is-above-horizon?)]
         '[midje.sweet :refer :all]
         '[gnuplot.core :as g]
         '[sfsim25.util :refer :all])
(import '[mikera.vectorz Vector])

(defn horizon-distance [planet radius]
  "Distance from point with specified radius to horizon of planet"
  (sqrt (- (sqr radius) (sqr (:sfsim25.sphere/radius planet)))))

(facts "Distance from point with radius to horizon of planet"
       (horizon-distance {:sfsim25.sphere/radius 4.0} 4.0) => 0.0
       (horizon-distance {:sfsim25.sphere/radius 4.0} 5.0) => 3.0)

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

(defn index-to-height
  "Convert index to point with corresponding height"
  [planet size index]
  (let [radius       (:sfsim25.sphere/radius planet)
        max-height   (:sfsim25.atmosphere/height planet)
        max-horizon  (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
        horizon-dist (* (/ index (dec size)) max-horizon)]
    (matrix [(sqrt (+ (sqr radius) (sqr horizon-dist))) 0 0])))

(facts "Convert index to point with corresponding height"
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.0) => (matrix [4 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 1.0) => (matrix [5 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.68718) => (roughly-matrix (matrix [4.5 0 0]) 1e-3)
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 3 2.0) => (matrix [5 0 0]))

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

(defn index-to-sin-sun-elevation
  "Convert index to sinus of sun elevation"
  [size index]
  (/ (+ (log (- 1 (* index (- 1 (exp -3.6))))) 0.6) -3))

(facts "Convert index to sinus of sun elevation"
       (index-to-sin-sun-elevation 2 1.0) => (roughly 1.0 1e-3)
       (index-to-sin-sun-elevation 2 0.0) => (roughly -0.2 1e-3)
       (index-to-sin-sun-elevation 2 0.463863) => (roughly 0.0 1e-3))

(defn heading-to-index  ; TODO: rename
  "Convert sun and viewing direction angle to index"
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(facts "Convert sun and viewing direction angle to index"
       (heading-to-index 2 (matrix [0 1 0]) (matrix [0 1 0])) => 1.0
       (heading-to-index 2 (matrix [0 1 0]) (matrix [0 -1 0])) => 0.0
       (heading-to-index 17 (matrix [0 1 0]) (matrix [1 0 0])) => 8.0)

(defn elevation-to-index
  "Convert elevation to index depending on height"
  [planet size point direction above-horizon]
  (let [epsilon       1e-3
        radius        (norm point)
        ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        sin-elevation (/ (dot point direction) radius)
        rho           (horizon-distance planet radius)
        Delta         (- (sqr (* radius sin-elevation)) (sqr rho))
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))]
    (* (dec size)
       (if above-horizon
         (- 0.5 (/ (- (* radius sin-elevation) (sqrt (max 0 (+ Delta (sqr H))))) (+ (* 2 rho) (* 2 H))))
         (+ 0.5 (/ (+ (* radius sin-elevation) (sqrt (max 0 Delta))) (* 2 (max rho epsilon))))))))

(facts "Convert elevation to index"
       (let [planet {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1}]
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [-1 0 0]) false) => (roughly 0.5 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-1 0 0]) false) => (roughly (/ 1 3) 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [(- (sqrt 0.5)) (sqrt 0.5) 0]) false) => (roughly 0.223 1e-3)
         (elevation-to-index planet 3 (matrix [4 0 0]) (matrix [-1 0 0]) false) => (roughly 1.0 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-0.6 0.8 0]) false) => (roughly 0.0 1e-3)
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [1 0 0]) true) => (roughly (/ 2 3) 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [0 1 0]) true) => (roughly 0.5 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-0.6 0.8 0]) true) => (roughly 1.0 1e-3)
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [0 1 0]) true) => (roughly 1.0 1e-3)
         (elevation-to-index planet 3 (matrix [4 0 0]) (matrix [0 1 0]) true) => (roughly 2.0 1e-3)))

(defn index-to-elevation
  "Convert index and radius to elevation"
  [planet size radius index]
  (let [epsilon      1e-3
        ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        horizon-dist  (horizon-distance planet radius)
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))
        scaled-index  (/ index (dec size))]
    (if (<= scaled-index 0.5)
      (let [ground-dist   (max epsilon (* horizon-dist (- 1 (* 2 scaled-index))))
            sin-elevation (max -1.0 (/ (- (sqr ground-radius) (sqr radius) (sqr ground-dist)) (* 2 radius ground-dist)))]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) false])
      (let [sky-dist      (* (+ horizon-dist H) (- (* 2 scaled-index) 1))
            sin-elevation (min 1.0 (/ (- (sqr top-radius) (sqr radius) (sqr sky-dist)) (* 2 radius sky-dist)))]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) true]))))

(facts "Convert index and height to elevation"
       (let [planet {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1}]
         (first (index-to-elevation planet 2 5.0 (/ 1 3))) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 3 5.0 (/ 2 3))) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (second (index-to-elevation planet 2 5.0 (/ 1 3))) => false
         (first (index-to-elevation planet 2 5.0 0.222549)) => (roughly-matrix (matrix [(- (sqrt 0.5)) (sqrt 0.5) 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.4)) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.4)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 (/ 2 3))) => (roughly-matrix (matrix [1 0 0]) 1e-3)
         (first (index-to-elevation planet 3 4.0 (/ 4 3))) => (roughly-matrix (matrix [1 0 0]) 1e-3)
         (second (index-to-elevation planet 2 4.0 (/ 2 3))) => true
         (first (index-to-elevation planet 2 4.0 1.0)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 1.0)) => (roughly-matrix (matrix [-0.6 0.8 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.5)) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.50001)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.5)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.50001)) => (roughly-matrix (matrix [1 0 0]) 1e-3)))


(defn transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [planet shape]
  (fn [point direction above-horizon]
      [(height-to-index planet (first shape) point)
       (elevation-to-index planet (second shape) point direction above-horizon)]))

(defn transmittance-backward
  "Backward transformation for looking up transmittance values"
  [planet shape]
  (fn [height-index elevation-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (mget point 0) elevation-index)]
        [point direction above-horizon])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet shape]
  #:sfsim25.interpolate{:shape shape :forward (transmittance-forward planet shape) :backward (transmittance-backward planet shape)})


(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   100000.0
             earth    {:sfsim25.sphere/radius radius :sfsim25.atmosphere/height height}
             space    (transmittance-space earth [15 17])
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [15 17]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) true) => [0.0 16.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [0 1 0]) true) => [14.0 8.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0]) false) => [0.0 8.0]
         (first (backward 0.0 16.0)) => (matrix [radius 0 0])
         (first (backward 14.0 8.0)) => (matrix [(+ radius height) 0 0])
         (second (backward 0.0 16.0)) => (matrix [0 1 0])
         (second (backward 14.0 8.0)) => (matrix [-1 0 0])
         (third (backward 0.0 16.0)) => true
         (third (backward 14.0 8.0)) => false))

(def Rg 6360000)
(def Rt 6420000)
;(def Rg 4)
;(def Rt 5)
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
