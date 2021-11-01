(ns sfsim25.t-atmosphere
  (:require [midje.sweet :refer :all]
            [clojure.core.matrix :refer :all]
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
    (mget (scattering mie 1200) 0) => (roughly (/ 2e-5 Math/E) 1e-12)))

(fact "Compute sum of scattering and absorption (i.e. Mie extinction)"
  (let [mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}]
    (mget (extinction mie 1200) 0) => (roughly (/ 2e-5 0.9 Math/E) 1e-12))
  (let [rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 8000}]
    (mget (extinction rayleigh 8000) 0) => (roughly (/ 2e-5 Math/E) 1e-12)))

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

(facts "Intersection of ray with fringe of atmosphere or surface of planet"
  (let [radius 6378000.0
        height 100000.0
        earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        moved  #:sfsim25.sphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :sfsim25.atmosphere/height height}]
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 -1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 radius 0.0]) :surface true}
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 (+ radius 100) 0]) :direction (matrix [0 -1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 radius 0.0]) :surface true}
    (ray-extremity moved #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 radius 0.0]) :surface true}
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 (+ radius height) 0.0]) :surface false}
    (ray-extremity earth #:sfsim25.ray{:origin (matrix [0 (- radius 0.1) 0]) :direction (matrix [0 1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 (+ radius height) 0.0]) :surface false}
    (ray-extremity earth #:sfsim25.ray {:origin (matrix [0 (+ radius height 0.1) 0]) :direction (matrix [0 1 0])})
    => #:sfsim25.atmosphere{:point (matrix [0.0 (+ radius height 0.1) 0.0]) :surface false}))

(facts "Transmittance function"
  (let [radius   6378000.0
        height   100000.0
        earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000}
        mie      #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}
        both     [rayleigh mie]
        x        (matrix [0 radius 0])
        l        1000]
    (mget (transmittance earth [rayleigh] 10 x (matrix [0 radius 0])) 0) => (roughly 1.0 1e-6)
    (mget (transmittance earth [rayleigh] 10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l 5.8e-6))))
    (mget (transmittance earth both       10 x (matrix [l radius 0])) 0) => (roughly (Math/exp (- (* l (+ 5.8e-6 (/ 2e-5 0.9))))))
    (with-redefs [atmosphere/ray-extremity (fn [planet {:sfsim25.ray/keys [origin direction]}]
                                               {:sfsim25.atmosphere/point (add origin (mul direction l))})]
      (mget (transmittance earth [rayleigh] 10 #:sfsim25.ray{:origin x :direction (matrix [1 0 0])}) 0)
      => (roughly (Math/exp (- (* l 5.8e-6)))))))

(facts "Scatter-free radiation emitted from surface of planet (E[L0])"
  (let [radius    6378000.0
        height    100000.0
        earth     #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        moved     #:sfsim25.sphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :sfsim25.atmosphere/height height}
        sun-light (matrix [1.0 1.0 1.0])]
    (with-redefs [atmosphere/transmittance
                  (fn [planet scatter steps {:sfsim25.ray/keys [origin direction]}]
                      (fact [scatter steps origin] => [[] 10 (matrix [0 radius 0])])
                      (matrix [0.5 0.5 0.5]))]
      (surface-radiance-base earth [] 10 sun-light (matrix [0 radius 0]) (matrix [1 0 0]))             => (matrix [0.0 0.0 0.0])
      (surface-radiance-base moved [] 10 sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => (mul 0.5 sun-light)
      (surface-radiance-base earth [] 10 sun-light (matrix [0 radius 0]) (matrix [0 1 0]))             => (mul 0.5 sun-light)
      (surface-radiance-base earth [] 10 sun-light (matrix [0 radius 0]) (matrix [0 -1 0]))            => (matrix [0.0 0.0 0.0]))))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(facts "Single-scatter in-scattered light at a point in the atmosphere (J[L0])"
  (let [radius        6378000.0
        height        100000.0
        earth         #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie           #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        sun-light     (matrix [1.0 1.0 1.0])
        sun-direction (matrix [1.0 0.0 0.0])]
    (with-redefs [atmosphere/scattering
                  (fn ^Vector [mie ^double height]
                      (facts (:sfsim25.atmosphere/scatter-base mie) => (matrix [2e-5 2e-5 2e-5])
                             height => 1000.0)
                      (matrix [2e-5 2e-5 2e-5]))
                  atmosphere/phase
                  (fn [mie mu]
                      (facts (:sfsim25.atmosphere/scatter-g mie) => 0.76
                             mu => 0.36)
                      0.1)
                  atmosphere/transmittance
                  (fn [planet scatter steps ray]
                      (facts (:sfsim25.atmosphere/scatter-g (first scatter)) => 0.76
                             steps => 10
                             (:sfsim25.ray/origin ray) => (matrix [0 (+ radius 1000) 0])
                             (:sfsim25.ray/direction ray) => sun-direction)
                      (matrix [0.5 0.5 0.5]))]
      (point-scatter-base earth [mie] 10 sun-light (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) sun-direction)
      => (roughly-matrix (mul sun-light 2e-5 0.1 0.5) 1e-12))))

(facts "In-scattered light from a direction (S) depending on point scatter function (J)"
  (let [radius           6378000.0
        height           100000.0
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        sun-direction    (matrix [0.36 0.48 0.8])
        constant-scatter (fn [y view-direction sun-direction]
                             (facts view-direction => (matrix [0 1 0])
                                    sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [2e-5 2e-5 2e-5]))]
    (with-redefs [atmosphere/transmittance
                  (fn [planet scatter steps x x0]
                      (facts (:sfsim25.atmosphere/scatter-g (first scatter)) => 0.76
                             steps => 10
                             x => (matrix [0 radius 0]))
                      0.5)]
      (ray-scatter earth [mie] 10 constant-scatter (matrix [0 radius 0]) (matrix [0 1 0]) sun-direction)
      => (roughly-matrix (mul (matrix [2e-5 2e-5 2e-5]) height 0.5) 1e-6))))

(facts "Compute in-scattering of light at a point (J) depending on in-scattering from direction (S) and surface radiance (E)"
  (let [radius           6378000.0
        height           100000.0
        x1               (matrix [0 radius 0])
        x2               (matrix [0 (+ radius height) 0])
        sun-direction    (matrix [0.36 0.48 0.8])
        sun-light        (matrix [1 1 1])
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                          :sfsim25.atmosphere/brightness (mul Math/PI (matrix [0.3 0.3 0.3]))}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        zero             #:sfsim25.atmosphere{:scatter-base (matrix [0 0 0]) :scatter-scale 1000}
        ray-scatter1     (fn [x view-direction sun-direction]
                             (facts x => x1 view-direction => (matrix [0 1 0]) sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [1 2 3]))
        ray-scatter2     (fn [x view-direction sun-direction]
                             (facts x => x2 view-direction => (matrix [0 -1 0]) sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [1 2 3]))
        surface-radiance (fn [x sun-direction]
                             (facts x => x1 sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [3 4 5]))]
    (with-redefs [atmosphere/phase (fn [mie mu] 0.5)]
      (with-redefs [atmosphere/ray-extremity
                    (fn [planet ray] {:sfsim25.atmosphere/point (matrix [0 0 0]) :sfsim25.atmosphere/surface false})
                    sphere/integral-sphere
                    (fn [steps normal fun]
                        (facts steps => 64
                               normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                               (fun (matrix [0 1 0])) => (roughly-matrix (mul 0.5 (matrix [1 2 3]) (matrix [2e-5 2e-5 2e-5])) 1e-10))
                      (matrix [2e-5 3e-5 5e-5]))]
        (point-scatter earth [mie] ray-scatter1 surface-radiance sun-light 64 10 x1 (matrix [0 1 0]) sun-direction)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10))
      (with-redefs (atmosphere/ray-extremity
                    (fn [planet ray]
                        (facts planet => earth
                               ray => #:sfsim25.ray{:origin x2 :direction (matrix [0 -1 0])})
                        {:sfsim25.atmosphere/point (matrix [0 radius 0]) :sfsim25.atmosphere/surface true})
                    atmosphere/transmittance
                    (fn [planet scatter steps x x0]
                        (facts planet => earth
                               steps => 10
                               x => x2
                               x0 => (matrix [0 radius 0]))
                        (matrix [0.9 0.8 0.7]))
                    sphere/integral-sphere
                    (fn [steps normal fun]
                        (facts steps => 64
                               normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                               (fun (matrix [0 -1 0])) => (roughly-matrix (mul (matrix [3 4 5]) (matrix [0.9 0.8 0.7]) 0.3) 1e-10))
                      (matrix [2e-5 3e-5 5e-5])))
        (point-scatter earth [zero] ray-scatter2 surface-radiance sun-light 64 10 x2 (matrix [0 1 0]) sun-direction)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10)))))
