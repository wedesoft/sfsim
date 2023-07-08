(ns sfsim25.atmosphere
    "Functions for computing the atmosphere"
    (:require [clojure.java.io :as io]
              [fastmath.vector :refer (vec3 mag normalize add sub div dot mult emult) :as fv]
              [clojure.math :refer (exp pow PI sqrt log)]
              [sfsim25.ray :refer (integral-ray)]
              [sfsim25.sphere :refer (height integral-half-sphere integral-sphere ray-sphere-intersection)]
              [sfsim25.util :refer (third fourth limit-quot sqr)])
    (:import [fastmath.vector Vec3]))

(set! *unchecked-math* true)

(defn scattering
  "Compute scattering or absorption amount in atmosphere"
  (^Vec3 [^clojure.lang.IPersistentMap {:sfsim25.atmosphere/keys [scatter-base scatter-scale]} ^double height]
         (mult scatter-base (-> height (/ scatter-scale) - exp)))
  (^Vec3 [^clojure.lang.IPersistentMap planet ^clojure.lang.IPersistentMap component ^Vec3 x]
         (scattering component (height planet x))))

(defn extinction
  "Compute Mie or Rayleigh extinction for given atmosphere and height"
  ^Vec3 [^clojure.lang.IPersistentMap scattering-type ^double height]
  (div (scattering scattering-type height) (or (::scatter-quotient scattering-type) 1)))

(defn phase
  "Mie scattering phase function by Henyey-Greenstein depending on assymetry g and mu = cos(theta)"
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
    (add (:sfsim25.ray/origin ray) (mult (:sfsim25.ray/direction ray) (+ distance length)))))

(defn surface-intersection
  "Get intersection of ray with surface of planet or nearest point if there is no intersection"
  [planet ray]
  (let [{:sfsim25.intersection/keys [distance]} (ray-sphere-intersection planet ray)]
    (add (:sfsim25.ray/origin ray) (mult (:sfsim25.ray/direction ray) distance))))

(defn surface-point?
  "Check whether a point is near the surface or near the edge of the atmosphere"
  [planet point]
  (< (* 2 (height planet point)) (:sfsim25.atmosphere/height planet)))

(defn is-above-horizon?
  "Check whether there is sky or ground in a certain direction"
  [planet point direction]
  (let [norm-point           (mag point)
        sin-elevation-radius (dot direction point)
        horizon-distance-sqr (->> planet :sfsim25.sphere/radius sqr (- (sqr norm-point)))]
    (or (>= sin-elevation-radius 0) (<= (sqr sin-elevation-radius) horizon-distance-sqr))))

(defn ray-extremity
  "Get intersection with surface of planet or artificial limit of atmosphere assuming that ray starts inside atmosphere"
  [planet ray]
  (if (is-above-horizon? planet (:sfsim25.ray/origin ray) (:sfsim25.ray/direction ray))
    (atmosphere-intersection planet ray)
    (surface-intersection planet ray)))

(defn transmittance
  "Compute transmissiveness of atmosphere between two points x and x0 considering specified scattering effects"
  ([planet scatter steps x x0]
   (let [overall-extinction (fn [point] (apply add (map #(extinction % (height planet point)) scatter)))]
     (-> (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 overall-extinction) sub fv/exp)))
  ([planet scatter steps x v above-horizon]
   (let [intersection (if above-horizon atmosphere-intersection surface-intersection)]
     (transmittance planet scatter steps x (intersection planet #:sfsim25.ray{:origin x :direction v})))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E0) depending on position of sun"
  [planet scatter steps intensity x light-direction]
  (let [normal (normalize (sub x (:sfsim25.sphere/centre planet)))]
    (mult (emult (transmittance planet scatter steps x light-direction true) intensity)
          (max 0.0 (dot normal light-direction)))))

(defn- in-scattering-component
  "Determine amount of scattering for a scattering component, view direction, and light direction"
  [planet component x view-direction light-direction]
  (mult (scattering planet component x) (phase component (dot view-direction light-direction))))

(defn- overall-in-scattering
  "Determine overall amount of in-scattering for multiple scattering components"
  [planet scatter x view-direction light-direction]
  (apply add (map #(in-scattering-component planet % x view-direction light-direction) scatter)))

(defn- filtered-sun-light
  "Determine amount of incoming direct sun light"
  [planet scatter steps x light-direction intensity]
  (if (is-above-horizon? planet x light-direction)
    (emult intensity (transmittance planet scatter steps x light-direction true))
    (vec3 0 0 0)))  ; No direct sun light if sun is below horizon.

(defn- overall-point-scatter
  "Compute single-scatter components of light at a point and given direction in atmosphere"
  [planet scatter components steps intensity x view-direction light-direction]
  (emult (overall-in-scattering planet components x view-direction light-direction)
         (filtered-sun-light planet scatter steps x light-direction intensity)))

(defn point-scatter-component
  "Compute single-scatter component of light at a point and given direction in atmosphere"
  [planet scatter component steps intensity x view-direction light-direction _above-horizon]
  (overall-point-scatter planet scatter [component] steps intensity x view-direction light-direction))

(defn strength-component
  "Compute scatter strength of component of light at a point and given direction in atmosphere"
  [planet scatter component steps intensity x _view-direction light-direction _above-horizon]
  (emult (scattering planet component x)
         (filtered-sun-light planet scatter steps x light-direction intensity)))

(defn point-scatter-base
  "Compute total single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  [planet scatter steps intensity x view-direction light-direction _above-horizon]
  (overall-point-scatter planet scatter scatter steps intensity x view-direction light-direction))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  [planet scatter steps point-scatter x view-direction light-direction above-horizon]
  (let [intersection (if above-horizon atmosphere-intersection surface-intersection)
        point        (intersection planet #:sfsim25.ray{:origin x :direction view-direction})
        ray          #:sfsim25.ray{:origin x :direction (sub point x)}]
    (integral-ray ray steps 1.0 #(emult (transmittance planet scatter steps x %)
                                        (point-scatter % view-direction light-direction above-horizon)))))

(defn point-scatter
  "Compute in-scattering of light at a point and given direction in atmosphere (J) plus light received from surface (E)"
  [planet scatter ray-scatter surface-radiance _intensity sphere-steps ray-steps x view-direction light-direction _above-horizon]
  (let [normal        (normalize (sub x (:sfsim25.sphere/centre planet)))]
    (integral-sphere sphere-steps
                     normal
                     (fn [omega]
                         (let [ray             #:sfsim25.ray{:origin x :direction omega}
                               point           (ray-extremity planet ray)
                               surface         (surface-point? planet point)
                               overall-scatter (overall-in-scattering planet scatter x view-direction omega)]
                           (emult overall-scatter
                                  (add (ray-scatter x omega light-direction (not surface))
                                       (if surface
                                         (let [surface-brightness (-> (::brightness planet) (div PI)
                                                                      (emult (surface-radiance point light-direction)))]
                                           (emult (transmittance planet scatter ray-steps x point) surface-brightness))
                                         (vec3 0 0 0)))))))))

(defn surface-radiance
  "Integrate over half sphere to get surface radiance E(S) depending on ray scatter"
  [planet ray-scatter steps x light-direction]
  (let [normal (normalize (sub x (:sfsim25.sphere/centre planet)))]
    (integral-half-sphere steps normal #(mult (ray-scatter x % light-direction true) (dot % normal)))))

(defn horizon-distance
  "Distance from point with specified radius to horizon of planet"
  [planet radius]
  (sqrt (max 0.0 (- (sqr radius) (sqr (:sfsim25.sphere/radius planet))))))

(defn elevation-to-index
  "Convert elevation to index depending on height"
  [planet size point direction above-horizon]
  (let [radius        (mag point)
        ground-radius (:sfsim25.sphere/radius planet)
        top-radius    (+ ground-radius (:sfsim25.atmosphere/height planet))
        sin-elevation (/ (dot point direction) radius)
        rho           (horizon-distance planet radius)
        Delta         (- (sqr (* radius sin-elevation)) (sqr rho))
        H             (sqrt (- (sqr top-radius) (sqr ground-radius)))]
    (* (dec size)
       (if above-horizon
         (- 0.5 (limit-quot (- (* radius sin-elevation) (sqrt (max 0.0 (+ Delta (sqr H))))) (+ (* 2 rho) (* 2 H)) -0.5 0.0))
         (+ 0.5 (limit-quot (+ (* radius sin-elevation) (sqrt (max 0.0 Delta))) (* 2 rho) -0.5 0.0))))))

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
        [(vec3 sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0) false])
      (let [sky-dist      (* (+ horizon-dist H) (- (* 2 scaled-index) 1))
            sin-elevation (limit-quot (- (sqr top-radius) (sqr radius) (sqr sky-dist)) (* 2 radius sky-dist) -1.0 1.0)]
        [(vec3 sin-elevation (sqrt (- 1 (sqr sin-elevation))) 0) true]))))

(defn height-to-index
  "Convert height of point to index"
  [planet size point]
  (let [radius     (:sfsim25.sphere/radius planet)
        max-height (:sfsim25.atmosphere/height planet)]
    (* (dec size) (/ (horizon-distance planet (mag point)) (horizon-distance planet (+ radius max-height))))))

(defn index-to-height
  "Convert index to point with corresponding height"
  [planet size index]
  (let [radius       (:sfsim25.sphere/radius planet)
        max-height   (:sfsim25.atmosphere/height planet)
        max-horizon  (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
        horizon-dist (* (/ index (dec size)) max-horizon)]
    (vec3 (sqrt (+ (sqr radius) (sqr horizon-dist))) 0 0)))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [planet shape]
  (fn [point direction above-horizon]
      (let [height-index    (height-to-index planet (first shape) point)
            elevation-index (elevation-to-index planet (second shape) point direction above-horizon)]
        [height-index elevation-index])))

(defn- transmittance-backward
  "Backward transformation for looking up transmittance values"
  [planet shape]
  (fn [height-index elevation-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (point 0) elevation-index)]
        [point direction above-horizon])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet shape]
  #:sfsim25.interpolate{:shape shape
                        :forward (transmittance-forward planet shape)
                        :backward (transmittance-backward planet shape)})

(defn sun-elevation-to-index
  "Convert sun elevation to index"
  [size point light-direction]
  (let [sin-elevation (/ (dot point light-direction) (mag point))]
    (* (dec size) (max 0.0 (/ (- 1 (exp (- 0 (* 3 sin-elevation) 0.6))) (- 1 (exp -3.6)))))))

(defn index-to-sin-sun-elevation
  "Convert index to sinus of sun elevation"
  [size index]
  (/ (+ (log (- 1 (* (/ index (dec size)) (- 1 (exp -3.6))))) 0.6) -3))

(defn- surface-radiance-forward
  "Forward transformation for interpolating surface-radiance function"
  [planet shape]
  (fn [point direction]
      (let [height-index        (height-to-index planet (first shape) point)
            sun-elevation-index (sun-elevation-to-index (second shape) point direction)]
        [height-index sun-elevation-index])))

(defn- surface-radiance-backward
  "Backward transformation for looking up surface-radiance values"
  [planet shape]
  (fn [height-index sun-elevation-index]
      (let [point             (index-to-height planet (first shape) height-index)
            sin-sun-elevation (index-to-sin-sun-elevation (second shape) sun-elevation-index)
            cos-sun-elevation (->> sin-sun-elevation sqr (- 1) (max 0.0) sqrt)
            light-direction   (vec3 sin-sun-elevation cos-sun-elevation 0)]
        [point light-direction])))

(defn surface-radiance-space
  "Create transformations for interpolating surface-radiance function"
  [planet shape]
  #:sfsim25.interpolate{:shape shape
                        :forward (surface-radiance-forward planet shape)
                        :backward (surface-radiance-backward planet shape)})

(defn sun-angle-to-index
  "Convert sun and viewing direction angle to index"
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(defn index-to-sun-direction
  "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
  [size direction sin-sun-elevation index]
  (let [dot-view-sun (- (* 2.0 (/ index (dec size))) 1.0)
        max-sun-1    (->> sin-sun-elevation sqr (- 1) (max 0.0) sqrt)
        sun-1        (limit-quot (->> sin-sun-elevation (* (direction 0)) (- dot-view-sun)) (direction 1) max-sun-1)
        sun-2        (->> sin-sun-elevation sqr (- 1 (sqr sun-1)) (max 0.0) sqrt)]
    (vec3 sin-sun-elevation sun-1 sun-2)))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [point direction light-direction above-horizon]
      (let [height-index        (height-to-index planet (first shape) point)
            elevation-index     (elevation-to-index planet (second shape) point direction above-horizon)
            sun-elevation-index (sun-elevation-to-index (third shape) point light-direction)
            sun-angle-index     (sun-angle-to-index (fourth shape) direction light-direction)]
        [height-index elevation-index sun-elevation-index sun-angle-index])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [planet shape]
  (fn [height-index elevation-index sun-elevation-index sun-angle-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (point 0) elevation-index)
            sin-sun-elevation         (index-to-sin-sun-elevation (third shape) sun-elevation-index)
            light-direction           (index-to-sun-direction (fourth shape) direction sin-sun-elevation sun-angle-index)]
        [point direction light-direction above-horizon])))

(defn ray-scatter-space
  "Create transformation for interpolating ray scatter function"
  [planet shape]
  #:sfsim25.interpolate {:shape shape :forward (ray-scatter-forward planet shape) :backward (ray-scatter-backward planet shape)})

(def point-scatter-space ray-scatter-space)

(def transmittance-outer
  "Shader function to compute transmittance between point in the atmosphere and space"
  (slurp (io/resource "shaders/atmosphere/transmittance-outer.glsl")))

(def transmittance-track
  "Shader function to compute transmittance between two points in the atmosphere"
  (slurp (io/resource "shaders/atmosphere/transmittance-track.glsl")))

(def ray-scatter-outer
  "Shader function to determine in-scattered light between point in the atmosphere and space"
  (slurp (io/resource "shaders/atmosphere/ray-scatter-outer.glsl")))

(def ray-scatter-track
  "Shader function to determine in-scattered light between two points in the atmosphere"
  (slurp (io/resource "shaders/atmosphere/ray-scatter-track.glsl")))

(def attenuation-outer
  "Shader function combining transmittance and in-scattered light between point in the atmosphere and space"
  (slurp (io/resource "shaders/atmosphere/attenuation-outer.glsl")))

(def attenuation-track
  "Shader function combining transmittance and in-scattered light between two points in the atmosphere"
  (slurp (io/resource "shaders/atmosphere/attenuation-track.glsl")))

(def vertex-atmosphere
  "Pass through coordinates of quad for rendering atmosphere and determine viewing direction and camera origin"
  (slurp (io/resource "shaders/atmosphere/vertex.glsl")))

(def fragment-atmosphere
  "Fragment shader for rendering atmosphere and sun"
  (slurp (io/resource "shaders/atmosphere/fragment.glsl")))

(def phase-function
  "Shader function for scattering phase function"
  (slurp (io/resource "shaders/atmosphere/phase-function.glsl")))

(set! *unchecked-math* false)
