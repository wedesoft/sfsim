(require '[clojure.math :refer (sqrt exp sin cos log acos)]
         '[clojure.core.matrix :refer (matrix dot sub normalise mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.conftest :refer (roughly-matrix roughly-vector)]
         '[sfsim25.atmosphere :refer (is-above-horizon?)]
         '[midje.sweet :refer :all]
         '[gnuplot.core :as g]
         '[sfsim25.util :refer :all])
(import '[mikera.vectorz Vector])

(defn horizon-distance [planet radius]
  "Distance from point with specified radius to horizon of planet"
  (sqrt (- (sqr radius) (sqr (:sfsim25.sphere/radius planet)))))

(facts "Distance from point with radius to horizon of planet"
       (horizon-distance {:sfsim25.sphere/radius 4.0} 4.0) => 0.0
       (horizon-distance {:sfsim25.sphere/radius 4.0} 5.0) => 3.0)

(defn height-to-index
  "Convert height of point to index"
  [planet size point]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)]
    (* (dec size) (/ (horizon-distance planet (norm point)) (horizon-distance planet (+ radius max-height))))))

(facts "Convert height of point to index"
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4 0 0])) => 0.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [5 0 0])) => 1.0
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 (matrix [4.5 0 0])) => (roughly 0.687 1e-3)
       (height-to-index {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 17 (matrix [5 0 0])) => 16.0)

(defn index-to-height
  "Convert index to point with corresponding height"
  [planet size index]
  (let [radius       (:sfsim25.sphere/radius planet)
        max-height   (:sfsim25.atmosphere/height planet)
        max-horizon  (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
        horizon-dist (* (/ index (dec size)) max-horizon)]
    (matrix [(sqrt (+ (sqr radius) (sqr horizon-dist))) 0 0])))

(facts "Convert index to point with corresponding height"
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.0) => (matrix [4 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 1.0) => (matrix [5 0 0])
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 2 0.68718) => (roughly-matrix (matrix [4.5 0 0]) 1e-3)
       (index-to-height {:sfsim25.sphere/radius 4 :sfsim25.atmosphere/height 1} 3 2.0) => (matrix [5 0 0]))

(defn sun-elevation-to-index
  "Convert sun elevation to index"
  [size point light-direction]
  (let [sin-elevation (/ (dot point light-direction) (norm point))]
    (* (dec size) (max 0.0 (/ (- 1 (exp (- 0 (* 3 sin-elevation) 0.6))) (- 1 (exp -3.6)))))))

(facts "Convert sun elevation to index"
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [1 0 0])) => 1.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [0 1 0])) => (roughly 0.464 1e-3)
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-0.2 0.980 0])) => 0.0
       (sun-elevation-to-index 2 (matrix [4 0 0]) (matrix [-1 0 0])) => 0.0
       (sun-elevation-to-index 17 (matrix [4 0 0]) (matrix [1 0 0])) => 16.0)

(defn index-to-sin-sun-elevation
  "Convert index to sinus of sun elevation"
  [size index]
  (/ (+ (log (- 1 (* (/ index (dec size)) (- 1 (exp -3.6))))) 0.6) -3))

(facts "Convert index to sinus of sun elevation"
       (index-to-sin-sun-elevation 2 1.0) => (roughly 1.0 1e-3)
       (index-to-sin-sun-elevation 2 0.0) => (roughly -0.2 1e-3)
       (index-to-sin-sun-elevation 2 0.463863) => (roughly 0.0 1e-3)
       (index-to-sin-sun-elevation 2 0.5) => (roughly 0.022 1e-3)
       (index-to-sin-sun-elevation 3 1.0) => (roughly 0.022 1e-3))

(defn sun-angle-to-index
  "Convert sun and viewing direction angle to index"
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(facts "Convert sun and viewing direction angle to index"
       (sun-angle-to-index 2 (matrix [0 1 0]) (matrix [0 1 0])) => 1.0
       (sun-angle-to-index 2 (matrix [0 1 0]) (matrix [0 -1 0])) => 0.0
       (sun-angle-to-index 17 (matrix [0 1 0]) (matrix [1 0 0])) => 8.0)

(defn limit-quot
  "Compute quotient and limit it"
  [a b limit]
  (if (zero? a)
    a
    (if (< b 0)
      (limit-quot (- a) (- b) limit)
      (if (< a (* b limit))
        (if (> a (- (* b limit)))
          (/ a b)
          (- limit))
        limit))))

(facts "Compute quotient and limit it"
       (limit-quot 0 0 1) => 0
       (limit-quot 4 2 1) => 1
       (limit-quot -4 2 1) => -1
       (limit-quot 1 2 1) => 1/2
       (limit-quot -4 -2 1) => 1)

(defn index-to-sun-direction
  "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
  [size direction sin-sun-elevation index]
  (let [dot-view-sun (- (* 2.0 (/ index (dec size))) 1.0)
        max-sun-1    (sqrt (max 0 (- 1 (sqr sin-sun-elevation))))
        sun-1        (limit-quot (- dot-view-sun (* sin-sun-elevation (mget direction 0))) (mget direction 1) max-sun-1)
        sun-2        (sqrt (max 0 (- 1 (sqr sin-sun-elevation) (sqr sun-1))))]
    (matrix [sin-sun-elevation sun-1 sun-2])))

(facts "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 1.0) => (matrix [0 1 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 0.0) => (matrix [0 -1 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 1.0 0.5) => (matrix [1 0 0])
       (index-to-sun-direction 2 (matrix [0 1 0]) 1.00001 0.5) => (roughly-matrix (matrix [1 0 0]) 1e-3)
       (index-to-sun-direction 2 (matrix [0 1 0]) 0.0 0.5) => (matrix [0 0 1])
       (index-to-sun-direction 2 (matrix [1 0 0]) 1.0 1.0) => (matrix [1 0 0])
       (index-to-sun-direction 2 (matrix [0 -1 0]) 0.0 1.0) => (matrix [0 -1 0])
       (index-to-sun-direction 3 (matrix [0 1 0]) 0.0 1.0) => (matrix [0 0 1]))

(defn elevation-to-index
  "Convert elevation to index depending on height"
  [planet size point direction above-horizon]
  (let [radius        (norm point)
        ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        sin-elevation (/ (dot point direction) radius)
        rho           (horizon-distance planet radius)
        Delta         (- (sqr (* radius sin-elevation)) (sqr rho))
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))]
    (* (dec size)
       (if above-horizon
         (- 0.5 (/ (- (* radius sin-elevation) (sqrt (max 0 (+ Delta (sqr H))))) (+ (* 2 rho) (* 2 H))))
         (+ 0.5 (limit-quot (+ (* radius sin-elevation) (sqrt (max 0 Delta))) (* 2 rho) 0.5))))))

(facts "Convert elevation to index"  ; TODO: clip extreme cases where above-horizon is wrong
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
         (elevation-to-index planet 3 (matrix [4 0 0]) (matrix [0 1 0]) true) => (roughly 2.0 1e-3)))

(defn index-to-elevation
  "Convert index and radius to elevation"
  [planet size radius index]
  (let [ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        horizon-dist  (horizon-distance planet radius)
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))
        scaled-index  (/ index (dec size))]
    (if (<= scaled-index 0.5)
      (let [ground-dist   (* horizon-dist (- 1 (* 2 scaled-index)))
            sin-elevation (limit-quot (- (sqr ground-radius) (sqr radius) (sqr ground-dist)) (* 2 radius ground-dist) 1.0)]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) false])
      (let [sky-dist      (* (+ horizon-dist H) (- (* 2 scaled-index) 1))
            sin-elevation (min 1.0 (/ (- (sqr top-radius) (sqr radius) (sqr sky-dist)) (* 2 radius sky-dist)))]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) true]))))

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
         (first (index-to-elevation planet 2 5.0 0.5)) => (roughly-matrix (matrix [-1 0 0]) 1e-3)
         (first (index-to-elevation planet 2 5.0 0.50001)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.5)) => (roughly-matrix (matrix [0 1 0]) 1e-3)
         (first (index-to-elevation planet 2 4.0 0.50001)) => (roughly-matrix (matrix [1 0 0]) 1e-3)))

(defn transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [planet shape]
  (fn [point direction above-horizon]
      [(height-to-index planet (first shape) point)
       (elevation-to-index planet (second shape) point direction above-horizon)]))

(defn transmittance-backward
  "Backward transformation for looking up transmittance values"
  [planet shape]
  (fn [height-index elevation-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (mget point 0) elevation-index)]
        [point direction above-horizon])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet shape]
  #:sfsim25.interpolate{:shape shape :forward (transmittance-forward planet shape) :backward (transmittance-backward planet shape)})

(facts "Create transformations for interpolating transmittance function"
       (let [radius   6378000.0
             height   100000.0
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
         (second (backward 0.0 16.0)) => (matrix [0 1 0])
         (second (backward 14.0 8.0)) => (matrix [-1 0 0])
         (third (backward 0.0 16.0)) => true
         (third (backward 14.0 8.0)) => false))

(defn ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [point direction light-direction above-horizon]
      [(height-to-index planet (first shape) point)
       (elevation-to-index planet (second shape) point direction above-horizon)
       (sun-elevation-to-index (third shape) point light-direction)
       (sun-angle-to-index (fourth shape) direction light-direction)]))

(defn ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [height-index elevation-index sun-elevation-index sun-heading-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (mget point 0) elevation-index)
            sin-sun-elevation         (index-to-sin-sun-elevation (third shape) sun-elevation-index)
            light-direction           (index-to-sun-direction (fourth shape) direction sin-sun-elevation sun-heading-index)]
        [point direction light-direction above-horizon])))

(defn ray-scatter-space
  "Create transformation for interpolating ray scatter function"
  [planet shape]
  #:sfsim25.interpolate {:shape shape :forward (ray-scatter-forward planet shape) :backward (ray-scatter-backward planet shape)})

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
