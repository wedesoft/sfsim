(require '[clojure.core.matrix :refer (matrix div sub mul add mget) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.ray :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(def factor 1)
(def radius 6378000.0)
(def height (* factor 35000.0))
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (div (matrix [2e-5 2e-5 2e-5]) factor) :scatter-scale (* factor 1200) :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (div (matrix [5.8e-6 13.5e-6 33.1e-6]) factor) :scatter-scale (* factor 8000)})

(def scatter [mie rayleigh])
(def ray-steps 100)
(def transmittance-planet (partial transmittance earth scatter ray-extremity ray-steps))

(def size 17)
(def power 2)
(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (sqrt (/ (count data) 3))))
(def transmittance-space-planet (transmittance-space earth size power))
(def T (interpolation-table (partition size (map (comp matrix reverse) (partition 3 data))) transmittance-space-planet))
(def TT (partial transmittance earth scatter ray-steps))
(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (pow (/ (count data) 3) 0.25)))
(def ray-scatter-space-planet (ray-scatter-space earth size power))
(def S (interpolation-table (convert-2d-to-4d (mapv vec (partition (* size size) (map (comp matrix reverse) (partition 3 data))))) ray-scatter-space-planet))
(def point-scatter-base-planet (partial point-scatter-base earth scatter ray-steps (matrix [1 1 1])))
(def SS (partial ray-scatter earth scatter ray-steps point-scatter-base-planet))
;(def S (interpolate-function SS ray-scatter-space-planet))

(def d (sqrt (- (pow (+ radius height) 2) (pow radius 2))))

(def v 10000)
(def p (matrix [(* -1 d) 0 (- radius v)]))
(def q (matrix [(* -0.5 d) 0 (- radius v)]))

(defn Tt [p q above]
  (let [direction (normalize (sub q p))]
    (div (T p direction above) (T q direction above))))
(defn St [p q l above]
  (let [direction (normalize (sub q p))]
    (sub (S p direction l above) (mul (Tt p q above) (S q direction l above)))))

(defn TTt [p q above]
  (transmittance earth scatter ray-steps p q))

(defn SSt [x q light-direction above-horizon]
  (let [ray            {:sfsim25.ray/origin x :sfsim25.ray/direction (sub q x)}
        view-direction (normalize (sub q x))]
    (integral-ray ray ray-steps 1.0 #(mul (transmittance earth scatter ray-steps x %) (point-scatter-base-planet % view-direction light-direction above-horizon)))))

(def angle (* 0.7 PI))
(def l (matrix [(sin angle) 0 (- (cos angle))]))

(Tt p q true)
(Tt p q false)

(St p q l true)
(St p q l false)

(def w 1024)
(def h 768)
(def img {:width w :height h :data (int-array (* w h))})

(def n (atom 0))

(defn clipmatrix [m] (matrix [(min 255 (max 0 (mget m 0))) (min 255 (max 0 (mget m 1))) (min 255 (max 0 (mget m 2)))]))

(
;doseq [angle (range (* 0 PI) (* 2.0 PI) (* 0.05 PI))]
let [angle (* 0.7 PI)]
       (let [l (matrix [(sin angle) 0 (- (cos angle))])]
         (cp/pdoseq (+ (cp/ncpus) 2) [y (range h) x (range w)]
                    (let [xx (* (+ radius height) (/ (- x (/ w 2)) (/ h 2)))
                          yy (* (+ radius height) (/ (- y (/ h 2)) (/ h 2)))
                          o (matrix [xx yy (* -2 radius)])
                          d (matrix [0 0 1])
                          z (if (> yy 0) radius (* (- xx) 0.4))
                          a {:sfsim25.sphere/centre (matrix [0 0 0]) :sfsim25.sphere/radius (+ radius height)}
                          e {:sfsim25.sphere/centre (matrix [0 0 0]) :sfsim25.sphere/radius radius}
                          r {:sfsim25.ray/origin o :sfsim25.ray/direction d}
                          i (ray-sphere-intersection a r)
                          j (ray-sphere-intersection e r)
                          b (zero? (:sfsim25.intersection/length j))
                          p (add o (mul (:sfsim25.intersection/distance i) d))
                          q (if b
                              (add o (mul (+ (:sfsim25.intersection/distance i) (:sfsim25.intersection/length i)) d))
                              (add o (mul (:sfsim25.intersection/distance j) d)))
                          qq (if (< z (mget q 2)) (matrix [xx yy z]) q)
                          s (if (and (> (:sfsim25.intersection/length i) 0) (> (mget qq 2) (mget p 2)))
                              (if (or (> yy 0) (< yy (* -0.2 radius))) (St p qq l b) (SSt p qq l b))
                              (matrix [0 0 0]))]
                      (set-pixel! img y x (clipmatrix (mul 20 255 s))))))
       ;(spit-image (format "test%02d.png" @n) img)
       (show-image img)
       (swap! n + 1))

(def delta 20000)
(def xx (+ radius delta))
(def o (matrix [xx 0 (* -2 radius)]))
(def a {:sfsim25.sphere/centre (matrix [0 0 0]) :sfsim25.sphere/radius (+ radius height)})
(def d (matrix [0 0 1]))
(def r {:sfsim25.ray/origin o :sfsim25.ray/direction d})
(def i (ray-sphere-intersection a r))
(def p (add o (mul (:sfsim25.intersection/distance i) d)))
(def z (* (- (mget o 0)) 0.4))
(def qq (matrix [xx 0 z]))
(def b true)
(def direction (normalize (sub qq p)))

(S p direction l b)
(sub (S p direction l b) (mul (Tt p qq b) (S qq direction l b)))
(sub (SS p direction l b) (mul (TTt p qq b) (SS qq direction l b)))
(S p direction l b)
(SS p direction l b)
(S qq direction l b)
(SS qq direction l b)
(St p qq l b)
(SSt p qq l b)
(Tt p qq b)
(TTt p qq b)

(def angle (* 0.3 PI))
(def l (matrix [(sin angle) (cos angle) 0]))

(g/raw-plot! [[:set :title "compare S and SS for different angles"]
              [:plot (g/list ["-" :title "S" :with :lines]
                             ["-" :title "SS" :with :lines])]]
             [(for [angle (range 0 (/ PI 2) 0.01)]
                   [angle
                    (mget (S (matrix [radius 0 0]) (matrix [(sin angle) (cos angle) 0]) l true) 0)])
              (for [angle (range 0 (/ PI 2) 0.01)]
                   [angle
                    (mget (SS (matrix [radius 0 0]) (matrix [(sin angle) (cos angle) 0]) l true) 0)])])

(g/raw-plot! [[:set :title "compare T and TT for different angles"]
              [:plot (g/list ["-" :title "T" :with :lines]
                             ["-" :title "TT" :with :lines])]]
             [(for [angle (range 0 (/ PI 2) 0.01)]
                   [angle
                    (mget (T (matrix [radius 0 0]) (matrix [(sin angle) (cos angle) 0]) true) 0)])
              (for [angle (range 0 (/ PI 2) 0.01)]
                   [angle
                    (mget (TT (matrix [radius 0 0]) (matrix [(sin angle) (cos angle) 0]) true) 0)])])

(g/raw-plot! [[:set :title "compare S and SS for different heights"]
              [:plot (g/list ["-" :title "S" :with :lines]
                             ["-" :title "SS" :with :lines])]]
             [(for [h (range 0 height (/ height 200))]
                   [h
                    (mget (S (matrix [(+ radius h) 0 0]) (matrix [0 1 0]) l true) 0)])
              (for [h (range 0 height (/ height 200))]
                   [h
                    (mget (SS (matrix [(+ radius h) 0 0]) (matrix [0 1 0]) l true) 0)])])

(g/raw-plot! [[:set :title "compare T and TT for different heights"]
              [:plot (g/list ["-" :title "T" :with :lines]
                             ["-" :title "TT" :with :lines])]]
             [(for [h (range 0 height (/ height 200))]
                   [h
                    (mget (T (matrix [(+ radius h) 0 0]) (matrix [0 1 0]) true) 0)])
              (for [h (range 0 height (/ height 200))]
                   [h
                    (mget (TT (matrix [(+ radius h) 0 0]) (matrix [0 1 0]) true) 0)])])
