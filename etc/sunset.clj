; Ground view of sunset interpolated
; Youtube video size: 426 x 240

(require '[clojure.core.matrix :refer :all])
(require '[clojure.core.matrix.linear :refer (norm)])
(require '[com.climate.claypoole :as cp])
(require '[sfsim25.interpolate :refer :all])
(require '[sfsim25.atmosphere :refer :all])
(require '[sfsim25.matrix :refer :all])
(require '[sfsim25.sphere :refer (ray-sphere-intersection)])
(require '[sfsim25.util :refer :all])
(import '[mikera.vectorz Vector])

(set! *unchecked-math* true)

(def radius 6378000.0)
(def height 35000.0)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000})
(def scatter [mie rayleigh])
(def size 17)
(def steps 100)
(def power 2.0)

;---
(defn transmittance-earth [^Vector x ^Vector v] (transmittance earth scatter ray-extremity steps x v))
(defn surface-radiance-base-earth [^Vector point ^Vector light-direction] (surface-radiance-base earth scatter steps (matrix [1 1 1]) point light-direction))
(defn ray-scatter-base-earth [^Vector point ^Vector direction ^Vector light-direction] (ray-scatter earth scatter ray-extremity steps (partial point-scatter-base earth scatter steps (matrix [1 1 1])) point direction light-direction))

(def transmittance-space-earth (transmittance-space earth size power))
(def surface-radiance-space-earth (surface-radiance-space earth size power))
(def ray-scatter-space-earth (ray-scatter-space earth size power))

(def T (interpolate-function transmittance-earth transmittance-space-earth))
(def E (interpolate-function surface-radiance-base-earth surface-radiance-space-earth))
(def S (interpolate-function ray-scatter-base-earth ray-scatter-space-earth))

;---
(defn T [^Vector x ^Vector v] (transmittance earth scatter ray-extremity steps x v))
(defn E [^Vector point ^Vector light-direction] (surface-radiance-base earth scatter steps (matrix [1 1 1]) point light-direction))
(defn S [^Vector point ^Vector direction ^Vector light-direction] (ray-scatter earth scatter ray-extremity steps (partial point-scatter-base earth scatter steps (matrix [1 1 1])) point direction light-direction))

;---
(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def transmittance-space-earth (transmittance-space earth size power))
(def T (interpolation-table (mapv vec (partition size (map (comp matrix reverse) (partition 3 data)))) transmittance-space-earth))

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def surface-radiance-space-earth (surface-radiance-space earth size power))
(def E (interpolation-table (mapv vec (partition size (map (comp matrix reverse) (partition 3 data)))) surface-radiance-space-earth))

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (Math/pow (/ (count data) 3) 0.25)))
(def ray-scatter-space-earth (ray-scatter-space earth size power))
(def arr (mapv vec (partition (* size size) (mapv (comp matrix reverse) (partition 3 data)))))
(def S (interpolation-table (convert-2d-to-4d arr) ray-scatter-space-earth))
;---

(def w2 239)
(def -w2 (- w2))
(def w (inc (* 2 w2)))
(def img {:width w :height w :data (int-array (sqr w))})


(def m 0.1)
(def n (atom 0))
(;doseq [hh [2] angle (range (* -0.6 Math/PI) (* 0.6 Math/PI) 0.01)]
  let [angle  (* -0.20 Math/PI) hh 2]
  (let [light-direction (matrix [0 (Math/cos angle) (Math/sin angle)])
        point (matrix [0 (* 1 (+ radius hh)) (* 0.01 radius)])
        data  (vec (pmap (fn [y]
                             (mapv (fn [x]
                                       (let [f   (/ w2 (Math/tan (Math/toRadians 60)))
                                             dir (mmul (rotation-x (Math/toRadians 45)) (normalize (matrix [x (- y) (- f)])))
                                             ray {:sfsim25.ray/origin point :sfsim25.ray/direction dir}
                                             hit (ray-sphere-intersection earth ray)
                                             atm {:sfsim25.sphere/centre (matrix [0 0 0])
                                                  :sfsim25.sphere/radius (+ radius (:sfsim25.atmosphere/height earth))}
                                             h2  (ray-sphere-intersection atm ray)
                                             l   (Math/pow (max 0 (dot dir light-direction)) 5000)]
                                         (if (> (:sfsim25.intersection/length hit) 0)
                                           (let [p  (add point (mul dir (:sfsim25.intersection/distance hit)))
                                                 p2 (add point (mul dir (:sfsim25.intersection/distance h2)))
                                                 n  (normalize p)
                                                 l  (mul (T p light-direction) (max 0 (dot n light-direction)) )
                                                 e  (add l (E p light-direction))
                                                 s  (S p2 dir light-direction)
                                                 t  (T p2 dir)
                                                 b (add s (div (mul 0.3 t e) (* 2 Math/PI)))]
                                             b)
                                           (if (> (:sfsim25.intersection/length h2) 0)
                                             (let [p (add point (mul dir (:sfsim25.intersection/distance h2)))
                                                   s (S p dir light-direction)
                                                   t (T p dir)]
                                               (add s (mul l t)))
                                             (mul l (matrix [1 1 1]))))))
                                   (range -w2 (inc w2))))
                         (range -w2 (inc w2))))]
    (println (apply max (map #(mget % 1)  (flatten data))))
    (cp/pdoseq (+ (cp/ncpus) 2) [y (range w) x (range w)] (set-pixel! img y x (matrix (vec (map #(clip % 255) (mul (/ 255 m) (nth (nth data y) x)))))))
    ;(spit-image (format "sun%04d.png" @n) img)
    (show-image img)
    (println (swap! n inc) "/" 377)))

(def h (* 2 360))
(def w 200)
(def img {:width w :height h :data (int-array (* w h))})
(def m (apply max (S (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [1 0 0]))))
(cp/pdoseq (+ (cp/ncpus) 2) [y (range h)]
           (let [point (matrix [radius 0 0])
                 angle (Math/toRadians (* 360 (/ y h)))
                 dir   (matrix [(Math/cos angle) (Math/sin angle) 0])
                 light (matrix [1 0 0])
                 c     (S point dir light)
                 v     (matrix (vec (map #(clip % 255) (mul (/ 255 m) c))))]
             (doseq [x (range w)]
                    (set-pixel! img y x v))))
(show-image img)

; convert sun0200.png -background black -gravity center -extent 426x240 test.png

; (def table (vec (for [l (range 16)] (vec (for [k (range 16)] (vec (for [j (range 16)] (vec (for [i (range 16)] (if (even? (+ i j k l)) (matrix [1 1 1]) (matrix [0 0 0])))))))))))
; (reset! S (interpolation-table table ray-scatter-space-earth))

; ---
