(ns sfsim25.t-atmosphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix mget sub dot slice mmul transpose identity-matrix det)]
            [clojure.core.matrix.linear :refer (norm)]
            [sfsim25.sphere :as sphere]
            [sfsim25.atmosphere :refer :all :as atmosphere])
  (:import [mikera.vectorz Vector]))

(def radius1 6378000.0)
(def radius2 6357000.0)

(facts "Compute approximate air density at different heights"
  (air-density 0 1.225 8429) => 1.225
  (air-density 8429 1.225 8429) => (roughly (/ 1.225 Math/E) 1e-6)
  (air-density (* 2 8429) 1.225 8429) => (roughly (/ 1.225 Math/E Math/E) 1e-6))

(facts "Create an air density table"
  (nth (air-density-table 1.0 21 10000 1000) 0) => 1.0
  (nth (air-density-table 1.0 21 10000 1000) 2) => (roughly (/ 1.0 Math/E) 1e-6)
  (nth (air-density-table 1.0 21 10000 1000) 4) => (roughly (/ 1.0 Math/E Math/E) 1e-6))

(facts "Air density for a given 3D point assuming spherical body"
  (air-density-at-point (matrix [1000 0 0]) 1.0 1000 10) => 1.0
  (air-density-at-point (matrix [1010 0 0]) 1.0 1000 10) => (roughly (/ 1.0 Math/E) 1e-6))

(facts "Determine height above surface for given point"
  (height #:sfsim25.atmosphere{:centre (matrix [0 0 0]) :radius 10} (matrix [10 0 0])) => 0.0
  (height #:sfsim25.atmosphere{:centre (matrix [0 0 0]) :radius 10} (matrix [13 0 0])) => 3.0
  (height #:sfsim25.atmosphere{:centre (matrix [2 0 0]) :radius 10} (matrix [13 0 0])) => 1.0)

(facts "Compute optical depth of atmosphere at different points and for different directions"
  (with-redefs [sphere/ray-sphere-intersection
                (fn [{:sfsim25.sphere/keys [centre radius]} {:sfsim25.ray/keys [origin direction]}]
                  (fact [centre radius origin direction]
                        => [(matrix [0 0 0]) 1100.0 (matrix [0 1010 0]) (matrix [1 0 0])])
                  {:length 20.0})
                atmosphere/air-density-at-point
                (fn ^double [^Vector point ^double base ^double radius ^double scale]
                  (fact [base radius scale] => [1.0 1000.0 42.0])
                  (fact (.contains [(matrix [5.0 1010.0 0.0]) (matrix [15.0 1010.0 0.0])] point) => true)
                  0.25)]
    (optical-depth (matrix [0 1010 0]) (matrix [1 0 0]) 1.0 1000.0 100 42 2))  => (+ (* 0.25 10) (* 0.25 10)))

(facts "Lookup table for optical density"
  (let [result (optical-depth-table 5 9 1.0 1000 100 8 20)]
    (:width result) => 5
    (:height result) => 9
    (count (:data result)) => (* 5 9)
    (nth (:data result) (* 5 8)) => (roughly (optical-depth (matrix [0 1000 0]) (matrix [0 1 0]) 1.0 1000 100 8 20) 1e-4)
    (nth (:data result) (* 5 4)) => (roughly (optical-depth (matrix [0 1000 0]) (matrix [1 0 0]) 1.0 1000 100 8 20) 1e-4)
    (nth (:data result) (+ (* 5 4) 2)) => (roughly (optical-depth (matrix [0 1050 0]) (matrix [1 0 0]) 1.0 1000 100 8 20) 1e-4)))

(facts "Compute approximate scattering at different heights (testing with one component vector, normally three components)"
  (let [rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6]) :scatter-scale 8000}]
    (mget (scattering rayleigh          0) 0) => 5.8e-6
    (mget (scattering rayleigh       8000) 0) => (roughly (/ 5.8e-6 Math/E) 1e-12)
    (mget (scattering rayleigh (* 2 8000)) 0) => (roughly (/ 5.8e-6 Math/E Math/E) 1e-12))
  (let [mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 1200}]
    (mget (scattering mie 1200) 0) => (roughly (/ 2e-5 Math/E))))

(fact "Compute sum of scattering and absorption (i.e. Mie extinction)"
  (let [mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}]
    (mget (extinction mie 1200) 0) => (roughly (/ 2e-5 0.9 Math/E)))
  (let [rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 8000}]
    (mget (extinction rayleigh 8000) 0) => (roughly (/ 2e-5 Math/E))))

(facts "Rayleigh phase function"
  (phase {}  0) => (roughly (/ 3 (* 16 Math/PI)))
  (phase {}  1) => (roughly (/ 6 (* 16 Math/PI)))
  (phase {} -1) => (roughly (/ 6 (* 16 Math/PI))))

(facts "Mie phase function"
  (let [g (fn [value] {:sfsim25.atmosphere/scatter-g value})]
    (phase (g 0  )  0) => (roughly (/ 3 (* 16 Math/PI)))
    (phase (g 0  )  1) => (roughly (/ 6 (* 16 Math/PI)))
    (phase (g 0  ) -1) => (roughly (/ 6 (* 16 Math/PI)))
    (phase (g 0.5)  0) => (roughly (/ (* 3 0.75) (* 8 Math/PI 2.25 (Math/pow 1.25 1.5))))
    (phase (g 0.5)  1) => (roughly (/ (* 6 0.75) (* 8 Math/PI 2.25 (Math/pow 0.25 1.5))))))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(facts "Transmittance function"
  (let [radius   6378000
        earth    #:sfsim25.atmosphere{:centre (matrix [0 0 0]) :radius radius}
        rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000}
        mie      #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}
        both     [rayleigh mie]
        x        (matrix [0 radius 0])
        l        1000]
    (mget (transmittance earth [rayleigh] 10 x (matrix [0 radius 0])) 0) => (roughly 1.0 1e-6)
    (mget (transmittance earth [rayleigh] 10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l 5.8e-6))))
    (mget (transmittance earth both       10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l (+ 5.8e-6 (/ 2e-5 0.9))))))))

(facts "Intersection of ray with fringe of atmosphere or surface of planet"
  (let [radius 6378000.0
        height 100000.0
        earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        moved  #:sfsim25.sphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :sfsim25.atmosphere/height height}]
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 -1 0])})
    => (matrix [0.0 radius 0.0])
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 (+ radius 100) 0]) :direction (matrix [0 -1 0])})
    => (matrix [0.0 radius 0.0])
    (ray-extremity moved #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
    => (matrix [0.0 radius 0.0])
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
    => (matrix [0.0 (+ radius height) 0.0])
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 (- radius 0.1) 0]) :direction (matrix [0 1 0])})
    => (matrix [0.0 (+ radius height) 0.0])))

(facts "Scatter-free radiation emitted from surface of planet or fringe of atmosphere"
  (let [radius    6378000.0
        height    100000.0
        earth     #:sfsim25.atmosphere{:centre (matrix [0 0 0]) :radius radius :height height}
        moved     #:sfsim25.atmosphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :height height}
        sun-light (matrix [1.0 1.0 1.0])]
    (epsilon0 earth sun-light (matrix [0 radius 0]) (matrix [1 0 0]))             => (matrix [0.0 0.0 0.0])
    (epsilon0 moved sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => sun-light
    (epsilon0 earth sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => (matrix [0.0 0.0 0.0])
    (epsilon0 earth sun-light (matrix [0 (+ radius height) 0]) (matrix [0 1 0]))  => (matrix [0.0 0.0 0.0])))

(facts "Generate orthogonal vector"
  (dot (orthogonal (matrix [1 0 0])) (matrix [1 0 0])) => 0.0
  (norm (orthogonal (matrix [1 0 0]))) => 1.0
  (dot (orthogonal (matrix [0 1 0])) (matrix [0 1 0])) => 0.0
  (norm (orthogonal (matrix [0 1 0]))) => 1.0
  (dot (orthogonal (matrix [0 0 1])) (matrix [0 0 1])) => 0.0)

(facts "Generate isometry with given normal vector as first column"
  (let [n (matrix [0.36 0.48 0.8])
        m (oriented-matrix n)]
    (slice m 1 0) => (roughly-matrix n 1e-6)
    (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3) 1e-6)
    (det m) => (roughly 1.0 1e-6)))

(facts "Integrate over a circle"
  (integrate-circle 64 (fn [x] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
  (integrate-circle 64 (fn [x] (matrix [1]))) => (roughly-matrix (matrix (* 2 Math/PI)) 1e-6))

(facts "Integrate over half unit sphere"
  (let [left (matrix [1 0 0])
        up   (matrix [0 1 0])]
    (integral-half-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 2 Math/PI)]) 1e-6)
    (integral-half-sphere 64 left (fn [v] (matrix [1 (mget v 1) (mget v 2)]))) => (roughly-matrix (matrix [(* 2 Math/PI) 0 0]) 1e-6)
    (integral-half-sphere 64 up (fn [v] (matrix [(mget v 0) 1 (mget v 2)]))) => (roughly-matrix (matrix [0 (* 2 Math/PI) 0]) 1e-6)))

(facts "Integrate over unit sphere"
  (let [left (matrix [1 0 0])]
    (integral-sphere 64 left (fn [v] (matrix [0]))) => (roughly-matrix (matrix [0]) 1e-6)
    (integral-sphere 64 left (fn [v] (matrix [1]))) => (roughly-matrix (matrix [(* 4 Math/PI)]) 1e-6)))
