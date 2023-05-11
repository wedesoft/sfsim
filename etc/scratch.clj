(require '[sfsim25.atmosphere :refer :all]
         '[sfsim25.util :refer :all]
         '[clojure.core.matrix :refer (matrix emax mget mul)]
         '[clojure.math :refer (exp log PI sqrt round cos sin to-radians atan2)]
         '[com.climate.claypoole :refer (pfor ncpus)])

(def radius 6378000)
(def max-height 35000)

(def earth #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height max-height})

(def mie #:sfsim25.atmosphere{:scatter-base (matrix [5e-6 5e-6 5e-6]) :scatter-scale 2000 :scatter-g 0.76 :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000})

(scattering rayleigh (* (log 2) 8000))

(extinction mie 0)
(extinction rayleigh 0)

(phase rayleigh 1)
(phase rayleigh 0)
(phase rayleigh -1)

(phase mie 1)
(phase mie 0)
(phase mie -1)

(atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [1 0 0])})
(atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [0 1 0])})
(atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 1000) 0 0]) :direction (matrix [1 0 0])})
(atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 1000) 0 0]) :direction (matrix [0 1 0])})
(atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius max-height) 0 0]) :direction (matrix [0 1 0])})

(ray-extremity earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [1 0 0])})
(ray-extremity earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [0 1 0])})
(ray-extremity earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [-1e-6 1 0])})

(transmittance earth [rayleigh] 100 (matrix [radius 0 0]) (matrix [1 0 0]) true)
(transmittance earth [rayleigh] 100 (matrix [radius 0 0]) (matrix [(+ radius 1000) 0 0]))
(transmittance earth [rayleigh] 100 (matrix [radius 0 0]) (matrix [0 1 0]) true)
(transmittance earth [mie rayleigh] 100 (matrix [radius 0 0]) (matrix [1 0 0]) true)

(surface-radiance-base earth [mie rayleigh] 100 1.0 (matrix [radius 0 0]) (matrix [1 0 0]))

(point-scatter-base earth [rayleigh mie] 100 1.0 (matrix [(+ radius 1000) 0 0]) (matrix [1 0 0]) (matrix [1 0 0]) true)

(def a (to-radians 90))
;(def a (to-radians 15))
;(def a (to-radians 3))

(def f (partial point-scatter-base earth [rayleigh mie] 100 1.0))
(ray-scatter earth [rayleigh mie] 100 f (matrix [(+ radius 1) 0 0]) (matrix [(sin a) (cos a) 0]) (matrix [(sin a) (cos a) 0]) true)
(ray-scatter earth [rayleigh mie] 100 f (matrix [(+ radius 1) 0 0]) (matrix [0 1 0]) (matrix [(sin a) (cos a) 0]) true)

(def n 150)
(def arr
  (pfor (ncpus) [j (range (- n) (inc n)) i (range (- n) (inc n))]
        (let [r (sqrt (+ (* i i) (* j j)))]
          (if (> r n)
            (matrix [0 0 0])
            (let [alpha (atan2 j i)
                  beta  (to-radians (- 90.0 (* (/ r n) 90.0)))
                  x (* (cos alpha) (cos beta))
                  y (* (sin alpha) (cos beta))
                  z (sin beta)]
              (ray-scatter earth [rayleigh mie] 100 f (matrix [0 0 (+ radius 2)]) (matrix [x y z]) (matrix [(cos a) 0 (sin a)]) true))))))
(def m (apply max (map emax arr)))
(def s (/ 255.0 m))
(def p (fn [c] (bit-or (bit-shift-left -1 24) (bit-shift-left (round (mget c 0)) 16) (bit-shift-left (round (mget c 1)) 8) (round (mget c 2)))))

(def img {:width (inc (* 2 n)) :height (inc (* 2 n)) :data (int-array (map #(p (mul % s)) arr))})

(show-image img)
