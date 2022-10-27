(require '[sfsim25.atmosphere :refer :all])
(require '[sfsim25.interpolate :refer :all])
(require '[clojure.core.matrix :refer (matrix)])
(require '[clojure.math :refer (sin cos PI)])

(def radius 6378000)
(def max-height 100000)
(def ray-steps 10)
(def size1 7)
(def size2 7)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000})
(def scatter [mie rayleigh])
(def transmittance-earth (partial transmittance earth scatter ray-steps))
(def transmittance-space-earth (transmittance-space earth [size1 size2]))
(def point-scatter-earth (partial point-scatter-base earth scatter ray-steps (matrix [1 1 1])))
(def ray-scatter-earth (partial ray-scatter earth scatter ray-steps point-scatter-earth))
(def T (interpolate-function transmittance-earth transmittance-space-earth))
(def ray-scatter-space-earth (ray-scatter-space earth [size1 size2 size2 size2]))
(def S (interpolate-function ray-scatter-earth ray-scatter-space-earth))

(T (matrix [radius 0 0]) (matrix [1 0 0]) true)
(T (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(transmittance-earth (matrix [radius 0 0]) (matrix [1 0 0]))
(transmittance-earth (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)

(def forward (:sfsim25.interpolate/forward transmittance-space-earth))
(def backward (:sfsim25.interpolate/backward transmittance-space-earth))
(forward (matrix [(+ radius max-height) 0 0]) (matrix [1 0 0]) true)
(backward 127.0 64.0)

(transmittance-earth (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
(T (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
(ray-scatter-earth (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)
(S (matrix [0 (+ radius 1000) 0]) (matrix [0 (sin 0.2) (- (cos 0.2))]) (matrix [0 (sin 0.2) (- (cos 0.2))]) true)

(map
  (fn [angle]
      (let [direction (matrix [(cos angle) (sin angle) 0])]
        (println (transmittance-earth (matrix [(+ radius max-height) 0 0]) direction true)
                 (forward (matrix [(+ radius max-height) 0 0]) direction true)
                 (T (matrix [(+ radius max-height) 0 0]) direction true))))
     (range (/ (- PI) 2) (/ (+ PI) 2) 0.2))
