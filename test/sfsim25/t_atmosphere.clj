(ns sfsim25.t-atmosphere
    (:require [midje.sweet :refer :all]
              [comb.template :as template]
              [clojure.math :refer (sqrt exp pow E PI sin cos to-radians)]
              [clojure.core.matrix :refer (matrix mget mul sub identity-matrix)]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.matrix :refer :all]
              [sfsim25.sphere :as sphere]
              [sfsim25.interpolate :refer :all]
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
       (let [radius 6378000
             height 100000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [radius 0 0]) :direction (matrix [1 0 0])})
         => (matrix [(+ radius height) 0 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 radius 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])
         (atmosphere-intersection earth #:sfsim25.ray{:origin (matrix [0 (* -2 radius) 0]) :direction (matrix [0 1 0])})
         => (matrix [0 (+ radius height) 0])))

(facts "Get intersection with surface of planet or nearest point if there is no intersection"
       (let [radius 6378000
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}]
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
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [1 0 0]) true)   => (matrix [0.0 0.0 0.0])
      (surface-radiance-base moved [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0]) true)  => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 1 0]) true)   => (mul 0.5 intensity)
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 -1 0]) false) => (matrix [0.0 0.0 0.0])
      (surface-radiance-base earth [] 10 intensity (matrix [0 radius 0]) (matrix [0 1 0]) false) => (matrix [0.0 0.0 0.0])
      )))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

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
        surface-radiance (fn [x light-direction light-above]
                             (facts x => x1
                                    light-direction => (matrix [0.36 0.48 0.8])
                                    light-above => true)
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
      (surface-radiance earth ray-scatter 64 x light-direction true) => (matrix [0.2 0.3 0.5]))))

(facts "Get angle of planet's horizon below horizontal plane depending on the height of the observer"
       (let [radius 6378000.0
             earth  #:sfsim25.sphere {:centre (matrix [0 0 0]) :radius radius}]
         (horizon-angle earth (matrix [radius 0 0])) => 0.0
         (horizon-angle earth (matrix [(* (sqrt 2) radius) 0 0])) => (roughly (/ PI 4) 1e-12)
         (horizon-angle earth (matrix [(* 2 radius) 0 0])) => (roughly (/ PI 3) 1e-12)
         (horizon-angle earth (matrix [(- radius 0.1) 0 0])) => 0.0))

(facts "Check whether there is sky or ground in a certain direction"
       (let [radius 6378000.0
             earth #:sfsim25.sphere {:centre (matrix [0 0 0]) :radius radius}]
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [1 0 0])) => true
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [-1 0 0])) => false
         (is-above-horizon? earth (matrix [radius 0 0]) (matrix [-1e-4 1 0])) => false
         (is-above-horizon? earth (matrix [(+ radius 100000) 0 0]) (matrix [-1e-4 1 0])) => true))

(tabular "Map elevation value to lookup table index depending on position of horizon"
         (let [radius  6378000.0
               earth   #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}]
           (fact (elevation-to-index earth 17 ?pow (matrix [?x ?y ?z]) (matrix [?dx ?dy ?dz]) ?sky) => (roughly ?result 1e-6)))
           ?pow ?x                  ?y     ?z ?dx            ?dy        ?dz ?sky ?result
           1.0  radius              0      0  1              0          0   true   0.0
           1.0  radius              0      0  0              1          0   true   8.0
           1.0  0                   radius 0  0              1          0   true   0.0
           1.0  radius              0      0  (sqrt 0.5)     (sqrt 0.5) 0   true   4.0
           2.0  radius              0      0  (sqrt 0.5)     (sqrt 0.5) 0   true  (- 8 (sqrt 32))
           2.0  radius              0      0  0              1          0   true   8.0
           1.0  radius              0      0  0              1          0   false  9.0
           1.0  radius              0      0 -1              0          0   false 16.0
           1.0  radius              0      0  (- (sqrt 0.5)) (sqrt 0.5) 0   false 12.5
           1.0  (* (sqrt 2) radius) 0      0  (- (sqrt 0.5)) (sqrt 0.5) 0   true   8.0
           1.0  (* (sqrt 2) radius) 0      0  (- (sqrt 0.5)) (sqrt 0.5) 0   false  9.0
           1.0  (* (sqrt 2) radius) 0      0 -1              0          0   false 16.0
           2.0  radius              0      0  (- (sqrt 0.5)) (sqrt 0.5) 0   false 13.949747
           2.0  radius              0      0  0              1          0   false  9.0
           1.0  radius              0      0 -1              0          0   true   8.0
           1.0  radius              0      0  1              0          0   false  9.0)

(tabular "Map elevation lookup index to directional vector depending on position of horizon"
         (let [radius            6378000.0
               earth             #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius}
               [direction above] (index-to-elevation earth 17 ?pow ?height ?index) ]
           (facts direction => (roughly-matrix (matrix [?dx ?dy 0]) 1e-6)
                  above     => ?sky))
           ?pow ?height                   ?index     ?dx            ?dy        ?sky
           1.0  0                          0         1              0          true
           1.0  0                          8         0              1          true
           1.0  0                          9         0              1          false
           1.0  0                         16        -1              0          false
           1.0  (* (- (sqrt 2) 1) radius)  8         (- (sqrt 0.5)) (sqrt 0.5) true
           1.0  (* (- (sqrt 2) 1) radius)  9         (- (sqrt 0.5)) (sqrt 0.5) false
           1.0  (* (- (sqrt 2) 1) radius) 16        -1              0          false
           2.0  0                          2.343146  (sqrt 0.5)     (sqrt 0.5) true
           2.0  0                          8         0              1          true
           2.0  0                         13.949747  (- (sqrt 0.5)) (sqrt 0.5) false
           2.0  0                          9         0              1          false)

(facts "Convert height to index"
       (let [radius 6378000.0
             height 35000.0
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (height-to-index earth 17 (matrix [radius 0 0])) => 0.0
         (height-to-index earth 17 (matrix [(+ radius height) 0 0])) => 16.0))

(facts "Convert index to point at certain height"
       (let [radius 6378000.0
             height 35000.0
             earth  #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}]
         (index-to-height earth 17 0) => (matrix [radius 0 0])
         (index-to-height earth 17 16) => (matrix [(+ radius height) 0 0])))

(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   100000.0
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             space    (transmittance-space earth [15 17] 1.0)
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space)                               => [15 17]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) true)            => [0.0 0.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0]) true) => [14.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) true)            => [0.0 8.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) false)           => [0.0 9.0]
         (first (backward 0.0 0.0))                                       => (matrix [radius 0 0])
         (first (backward 14.0 0.0))                                      => (matrix [(+ radius height) 0 0])
         (second (backward 0.0 0.0))                                      => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (second (backward 0.0 8.0))                                      => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 0.0) 2)                                       => true
         (nth (backward 0.0 8.0) 2)                                       => true
         (nth (backward 0.0 9.0) 2)                                       => false))

(fact "Transformation for surface radiance interpolation is the same as the one for transmittance"
      surface-radiance-space => (exactly transmittance-space))

(facts "Convert absolute sun heading to lookup index"
       (let [radius 6378000.0
             size 17]
         (heading-to-index size (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 1 0])) => 0.0
         (heading-to-index size (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 0 1])) => (roughly 8.0 1e-6)
         (heading-to-index size (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1])) => 0.0
         (heading-to-index size (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 1 0])) => (roughly 8.0 1e-6)
         (heading-to-index size (matrix [radius 0 0]) (matrix [0 -1 -1e-8]) (matrix [0 -1 1e-8])) => (roughly 0.0 1e-6)
         (heading-to-index size (matrix [0 radius 0]) (matrix [1 0 0]) (matrix [-1 0 0])) => (roughly 16.0 1e-6)))

(facts "Convert index to absolute sun heading"
       (let [size 17]
         (index-to-heading size 0) => 0.0
         (index-to-heading size 16) => PI))

(facts "Create transformation for interpolating ray scatter and point scatter"
       (let [radius   6378000.0
             height   100000.0
             earth    #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius radius :sfsim25.atmosphere/height height}
             space    (ray-scatter-space earth [21 19 17 15] 1.0)
             forward  (:sfsim25.interpolate/forward space)
             backward (:sfsim25.interpolate/backward space)]
         (:sfsim25.interpolate/shape space) => [21 19 17 15]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [1 0 0]) true)            => [0.0 0.0 0.0 0.0]
         (forward (matrix [(+ radius height) 0 0]) (matrix [1 0 0]) (matrix [1 0 0]) true) => [20.0 0.0 0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [-1 0 0]) (matrix [1 0 0]) false)          => [0.0 18.0 0.0 0.0]
         (forward (matrix [0 radius 0]) (matrix [0 -1 0]) (matrix [0 1 0]) false)          => [0.0 18.0 0.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [1 0 0]) (matrix [0 0 1]) true)            => [0.0 0.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1]) true)            => [0.0 9.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 1]) false)           => [0.0 10.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 -1 0]) true)           => [0.0 9.0 8.0 7.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 1 0]) true)            => [0.0 9.0 8.0 7.0]
         (forward (matrix [radius 0 0]) (matrix [0 1 0]) (matrix [0 1 0]) true)            => [0.0 9.0 8.0 0.0]
         (forward (matrix [radius 0 0]) (matrix [0 0 1]) (matrix [0 0 -1]) true)           => [0.0 9.0 8.0 14.0]
         (nth (backward 0.0 0.0 0.0 0.0) 0)   => (matrix [radius 0 0])
         (nth (backward 20.0 0.0 0.0 0.0) 0)  => (matrix [(+ radius height) 0 0])
         (nth (backward 0.0 0.0 0.0 0.0) 1)   => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (nth (backward 0.0 9.0 0.0 0.0) 1)   => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 0.0 0.0 0.0) 2)   => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (nth (backward 0.0 0.0 8.0 0.0) 2)   => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (nth (backward 0.0 0.0 8.0 7.0) 2)   => (roughly-matrix (matrix [0 0 1]) 1e-6)
         (nth (backward 0.0 0.0 0.0 7.0) 2)   => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (nth (backward 0.0 9.0 0.0 0.0) 3)   => true
         (nth (backward 0.0 10.0 0.0 0.0) 3)   => false))

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
vec3 transmittance_track(sampler2D transmittance, float radius, float max_height, int height_size, int elevation_size,
                         float power, vec3 p, vec3 q);
void main()
{
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  fragColor = transmittance_track(transmittance, 6378000, 100000, 17, 17, 1, p, q);
}"))

(def transmittance-track-test (transmittance-shader-test transmittance-track-probe transmittance-track
                                                         shaders/transmittance-forward shaders/horizon-angle
                                                         shaders/elevation-to-index shaders/interpolate-2d
                                                         shaders/convert-2d-index shaders/is-above-horizon))

(tabular "Shader function to compute transmittance between two points in the atmosphere"
         (fact (mget (transmittance-track-test ?px ?py ?pz ?qx ?qy ?qz) 0) => (roughly ?result 1e-6))
         ?px ?py ?pz     ?qx ?qy ?qz     ?result
         0   0   6478000 0   0   6478000 1
         0   0   6428000 0   0   6478000 0.5
         0   0   6453000 0   0   6478000 0.75
         0   0   6428000 0   0   6453000 (/ 0.5 0.75))

(defn ray-scatter-shader-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                data-trans    (flatten (map #(repeat 5 (repeat 3 (/ % 4))) (range 5)))
                transmittance (make-vector-texture-2d {:width 5 :height 5 :data (float-array data-trans)})
                data-ray      (flatten (repeat (* 5 5 5 5) (repeat 3 1)))
                ray-scatter   (make-vector-texture-2d {:width 25 :height 25 :data (float-array data-ray)})
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render 1 1 true
                                              (use-program program)
                                              (uniform-sampler program :transmittance 0)
                                              (uniform-sampler program :ray_scatter 1)
                                              (use-textures transmittance ray-scatter)
                                              (render-quads vao))
                img           (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-texture ray-scatter)
            (destroy-texture transmittance)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ray-scatter-track-probe
  (template/fn [px py pz qx qy qz] "#version 410 core
uniform sampler2D transmittance;
uniform sampler2D ray_scatter;
out lowp vec3 fragColor;
vec3 ray_scatter_track(sampler2D ray_scatter, sampler2D transmittance, float radius, float max_height, int height_size,
                       int elevation_size, int light_elevation_size, int heading_size, float power, vec3 light_direction,
                       vec3 p, vec3 q);
void main()
{
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  fragColor = ray_scatter_track(ray_scatter, transmittance, 6378000, 100000, 5, 5, 5, 5, 1, vec3(0, 0, 1), p, q);
}"))

(def ray-scatter-track-test (ray-scatter-shader-test ray-scatter-track-probe ray-scatter-track shaders/ray-scatter-forward
                                                     shaders/horizon-angle shaders/oriented-matrix shaders/orthogonal-vector
                                                     shaders/clip-angle shaders/elevation-to-index shaders/interpolate-4d
                                                     shaders/convert-4d-index transmittance-track shaders/transmittance-forward
                                                     shaders/interpolate-2d shaders/convert-2d-index shaders/is-above-horizon))

(tabular "Shader function to determine in-scattered light between two points in the atmosphere"
         (fact (mget (ray-scatter-track-test ?px ?py ?pz ?qx ?qy ?qz) 0) => ?result)
         ?px ?py ?pz     ?qx ?qy ?qz     ?result
         0   0   6478000 0   0   6478000 0.0
         0   0   6428000 0   0   6478000 (- 1.0 (* 0.5 1.0)))

(def vertex-atmosphere-probe
  (template/fn [selector] "#version 410 core
in VS_OUT
{
  highp vec3 direction;
} fs_in;
out lowp vec3 fragColor;
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

(def radius 6378000)
(def max-height 100000)
(def ray-steps 10)
(def size 7)
(def power 2.0)
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
(def transmittance-earth (partial transmittance earth scatter ray-steps))
(def transmittance-space-earth (transmittance-space earth [size size] power))
(def point-scatter-earth (partial point-scatter-base earth scatter ray-steps (matrix [1 1 1])))
(def ray-scatter-earth (partial ray-scatter earth scatter ray-steps point-scatter-earth))
(def ray-scatter-space-earth (ray-scatter-space earth [size size size size] power))
(def T (pack-matrices (make-lookup-table (interpolate-function transmittance-earth transmittance-space-earth)
                                         transmittance-space-earth)))
(def S (pack-matrices (convert-4d-to-2d (make-lookup-table (interpolate-function ray-scatter-earth ray-scatter-space-earth)
                                                           ray-scatter-space-earth))))

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
                                                               :fragment [fragment-atmosphere shaders/ray-sphere
                                                                          shaders/transmittance-forward shaders/horizon-angle
                                                                          shaders/elevation-to-index shaders/ray-scatter-forward
                                                                          shaders/oriented-matrix shaders/orthogonal-vector
                                                                          shaders/clip-angle shaders/interpolate-2d
                                                                          shaders/convert-2d-index shaders/interpolate-4d
                                                                          shaders/convert-4d-index shaders/is-above-horizon])
                                   variables     [:point 3]
                                   transmittance (make-vector-texture-2d {:width size :height size :data T})
                                   ray-scatter   (make-vector-texture-2d {:width (* size size) :height (* size size) :data S})
                                   vao           (make-vertex-array-object program indices vertices variables)]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :transmittance 0)
                               (uniform-sampler program :ray_scatter 1)
                               (uniform-matrix4 program :projection (projection-matrix 256 256 0.5 1.5 (/ PI 3)))
                               (uniform-vector3 program :origin origin)
                               (uniform-matrix4 program :transform transform)
                               (uniform-vector3 program :light (matrix [?lx ?ly ?lz]))
                               (uniform-float program :radius radius)
                               (uniform-float program :polar_radius ?polar)
                               (uniform-float program :max_height max-height)
                               (uniform-float program :specular 500)
                               (uniform-int program :height_size size)
                               (uniform-int program :elevation_size size)
                               (uniform-int program :light_elevation_size size)
                               (uniform-int program :heading_size size)
                               (uniform-float program :power power)
                               (uniform-float program :amplification 5)
                               (use-textures transmittance ray-scatter)
                               (render-quads vao)
                               (destroy-texture ray-scatter)
                               (destroy-texture transmittance)
                               (destroy-vertex-array-object vao)
                               (destroy-program program)))   => (is-image (str "test/sfsim25/fixtures/atmosphere/"?result)))
         ?x ?y           ?z                      ?polar       ?rotation   ?lx ?ly ?lz  ?result
         0  0            (- 0 radius max-height) radius       0           0   0   -1   "sun.png"
         0  0            (- 0 radius max-height) radius       0           0   0    1   "space.png"
         0  0            (* 2.5 radius)          radius       0           0   1    0   "haze.png"
         0  radius       (* 0.5 radius)          radius       0           0   0   -1   "sunset.png"
         0  0            (- 0 radius 2)          radius       0           0   0   -1   "inside.png"
         0  (* 3 radius) 0                       radius       (* -0.5 PI) 0   1    0   "yview.png"
         0  (* 3 radius) 0                       (/ radius 2) (* -0.5 PI) 0   1    0   "ellipsoid.png")

(defn shader-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program  (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao      (make-vertex-array-object program indices vertices [:point 3])
                tex      (texture-render 1 1 true (use-program program) (render-quads vao))
                img      (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def phase-probe
  (template/fn [g mu] "#version 410 core
out lowp vec3 fragColor;
float phase(float g, float mu);
void main()
{
  float result = phase(<%= g %>, <%= mu %>);
  fragColor = vec3(result, 0, 0);
}"))

(def phase-test (shader-test phase-probe phase-function))

(tabular "Shader function for scattering phase function"
         (fact (mget (phase-test ?g ?mu) 0) => (roughly ?result))
         ?g  ?mu ?result
         0   0   (/ 3 (* 16 PI))
         0   1   (/ 6 (* 16 PI))
         0  -1   (/ 6 (* 16 PI))
         0.5 0   (/ (* 3 0.75) (* 8 PI 2.25 (pow 1.25 1.5)))
         0.5 1   (/ (* 6 0.75) (* 8 PI 2.25 (pow 0.25 1.5))))
