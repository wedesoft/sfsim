(ns sfsim25.atmosphere
    "Functions for computing the atmosphere"
    (:require [clojure.core.matrix :refer :all]
              [clojure.core.matrix.linear :refer (norm)]
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
  (mul scatter-base (Math/exp (- (/ height scatter-scale)))))

(defn extinction
  "Compute Mie or Rayleigh extinction for given atmosphere and height"
  ^Vector [scattering-type ^double height]
  (div (scattering scattering-type height) (or (::scatter-quotient scattering-type) 1)))

(defn phase
  "Mie scattering phase function by Cornette and Shanks depending on assymetry g and mu = cos(theta)"
  [{:sfsim25.atmosphere/keys [scatter-g] :or {scatter-g 0}} mu]
  (let [scatter-g-sqr (sqr scatter-g)]
    (/ (* 3 (- 1 scatter-g-sqr) (+ 1 (sqr mu)))
       (* 8 Math/PI (+ 2 scatter-g-sqr) (Math/pow (- (+ 1 scatter-g-sqr) (* 2 scatter-g mu)) 1.5)))))

(defn atmosphere-intersection
  "Get intersection of ray with artificial limit of atmosphere"
  [{:sfsim25.sphere/keys [centre radius] :as planet} ray]
  (let [height                    (:sfsim25.atmosphere/height planet)
        atmosphere                #:sfsim25.sphere{:centre centre :radius (+ radius height)}
        {:sfsim25.intersection/keys [distance length]} (ray-sphere-intersection atmosphere ray)]
    (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) (+ distance length)))))

(defn surface-intersection
  "Get intersection of ray with surface of planet or nil if there is no intersection"
  [planet ray]
  (let [{:sfsim25.intersection/keys [distance length]} (ray-sphere-intersection planet ray)]
    (if (zero? length)
      nil
      (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) distance)))))

(defn surface-point?
  "Check whether a point is near the surface or near the edge of the atmosphere"
  [planet point]
  (< (* 2 (height planet point)) (:sfsim25.atmosphere/height planet)))

(defn ray-extremity
  "Get intersection with surface of planet or artificial limit of atmosphere assuming that ray starts inside atmosphere"
  [planet ray]
  (let [surface-point (and (ray-pointing-downwards planet ray) (surface-intersection planet ray))]
    (or surface-point (atmosphere-intersection planet ray))))

(defn- exp-negative
  "Negative exponentiation"
  [x]
  (exp (sub x)))

(defn transmittance
  "Compute transmissiveness of atmosphere between two points x and x0 considering specified scattering effects"
  ([planet scatter steps x x0]
   (let [overall-extinction (fn [point] (apply add (map #(extinction % (height planet point)) scatter)))]
     (exp-negative (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 overall-extinction))))
  ([planet scatter intersection steps x v]
   (transmittance planet scatter steps x (intersection planet #:sfsim25.ray{:origin x :direction v}))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E0) depending on position of sun"
  [planet scatter steps intensity x light-direction]
  (let [normal (normalise (sub x (:sfsim25.sphere/centre planet)))]
    (mul (max 0 (dot normal light-direction))
         (transmittance planet scatter atmosphere-intersection steps x light-direction) intensity)))

(defn point-scatter-base
  "Compute single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  [planet scatter steps intensity x view-direction light-direction]
  (let [height-of-x     (height planet x)
        scattering-at-x #(mul (scattering % height-of-x) (phase % (dot view-direction light-direction)))
        sun-ray         #:sfsim25.ray{:origin x :direction light-direction}]
    (if (surface-intersection planet sun-ray)
      (matrix [0 0 0])
      (let [overall-scatter (apply add (map scattering-at-x scatter))]
        (mul intensity overall-scatter (transmittance planet scatter atmosphere-intersection steps x light-direction))))))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  [planet scatter intersection steps point-scatter x view-direction light-direction]
  (let [point (intersection planet #:sfsim25.ray{:origin x :direction view-direction})
        ray   #:sfsim25.ray{:origin x :direction (sub point x)}]
    (integral-ray ray steps 1.0 #(mul (transmittance planet scatter steps x %) (point-scatter % view-direction light-direction)))))

(defn point-scatter
  "Compute in-scattering of light at a point and given direction in atmosphere (J) plus light received from surface (E)"
  [planet scatter ray-scatter surface-radiance intensity sphere-steps ray-steps x view-direction light-direction]
  (let [normal        (normalise (sub x (:sfsim25.sphere/centre planet)))
        height-of-x   (height planet x)
        scatter-at-x  #(mul (scattering %2 height-of-x) (phase %2 (dot view-direction %1)))]
    (integral-sphere sphere-steps
                     normal
                     (fn [omega]
                         (let [ray             #:sfsim25.ray{:origin x :direction omega}
                               point           (ray-extremity planet ray)
                               overall-scatter (apply add (map (partial scatter-at-x omega) scatter))]
                           (mul overall-scatter
                                (add (ray-scatter x omega light-direction)
                                     (if (surface-point? planet point)
                                       (let [surface-brightness (mul (div (::brightness planet) Math/PI)
                                                                     (surface-radiance point light-direction))]
                                         (mul (transmittance planet scatter ray-steps x point) surface-brightness))
                                       (matrix [0 0 0])))))))))

(defn surface-radiance
  "Integrate over half sphere to get surface radiance E(S) depending on ray scatter"
  [planet ray-scatter steps x light-direction]
  (let [normal (normalise (sub x (:sfsim25.sphere/centre planet)))]
    (integral-half-sphere steps normal #(mul (ray-scatter x % light-direction) (dot % normal)))))

(defn horizon-angle
  "Get angle of planet's horizon below the horizontal plane depending on the height of the observer"
  ^double [{:sfsim25.sphere/keys [^Vector centre ^double radius]} ^Vector point]
  (let [distance (max radius (norm (sub point centre)))]
    (Math/acos (/ radius distance))))

(defn elevation-to-index
  "Convert elevation value to lookup table index depending on position of horizon"
  [{:sfsim25.sphere/keys [^Vector centre ^double radius] :as planet} size power]
  (let [sky-size    (inc (quot size 2))
        ground-size (quot (dec size) 2)
        pi2         (/ Math/PI 2)]
    (fn ^double [^Vector point ^Vector direction]
        (let [distance      (norm (sub point centre))
              cos-elevation (/ (dot point direction) (norm point))
              elevation     (Math/acos cos-elevation)
              horizon       (horizon-angle planet point)
              invert        #(- 1 %)]
          (if (<= elevation (+ pi2 horizon))
            (-> elevation (/ (+ pi2 horizon)) invert (Math/pow (/ 1.0 power)) invert (* (dec sky-size)))
            (-> elevation (- pi2 horizon) (/ (- pi2 horizon)) (Math/pow (/ 1.0 power)) (* (dec ground-size)) (+ sky-size)))))))

(defn index-to-elevation
  "Convert elevation lookup index to directional vector depending on position of horizon"
  [{:sfsim25.sphere/keys [^double radius] :as planet} size power]
  (let [sky-size    (inc (quot size 2))
        ground-size (quot (dec size) 2)
        pi2         (/ Math/PI 2)
        invert      #(- 1 %)]
    (fn ^Vector [^double height ^double index]
        (let [horizon (horizon-angle planet (matrix [(+ radius height) 0 0]))]
          (if (<= index (+ (dec sky-size) 0.5))
            (let [angle (-> index (/ (dec sky-size)) invert (Math/pow power) invert (* (+ pi2 horizon)))]
              (matrix [(Math/cos angle) (Math/sin angle) 0]))
            (let [angle (-> index (- sky-size) (/ (dec ground-size)) (Math/pow power) (* (- pi2 horizon)) (+ pi2 horizon))]
              (matrix [(Math/cos angle) (Math/sin angle) 0])))))))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [{:sfsim25.sphere/keys [centre radius] :as planet} size power]
  (fn [^Vector point ^Vector direction]
      (let [height          (- (norm (sub point centre)) radius)
            elevation-index ((elevation-to-index planet size power) point direction)]
        [height elevation-index])))

(defn- transmittance-backward
  "Backward transformation for looking up transmittance values"
  [{:sfsim25.sphere/keys [centre radius] :as planet} size power]
  (fn [^double height ^double elevation-index]
      (let [point     (add centre (matrix [(+ radius height) 0 0]))
            direction ((index-to-elevation planet size power) height elevation-index)]
        [point direction])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet size power]
  (let [shape   [size size]
        height  (:sfsim25.atmosphere/height planet)
        scaling (linear-space [0 0] [height (dec size)] shape)]
    (compose-space scaling #:sfsim25.interpolate{:forward (transmittance-forward planet size power)
                                                 :backward (transmittance-backward planet size power)})))

(def surface-radiance-space transmittance-space)

(defn- clip-angle [angle] (if (< angle (- Math/PI)) (+ angle (* 2 Math/PI)) (if (>= angle Math/PI) (- angle (* 2 Math/PI)) angle)))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [centre radius] :as planet} size power]
  (fn [^Vector point ^Vector direction ^Vector light-direction]
      (let [radius-vector           (sub point centre)
            height                  (- (norm radius-vector) radius)
            plane                   (oriented-matrix (normalise radius-vector))
            direction-rotated       (mmul plane direction)
            light-direction-rotated (mmul plane light-direction)
            elevation-index         ((elevation-to-index planet size power) point direction)
            sun-elevation-index     ((elevation-to-index planet size power) point light-direction)
            direction-azimuth       (Math/atan2 (mget direction-rotated 2) (mget direction-rotated 1))
            light-direction-azimuth (Math/atan2 (mget light-direction-rotated 2) (mget light-direction-rotated 1))
            sun-heading             (Math/abs (clip-angle (- light-direction-azimuth direction-azimuth)))]
        [height elevation-index sun-elevation-index sun-heading])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [centre radius] :as planet} size power]
  (fn [^double height ^double elevation-index ^double sun-elevation-index ^double sun-heading]
      (let [point             (matrix [(+ radius height) 0 0])
            direction         ((index-to-elevation planet size power) height elevation-index)
            light-elevation   ((index-to-elevation planet size power) height sun-elevation-index)
            cos-sun-elevation (mget light-elevation 0)
            sin-sun-elevation (mget light-elevation 1)
            cos-sun-heading   (Math/cos sun-heading)
            sin-sun-heading   (Math/sin sun-heading)
            light-direction   (matrix [cos-sun-elevation (* sin-sun-elevation cos-sun-heading) (* sin-sun-elevation sin-sun-heading)])]
        [point direction light-direction])))

(defn ray-scatter-space
  "Create transformations for interpolating ray scatter function"
  [planet size power]
  (let [shape   [size size size size]
        height  (:sfsim25.atmosphere/height planet)
        scaling (linear-space [0 0 0 0] [height (dec size) (dec size) Math/PI] shape)]
    (compose-space scaling #:sfsim25.interpolate{:forward (ray-scatter-forward planet size power)
                                                 :backward (ray-scatter-backward planet size power)})))

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
