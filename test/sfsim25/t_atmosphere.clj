(ns sfsim25.t-atmosphere
    (:require [midje.sweet :refer :all]
              [comb.template :as template]
              [clojure.core.matrix :refer :all]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.matrix :refer :all]
              [sfsim25.sphere :as sphere]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.util :refer :all]
              [sfsim25.atmosphere :refer :all :as atmosphere])
    (:import [mikera.vectorz Vector]))

; Compare RGB components of image and ignore alpha values.
(defn is-image [filename]
  (fn [other]
      (let [img (slurp-image filename)]
        (and (= (:width img) (:width other))
             (= (:height img) (:height other))
             (= (map #(bit-and % 0x00ffffff) (:data img)) (map #(bit-and % 0x00ffffff) (:data other)))))))

; Use this test function to record the image the first time.
(defn record-image [filename]
  (fn [other]
      (spit-image filename other)))

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

(facts "Get intersection with artificial limit of atmosphere"
       (let [radius 6378000
             height 100000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [1 0 0])})
         => (matrix [(+ radius height) 0 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 (* -2 radius) 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])))

(facts "Get intersection with surface of planet"
       (let [radius 6378000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}]
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [-1 0 0])})
         => (matrix [radius 0 0])
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 10000) 0 0]) :direction (matrix [-1 0 0])})
         => (matrix [radius 0 0])
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 10000) 0 0]) :direction (matrix [1 0 0])})
         => nil))

(facts "Check whether a point is near the surface or near the edge of the atmosphere"
       (let [radius 6378000
             height 100000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (surface-point? earth (matrix [radius 0 0])) => true
         (surface-point? earth (matrix [(+ radius height) 0 0])) => false))

(facts "Get intersection with surface of planet or artificial limit of atmosphere"
       (let [radius 6378000
             height 100000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (ray-extremity earth #:sfsim25.ray{:origin (matrix [(+ radius 10000) 0 0]) :direction (matrix [-1 0 0])})
         => (matrix [radius 0 0])
         (ray-extremity earth #:sfsim25.ray{:origin (matrix [(+ radius 10000) 0 0]) :direction (matrix [1 0 0])})
         => (matrix [(+ radius 100000) 0 0])
         (ray-extremity earth #:sfsim25.ray{:origin (matrix [(- radius 0.1) 0 0]) :direction (matrix [1 0 0])})
         => (matrix [(+ radius 100000) 0 0])))

(facts "Determine transmittance of atmosphere for all color channels"
       (let [radius       6378000
             height       100000
             earth        #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             rayleigh     #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000}
             mie          #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}
             both         [rayleigh mie]
             x            (matrix [0 radius 0])
             l            1000
             intersection (fn [planet ray]
                              (facts "Intersection function called with correct values"
                                     planet                       => earth
                                     (:sfsim25.ray/origin ray)    => (matrix [0 radius 0])
                                     (:sfsim25.ray/direction ray) => (matrix [1 0 0]))
                              (matrix [l radius 0]))]
         (mget (transmittance earth [rayleigh] 50 (matrix [0 radius 0])          (matrix [0 radius 0])         ) 0)
         => (roughly 1.0 1e-6)
         (mget (transmittance earth [rayleigh] 50 (matrix [0 radius 0])          (matrix [l radius 0])         ) 0)
         => (roughly (Math/exp (- (* l 5.8e-6))) 1e-6)
         (mget (transmittance earth [rayleigh] 50 (matrix [0 (+ radius 8000) 0]) (matrix [l (+ radius 8000) 0])) 0)
         => (roughly (Math/exp (- (/ (* l 5.8e-6) Math/E))) 1e-6)
         (mget (transmittance earth both       50 (matrix [0 radius 0])          (matrix [l radius 0])         ) 0)
         => (roughly (Math/exp (- (* l (+ 5.8e-6 (/ 2e-5 0.9))))) 1e-6)
         (mget (transmittance earth [rayleigh] intersection 50 (matrix [0 radius 0]) (matrix [1 0 0])) 0)
         => (roughly (Math/exp (- (* l 5.8e-6))) 1e-6)))

(facts "Scatter-free radiation emitted from surface of planet (E[L0])"
  (let [radius    6378000.0
        height    100000.0
        earth     #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        moved     #:sfsim25.sphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :sfsim25.atmosphere/height height}
        intensity (matrix [1.0 1.0 1.0])]
    (with-redefs [atmosphere/transmittance
                  (fn [planet scatter intersection steps origin direction]
                      (facts "Transmittance function gets called with correct arguments"
                             scatter => []
                             intersection => (exactly atmosphere-intersection)
                             steps   => 10
                             origin  => (matrix [0 radius 0]))
                      (matrix [0.5 0.5 0.5]))]
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [1 0 0]))             => (matrix [0.0 0.0 0.0])
      (surface-radiance-base moved [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0]))            => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 1 0]))             => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0]))            => (matrix [0.0 0.0 0.0]))))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(fact "Single-scatter in-scattered light at a point in the atmosphere (J[L0])"
  (let [radius           6378000.0
        height           100000.0
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        intensity        (matrix [1.0 1.0 1.0])
        light-direction  (matrix [1.0 0.0 0.0])
        light-direction2 (matrix [-1.0 0.0 0.0])]
    (with-redefs [atmosphere/surface-intersection
                  (fn [planet ray]
                      (facts "surface-intersection gets called with correct arguments"
                             planet => earth
                             (:sfsim25.ray/origin ray) => (matrix [0 (+ radius 1000) 0])
                             (:sfsim25.ray/direction ray) => light-direction)
                      nil)
                  atmosphere/scattering
                  (fn ^Vector [mie ^double height]
                      (facts "Scattering function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-base mie) => (matrix [2e-5 2e-5 2e-5])
                             height => 1000.0)
                      (matrix [2e-5 2e-5 2e-5]))
                  atmosphere/phase
                  (fn [mie mu]
                      (facts "Phase function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-g mie) => 0.76
                             mu => 0.36)
                      0.1)
                  atmosphere/transmittance
                  (fn [planet scatter intersection steps origin direction]
                      (facts "Transmittance function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-g (first scatter)) => 0.76
                             intersection => (exactly atmosphere-intersection)
                             steps => 10
                             origin => (matrix [0 (+ radius 1000) 0])
                             direction => light-direction)
                      (matrix [0.5 0.5 0.5]))]
      (point-scatter-base earth [mie] 10 intensity (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) light-direction)
      => (roughly-matrix (mul intensity 2e-5 0.1 0.5) 1e-12))
    (with-redefs [atmosphere/surface-intersection
                  (fn [planet ray]
                      (facts planet => earth
                             (:sfsim25.ray/origin ray) => (matrix [0 (+ radius 1000) 0])
                             (:sfsim25.ray/direction ray) => light-direction2)
                      (matrix [0 radius 0]))]
      (point-scatter-base earth [mie] 10 intensity (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) light-direction2)
      => (matrix [0 0 0]))))

(facts "In-scattered light from a direction (S) depending on point scatter function (J)"
  (let [radius           6378000.0
        height           100000.0
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        light-direction  (matrix [0.36 0.48 0.8])
        intersection     (fn [planet ray]
                             (facts "Intersection function is called with correct values"
                                    planet                       => earth
                                    (:sfsim25.ray/origin ray)    => (matrix [0 radius 0])
                                    (:sfsim25.ray/direction ray) => (matrix [0 1 0]))
                             (matrix [0 (+ radius height) 0])
                             )
        constant-scatter (fn [y view-direction light-direction]
                             (facts "Check point-scatter function gets called with correct arguments"
                                    view-direction => (matrix [0 1 0])
                                    light-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [2e-5 2e-5 2e-5]))]
    (with-redefs [atmosphere/transmittance
                  (fn [planet scatter steps x x0]
                      (facts "Check transmittance function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-g (first scatter)) => 0.76
                             steps => 10
                             x => (matrix [0 radius 0]))
                      0.5)]
      (ray-scatter earth [mie] intersection 10 constant-scatter (matrix [0 radius 0]) (matrix [0 1 0]) light-direction)
      => (roughly-matrix (mul (matrix [2e-5 2e-5 2e-5]) height 0.5) 1e-6))))

(facts "Compute in-scattering of light at a point (J) depending on in-scattering from direction (S) and surface radiance (E)"
  (let [radius           6378000.0
        height           100000.0
        x1               (matrix [0 radius 0])
        x2               (matrix [0 (+ radius 1200) 0])
        light-direction  (matrix [0.36 0.48 0.8])
        intensity        (matrix [1 1 1])
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                          :sfsim25.atmosphere/brightness (mul Math/PI (matrix [0.3 0.3 0.3]))}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        ray-scatter1     (fn [x view-direction light-direction]
                             (facts x => x1 view-direction => (matrix [0 1 0]) light-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [1 2 3]))
        ray-scatter2     (fn [x view-direction light-direction]
                             (facts x => x2 view-direction => (matrix [0 -1 0]) light-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [0 0 0]))
        surface-radiance (fn [x light-direction]
                             (facts x => x1 light-direction => (matrix [0.36 0.48 0.8]))
                             (matrix [3 4 5]))]
    (with-redefs [atmosphere/phase (fn [mie mu] 0.5)]
      (with-redefs [atmosphere/ray-extremity
                    (fn [planet ray] (matrix [0 (+ radius height) 0]))
                    sphere/integral-sphere
                    (fn [steps normal fun]
                        (facts steps => 64
                               normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                               (fun (matrix [0 1 0])) => (roughly-matrix (mul 0.5 (matrix [1 2 3]) (matrix [2e-5 2e-5 2e-5])) 1e-10))
                      (matrix [2e-5 3e-5 5e-5]))]
        (point-scatter earth [mie] ray-scatter1 surface-radiance intensity 64 10 x1 (matrix [0 1 0]) light-direction)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10))
      (with-redefs (atmosphere/ray-extremity
                    (fn [planet ray]
                        (facts planet => earth
                               ray => #:sfsim25.ray{:origin x2 :direction (matrix [0 -1 0])})
                        (matrix [0 radius 0]))
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
        (point-scatter earth [mie] ray-scatter2 surface-radiance intensity 64 10 x2 (matrix [0 1 0]) light-direction)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10)))))

(facts "Scattered light emitted from surface of planet depending on ray scatter (E(S))"
  (let [radius          6378000.0
        height          100000.0
        x               (matrix [0 radius 0])
        light-direction (matrix [0.6 0.8 0])
        earth           #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                         :sfsim25.atmosphere/brightness (mul Math/PI (matrix [0.3 0.3 0.3]))}
        ray-scatter     (fn [x view-direction light-direction]
                            (facts x => (matrix [0 radius 0])
                                   view-direction => (matrix [0.36 0.48 0.8])
                                   light-direction => (matrix [0.6 0.8 0]))
                            (matrix [1 2 3]))]
    (with-redefs [sphere/integral-half-sphere
                  (fn [steps normal fun]
                      (facts steps => 64
                             normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                             (fun (matrix [0.36 0.48 0.8])) => (mul 0.48 (matrix [1 2 3])))
                      (matrix [0.2 0.3 0.5]))]
      (surface-radiance earth ray-scatter 64 x light-direction) => (matrix [0.2 0.3 0.5]))))

(facts "Get angle of planet's horizon below horizontal plane depending on the height of the observer"
       (let [radius 6378000.0
             earth  #:sfsim25.sphere {:centre (matrix [0 0 0]) :radius radius}]
         (horizon-angle earth (matrix [radius 0 0])) => 0.0
         (horizon-angle earth (matrix [(* (Math/sqrt 2) radius) 0 0])) => (roughly (/ Math/PI 4) 1e-12)
         (horizon-angle earth (matrix [(* 2 radius) 0 0])) => (roughly (/ Math/PI 3) 1e-12)
         (horizon-angle earth (matrix [(- radius 0.1) 0 0])) => 0.0))

(facts "Map elevation value to lookup table index depending on position of horizon"
       (let [radius   6378000.0
             earth       #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}
             forward     (elevation-to-index earth 17 1.0)
             forward-exp (elevation-to-index earth 17 2.0)
             sqrthalf    (Math/sqrt 0.5)]
         (forward (matrix [radius 0 0]) (matrix [1 0 0])) => (roughly 0.0 1e-6)
         (forward (matrix [radius 0 0]) (matrix [1e-5 1 0])) => (roughly 8.0 1e-3)
         (forward (matrix [radius 0 0]) (matrix [-1e-5 1 0])) => (roughly 9.0 1e-3)
         (forward (matrix [radius 0 0]) (matrix [-1 0 0])) => (roughly 16.0 1e-6)
         (forward (matrix [(* (Math/sqrt 2) radius) 0 0]) (matrix [(- 1e-5 sqrthalf) sqrthalf 0])) => (roughly 8.0 1e-3)
         (forward (matrix [(* (Math/sqrt 2) radius) 0 0]) (matrix [(- -1e-5 sqrthalf) sqrthalf 0])) => (roughly 9.0 1e-3)
         (forward (matrix [(* (Math/sqrt 2) radius) 0 0]) (matrix [-1 0 0])) => (roughly 16.0 1e-6)
         (forward (matrix [radius 0 0]) (matrix [sqrthalf sqrthalf 0])) => (roughly 4.0 1e-6)
         (forward-exp (matrix [radius 0 0]) (matrix [sqrthalf sqrthalf 0])) => (roughly 2.343146 1e-6)
         (forward-exp (matrix [radius 0 0]) (matrix [1e-8 1 0])) => (roughly 8.0 1e-3)
         (forward (matrix [radius 0 0]) (matrix [(- sqrthalf) sqrthalf 0])) => (roughly 12.5 1e-6)
         (forward-exp (matrix [radius 0 0]) (matrix [(- sqrthalf) sqrthalf 0])) => (roughly 13.949747 1e-6)
         (forward-exp (matrix [radius 0 0]) (matrix [-1e-8 1 0])) => (roughly 9.0 1e-3)))

(facts "Map elevation lookup index to directional vector depending on position of horizon"
       (let [radius       6378000.0
             earth        #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}
             backward     (index-to-elevation earth 17 1.0)
             backward-exp (index-to-elevation earth 17 2.0)
             height       (* (- (Math/sqrt 2) 1) radius)
             sqrthalf     (Math/sqrt 0.5)]
         (backward 0 0) => (matrix [1 0 0])
         (backward 0 8) => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (backward 0 9) => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (backward 0 16) => (roughly-matrix (matrix [-1 0 0]) 1e-6)
         (backward height 8) => (roughly-matrix (matrix [(- sqrthalf) sqrthalf 0]) 1e-6)
         (backward height 9) => (roughly-matrix (matrix [(- sqrthalf) sqrthalf 0]) 1e-6)
         (backward height 16) => (roughly-matrix (matrix [-1 0 0]) 1e-6)
         (backward-exp 0 2.343146) => (roughly-matrix (matrix [sqrthalf sqrthalf 0]) 1e-6)
         (backward-exp 0 8) => (roughly-matrix [0 1 0] 1e-6)
         (backward-exp 0 13.949747) => (roughly-matrix (matrix [(- sqrthalf) sqrthalf 0]) 1e-6)
         (backward-exp 0 9) => (roughly-matrix (matrix [0 1 0]) 1e-6)))

(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   100000.0
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             space    (transmittance-space earth 17 1.0)
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
             space    (ray-scatter-space earth 17 1.0)
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

(def vertex-passthrough "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(defn transmittance-shader-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                data          (flatten (map #(repeat 17 (repeat 3 (/ % 16))) (range 17)))
                transmittance (make-vector-texture-2d {:width 17 :height 17 :data (float-array data)})
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render 1 1 true
                                              (use-program program)
                                              (uniform-sampler program :transmittance 0)
                                              (use-textures transmittance)
                                              (render-quads vao))
                img           (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-texture transmittance)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def transmittance-track-probe
  (template/fn [px py pz qx qy qz] "#version 410 core
uniform sampler2D transmittance;
out lowp vec3 fragColor;
vec3 transmittance_track(sampler2D transmittance, float radius, float max_height, int size, float power, vec3 p, vec3 q);
void main()
{
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  fragColor = transmittance_track(transmittance, 6378000, 100000, 17, 1, p, q);
}"))

(def transmittance-track-test (transmittance-shader-test transmittance-track-probe transmittance-track
                                                         shaders/transmittance-forward shaders/horizon-angle
                                                         shaders/elevation-to-index shaders/interpolate-2d
                                                         shaders/convert-2d-index))

(tabular "Shader function to compute transmittance between two points in the atmosphere"
         (fact (mget (transmittance-track-test ?px ?py ?pz ?qx ?qy ?qz) 0) => (roughly ?result 1e-6))
         ?px ?py ?pz     ?qx ?qy ?qz     ?result
         0   0   6478000 0   0   6478000 1
         0   0   6428000 0   0   6478000 0.5
         0   0   6453000 0   0   6478000 0.75
         0   0   6428000 0   0   6453000 (/ 0.5 0.75))

(def vertex-atmosphere-probe
  (template/fn [selector] "#version 410 core
in VS_OUT
{
  highp vec3 direction;
  highp vec3 origin;
} fs_in;
out lowp vec3 fragColor;
void main()
{
  fragColor = <%= selector %>;
}"))

(def initial (identity-matrix 4))
(def shifted (transformation-matrix (identity-matrix 3) (matrix [0.2 0.4 0.5])))

(tabular "Draw quad for rendering atmosphere and determine viewing direction and camera origin"
         (fact
           (offscreen-render 256 256
                             (let [indices   [0 1 3 2]
                                   vertices  [-0.5 -0.5 -1
                                               0.5 -0.5 -1
                                              -0.5  0.5 -1
                                               0.5  0.5 -1]
                                   program   (make-program :vertex [vertex-atmosphere]
                                                           :fragment [(vertex-atmosphere-probe ?selector)])
                                   variables [:point 3]
                                   vao       (make-vertex-array-object program indices vertices variables)]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-matrix4 program :projection (projection-matrix 256 256 0.5 1.5 (/ Math/PI 3)))
                               (uniform-matrix4 program :inverse_transform ?matrix)
                               (render-quads vao)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
         ?selector                               ?matrix ?result
         "vec3(1, 1, 1)"                         initial "test/sfsim25/fixtures/atmosphere-quad.png"
         "fs_in.direction + vec3(0.5, 0.5, 1.5)" initial "test/sfsim25/fixtures/atmosphere-direction.png"
         "fs_in.origin + vec3(0.5, 0.5, 0.5)"    initial "test/sfsim25/fixtures/atmosphere-origin.png"
         "fs_in.origin + vec3(0.5, 0.5, 0.5)"    shifted "test/sfsim25/fixtures/atmosphere-shifted.png")
