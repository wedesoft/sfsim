(ns sfsim25.atmosphere
    "Functions for computing the atmosphere"
    (:require [clojure.core.matrix :refer (matrix mget mmul add sub mul div normalise dot) :as m]
              [clojure.core.matrix.linear :refer (norm)]
              [clojure.math :refer (cos sin exp pow atan2 acos asin PI sqrt log)]
              [sfsim25.interpolate :refer :all]
              [sfsim25.matrix :refer :all]
              [sfsim25.ray :refer :all]
              [sfsim25.sphere :refer :all]
              [sfsim25.util :refer :all])
    (:import [mikera.vectorz Vector]))

(set! *unchecked-math* true)

(defn scattering
  "Compute scattering or absorption amount in atmosphere"
  ^Vector [{:sfsim25.atmosphere/keys [scatter-base scatter-scale]} ^double height]
  (mul scatter-base (exp (- (/ height scatter-scale)))))

(defn extinction
  "Compute Mie or Rayleigh extinction for given atmosphere and height"
  ^Vector [scattering-type ^double height]
  (div (scattering scattering-type height) (or (::scatter-quotient scattering-type) 1)))

(defn phase
  "Mie scattering phase function by Cornette and Shanks depending on assymetry g and mu = cos(theta)"
  [{:sfsim25.atmosphere/keys [scatter-g] :or {scatter-g 0}} mu]
  (let [scatter-g-sqr (sqr scatter-g)]
    (/ (* 3 (- 1 scatter-g-sqr) (+ 1 (sqr mu)))
       (* 8 PI (+ 2 scatter-g-sqr) (pow (- (+ 1 scatter-g-sqr) (* 2 scatter-g mu)) 1.5)))))

(defn atmosphere-intersection
  "Get intersection of ray with artificial limit of atmosphere"
  [{:sfsim25.sphere/keys [centre radius] :as planet} ray]
  (let [height                    (:sfsim25.atmosphere/height planet)
        atmosphere                #:sfsim25.sphere{:centre centre :radius (+ radius height)}
        {:sfsim25.intersection/keys [distance length]} (ray-sphere-intersection atmosphere ray)]
    (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) (+ distance length)))))

(defn surface-intersection
  "Get intersection of ray with surface of planet or nearest point if there is no intersection"
  [planet ray]
  (let [{:sfsim25.intersection/keys [distance length]} (ray-sphere-intersection planet ray)]
    (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) distance))))

(defn surface-point?
  "Check whether a point is near the surface or near the edge of the atmosphere"
  [planet point]
  (< (* 2 (height planet point)) (:sfsim25.atmosphere/height planet)))

(defn horizon-angle
  "Get angle of planet's horizon below the horizontal plane depending on the height of the observer"
  ^double [{:sfsim25.sphere/keys [^Vector centre ^double radius]} ^Vector point]
  (let [distance (max radius (norm (sub point centre)))]
    (acos (/ radius distance))))

(defn is-above-horizon?
  "Check whether there is sky or ground in a certain direction"
  [planet point direction]
  (let [radius               (norm point)
        sin-elevation-radius (dot direction point)
        horizon-distance-2   (- (sqr radius) (sqr (:sfsim25.sphere/radius planet)))]
    (or (>= sin-elevation-radius 0) (<= (sqr sin-elevation-radius) horizon-distance-2))))

(defn ray-extremity
  "Get intersection with surface of planet or artificial limit of atmosphere assuming that ray starts inside atmosphere"
  [planet ray]
  (if (is-above-horizon? planet (:sfsim25.ray/origin ray) (:sfsim25.ray/direction ray))
    (atmosphere-intersection planet ray)
    (surface-intersection planet ray)))

(defn- exp-negative
  "Negative exponentiation"
  [x]
  (m/exp (sub x)))

(defn transmittance
  "Compute transmissiveness of atmosphere between two points x and x0 considering specified scattering effects"
  ([planet scatter steps x x0]
   (let [overall-extinction (fn [point] (apply add (map #(extinction % (height planet point)) scatter)))]
     (exp-negative (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 overall-extinction))))
  ([planet scatter steps x v above-horizon]
   (let [intersection (if above-horizon atmosphere-intersection surface-intersection)]
     (transmittance planet scatter steps x (intersection planet #:sfsim25.ray{:origin x :direction v})))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E0) depending on position of sun"
  [planet scatter steps intensity x light-direction above-horizon]
  (let [normal (normalise (sub x (:sfsim25.sphere/centre planet)))]
    (if above-horizon
      (mul (max 0 (dot normal light-direction))
           (transmittance planet scatter steps x light-direction true) intensity)
      (matrix [0 0 0]))))

(defn point-scatter-base
  "Compute single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  [planet scatter steps intensity x view-direction light-direction above-horizon]
  (let [height-of-x     (height planet x)
        scattering-at-x #(mul (scattering % height-of-x) (phase % (dot view-direction light-direction)))]
    (if (is-above-horizon? planet x light-direction)
      (let [overall-scatter (apply add (map scattering-at-x scatter))]
        (mul intensity overall-scatter (transmittance planet scatter steps x light-direction true)))
      (matrix [0 0 0]))))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  [planet scatter steps point-scatter x view-direction light-direction above-horizon]
  (let [intersection (if above-horizon atmosphere-intersection surface-intersection)
        point        (intersection planet #:sfsim25.ray{:origin x :direction view-direction})
        ray          #:sfsim25.ray{:origin x :direction (sub point x)}]
    (integral-ray ray steps 1.0 #(mul (transmittance planet scatter steps x %) (point-scatter % view-direction light-direction above-horizon)))))

(defn point-scatter
  "Compute in-scattering of light at a point and given direction in atmosphere (J) plus light received from surface (E)"
  [planet scatter ray-scatter surface-radiance intensity sphere-steps ray-steps x view-direction light-direction above-horizon]
  (let [normal        (normalise (sub x (:sfsim25.sphere/centre planet)))
        height-of-x   (height planet x)
        scatter-at-x  #(mul (scattering %2 height-of-x) (phase %2 (dot view-direction %1)))
        light-above   #(is-above-horizon? planet % light-direction)]
    (integral-sphere sphere-steps
                     normal
                     (fn [omega]
                         (let [ray             #:sfsim25.ray{:origin x :direction omega}
                               point           (ray-extremity planet ray)
                               surface         (surface-point? planet point)
                               overall-scatter (apply add (map (partial scatter-at-x omega) scatter))]
                           (mul overall-scatter
                                (add (ray-scatter x omega light-direction (not surface))
                                     (if surface
                                       (let [surface-brightness
                                             (mul (div (::brightness planet) PI)
                                                  (surface-radiance point light-direction (light-above point)))]
                                         (mul (transmittance planet scatter ray-steps x point) surface-brightness))
                                       (matrix [0 0 0])))))))))

(defn surface-radiance
  "Integrate over half sphere to get surface radiance E(S) depending on ray scatter"
  [planet ray-scatter steps x light-direction light-above]
  (let [normal (normalise (sub x (:sfsim25.sphere/centre planet)))]
    (integral-half-sphere steps normal #(mul (ray-scatter x % light-direction true) (dot % normal)))))

(defn horizon-distance [planet radius]
  "Distance from point with specified radius to horizon of planet"
  (sqrt (max 0.0 (- (sqr radius) (sqr (:sfsim25.sphere/radius planet))))))

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
         (- 0.5 (limit-quot (- (* radius sin-elevation) (sqrt (max 0 (+ Delta (sqr H))))) (+ (* 2 rho) (* 2 H)) -0.5 0.0))
         (+ 0.5 (limit-quot (+ (* radius sin-elevation) (sqrt (max 0 Delta))) (* 2 rho) -0.5 0.0))))))

(defn index-to-elevation
  "Convert index and radius to elevation"
  [planet size radius index]
  (let [ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        horizon-dist  (horizon-distance planet radius)
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))
        scaled-index  (/ index (dec size))]
    (if (or (< scaled-index 0.5) (and (== scaled-index 0.5) (< (* 2 radius) (+ ground-radius top-radius))))
      (let [ground-dist   (* horizon-dist (- 1 (* 2 scaled-index)))
            sin-elevation (limit-quot (- (sqr ground-radius) (sqr radius) (sqr ground-dist)) (* 2 radius ground-dist) 1.0)]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) false])
      (let [sky-dist      (* (+ horizon-dist H) (- (* 2 scaled-index) 1))
            sin-elevation (limit-quot (- (sqr top-radius) (sqr radius) (sqr sky-dist)) (* 2 radius sky-dist) -1.0 1.0)]
        [(matrix [sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0]) true]))))

(defn height-to-index
  "Convert height of point to index"
  [planet size point]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)]
    (* (dec size) (/ (horizon-distance planet (norm point)) (horizon-distance planet (+ radius max-height))))))

(defn index-to-height
  "Convert index to point with corresponding height"
  [planet size index]
  (let [radius       (:sfsim25.sphere/radius planet)
        max-height   (:sfsim25.atmosphere/height planet)
        max-horizon  (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
        horizon-dist (* (/ index (dec size)) max-horizon)]
    (matrix [(sqrt (+ (sqr radius) (sqr horizon-dist))) 0 0])))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [planet shape]
  (fn [point direction above-horizon]
      [(height-to-index planet (first shape) point)
       (elevation-to-index planet (second shape) point direction above-horizon)]))

(defn- transmittance-backward
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

(def surface-radiance-space transmittance-space)

(defn- clip-angle [angle] (if (< angle (- PI)) (+ angle (* 2 PI)) (if (>= angle PI) (- angle (* 2 PI)) angle)))

(defn sun-elevation-to-index
  "Convert sun elevation to index"
  [size point light-direction]
  (let [sin-elevation (/ (dot point light-direction) (norm point))]
    (* (dec size) (max 0.0 (/ (- 1 (exp (- 0 (* 3 sin-elevation) 0.6))) (- 1 (exp -3.6)))))))

(defn index-to-sin-sun-elevation
  "Convert index to sinus of sun elevation"
  [size index]
  (/ (+ (log (- 1 (* (/ index (dec size)) (- 1 (exp -3.6))))) 0.6) -3))

(defn sun-angle-to-index
  "Convert sun and viewing direction angle to index"
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(defn index-to-sun-direction
  "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
  [size direction sin-sun-elevation index]
  (let [dot-view-sun (- (* 2.0 (/ index (dec size))) 1.0)
        max-sun-1    (sqrt (max 0 (- 1 (sqr sin-sun-elevation))))
        sun-1        (limit-quot (- dot-view-sun (* sin-sun-elevation (mget direction 0))) (mget direction 1) max-sun-1)
        sun-2        (sqrt (max 0 (- 1 (sqr sin-sun-elevation) (sqr sun-1))))]
    (matrix [sin-sun-elevation sun-1 sun-2])))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [point direction light-direction above-horizon]
      (let [height-index        (height-to-index planet (first shape) point)
            elevation-index     (elevation-to-index planet (second shape) point direction above-horizon)
            sun-elevation-index (sun-elevation-to-index (third shape) point light-direction)
            sun-angle-index     (sun-angle-to-index (fourth shape) direction light-direction)]
        (if (some (memfn Double/isNaN) [height-index elevation-index sun-elevation-index sun-angle-index])
          (throw (Exception. (str "ray-scatter-forward: " point " " direction " " light-direction " " above-horizon))))
        [height-index elevation-index sun-elevation-index sun-angle-index])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [height-index elevation-index sun-elevation-index sun-angle-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (mget point 0) elevation-index)
            sin-sun-elevation         (index-to-sin-sun-elevation (third shape) sun-elevation-index)
            light-direction           (index-to-sun-direction (fourth shape) direction sin-sun-elevation sun-angle-index)]
        (if (some (memfn Double/isNaN) [(mget point 0) (mget point 1) (mget point 2)
                                        (mget direction 0) (mget direction 1) (mget direction 2)
                                        (mget light-direction 0) (mget light-direction 1) (mget light-direction 2)])
          (throw (Exception. (str "ray-scatter-backward: " height-index " " elevation-index " " sun-elevation-index " " sun-angle-index))))
        [point direction light-direction above-horizon])))

(defn ray-scatter-space
  "Create transformation for interpolating ray scatter function"
  [planet shape]
  #:sfsim25.interpolate {:shape shape :forward (ray-scatter-forward planet shape) :backward (ray-scatter-backward planet shape)})

(def point-scatter-space ray-scatter-space)

(def transmittance-outer
  "Shader function to compute transmittance between point in the atmosphere and space"
  (slurp "resources/shaders/atmosphere/transmittance_outer.glsl"))

(def transmittance-track
  "Shader function to compute transmittance between two points in the atmosphere"
  (slurp "resources/shaders/atmosphere/transmittance_track.glsl"))

(def ray-scatter-outer
  "Shader function to determine in-scattered light between point in the atmosphere and space"
  (slurp "resources/shaders/atmosphere/ray_scatter_outer.glsl"))

(def ray-scatter-track
  "Shader function to determine in-scattered light between two points in the atmosphere"
  (slurp "resources/shaders/atmosphere/ray_scatter_track.glsl"))

(def attenuation-outer
  "Shader function combining transmittance and in-scattered light between point in the atmosphere and space"
  (slurp "resources/shaders/atmosphere/attenuation_outer.glsl"))

(def attenuation-track
  "Shader function combining transmittance and in-scattered light between two points in the atmosphere"
  (slurp "resources/shaders/atmosphere/attenuation_track.glsl"))

(def vertex-atmosphere
  "Pass through coordinates of quad for rendering atmosphere and determine viewing direction and camera origin"
  (slurp "resources/shaders/atmosphere/vertex.glsl"))

(def fragment-atmosphere
  "Fragment shader for rendering atmosphere and sun"
  (slurp "resources/shaders/atmosphere/fragment.glsl"))

(def phase-function
  "Shader function for scattering phase function"
  (slurp "resources/shaders/atmosphere/phase_function.glsl"))

(set! *unchecked-math* false)
