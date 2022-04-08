(require '[clojure.core.matrix :refer (matrix div sub mul add)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (sqrt pow)]
         '[com.climate.claypoole :as cp]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(def radius 6378000.0)
(def height 2000000)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (div (matrix [2e-5 2e-5 2e-5]) 50) :scatter-scale 70000 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (div (matrix [5.8e-6 13.5e-6 33.1e-6]) 50) :scatter-scale 450000})

(def scatter [mie rayleigh])
(def ray-steps 1000)
(def transmittance-planet (partial transmittance earth scatter ray-extremity ray-steps))

(def size 15)
(def power 2)
(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (sqrt (/ (count data) 3))))
(def transmittance-space-planet (transmittance-space earth size power))
(def T (interpolation-table (partition size (map matrix (partition 3 data))) transmittance-space-planet))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (pow (/ (count data) 3) 0.25)))
(def ray-scatter-space-planet (ray-scatter-space earth size power))
(def S (interpolation-table (convert-2d-to-4d (mapv vec (partition (* size size) (map matrix (partition 3 data))))) ray-scatter-space-planet))

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

(def l (matrix [(cos (/ PI 6)) 0 (sin (/ PI 6))]))

(Tt p q true)
(Tt p q false)

(St p q l true)
(St p q l false)

(def w 640)
(def h 480)
(def img {:width w :height h :data (int-array (* w h))})

(cp/pdoseq (+ (cp/ncpus) 2) [y (range h) x (range w)]
           (let [o (matrix [(* (+ radius height) (/ (- x (/ w 2)) (/ h 2)))
                            (* (+ radius height) (/ (- y (/ h 2)) (/ h 2))) (* -2 radius)])
                 d (matrix [0 0 1])
                 a {:sfsim25.sphere/centre (matrix [0 0 0]) :sfsim25.sphere/radius (+ radius height)}
                 r {:sfsim25.ray/origin o :sfsim25.ray/direction d}
                 i (ray-sphere-intersection a r)
                 p (add o (mul (:sfsim25.intersection/distance i) d))
                 s (S p d l false)]
             (set-pixel! img y x (mul 255 s))))
(show-image img)
