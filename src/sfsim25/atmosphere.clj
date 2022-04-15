(ns sfsim25.atmosphere
    "Functions for computing the atmosphere"
    (:require [clojure.core.matrix :refer (matrix mget mmul add sub mul div normalise dot) :as m]
              [clojure.core.matrix.linear :refer (norm)]
              [clojure.math :refer (cos sin exp pow atan2 acos asin PI)]
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
  (let [angle  (horizon-angle planet point)
        normal (normalise point)]
    (>= (dot normal direction) (- (sin angle)))))

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

(defn elevation-to-index
  "Convert elevation value to lookup table index depending on position of horizon"
  [{:sfsim25.sphere/keys [^Vector centre ^double radius] :as planet} size power point direction above-horizon]
  (let [sky-size      (inc (quot size 2))
        ground-size   (quot (dec size) 2)
        horizon       (horizon-angle planet point)
        normal        (normalise point)
        sin-elevation (dot normal direction)
        elevation     (asin sin-elevation)
        pi2           (/ PI 2)
        invert        #(- 1 %)
        distort       #(pow % (/ 1.0 power))]
    (if above-horizon
      (-> elevation (- (- horizon)) (max 0) (/ (+ pi2 horizon)) distort invert (* (dec sky-size)))
      (-> (- (- horizon) elevation) (max 0) (/ (- pi2 horizon)) distort (* (dec ground-size)) (+ sky-size)))))

(defn index-to-elevation
  "Map elevation lookup index to directional vector depending on position of horizon"
  [{:sfsim25.sphere/keys [^double radius] :as planet} size power height index]
  (let [sky-size    (inc (quot size 2))
        ground-size (quot (dec size) 2)
        horizon     (horizon-angle planet (matrix [(+ radius height) 0 0]))
        pi2         (/ PI 2)
        invert      #(- 1 %)]
    (if (<= index (dec sky-size))
      (let [angle (-> index (/ (dec sky-size)) invert (pow power) (* (+ pi2 horizon)) (- horizon))]
        [(matrix [(sin angle) (cos angle) 0]) true])
      (let [angle (-> index (- sky-size) (/ (dec ground-size)) (pow power) invert (* (- pi2 horizon)) (- pi2))]
        [(matrix [(sin angle) (cos angle) 0]) false]))))

(defn height-to-index
  "Convert height to index"
  [planet size point]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)]
    (-> point norm (- radius) (/ max-height) (* (dec size)))))

(defn index-to-height
  "Convert index to point at certain height"
  [planet size index]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)
        height     (-> index (/ (dec size)) (* max-height))]
    (matrix [(+ radius height) 0 0])))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [planet shape power]
  (fn [point direction above-horizon]
      [(height-to-index planet (first shape) point)
       (elevation-to-index planet (second shape) power point direction above-horizon)]))

(defn- transmittance-backward
  "Backward transformation for looking up transmittance values"
  [planet shape power]
  (fn [height-index elevation-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) power (height planet point) elevation-index)]
        [point direction above-horizon])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet shape power]
  (let [height  (:sfsim25.atmosphere/height planet)]
    #:sfsim25.interpolate{:shape    shape
                          :forward  (transmittance-forward planet shape power)
                          :backward (transmittance-backward planet shape power)}))

(def surface-radiance-space transmittance-space)

(defn- clip-angle [angle] (if (< angle (- PI)) (+ angle (* 2 PI)) (if (>= angle PI) (- angle (* 2 PI)) angle)))

(defn heading-to-index
  "Convert absolute sun heading to lookup index"
  [size point direction light-direction]
  (let [normal                  (normalise point)
        plane                   (oriented-matrix normal)
        direction-rotated       (mmul plane direction)
        light-direction-rotated (mmul plane light-direction)
        direction-azimuth       (atan2 (mget direction-rotated 2) (mget direction-rotated 1))
        light-direction-azimuth (atan2 (mget light-direction-rotated 2) (mget light-direction-rotated 1))
        sun-abs-heading         (abs (clip-angle (- light-direction-azimuth direction-azimuth)))]
    (-> sun-abs-heading (/ PI) (* (dec size)))))

(defn index-to-heading
  "Convert index to absolute sun heading"
  [size index]
  (-> index (/ (dec size)) (* PI)))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [radius] :as planet} shape power]
  (fn [point direction light-direction above-horizon]
      (let []
        [(height-to-index planet (first shape) point)
         (elevation-to-index planet (second shape) power point direction above-horizon)
         (elevation-to-index planet (third shape) power point light-direction (is-above-horizon? planet point light-direction))
         (heading-to-index (fourth shape) point direction light-direction)])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [radius] :as planet} shape power]
  (fn [height-index elevation-index sun-elevation-index sun-heading-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            height                    (height planet point)
            [direction above-horizon] (index-to-elevation planet (second shape) power height elevation-index)
            [light-elevation _]       (index-to-elevation planet (third shape) power height sun-elevation-index)
            sun-heading               (index-to-heading (fourth shape) sun-heading-index)
            cos-sun-elevation         (mget light-elevation 0)
            sin-sun-elevation         (mget light-elevation 1)
            cos-sun-heading           (cos sun-heading)
            sin-sun-heading           (sin sun-heading)
            light-direction           (matrix [cos-sun-elevation
                                               (* sin-sun-elevation cos-sun-heading)
                                               (* sin-sun-elevation sin-sun-heading)])]
        [point direction light-direction above-horizon])))

(defn ray-scatter-space
  "Create transformations for interpolating ray scatter function"
  [planet shape power]
  #:sfsim25.interpolate{:shape shape
                        :forward (ray-scatter-forward planet shape power)
                        :backward (ray-scatter-backward planet shape power)})

(def point-scatter-space ray-scatter-space)

(def transmittance-track
  "Shader function to compute transmittance between two points in the atmosphere"
  (slurp "resources/shaders/atmosphere/transmittance_track.glsl"))

(def ray-scatter-track
  "Shader function to determine in-scattered light between two points in the atmosphere"
  (slurp "resources/shaders/atmosphere/ray_scatter_track.glsl"))

(def vertex-atmosphere
  "Pass through coordinates of quad for rendering atmosphere and determine viewing direction and camera origin"
  (slurp "resources/shaders/atmosphere/vertex.glsl"))

(def fragment-atmosphere
  "Fragment shader for rendering atmosphere and sun"
  (slurp "resources/shaders/atmosphere/fragment.glsl"))

(set! *unchecked-math* false)
