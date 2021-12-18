(ns sfsim25.t-atmosphere
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.sphere :as sphere]
              [sfsim25.atmosphere :refer :all :as atmosphere])
    (:import [mikera.vectorz Vector]))

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

(fact "Single-scatter in-scattered light at a point in the atmosphere (J[L0])"
  (let [radius         6378000.0
        height         100000.0
        earth          #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie            #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        sun-light      (matrix [1.0 1.0 1.0])
        sun-direction  (matrix [1.0 0.0 0.0])
        sun-direction2 (matrix [-1.0 0.0 0.0])]
    (with-redefs [atmosphere/ray-extremity
                  (fn [planet ray]
                      (facts planet => earth
                             (:sfsim25.ray/origin ray) => (matrix [0 (+ radius 1000) 0])
                             (:sfsim25.ray/direction ray) => sun-direction)
                      {:sfsim25.atmosphere/surface false})
                  atmosphere/scattering
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
      => (roughly-matrix (mul sun-light 2e-5 0.1 0.5) 1e-12))
    (with-redefs [atmosphere/ray-extremity
                  (fn [planet ray]
                      (facts planet => earth
                             (:sfsim25.ray/origin ray) => (matrix [0 (+ radius 1000) 0])
                             (:sfsim25.ray/direction ray) => sun-direction2)
                      {:sfsim25.atmosphere/surface true})]
      (point-scatter-base earth [mie] 10 sun-light (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) sun-direction2)
      => (matrix [0 0 0]))))

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
        x2               (matrix [0 (+ radius 1200) 0])
        sun-direction    (matrix [0.36 0.48 0.8])
        sun-light        (matrix [1 1 1])
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                          :sfsim25.atmosphere/brightness (mul Math/PI (matrix [0.3 0.3 0.3]))}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        ray-scatter1     (fn [x view-direction sun-direction]
                             (facts x => x1 view-direction => (matrix [0 1 0]) sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [1 2 3]))
        ray-scatter2     (fn [x view-direction sun-direction]
                             (facts x => x2 view-direction => (matrix [0 -1 0]) sun-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [0 0 0]))
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
                               (fun (matrix [0 -1 0])) =>
                               (roughly-matrix (mul 0.5 (/ 2e-5 Math/E) (matrix [0.9 0.8 0.7]) 0.3 (matrix [3 4 5])) 1e-10))
                      (matrix [2e-5 3e-5 5e-5])))
        (point-scatter earth [mie] ray-scatter2 surface-radiance sun-light 64 10 x2 (matrix [0 1 0]) sun-direction)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10)))))

(facts "Scattered light emitted from surface of planet depending on ray scatter (E(S))"
  (let [radius        6378000.0
        height        100000.0
        x             (matrix [0 radius 0])
        sun-direction (matrix [0.6 0.8 0])
        earth         #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                       :sfsim25.atmosphere/brightness (mul Math/PI (matrix [0.3 0.3 0.3]))}
        ray-scatter   (fn [x view-direction sun-direction]
                          (facts x => (matrix [0 radius 0])
                                 view-direction => (matrix [0.36 0.48 0.8])
                                 sun-direction => (matrix [0.6 0.8 0]))
                          (matrix [1 2 3]))]
    (with-redefs [sphere/integral-half-sphere
                  (fn [steps normal fun]
                      (facts steps => 64
                             normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                             (fun (matrix [0.36 0.48 0.8])) => (mul 0.48 (matrix [1 2 3])))
                      (matrix [0.2 0.3 0.5]))]
      (surface-radiance earth ray-scatter 64 x sun-direction) => (matrix [0.2 0.3 0.5]))))

(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   100000.0
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             space    (transmittance-space earth 17)
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space)                          => [17 17]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]))            => [0.0 0.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0])) => [16.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]))            => [0.0 8.0]
         (first (backward 0.0 0.0))                                  => (matrix [radius 0 0])
         (first (backward 16.0 0.0))                                 => (matrix [(+ radius height) 0 0])
         (second (backward 0.0 0.0))                                 => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (second (backward 0.0 8.0))                                 => (roughly-matrix (matrix [0 1 0]) 1e-6)))

(fact "Transformation for surface radiance interpolation is the same as the one for transmittance"
      surface-radiance-space => (exactly transmittance-space))

(facts "Create transformation for interpolating ray scatter and point scatter"
       (let [radius   6378000.0
             height   100000.0
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             space    (ray-scatter-space earth 17)
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [17 17 17 17]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [1 0 0]))            => [0.0 0.0 0.0 0.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0]) (matrix [1 0 0])) => [16.0 0.0 0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0]) (matrix [1 0 0]))           => [0.0 16.0 0.0 0.0]
         (forward (matrix [0 radius 0]) (matrix [0 -1 0]) (matrix [0 1 0]))           => [0.0 16.0 0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [0 0 1]))            => [0.0 0.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1]))            => [0.0 8.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 -1 0]))           => [0.0 8.0 8.0 8.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 1 0]))            => [0.0 8.0 8.0 8.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 1 0]))            => [0.0 8.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 -1]))           => [0.0 8.0 8.0 16.0]
         (nth (backward 0.0 0.0 0.0 0.0) 0)                                           => (matrix [radius 0 0])
         (nth (backward 16.0 0.0 0.0 0.0) 0)                                          => (matrix [(+ radius height) 0 0])
         (nth (backward 0.0 0.0 0.0 0.0) 1)                                           => (matrix [1 0 0])
         (nth (backward 0.0 8.0 0.0 0.0) 1)                                           => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 0.0 0.0 0.0) 2)                                           => (matrix [1 0 0])
         (nth (backward 0.0 0.0 8.0 0.0) 2)                                           => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 0.0 8.0 8.0) 2)                                           => (roughly-matrix (matrix [0 0 1]) 1e-6)
         (nth (backward 0.0 0.0 0.0 8.0) 2)                                           => (matrix [1 0 0])))

(fact "Transformation for point scatter interpolation is the same as the one for ray scatter"
      point-scatter-space => (exactly ray-scatter-space))
