(ns sfsim25.t-atmosphere
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix roughly-vector record-image is-image vertex-passthrough shader-test)]
              [comb.template :as template]
              [clojure.math :refer (sqrt exp pow E PI sin cos to-radians)]
              [clojure.core.matrix :refer (matrix mget mul add dot identity-matrix)]
              [sfsim25.matrix :refer :all]
              [sfsim25.sphere :as sphere]
              [sfsim25.interpolate :refer :all]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.util :refer :all]
              [sfsim25.atmosphere :refer :all :as atmosphere]
              [sfsim25.clouds :as clouds])
    (:import [mikera.vectorz Vector]))

(def radius 6378000)
(def max-height 100000)
(def ray-steps 10)
(def size 12)
(def earth #:sfsim25.sphere{:centre (matrix [0 0 0])
                            :radius radius
                            :sfsim25.atmosphere/height max-height
                            :sfsim25.atmosphere/brightness (matrix [0.3 0.3 0.3])})
(def mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5])
                              :scatter-scale 1200
                              :scatter-g 0.76
                              :scatter-quotient 0.9})
(def rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6])
                                   :scatter-scale 8000})
(def scatter [mie rayleigh])

(facts "Compute approximate scattering at different heights (testing with one component vector, normally three components)"
  (let [rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6]) :scatter-scale 8000}]
    (mget (scattering rayleigh          0) 0) => 5.8e-6
    (mget (scattering rayleigh       8000) 0) => (roughly (/ 5.8e-6 E) 1e-12)
    (mget (scattering rayleigh (* 2 8000)) 0) => (roughly (/ 5.8e-6 E E) 1e-12))
  (let [mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 1200}]
    (mget (scattering mie 1200) 0) => (roughly (/ 2e-5 E) 1e-12)))

(fact "Compute sum of scattering and absorption (i.e. Mie extinction)"
  (let [mie #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}]
    (mget (extinction mie 1200) 0) => (roughly (/ 2e-5 0.9 E) 1e-12))
  (let [rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [2e-5]) :scatter-scale 8000}]
    (mget (extinction rayleigh 8000) 0) => (roughly (/ 2e-5 E) 1e-12)))

(facts "Rayleigh phase function"
  (phase {}  0) => (roughly (/ 3 (* 16 PI)))
  (phase {}  1) => (roughly (/ 6 (* 16 PI)))
  (phase {} -1) => (roughly (/ 6 (* 16 PI))))

(facts "Mie phase function"
  (let [g (fn [value] {:sfsim25.atmosphere/scatter-g value})]
    (phase (g 0  )  0) => (roughly (/ 3 (* 16 PI)))
    (phase (g 0  )  1) => (roughly (/ 6 (* 16 PI)))
    (phase (g 0  ) -1) => (roughly (/ 6 (* 16 PI)))
    (phase (g 0.5)  0) => (roughly (/ (* 3 0.75) (* 8 PI 2.25 (pow 1.25 1.5))))
    (phase (g 0.5)  1) => (roughly (/ (* 6 0.75) (* 8 PI 2.25 (pow 0.25 1.5))))))

(facts "Get intersection with artificial limit of atmosphere"
       (let [height 100000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [1 0 0])})
         => (matrix [(+ radius height) 0 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 (* -2 radius) 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])))

(facts "Get intersection with surface of planet or nearest point if there is no intersection"
       (let [earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}]
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [-1 0 0])})
         => (matrix [radius 0 0])
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 10000) 0 0]) :direction (matrix [-1 0 0])})
         => (matrix [radius 0 0])
         (surface-intersection earth #:sfsim25.ray{:origin (matrix [(+ radius 100) -1000 0]) :direction (matrix [0 1 0])})
         => (matrix [(+ radius 100) 0 0])))

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
       (let [radius   6378000
             height   100000
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             rayleigh #:sfsim25.atmosphere{:scatter-base (matrix [5.8e-6 13.5e-6 33.1e-6]) :scatter-scale 8000}
             mie      #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-quotient 0.9}
             both     [rayleigh mie]]
         (with-redefs [atmosphere/surface-intersection
                       (fn [planet ray]
                           (facts planet => earth
                                  ray => #:sfsim25.ray{:origin (matrix [-1000 radius 0]) :direction (matrix [1 0 0])})
                           (matrix [0 radius 0]))
                       atmosphere/atmosphere-intersection
                       (fn [planet ray]
                           (facts planet => earth
                                  ray => #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
                           (matrix [0 (+ radius height) 0])
                           )
                       ]
           (mget (transmittance earth [rayleigh] 50 (matrix [0 radius 0]) (matrix [0 radius 0])) 0)
           => (roughly 1.0 1e-6)
           (mget (transmittance earth [rayleigh] 50 (matrix [0 radius 0]) (matrix [1000 radius 0])) 0)
           => (roughly (exp (- (* 1000 5.8e-6))) 1e-6)
           (mget (transmittance earth [rayleigh] 50 (matrix [0 (+ radius 8000) 0]) (matrix [1000 (+ radius 8000) 0])) 0)
           => (roughly (exp (- (/ (* 1000 5.8e-6) E))) 1e-6)
           (mget (transmittance earth both 50 (matrix [0 radius 0]) (matrix [1000 radius 0])) 0)
           => (roughly (exp (- (* 1000 (+ 5.8e-6 (/ 2e-5 0.9))))) 1e-6)
           (mget (transmittance earth [rayleigh] 50 (matrix [-1000 radius 0]) (matrix [1 0 0]) false) 0)
           => (roughly (exp (- (* 1000 5.8e-6))) 1e-6)
           (mget (transmittance earth both 50 (matrix [0 radius 0]) (matrix [0 1 0]) true) 0)
           => (roughly 0.932307 1e-6))))

(facts "Scatter-free radiation emitted from surface of planet (E[L0])"
  (let [radius    6378000.0
        height    100000.0
        earth     #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        moved     #:sfsim25.sphere{:centre (matrix [0 (* 2 radius) 0]) :radius radius :sfsim25.atmosphere/height height}
        intensity (matrix [1.0 1.0 1.0])]
    (with-redefs [atmosphere/transmittance
                  (fn [planet scatter steps origin direction above-horizon]
                      (facts "Transmittance function gets called with correct arguments"
                             scatter       => []
                             above-horizon => true
                             steps         => 10
                             origin        => (matrix [0 radius 0]))
                      (matrix [0.5 0.5 0.5]))]
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [1 0 0]))   => (matrix [0.0 0.0 0.0])
      (surface-radiance-base moved [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0]))  => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 1 0]))   => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0])) => (matrix [0.0 0.0 0.0]))))

(fact "Single-scatter in-scattered light at a point in the atmosphere (J[L0])"
  (let [radius           6378000.0
        height           100000.0
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        intensity        (matrix [1.0 1.0 1.0])
        light-direction  (matrix [1.0 0.0 0.0])
        light-direction2 (matrix [-1.0 0.0 0.0])]
    (with-redefs [atmosphere/is-above-horizon?
                  (fn [planet point direction]
                      (facts "surface-intersection gets called with correct arguments"
                             planet => earth
                             point => (matrix [0 (+ radius 1000) 0])
                             direction => light-direction)
                      true)
                  atmosphere/scattering
                  (fn ^Vector [^clojure.lang.IPersistentMap planet ^clojure.lang.IPersistentMap component ^Vector x]
                      (facts "Scattering function gets called with correct arguments"
                             planet => earth
                             component => mie
                             x => (matrix [0 (+ radius 1000) 0]))
                      (matrix [2e-5 2e-5 2e-5]))
                  atmosphere/phase
                  (fn [mie mu]
                      (facts "Phase function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-g mie) => 0.76
                             mu => 0.36)
                      0.1)
                  atmosphere/transmittance
                  (fn [planet scatter steps origin direction above-horizon]
                      (facts "Transmittance function gets called with correct arguments"
                             (:sfsim25.atmosphere/scatter-g (first scatter)) => 0.76
                             steps => 10
                             origin => (matrix [0 (+ radius 1000) 0])
                             direction => light-direction
                             above-horizon => true)
                      (matrix [0.5 0.5 0.5]))]
      (point-scatter-base earth [mie] 10 intensity (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) light-direction true)
      => (roughly-matrix (mul intensity 2e-5 0.1 0.5) 1e-12))
    (with-redefs [atmosphere/is-above-horizon?
                  (fn [planet point direction]
                      (facts planet => earth
                             point => (matrix [0 (+ radius 1000) 0])
                             direction => light-direction2)
                      false)]
      (point-scatter-base earth [mie] 10 intensity (matrix [0 (+ radius 1000) 0]) (matrix [0.36 0.48 0.8]) light-direction2 true)
      => (matrix [0 0 0]))))

(facts "In-scattered light from a direction (S) depending on point scatter function (J)"
  (let [radius           6378000.0
        height           100000.0
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        light-direction  (matrix [0.36 0.48 0.8])
        constant-scatter (fn [y view-direction light-direction above-horizon]
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
      (with-redefs [atmosphere/surface-intersection
                    (fn [planet ray]
                        (facts planet => earth
                               (:sfsim25.ray/origin ray) => (matrix [0 radius 0])
                               (:sfsim25.ray/direction ray) => (matrix [0 1 0]))
                        (matrix [0 (+ radius height) 0]))]
        (ray-scatter earth [mie] 10 constant-scatter (matrix [0 radius 0]) (matrix [0 1 0]) light-direction false)
        => (roughly-matrix (mul (matrix [2e-5 2e-5 2e-5]) height 0.5) 1e-6))
      (with-redefs [atmosphere/atmosphere-intersection
                    (fn [planet ray]
                        (facts planet => earth
                               (:sfsim25.ray/origin ray) => (matrix [0 radius 0])
                               (:sfsim25.ray/direction ray) => (matrix [0 1 0]))
                        (matrix [0 (+ radius height) 0]))]
        (ray-scatter earth [mie] 10 constant-scatter (matrix [0 radius 0]) (matrix [0 1 0]) light-direction true)
        => (roughly-matrix (mul (matrix [2e-5 2e-5 2e-5]) height 0.5) 1e-6)))))

(facts "Determine scattering component while taking into account overall absorption"
       (let [steps            100
             intensity        (matrix [1 1 1])
             x                (matrix [(+ radius 1000) 0 0])
             view-direction   (matrix [0 1 0])
             light-direction  (matrix [0.36 0.48 0.8])
             mu               (dot view-direction light-direction)]
         (add (point-scatter-component earth scatter mie steps intensity x view-direction light-direction true)
              (point-scatter-component earth scatter rayleigh steps intensity x view-direction light-direction true))
         => (roughly-matrix (point-scatter-base earth scatter steps intensity x view-direction light-direction true) 1e-12)
         (mul (strength-component earth scatter mie steps intensity x view-direction light-direction true) (phase mie mu))
         => (roughly-matrix (point-scatter-component earth scatter mie steps intensity x view-direction light-direction true) 1e-12)))

(facts "Compute in-scattering of light at a point (J) depending on in-scattering from direction (S) and surface radiance (E)"
  (let [radius           6378000.0
        height           100000.0
        x1               (matrix [0 radius 0])
        x2               (matrix [0 (+ radius 1200) 0])
        light-direction  (matrix [0.36 0.48 0.8])
        intensity        (matrix [1 1 1])
        earth            #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                          :sfsim25.atmosphere/brightness (mul PI (matrix [0.3 0.3 0.3]))}
        mie              #:sfsim25.atmosphere{:scatter-base (matrix [2e-5 2e-5 2e-5]) :scatter-scale 1200 :scatter-g 0.76}
        ray-scatter1     (fn [x view-direction light-direction above-horizon]
                             (facts x => x1
                                    view-direction => (matrix [0 1 0])
                                    light-direction => (matrix [0.36 0.48 0.8])
                                    above-horizon => true)
                             (matrix [1 2 3]))
        ray-scatter2     (fn [x view-direction light-direction above-horizon]
                             (facts x => x2
                                    view-direction => (matrix [0 -1 0])
                                    light-direction => (matrix [0.36 0.48 0.8])
                                    above-horizon => false)
                             (matrix [0 0 0]))
        surface-radiance (fn [x light-direction]
                             (facts x => x1
                                    light-direction => (matrix [0.36 0.48 0.8]))
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
        (point-scatter earth [mie] ray-scatter1 surface-radiance intensity 64 10 x1 (matrix [0 1 0]) light-direction true)
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
                               (roughly-matrix (mul 0.5 (/ 2e-5 E) (matrix [0.9 0.8 0.7]) 0.3 (matrix [3 4 5])) 1e-10))
                      (matrix [2e-5 3e-5 5e-5])))
        (point-scatter earth [mie] ray-scatter2 surface-radiance intensity 64 10 x2 (matrix [0 1 0]) light-direction true)
        => (roughly-matrix (matrix [2e-5 3e-5 5e-5]) 1e-10)))))

(facts "Scattered light emitted from surface of planet depending on ray scatter (E(S))"
  (let [radius          6378000.0
        height          100000.0
        x               (matrix [0 radius 0])
        light-direction (matrix [0.6 0.8 0])
        earth           #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height
                                         :sfsim25.atmosphere/brightness (mul PI (matrix [0.3 0.3 0.3]))}
        ray-scatter     (fn [x view-direction light-direction above-horizon]
                            (facts x => (matrix [0 radius 0])
                                   view-direction => (matrix [0.36 0.48 0.8])
                                   light-direction => (matrix [0.6 0.8 0])
                                   above-horizon => true)
                            (matrix [1 2 3]))]
    (with-redefs [sphere/integral-half-sphere
                  (fn [steps normal fun]
                      (facts steps => 64
                             normal => (roughly-matrix (matrix [0 1 0]) 1e-6)
                             (fun (matrix [0.36 0.48 0.8])) => (mul 0.48 (matrix [1 2 3])))
                      (matrix [0.2 0.3 0.5]))]
      (surface-radiance earth ray-scatter 64 x light-direction) => (matrix [0.2 0.3 0.5]))))

(facts "Check whether there is sky or ground in a certain direction"
       (let [radius 6378000.0
             earth #:sfsim25.sphere {:centre (matrix [0 0 0]) :radius radius}]
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [1 0 0])) => true
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [-1 0 0])) => false
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [-1e-4 1 0])) => false
         (is-above-horizon? earth (matrix [(+ radius 100000) 0 0]) (matrix [-1e-4 1 0])) => true
         (is-above-horizon? earth (matrix [(+ radius 100000) 0 0]) (matrix [(- (sqrt 0.5)) (sqrt 0.5) 0])) => false))

(facts "Distance from point with radius to horizon of planet"
       (horizon-distance {:sfsim25.sphere/radius 4.0} 4.0) => 0.0
       (horizon-distance {:sfsim25.sphere/radius 4.0} 5.0) => 3.0)

(facts "Convert elevation to index"
       (let [planet {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1}]
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [-1 0 0]) false) => (roughly 0.5 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-1 0 0]) false) => (roughly (/ 1 3) 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [(- (sqrt 0.5)) (sqrt 0.5) 0]) false) => (roughly 0.223 1e-3)
         (elevation-to-index planet 3 (matrix [4 0 0]) (matrix [-1 0 0]) false) => (roughly 1.0 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-0.6 0.8 0]) false) => (roughly 0.0 1e-3)
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [1 0 0]) true) => (roughly (/ 2 3) 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [0 1 0]) true) => (roughly 0.5 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-0.6 0.8 0]) true) => (roughly 1.0 1e-3)
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [0 1 0]) true) => (roughly 1.0 1e-3)
         (elevation-to-index planet 3 (matrix [4 0 0]) (matrix [0 1 0]) true) => (roughly 2.0 1e-3)
         (elevation-to-index planet 2 (matrix [5 0 0]) (matrix [-1 0 0]) true) => (roughly 1.0 1e-3)
         (elevation-to-index planet 2 (matrix [4 0 0]) (matrix [1 0 0]) false) => (roughly 0.5 1e-3)))

(facts "Convert index and height to elevation"
       (let [planet {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1}]
         (first (index-to-elevation planet 2 5.0 (/ 1 3))) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 3 5.0 (/ 2 3))) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (second (index-to-elevation planet 2 5.0 (/ 1 3))) => false
         (first (index-to-elevation planet 2 5.0 0.222549)) => (roughly-matrix (matrix [(- (sqrt 0.5)) (sqrt 0.5) 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.4)) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.4)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 (/ 2 3))) => (roughly-matrix (matrix [1 0 0]) 1e-3)
         (first (index-to-elevation planet 3 4.0 (/ 4 3))) => (roughly-matrix (matrix [1 0 0]) 1e-3)
         (second (index-to-elevation planet 2 4.0 (/ 2 3))) => true
         (first (index-to-elevation planet 2 4.0 1.0)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 1.0)) => (roughly-matrix (matrix [-0.6 0.8 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.5)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (second (index-to-elevation planet 2 5.0 0.5)) => true
         (first (index-to-elevation planet 2 5.0 0.5001)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.5)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (second (index-to-elevation planet 2 4.0 0.5)) => false
         (first (index-to-elevation planet 2 4.0 0.5001)) => (roughly-matrix (matrix [1 0 0]) 1e-3)))

(facts "Convert height of point to index"
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4 0 0])) => 0.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [5 0 0])) => 1.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4.5 0 0])) => (roughly 0.687 1e-3)
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 17 (matrix [5 0 0])) => 16.0
       (height-to-index {:sfsim25.sphere/radius 6378000.0 :sfsim25.atmosphere/height 35000.0} 32
                        (matrix [6377999.999549146 -16.87508805500576 73.93459155883768])) => (roughly 0.0 1e-6))

(facts "Convert index to point with corresponding height"
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.0) => (matrix [4 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 1.0) => (matrix [5 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.68718) => (roughly-matrix (matrix [4.5 0 0]) 1e-3)
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 3 2.0) => (matrix [5 0 0]))

(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   35000.0
             earth    {:sfsim25.sphere/radius radius :sfsim25.atmosphere/height height}
             space    (transmittance-space earth [15 17])
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [15 17]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) true) => [0.0 16.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [0 1 0]) true) => [14.0 8.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0]) false) => [0.0 8.0]
         (first (backward 0.0 16.0)) => (matrix [radius 0 0])
         (first (backward 14.0 8.0)) => (matrix [(+ radius height) 0 0])
         (second (backward 0.0 16.0)) => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (second (backward 14.0 8.0)) => (matrix [0 1 0])
         (second (backward 0.0 8.0)) => (matrix [0 1 0])
         (second (backward 0 8)) => (matrix [0 1 0])
         (third (backward 0.0 16.0)) => true
         (third (backward 14.0 8.0)) => true
         (third (backward 0.0 8.0)) => false
         (third (backward 0 8)) => false))

(fact "Transformation for surface radiance interpolation"
      (let [radius   6378000.0
             height   35000.0
             earth    {:sfsim25.sphere/radius radius :sfsim25.atmosphere/height height}
             space    (surface-radiance-space earth [15 17])
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [15 17]
         (forward (matrix [radius 0 0]) (matrix [1 0 0])) => [0.0 16.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0])) => [14.0 16.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0])) => [0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [-0.2 0.980 0])) => [0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0])) => (roughly-vector [0.0 7.422] 1e-3)
         (first (backward 0.0 16.0)) => (matrix [radius 0 0])
         (first (backward 14.0 16.0)) => (matrix [(+ radius height) 0 0])
         (second (backward 0.0 16.0)) => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (second (backward 14.0 0.0)) => (roughly-matrix (matrix [-0.2 0.980 0]) 1e-3)
         (second (backward 0.0 7.422)) => (roughly-matrix (matrix [0 1 0]) 1e-3)))

(facts "Convert sun elevation to index"
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [1 0 0])) => 1.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [0 1 0])) => (roughly 0.464 1e-3)
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-0.2 0.980 0])) => 0.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-1 0 0])) => 0.0
       (sun-elevation-to-index 17 (matrix [4 0 0]) (matrix [1 0 0])) => 16.0)

(facts "Convert index to sinus of sun elevation"
       (index-to-sin-sun-elevation 2 1.0) => (roughly 1.0 1e-3)
       (index-to-sin-sun-elevation 2 0.0) => (roughly -0.2 1e-3)
       (index-to-sin-sun-elevation 2 0.463863) => (roughly 0.0 1e-3)
       (index-to-sin-sun-elevation 2 0.5) => (roughly 0.022 1e-3)
       (index-to-sin-sun-elevation 3 1.0) => (roughly 0.022 1e-3))

(facts "Convert sun and viewing direction angle to index"
       (sun-angle-to-index 2 (matrix [0 1 0]) (matrix [0 1 0])) => 1.0
       (sun-angle-to-index 2 (matrix [0 1 0]) (matrix [0 -1 0])) => 0.0
       (sun-angle-to-index 2 (matrix [0 1 0]) (matrix [0 0 1])) => 0.5
       (sun-angle-to-index 17 (matrix [0 1 0]) (matrix [1 0 0])) => 8.0)

(facts "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 1.0) => (matrix [0 1 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 0.0) => (matrix [0 -1 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 1.0 0.5) => (matrix [1 0 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 1.00001 0.5) => (roughly-matrix (matrix [1 0 0]) 1e-3)
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 0.5) => (matrix [0 0 1])
       (index-to-sun-direction 2 (matrix [1 0 0]) 1.0 1.0) => (matrix [1 0 0])
       (index-to-sun-direction 2 (matrix [0 -1 0]) 0.0 1.0) => (matrix [0 -1 0])
       (index-to-sun-direction 3 (matrix [0 1 0]) 0.0 1.0) => (matrix [0 0 1]))

(facts "Create transformation for interpolating ray scatter and point scatter"
       (let [radius   6378000.0
             height   100000.0
             earth    {:sfsim25.sphere/radius radius :sfsim25.atmosphere/height height}
             space    (ray-scatter-space earth [21 19 17 15])
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [21 19 17 15]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [1 0 0]) true) => (roughly-vector [0.0 9.794 16.0 14.0] 1e-3)
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0]) (matrix [1 0 0]) true) => [20.0 9.0 16.0 14.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0]) (matrix [1 0 0]) false) => [0.0 9.0 16.0 0.0]
         (forward (matrix [0 radius 0]) (matrix [0 -1 0]) (matrix [0 1 0]) false) => [0.0 9.0 16.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [0 0 1]) true) => (roughly-vector [0.0 9.794 7.422 7.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1]) true) => (roughly-vector [0.0 18.0 7.422 14.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1]) false) => (roughly-vector [0.0 9.0 7.422 14.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 -1 0]) true) => (roughly-vector [0.0 18.0 7.422 7.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 1 0]) true) => (roughly-vector [0.0 18.0 7.422 7.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 1 0]) true) => (roughly-vector [0.0 18.0 7.422 14.0] 1e-3)
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 -1]) true) => (roughly-vector [0.0 18.0 7.422 0.0] 1e-3)
         (nth (backward 0.0 0.0 0.0 0.0) 0) => (matrix [radius 0 0])
         (nth (backward 20.0 0.0 0.0 0.0) 0) => (matrix [(+ radius height) 0 0])
         (nth (backward 0.0 9.79376 0.0 0.0) 1) => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (nth (backward 0.0 18.0 0.0 0.0) 1) => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 9.7937607 16.0 14.0) 2) => (roughly-matrix (matrix [1 0 0]) 1e-3)
         (nth (backward 0.0 9.7937607 7.421805 7.0) 2) => (roughly-matrix (matrix [0 0 1]) 1e-3)
         (nth (backward 0.0 18.0 7.421805 7.0) 2) => (roughly-matrix (matrix [0 0 1]) 1e-3)
         (nth (backward 0.0 9.79376 0.0 0.0) 3) => true
         (nth (backward 20.0 8.206 16.0 0.0) 3) => false))

(fact "Transformation for point scatter interpolation is the same as the one for ray scatter"
      point-scatter-space => (exactly ray-scatter-space))

(def transmittance-earth (partial transmittance earth scatter ray-steps))
(def transmittance-space-earth (transmittance-space earth [size size]))
(def point-scatter-rayleigh-earth (partial point-scatter-component earth scatter rayleigh ray-steps (matrix [1 1 1])))
(def scatter-strength-mie-earth (partial strength-component earth scatter mie ray-steps (matrix [1 1 1])))
(def ray-scatter-rayleigh-earth (partial ray-scatter earth scatter ray-steps point-scatter-rayleigh-earth))
(def ray-scatter-mie-strength   (partial ray-scatter earth scatter ray-steps point-scatter-rayleigh-earth))
(def ray-scatter-space-earth (ray-scatter-space earth [size size size size]))
(def T (pack-matrices (make-lookup-table transmittance-earth transmittance-space-earth)))
(def S (pack-matrices (convert-4d-to-2d (make-lookup-table ray-scatter-rayleigh-earth ray-scatter-space-earth))))
(def M (pack-matrices (convert-4d-to-2d (make-lookup-table ray-scatter-mie-strength ray-scatter-space-earth))))

(defn transmittance-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                transmittance (make-vector-texture-2d :linear :clamp {:width size :height size :data T})
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render-color
                                1 1 true
                                (use-program program)
                                (uniform-sampler program :transmittance 0)
                                (apply setup program uniforms)
                                (use-textures transmittance)
                                (render-quads vao))
                img           (rgb-texture->vectors3 tex)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-texture transmittance)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def transmittance-track-probe
  (template/fn [px py pz qx qy qz] "#version 410 core
out vec3 fragColor;
vec3 transmittance_track(vec3 p, vec3 q);
void main()
{
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  fragColor = transmittance_track(p, q);
}"))

(def transmittance-track-test
  (transmittance-shader-test
    (fn [program transmittance-height-size transmittance-elevation-size radius max-height]
        (uniform-int program :transmittance_height_size transmittance-height-size)
        (uniform-int program :transmittance_elevation_size transmittance-elevation-size)
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    transmittance-track-probe transmittance-track shaders/transmittance-forward shaders/height-to-index
    shaders/elevation-to-index shaders/interpolate-2d shaders/convert-2d-index shaders/is-above-horizon
    shaders/horizon-distance shaders/limit-quot phase-function))

(tabular "Shader function to compute transmittance between two points in the atmosphere"
         (fact (mget (transmittance-track-test [size size radius max-height] [?px ?py ?pz ?qx ?qy ?qz]) 0)
               => (roughly ?result 1e-6))
         ?px     ?py ?pz     ?qx     ?qy ?qz     ?result
         0       0   6478000 0       0   6478000 1
         0       0   6378000 0       0   6478000 0.976549
         6378000 0   0       6378000 0   100000  0.079658)

(defn ray-scatter-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                transmittance (make-vector-texture-2d :linear :clamp {:width size :height size :data T})
                ray-scatter   (make-vector-texture-2d :linear :clamp {:width (* size size) :height (* size size) :data S})
                mie-strength  (make-vector-texture-2d :linear :clamp {:width (* size size) :height (* size size) :data M})
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render-color
                                1 1 true
                                (use-program program)
                                (uniform-sampler program :transmittance 0)
                                (uniform-sampler program :ray_scatter 1)
                                (uniform-sampler program :mie_strength 2)
                                (apply setup program uniforms)
                                (use-textures transmittance ray-scatter mie-strength)
                                (render-quads vao))
                img           (rgb-texture->vectors3 tex)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-texture ray-scatter)
            (destroy-texture transmittance)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ray-scatter-track-probe
  (template/fn [px py pz qx qy qz] "#version 410 core
out vec3 fragColor;
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);
void main()
{
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  fragColor = ray_scatter_track(vec3(0, 0, 1), p, q);
}"))

(def ray-scatter-track-test
  (ray-scatter-shader-test
    (fn [program transmittance-height-size transmittance-elevation-size
         height-size elevation-size light-elevation-size heading-size radius max-height]
        (uniform-int program :transmittance_height_size transmittance-height-size)
        (uniform-int program :transmittance_elevation_size transmittance-elevation-size)
        (uniform-int program :height_size height-size)
        (uniform-int program :elevation_size elevation-size)
        (uniform-int program :light_elevation_size light-elevation-size)
        (uniform-int program :heading_size heading-size)
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    ray-scatter-track-probe ray-scatter-track shaders/ray-scatter-forward shaders/elevation-to-index shaders/interpolate-4d
    shaders/make-2d-index-from-4d transmittance-track shaders/transmittance-forward shaders/interpolate-2d
    shaders/convert-2d-index shaders/is-above-horizon shaders/height-to-index shaders/horizon-distance shaders/limit-quot
    shaders/sun-elevation-to-index shaders/sun-angle-to-index phase-function))

(tabular "Shader function to determine in-scattered light between two points in the atmosphere"
         (fact (mget (ray-scatter-track-test [size size size size size size radius max-height] [?px ?py ?pz ?qx ?qy ?qz]) 2)
               => (roughly ?result 1e-6))
         ?px ?py ?pz     ?qx    ?qy ?qz     ?result
         0   0   6378000 0      0   6378000 0.0
         0   0   6378000 0      0   6478000 0.043302
         0   0   6378000 100000 0   6378000 0.008272)

(def vertex-atmosphere-probe
  (template/fn [selector] "#version 410 core
in VS_OUT
{
  vec3 direction;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = <%= selector %>;
}"))

(def initial (identity-matrix 4))
(def shifted (transformation-matrix (identity-matrix 3) (matrix [0.2 0.4 0.5])))
(def rotated (transformation-matrix (rotation-x (to-radians 90)) (matrix [0 0 0])))

(tabular "Pass through coordinates of quad for rendering atmosphere and determine viewing direction and camera origin"
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
                               (uniform-matrix4 program :projection (projection-matrix 256 256 0.5 1.5 (/ PI 3)))
                               (uniform-matrix4 program :transform ?matrix)
                               (render-quads vao)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
         ?selector                               ?matrix ?result
         "vec3(1, 1, 1)"                         initial "test/sfsim25/fixtures/atmosphere/quad.png"
         "fs_in.direction + vec3(0.5, 0.5, 1.5)" initial "test/sfsim25/fixtures/atmosphere/direction.png"
         "fs_in.direction + vec3(0.5, 0.5, 1.5)" shifted "test/sfsim25/fixtures/atmosphere/direction.png"
         "fs_in.direction + vec3(0.5, 0.5, 1.5)" rotated "test/sfsim25/fixtures/atmosphere/rotated.png")

(def opacity-lookup-mock
"#version 410 core
float opacity_cascade_lookup(vec4 point)
{
  return 1.0;
}")

(def sampling-offset-mock
"#version 410 core
float sampling_offset()
{
  return 0.5;
}")

(tabular "Fragment shader for rendering atmosphere and sun"
         (fact
           (offscreen-render 256 256
                             (let [indices       [0 1 3 2]
                                   vertices      [-0.5 -0.5 -1
                                                   0.5 -0.5 -1
                                                  -0.5  0.5 -1
                                                   0.5  0.5 -1]
                                   origin        (matrix [?x ?y ?z])
                                   transform     (transformation-matrix (rotation-x ?rotation) origin)
                                   program       (make-program :vertex [vertex-atmosphere]
                                                               :fragment [fragment-atmosphere transmittance-outer
                                                                          ray-scatter-outer attenuation-outer shaders/ray-sphere
                                                                          shaders/transmittance-forward
                                                                          shaders/elevation-to-index shaders/ray-scatter-forward
                                                                          shaders/interpolate-2d shaders/convert-2d-index
                                                                          shaders/interpolate-4d shaders/make-2d-index-from-4d
                                                                          shaders/is-above-horizon clouds/sky-outer
                                                                          shaders/ray-shell clouds/cloud-track
                                                                          clouds/cloud-density
                                                                          clouds/cloud-shadow clouds/linear-sampling
                                                                          attenuation-track transmittance-track
                                                                          ray-scatter-track phase-function
                                                                          shaders/height-to-index shaders/horizon-distance
                                                                          shaders/limit-quot shaders/sun-elevation-to-index
                                                                          shaders/sun-angle-to-index opacity-lookup-mock
                                                                          sampling-offset-mock])
                                   variables     [:point 3]
                                   transmittance (make-vector-texture-2d :linear :clamp
                                                                         {:width size :height size :data T})
                                   ray-scatter   (make-vector-texture-2d :linear :clamp
                                                                         {:width (* size size) :height (* size size) :data S})
                                   mie-strength  (make-vector-texture-2d :linear :clamp
                                                                         {:width (* size size) :height (* size size) :data M})
                                   worley-data   (float-array (repeat (* 2 2 2) 1.0))
                                   worley        (make-float-texture-3d :linear :repeat
                                                                        {:width 2 :height 2 :depth 2 :data worley-data})
                                   profile-data  (float-array [0 1 1 1 1 1 1 0])
                                   profile       (make-float-texture-1d :linear :clamp profile-data)
                                   vao           (make-vertex-array-object program indices vertices variables)]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :transmittance 0)
                               (uniform-sampler program :ray_scatter 1)
                               (uniform-sampler program :mie_strength 2)
                               (uniform-sampler program :worley 3)
                               (uniform-sampler program :cloud_profile 4)
                               (uniform-matrix4 program :projection (projection-matrix 256 256 0.5 1.5 (/ PI 3)))
                               (uniform-vector3 program :origin origin)
                               (uniform-matrix4 program :transform transform)
                               (uniform-vector3 program :light_direction (matrix [?lx ?ly ?lz]))
                               (uniform-float program :radius radius)
                               (uniform-float program :polar_radius ?polar)
                               (uniform-float program :max_height max-height)
                               (uniform-float program :specular 500)
                               (uniform-int program :height_size size)
                               (uniform-int program :elevation_size size)
                               (uniform-int program :light_elevation_size size)
                               (uniform-int program :heading_size size)
                               (uniform-int program :transmittance_height_size size)
                               (uniform-int program :transmittance_elevation_size size)
                               (uniform-float program :amplification 5)
                               (uniform-float program :cloud_bottom 0)
                               (uniform-float program :cloud_top -1)
                               (uniform-float program :cloud_scale 16)
                               (uniform-int program :cloud_size 2)
                               (uniform-float program :cloud_multiplier 1.0)
                               (uniform-float program :anisotropic 0.4)
                               (uniform-int program :cloud_samples 64)
                               (uniform-float program :cloud_max_step 0.1)
                               (uniform-int program :cloud_base_samples 8)
                               (uniform-float program :cloud_scatter_amount 1.0)
                               (uniform-float program :transparency_cutoff 0.05)
                               (use-textures transmittance ray-scatter mie-strength worley profile)
                               (render-quads vao)
                               (destroy-texture profile)
                               (destroy-texture worley)
                               (destroy-texture ray-scatter)
                               (destroy-texture mie-strength)
                               (destroy-texture transmittance)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/atmosphere/" ?result)))
         ?x ?y              ?z                      ?polar       ?rotation   ?lx ?ly       ?lz           ?result
         0  0               (- 0 radius max-height) radius       0           0   0         -1            "sun.png"
         0  0               (- 0 radius max-height) radius       0           0   0          1            "space.png"
         0  0               (* 2.5 radius)          radius       0           0   1          0            "haze.png"
         0  radius          (* 0.5 radius)          radius       0           0   0         -1            "sunset.png"
         0  (+ radius 1000) 0                       radius       0           0   (sin 0.1) (- (cos 0.1)) "sunset2.png"
         0  0               (- 0 radius 2)          radius       0           0   0         -1            "inside.png"
         0  (* 3 radius)    0                       radius       (* -0.5 PI) 0   1          0            "yview.png"
         0  (* 3 radius)    0                       (/ radius 2) (* -0.5 PI) 0   1          0            "ellipsoid.png")

(def phase-probe
  (template/fn [g mu] "#version 410 core
out vec3 fragColor;
float phase(float g, float mu);
void main()
{
  float result = phase(<%= g %>, <%= mu %>);
  fragColor = vec3(result, 0, 0);
}"))

(def phase-test (shader-test (fn [program]) phase-probe phase-function))

(tabular "Shader function for scattering phase function"
         (fact (mget (phase-test [] [?g ?mu]) 0) => (roughly ?result))
         ?g  ?mu ?result
         0   0   (/ 3 (* 16 PI))
         0   1   (/ 6 (* 16 PI))
         0  -1   (/ 6 (* 16 PI))
         0.5 0   (/ (* 3 0.75) (* 8 PI 2.25 (pow 1.25 1.5)))
         0.5 1   (/ (* 6 0.75) (* 8 PI 2.25 (pow 0.25 1.5))))
