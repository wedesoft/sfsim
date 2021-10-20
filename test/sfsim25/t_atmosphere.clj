(ns sfsim25.t-atmosphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer (matrix mget sub)]
            [clojure.core.matrix.linear :refer (norm)]
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
  (height #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 0]) :sphere-radius 10} (matrix [10 0 0])) => 0.0
  (height #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 0]) :sphere-radius 10} (matrix [13 0 0])) => 3.0
  (height #:sfsim25.atmosphere{:sphere-centre (matrix [2 0 0]) :sphere-radius 10} (matrix [13 0 0])) => 1.0)

(facts "Compute intersection of line with sphere"
  (let [sphere #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 3]) :sphere-radius 1}]
    (ray-sphere sphere (matrix [-2 0 3]) (matrix [0 1 0])) => {:distance 0.0 :length 0.0}
    (ray-sphere sphere (matrix [-2 0 3]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
    (ray-sphere sphere (matrix [ 0 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 1.0}
    (ray-sphere sphere (matrix [ 2 0 3]) (matrix [1 0 0])) => {:distance 0.0 :length 0.0}
    (ray-sphere sphere (matrix [-2 0 3]) (matrix [2 0 0])) => {:distance 0.5 :length 1.0}))

(facts "Compute intersection of line with ellipsoid"
  (let [ellipsoid (fn [z] #:sfsim25.atmosphere{:ellipsoid-centre (matrix [0 0 z]) :ellipsoid-radius1 1 :ellipsoid-radius2 0.5})]
    (ray-ellipsoid (ellipsoid 0) (matrix [-2 0  0]) (matrix [1 0 0])) => {:distance 1.0 :length 2.0}
    (ray-ellipsoid (ellipsoid 0) (matrix [ 0 0 -2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0}
    (ray-ellipsoid (ellipsoid 4) (matrix [ 0 0  2]) (matrix [0 0 1])) => {:distance 1.5 :length 1.0}))

(facts "Compute optical depth of atmosphere at different points and for different directions"
  (with-redefs [atmosphere/ray-sphere
                (fn [{:sfsim25.atmosphere/keys [sphere-centre sphere-radius]} ^Vector origin ^Vector direction]
                  (fact [sphere-centre sphere-radius origin direction] =>
                        [(matrix [0 0 0]) 1100.0 (matrix [0 1010 0]) (matrix [1 0 0])])
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

(facts "Transmittance function"
  (let [radius   6378000
        earth    #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 0]) :sphere-radius radius}
        rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000}
        mie      #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}
        both     [rayleigh mie]
        x        (matrix [0 radius 0])
        l        1000]
    (mget (transmittance earth [rayleigh] 10 x (matrix [0 radius 0])) 0) => (roughly 1.0 1e-6)
    (mget (transmittance earth [rayleigh] 10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l 5.8e-6))))
    (mget (transmittance earth both       10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l (+ 5.8e-6 (/ 2e-5 0.9))))))))

(facts "Intersection of ray with fringe of atmosphere or surface of planet"
  (let [radius 6378000
        height 100000
        earth  #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 0]) :sphere-radius radius :height height}
        moved  #:sfsim25.atmosphere{:sphere-centre (matrix [0 (* 2 radius) 0]) :sphere-radius radius :height height}]
    (ray-extremity earth (matrix [0 radius 0]) (matrix [0 -1 0]))         => (matrix [0 radius 0])
    (ray-extremity earth (matrix [0 (+ radius 100) 0]) (matrix [0 -1 0])) => (matrix [0 radius 0])
    (ray-extremity moved (matrix [0 radius 0]) (matrix [0 1 0]))          => (matrix [0 radius 0])
    (ray-extremity earth (matrix [0 radius 0]) (matrix [0 1 0]))          => (matrix [0 (+ radius height) 0])
    (ray-extremity earth (matrix [0 (- radius 0.1) 0]) (matrix [0 1 0]))  => (matrix [0 (+ radius height) 0])))

(facts "Scatter-free radiation emitted from surface of planet or fringe of atmosphere"
  (let [radius    6378000
        height    100000
        earth     #:sfsim25.atmosphere{:sphere-centre (matrix [0 0 0]) :sphere-radius radius :height height}
        moved     #:sfsim25.atmosphere{:sphere-centre (matrix [0 (* 2 radius) 0]) :sphere-radius radius :height height}
        sun-light (matrix [1 1 1])]
    (epsilon0 earth sun-light (matrix [0 radius 0]) (matrix [1 0 0]))             => (matrix [0 0 0])
    (epsilon0 moved sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => sun-light
    (epsilon0 earth sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => (matrix [0 0 0])
    (epsilon0 earth sun-light (matrix [0 (+ radius height) 0]) (matrix [0 1 0]))  => (matrix [0 0 0])))

(defn roughly-vector [y error] (fn [x] (<= (norm (sub y x)) error)))

(facts "Integrate over a circle"
  (integrate-circle 64 (fn [x] (matrix [0]))) => (matrix [0])
  (integrate-circle 64 (fn [x] (matrix [1]))) => (roughly-vector (matrix (* 2 Math/PI)) 1e-6))

(facts "Integrate over half unit sphere"
  (integral-half-sphere 64 (matrix [1 0 0]) (fn [v] 0)) => 0.0
  (integral-half-sphere 64 (matrix [1 0 0]) (fn [v] 1)) => (roughly (* 2 Math/PI) 1e-3))
