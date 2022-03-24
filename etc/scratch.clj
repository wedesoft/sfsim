(require '[clojure.core.matrix :refer (matrix div sub)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (sqrt pow)]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.interpolate :refer :all])

(def radius 6378000.0)
(def height 2000000)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (div (matrix [2e-5 2e-5 2e-5]) 50) :scatter-scale 70000 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (div (matrix [5.8e-6 13.5e-6 33.1e-6]) 50) :scatter-scale 450000})

(def scatter [mie rayleigh])
(def ray-steps 1000)
(def transmittance-planet (partial transmittance earth scatter ray-extremity ray-steps))

(def size 15)
(def power 1)
(def transmittance-space-planet (transmittance-space earth size power))
(def T (interpolate-function transmittance-planet transmittance-space-planet))

(def d (sqrt (- (pow (+ radius height) 2) (pow radius 2))))

(def p (matrix [(* -1 d) 0 (+ radius 1)]))
(def q (matrix [0 0 (+ radius 1)]))
(def dist (norm (sub q p)))
(def direction (div (sub q p) dist))

(def t1 (transmittance-planet p direction))
(def t2 (transmittance-planet q direction))
(div t1 t2)

(def t1 (T p direction))
(def t2 (T q direction))
(div t1 t2)

((elevation-to-index earth size power) p direction)
((elevation-to-index earth size power) q direction)

(horizon-angle earth q)
