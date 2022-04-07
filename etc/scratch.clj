(require '[clojure.core.matrix :refer (matrix add mul div sub mmul inverse)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.core.async :refer (go-loop chan <! <!! >! >!! poll! close!)]
         '[clojure.math :refer (PI cos sin sqrt pow to-radians)]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.planet :refer :all]
         '[sfsim25.quadtree :refer :all]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.planet :refer :all]
         '[sfsim25.util :refer :all])

(def radius 6378000.0)
(def height (* 50 35000.0))
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (div (matrix [2e-5 2e-5 2e-5]) 50) :scatter-scale (* 50 1200) :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (div (matrix [5.8e-6 13.5e-6 33.1e-6]) 50) :scatter-scale (* 50 8000)})
(def scatter [mie rayleigh])
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

(def p (matrix [(* -1 d) 0 radius]))
(def q (matrix [0 0 radius]))
(def dist (norm (sub q p)))
(def direction (div (sub q p) dist))

(def t1 (T p direction true))
(def t2 (T q direction true))
(div t1 t2)
(T p direction false)

(def l (matrix [(cos (/ PI 6)) 0 (sin (/ PI 6))]))
(def s1 (S p direction l true))
(def s2 (S q direction l true))
(sub s1 (mul (div t1 t2) s2))
(S p direction l false)
