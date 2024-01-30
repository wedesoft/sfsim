(ns sfsim.atmosphere
    "Functions for computing the atmosphere"
    (:require [fastmath.vector :refer (vec3 mag normalize add sub div dot mult emult) :as fv]
              [malli.core :as m]
              [clojure.math :refer (exp pow PI sqrt log)]
              [sfsim.matrix :refer (fvec3)]
              [sfsim.ray :refer (integral-ray ray)]
              [sfsim.sphere :refer (height integral-half-sphere integral-sphere ray-sphere-intersection sphere)]
              [sfsim.interpolate :refer (interpolation-space)]
              [sfsim.render :refer (make-program use-program uniform-sampler uniform-int uniform-float uniform-matrix4
                                    uniform-vector3 use-textures destroy-program make-vertex-array-object
                                    destroy-vertex-array-object render-quads texture-2d texture-4d
                                    make-vector-texture-2d make-vector-texture-4d destroy-texture)]
              [sfsim.shaders :as shaders]
              [sfsim.util :refer (third fourth limit-quot sqr N slurp-floats)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def scatter (m/schema [:map [::scatter-base fvec3]
                             [::scatter-scale :double]
                             [::scatter-quotient {:optional true} :double]
                             [::scatter-g {:optional true} :double]]))

(defn scattering
  "Compute scattering or absorption amount in atmosphere"
  {:malli/schema [:function [:=> [:cat scatter :double] fvec3]
                            [:=> [:cat sphere scatter fvec3] fvec3]]}
  ([{::keys [scatter-base scatter-scale]} height]
   (mult scatter-base (-> height (/ scatter-scale) - exp)))
  ([planet component x]
   (scattering component (height planet x))))

(defn extinction
  "Compute Mie or Rayleigh extinction for given atmosphere and height"
  {:malli/schema [:=> [:cat scatter :double] fvec3]}
  [scattering-type height]
  (div (scattering scattering-type height) (or (::scatter-quotient scattering-type) 1.0)))

(defn phase
  "Mie scattering phase function by Henyey-Greenstein depending on assymetry g and mu = cos(theta)"
  {:malli/schema [:=> [:cat [:map [::scatter-g {:optional true} :double]] :double] :double]}
  [{::keys [scatter-g] :or {scatter-g 0}} mu]
  (let [scatter-g-sqr (sqr scatter-g)]
    (/ (* 3 (- 1 scatter-g-sqr) (+ 1 (sqr mu)))
       (* 8 PI (+ 2 scatter-g-sqr) (pow (- (+ 1 scatter-g-sqr) (* 2 scatter-g mu)) 1.5)))))

(def atmosphere (m/schema [:map [:sfsim.sphere/centre fvec3]
                                [:sfsim.sphere/radius :double]
                                [::height :double]]))

(defn atmosphere-intersection
  "Get intersection of ray with artificial limit of atmosphere"
  {:malli/schema [:=> [:cat atmosphere ray] fvec3]}
  [{:sfsim.sphere/keys [centre radius] :as planet} ray]
  (let [height                    (::height planet)
        atmosphere                #:sfsim.sphere{:centre centre :radius (+ radius height)}
        {:sfsim.intersection/keys [distance length]} (ray-sphere-intersection atmosphere ray)]
    (add (:sfsim.ray/origin ray) (mult (:sfsim.ray/direction ray) (+ distance length)))))

(defn surface-intersection
  "Get intersection of ray with surface of planet or nearest point if there is no intersection"
  {:malli/schema [:=> [:cat sphere ray] fvec3]}
  [planet ray]
  (let [{:sfsim.intersection/keys [distance]} (ray-sphere-intersection planet ray)]
    (add (:sfsim.ray/origin ray) (mult (:sfsim.ray/direction ray) distance))))

(defn surface-point?
  "Check whether a point is near the surface or near the edge of the atmosphere"
  {:malli/schema [:=> [:cat atmosphere fvec3] :boolean]}
  [planet point]
  (< (* 2 (height planet point)) (::height planet)))

(defn is-above-horizon?
  "Check whether there is sky or ground in a certain direction"
  {:malli/schema [:=> [:cat sphere fvec3 fvec3] :boolean]}
  [planet point direction]
  (let [norm-point           (mag point)
        sin-elevation-radius (dot direction point)
        horizon-distance-sqr (->> planet :sfsim.sphere/radius sqr (- (sqr norm-point)))]
    (or (>= sin-elevation-radius 0) (<= (sqr sin-elevation-radius) horizon-distance-sqr))))

(defn ray-extremity
  "Get intersection with surface of planet or artificial limit of atmosphere assuming that ray starts inside atmosphere"
  {:malli/schema [:=> [:cat atmosphere ray] fvec3]}
  [planet ray]
  (if (is-above-horizon? planet (:sfsim.ray/origin ray) (:sfsim.ray/direction ray))
    (atmosphere-intersection planet ray)
    (surface-intersection planet ray)))

(defn transmittance
  "Compute transmissiveness of atmosphere between two points x and x0 considering specified scattering effects"
  {:malli/schema [:function [:=> [:cat atmosphere [:vector scatter] N fvec3 fvec3] fvec3]
                            [:=> [:cat atmosphere [:vector scatter] N fvec3 fvec3 :boolean] fvec3]]}
  ([planet scatter steps x x0]
   (let [overall-extinction (fn [point] (apply add (map #(extinction % (height planet point)) scatter)))]
     (-> (integral-ray #:sfsim.ray{:origin x :direction (sub x0 x)} steps 1.0 overall-extinction) sub fv/exp)))
  ([planet scatter steps x v above-horizon]
   (let [intersection (if above-horizon atmosphere-intersection surface-intersection)]
     (transmittance planet scatter steps x (intersection planet #:sfsim.ray{:origin x :direction v})))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E0) depending on position of sun"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] N fvec3 fvec3 fvec3] fvec3]}
  [planet scatter steps intensity x light-direction]
  (let [normal (normalize (sub x (:sfsim.sphere/centre planet)))]
    (mult (emult (transmittance planet scatter steps x light-direction true) intensity)
          (max 0.0 (dot normal light-direction)))))

(defn- in-scattering-component
  "Determine amount of scattering for a scattering component, view direction, and light direction"
  {:malli/schema [:=> [:cat sphere scatter fvec3 fvec3 fvec3] fvec3]}
  [planet component x view-direction light-direction]
  (mult (scattering planet component x) (phase component (dot view-direction light-direction))))

(defn- overall-in-scattering
  "Determine overall amount of in-scattering for multiple scattering components"
  {:malli/schema [:=> [:cat sphere [:vector scatter] fvec3 fvec3 fvec3] fvec3]}
  [planet scatter x view-direction light-direction]
  (apply add (map #(in-scattering-component planet % x view-direction light-direction) scatter)))

(defn- filtered-sun-light
  "Determine amount of incoming direct sun light"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] N fvec3 fvec3 fvec3] fvec3]}
  [planet scatter steps x light-direction intensity]
  (if (is-above-horizon? planet x light-direction)
    (emult intensity (transmittance planet scatter steps x light-direction true))
    (vec3 0 0 0)))  ; No direct sun light if sun is below horizon.

(defn- overall-point-scatter
  "Compute single-scatter components of light at a point and given direction in atmosphere"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] [:vector scatter] N fvec3 fvec3 fvec3 fvec3] fvec3]}
  [planet scatter components steps intensity x view-direction light-direction]
  (emult (overall-in-scattering planet components x view-direction light-direction)
         (filtered-sun-light planet scatter steps x light-direction intensity)))

(defn point-scatter-component
  "Compute single-scatter component of light at a point and given direction in atmosphere"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] scatter N fvec3 fvec3 fvec3 fvec3 :boolean] fvec3]}
  [planet scatter component steps intensity x view-direction light-direction _above-horizon]
  (overall-point-scatter planet scatter [component] steps intensity x view-direction light-direction))

(defn strength-component
  "Compute scatter strength of component of light at a point and given direction in atmosphere"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] scatter N fvec3 fvec3 fvec3 fvec3 :boolean] fvec3]}
  [planet scatter component steps intensity x _view-direction light-direction _above-horizon]
  (emult (scattering planet component x)
         (filtered-sun-light planet scatter steps x light-direction intensity)))

(defn point-scatter-base
  "Compute total single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] N fvec3 fvec3 fvec3 fvec3 :boolean] fvec3]}
  [planet scatter steps intensity x view-direction light-direction _above-horizon]
  (overall-point-scatter planet scatter scatter steps intensity x view-direction light-direction))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] N fn? fvec3 fvec3 fvec3 :boolean] fvec3]}
  [planet scatter steps point-scatter x view-direction light-direction above-horizon]
  (let [intersection (if above-horizon atmosphere-intersection surface-intersection)
        point        (intersection planet #:sfsim.ray{:origin x :direction view-direction})
        ray          #:sfsim.ray{:origin x :direction (sub point x)}]
    (integral-ray ray steps 1.0 #(emult (transmittance planet scatter steps x %)
                                        (point-scatter % view-direction light-direction above-horizon)))))

(defn point-scatter
  "Compute in-scattering of light at a point and given direction in atmosphere (J) plus light received from surface (E)"
  {:malli/schema [:=> [:cat atmosphere [:vector scatter] fn? fn? fvec3 N N fvec3 fvec3 fvec3 :boolean] fvec3]}
  [planet scatter ray-scatter surface-radiance _intensity sphere-steps ray-steps x view-direction light-direction _above-horizon]
  (let [normal        (normalize (sub x (:sfsim.sphere/centre planet)))]
    (integral-sphere sphere-steps
                     normal
                     (fn [omega]
                         (let [ray             #:sfsim.ray{:origin x :direction omega}
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
  {:malli/schema [:=> [:cat atmosphere fn? N fvec3 fvec3] fvec3]}
  [planet ray-scatter steps x light-direction]
  (let [normal (normalize (sub x (:sfsim.sphere/centre planet)))]
    (integral-half-sphere steps normal #(mult (ray-scatter x % light-direction true) (dot % normal)))))

(defn horizon-distance
  "Distance from point with specified radius to horizon of planet"
  {:malli/schema [:=> [:cat sphere :double] :double]}
  [planet radius]
  (sqrt (max 0.0 (- (sqr radius) (sqr (:sfsim.sphere/radius planet))))))

(defn elevation-to-index
  "Convert elevation to index depending on height"
  {:malli/schema [:=> [:cat atmosphere N fvec3 fvec3 :boolean] :double]}
  [planet size point direction above-horizon]
  (let [radius        (mag point)
        ground-radius (:sfsim.sphere/radius planet)
        top-radius    (+ ground-radius (::height planet))
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
  {:malli/schema [:=> [:cat atmosphere N :double :double] [:tuple fvec3 :boolean]]}
  [planet size radius index]
  (let [ground-radius (:sfsim.sphere/radius planet)
        top-radius    (+ ground-radius (::height planet))
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
  {:malli/schema [:=> [:cat atmosphere N fvec3] :double]}
  [planet size point]
  (let [radius     (:sfsim.sphere/radius planet)
        max-height (::height planet)]
    (* (dec size) (/ (horizon-distance planet (mag point)) (horizon-distance planet (+ radius max-height))))))

(defn index-to-height
  "Convert index to point with corresponding height"
  {:malli/schema [:=> [:cat atmosphere N :double] fvec3]}
  [planet size index]
  (let [radius       (:sfsim.sphere/radius planet)
        max-height   (::height planet)
        max-horizon  (sqrt (- (sqr (+ radius max-height)) (sqr radius)))
        horizon-dist (* (/ index (dec size)) max-horizon)]
    (vec3 (sqrt (+ (sqr radius) (sqr horizon-dist))) 0 0)))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] [:=> [:cat fvec3 fvec3 :boolean] [:tuple :double :double]]]}
  [planet shape]
  (fn [point direction above-horizon]
      (let [height-index    (height-to-index planet (first shape) point)
            elevation-index (elevation-to-index planet (second shape) point direction above-horizon)]
        [height-index elevation-index])))

(defn- transmittance-backward
  "Backward transformation for looking up transmittance values"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] [:=> [:cat :double :double] [:tuple fvec3 fvec3 :boolean]]]}
  [planet shape]
  (fn [height-index elevation-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (point 0) elevation-index)]
        [point direction above-horizon])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] interpolation-space]}
  [planet shape]
  #:sfsim.interpolate{:shape shape
                      :forward (transmittance-forward planet shape)
                      :backward (transmittance-backward planet shape)})

(defn sun-elevation-to-index
  "Convert sun elevation to index"
  {:malli/schema [:=> [:cat N fvec3 fvec3] :double]}
  [size point light-direction]
  (let [sin-elevation (/ (dot point light-direction) (mag point))]
    (* (dec size) (max 0.0 (/ (- 1 (exp (- 0 (* 3 sin-elevation) 0.6))) (- 1 (exp -3.6)))))))

(defn index-to-sin-sun-elevation
  "Convert index to sinus of sun elevation"
  {:malli/schema [:=> [:cat N :double] :double]}
  [size index]
  (/ (+ (log (- 1 (* (/ index (dec size)) (- 1 (exp -3.6))))) 0.6) -3))

(defn- surface-radiance-forward
  "Forward transformation for interpolating surface-radiance function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] [:=> [:cat fvec3 fvec3] [:tuple :double :double]]]}
  [planet shape]
  (fn [point direction]
      (let [height-index        (height-to-index planet (first shape) point)
            sun-elevation-index (sun-elevation-to-index (second shape) point direction)]
        [height-index sun-elevation-index])))

(defn- surface-radiance-backward
  "Backward transformation for looking up surface-radiance values"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] [:=> [:cat :double :double] [:tuple fvec3 fvec3]]]}
  [planet shape]
  (fn [height-index sun-elevation-index]
      (let [point             (index-to-height planet (first shape) height-index)
            sin-sun-elevation (index-to-sin-sun-elevation (second shape) sun-elevation-index)
            cos-sun-elevation (->> sin-sun-elevation sqr (- 1) (max 0.0) sqrt)
            light-direction   (vec3 sin-sun-elevation cos-sun-elevation 0)]
        [point light-direction])))

(defn surface-radiance-space
  "Create transformations for interpolating surface-radiance function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N]] interpolation-space]}
  [planet shape]
  #:sfsim.interpolate{:shape shape
                      :forward (surface-radiance-forward planet shape)
                      :backward (surface-radiance-backward planet shape)})

(defn sun-angle-to-index
  "Convert sun and viewing direction angle to index"
  {:malli/schema [:=> [:cat N fvec3 fvec3] :double]}
  [size direction light-direction]
  (* (dec size) (/ (+ 1 (dot direction light-direction)) 2)))

(defn index-to-sun-direction
  "Convert sinus of sun elevation, sun angle index, and viewing direction to sun direction vector"
  {:malli/schema [:=> [:cat N fvec3 :double :double] fvec3]}
  [size direction sin-sun-elevation index]
  (let [dot-view-sun (- (* 2.0 (/ index (dec size))) 1.0)
        max-sun-1    (->> sin-sun-elevation sqr (- 1) (max 0.0) sqrt)
        sun-1        (limit-quot (->> sin-sun-elevation (* (direction 0)) (- dot-view-sun)) (direction 1) max-sun-1)
        sun-2        (->> sin-sun-elevation sqr (- 1 (sqr sun-1)) (max 0.0) sqrt)]
    (vec3 sin-sun-elevation sun-1 sun-2)))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N N N]]
                      [:=> [:cat fvec3 fvec3 fvec3 :boolean] [:tuple :double :double :double :double]]]}
  [planet shape]
  (fn [point direction light-direction above-horizon]
      (let [height-index        (height-to-index planet (first shape) point)
            elevation-index     (elevation-to-index planet (second shape) point direction above-horizon)
            sun-elevation-index (sun-elevation-to-index (third shape) point light-direction)
            sun-angle-index     (sun-angle-to-index (fourth shape) direction light-direction)]
        [height-index elevation-index sun-elevation-index sun-angle-index])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N N N]]
                      [:=> [:cat :double :double :double :double] [:tuple fvec3 fvec3 fvec3 :boolean]]]}
  [planet shape]
  (fn [height-index elevation-index sun-elevation-index sun-angle-index]
      (let [point                     (index-to-height planet (first shape) height-index)
            [direction above-horizon] (index-to-elevation planet (second shape) (point 0) elevation-index)
            sin-sun-elevation         (index-to-sin-sun-elevation (third shape) sun-elevation-index)
            light-direction           (index-to-sun-direction (fourth shape) direction sin-sun-elevation sun-angle-index)]
        [point direction light-direction above-horizon])))

(defn ray-scatter-space
  "Create transformation for interpolating ray scatter function"
  {:malli/schema [:=> [:cat atmosphere [:tuple N N N N]] interpolation-space]}
  [planet shape]
  #:sfsim.interpolate {:shape shape :forward (ray-scatter-forward planet shape) :backward (ray-scatter-backward planet shape)})

(def point-scatter-space ray-scatter-space)

(def phase-function
  "Shader function for scattering phase function"
  (slurp "resources/shaders/atmosphere/phase-function.glsl"))

(def transmittance-outer
  "Shader function to compute transmittance between point in the atmosphere and space"
  [shaders/transmittance-forward shaders/interpolate-2d (slurp "resources/shaders/atmosphere/transmittance-outer.glsl")])

(def transmittance-track
  "Shader function to compute transmittance between two points in the atmosphere"
  [shaders/transmittance-forward shaders/interpolate-2d shaders/is-above-horizon
   (slurp "resources/shaders/atmosphere/transmittance-track.glsl")])

(def ray-scatter-outer
  "Shader function to determine in-scattered light between point in the atmosphere and space"
  [shaders/ray-scatter-forward shaders/interpolate-4d phase-function
   (slurp "resources/shaders/atmosphere/ray-scatter-outer.glsl")])

(def ray-scatter-track
  "Shader function to determine in-scattered light between two points in the atmosphere"
  [shaders/ray-scatter-forward shaders/interpolate-4d transmittance-track shaders/is-above-horizon phase-function
   (slurp "resources/shaders/atmosphere/ray-scatter-track.glsl")])

(def attenuation-outer
  "Shader function combining transmittance and in-scattered light between point in the atmosphere and space"
  [transmittance-outer ray-scatter-outer (slurp "resources/shaders/atmosphere/attenuation-outer.glsl")])

(def attenuation-track
  "Shader function combining transmittance and in-scattered light between two points in the atmosphere"
  [transmittance-track ray-scatter-track (slurp "resources/shaders/atmosphere/attenuation-track.glsl")])

(def vertex-atmosphere
  "Pass through coordinates of quad for rendering atmosphere and determine viewing direction and camera origin"
  (slurp "resources/shaders/atmosphere/vertex.glsl"))

(def cloud-overlay
  "Shader function to lookup cloud overlay values in lower resolution texture"
  (slurp "resources/shaders/atmosphere/cloud-overlay.glsl"))

(def fragment-atmosphere
  "Fragment shader for rendering atmosphere and sun"
  [shaders/ray-sphere attenuation-outer cloud-overlay (slurp "resources/shaders/atmosphere/fragment.glsl")])

(def transmittance-elevation-size 255)
(def transmittance-height-size 64)
(def heading-size 8)
(def light-elevation-size 32)
(def elevation-size 127)
(def height-size 32)
(def surface-sun-elevation-size 63)
(def surface-height-size 16)

(defn make-atmosphere-luts
  "Load atmosphere lookup tables"
  {:malli/schema [:=> [:cat [:* :any]] [:map [::transmittance texture-2d]
                                             [::scatter texture-4d]
                                             [::mie texture-2d]
                                             [::surface-radiance texture-2d]]]}
  [max-height]
  (let [transmittance-data    (slurp-floats "data/atmosphere/transmittance.scatter")
        transmittance         (make-vector-texture-2d :linear :clamp
                                                      #:sfsim.image{:width transmittance-elevation-size
                                                                    :height transmittance-height-size
                                                                    :data transmittance-data})
        scatter-data          (slurp-floats "data/atmosphere/ray-scatter.scatter")
        scatter               (make-vector-texture-4d :linear :clamp
                                                      #:sfsim.image{:width heading-size
                                                                    :height light-elevation-size
                                                                    :depth elevation-size
                                                                    :hyperdepth height-size
                                                                    :data scatter-data})
        mie-data              (slurp-floats "data/atmosphere/mie-strength.scatter")
        mie                   (make-vector-texture-2d :linear :clamp
                                                      #:sfsim.image{:width heading-size
                                                                    :height light-elevation-size
                                                                    :depth elevation-size
                                                                    :hyperdepth height-size
                                                                    :data mie-data})
        surface-radiance-data (slurp-floats "data/atmosphere/surface-radiance.scatter")
        surface-radiance      (make-vector-texture-2d :linear :clamp
                                                      #:sfsim.image{:width surface-sun-elevation-size
                                                                    :height surface-height-size
                                                                    :data surface-radiance-data})]
    {::transmittance transmittance
     ::scatter scatter
     ::mie mie
     ::surface-radiance surface-radiance
     ::max-height max-height}))

(defn destroy-atmosphere-luts
  "Destroy atmosphere lookup tables"
  [{::keys [transmittance scatter mie surface-radiance]}]
  (destroy-texture transmittance)
  (destroy-texture scatter)
  (destroy-texture mie)
  (destroy-texture surface-radiance))

(defn setup-atmosphere-uniforms
  "Set up uniforms for atmospheric lookup tables"
  {:malli/schema [:=> [:cat :int :map :int :boolean] :nil]}
  [program atmosphere-luts sampler-offset surface-radiance]
  (uniform-sampler program "transmittance" sampler-offset)
  (uniform-sampler program "ray_scatter" (+ sampler-offset 1))
  (uniform-sampler program "mie_strength" (+ sampler-offset 2))
  (when surface-radiance
    (uniform-sampler program "surface_radiance" (+ sampler-offset 3)))
  (uniform-int program "height_size" (:hyperdepth (::scatter atmosphere-luts)))
  (uniform-int program "elevation_size" (:depth (::scatter atmosphere-luts)))
  (uniform-int program "light_elevation_size" (:height (::scatter atmosphere-luts)))
  (uniform-int program "heading_size" (:width (::scatter atmosphere-luts)))
  (uniform-int program "transmittance_elevation_size" (:width (::transmittance atmosphere-luts)))
  (uniform-int program "transmittance_height_size" (:height (::transmittance atmosphere-luts)))
  (when surface-radiance
    (uniform-int program "surface_height_size" (:height (::surface-radiance atmosphere-luts)))
    (uniform-int program "surface_sun_elevation_size" (:width (::surface-radiance atmosphere-luts))))
  (uniform-float program "max_height" (::max-height atmosphere-luts)))

(defn make-atmosphere-renderer
  "Initialise atmosphere rendering program (untested)"
  {:malli/schema [:=> [:cat [:* :any]] :map]}
  [& {:keys [render-config atmosphere-luts planet-config]}]
  (let [program (make-program :sfsim.render/vertex [vertex-atmosphere]
                              :sfsim.render/fragment [fragment-atmosphere])]
    (use-program program)
    (setup-atmosphere-uniforms program atmosphere-luts 0 false)
    (uniform-sampler program "clouds" 3)
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    (uniform-float program "specular" (:sfsim.render/specular render-config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))
    {:program program
     :atmosphere-luts atmosphere-luts}))

(defn render-atmosphere
  "Render atmosphere with cloud overlay (untested)"
  {:malli/schema [:=> [:cat :map [:* :any]] :nil]}
  [{:keys [program atmosphere-luts]} render-vars & {:keys [clouds]}]
  (let [indices    [0 1 3 2]
        vertices   (mapv #(* % (:sfsim.render/z-far render-vars)) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
        vao        (make-vertex-array-object program indices vertices ["point" 3])]
    (use-program program)
    (uniform-matrix4 program "projection" (:sfsim.render/projection render-vars))
    (uniform-matrix4 program "extrinsics" (:sfsim.render/extrinsics render-vars))
    (uniform-vector3 program "origin" (:sfsim.render/origin render-vars))
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction render-vars))
    (uniform-int program "window_width" (:sfsim.render/window-width render-vars))
    (uniform-int program "window_height" (:sfsim.render/window-height render-vars))
    (use-textures {0 (::transmittance atmosphere-luts) 1 (::scatter atmosphere-luts) 2 (::mie atmosphere-luts) 3 clouds})
    (render-quads vao)
    (destroy-vertex-array-object vao)))

(defn destroy-atmosphere-renderer
  "Destroy atmosphere renderer (untested)"
  {:malli/schema [:=> [:cat :map] :nil]}
  [{:keys [program]}]
  (destroy-program program))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
