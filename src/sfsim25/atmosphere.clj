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
  (/ (* 3 (- 1 (sqr scatter-g)) (+ 1 (sqr mu)))
     (* 8 Math/PI (+ 2 (sqr scatter-g)) (Math/pow (- (+ 1 (sqr scatter-g)) (* 2 scatter-g mu)) 1.5))))

(defn atmosphere-intersection
  "Get intersection of ray with artificial limit of atmosphere"
  [{:sfsim25.sphere/keys [centre radius] :as planet} ray]
  (let [height                    (:sfsim25.atmosphere/height planet)
        atmosphere                #:sfsim25.sphere {:centre centre :radius (+ radius height)}
        {:keys [distance length]} (ray-sphere-intersection atmosphere ray)]
    (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) (+ distance length)))))

(defn surface-intersection
  "Get intersection of ray with surface of planet or nil if there is no intersection"
  [planet ray]
  (let [{:keys [distance length]} (ray-sphere-intersection planet ray)]
    (if (zero? length) nil (add (:sfsim25.ray/origin ray) (mul (:sfsim25.ray/direction ray) distance)))))

(defn surface-point?
  "Check whether a point is near the surface or near the edge of the atmosphere"
  [planet point]
  (let [atmosphere-height (:sfsim25.atmosphere/height planet)]
    (< (* 2 (height planet point)) atmosphere-height)))

(defn ray-extremity
  "Get intersection with surface of planet or artificial limit of atmosphere assuming that ray starts inside atmosphere"
  [planet ray]
  (let [surface-point (and (ray-pointing-downwards planet ray) (surface-intersection planet ray))]
    (or surface-point (atmosphere-intersection planet ray))))

(defn transmittance
  "Compute transmissiveness of atmosphere between two points x and x0 considering specified scattering effects"
  ([planet scatter steps x x0]
   (let [fun (fn [point] (apply add (map #(extinction % (height planet point)) scatter)))]
     (exp (sub (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 fun)))))
  ([planet scatter intersection steps x v]
   (transmittance planet scatter steps x (intersection planet #:sfsim25.ray{:origin x :direction v}))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E0) depending on position of sun"
  [planet scatter steps sun-light x sun-direction]
  (let [radial-vector (sub x (:sfsim25.sphere/centre planet))
        vector-length (length radial-vector)
        normal        (div radial-vector vector-length)]
    (mul (max 0 (dot normal sun-direction))
         (transmittance planet scatter atmosphere-intersection steps x sun-direction) sun-light)))

(defn point-scatter-base
  "Compute single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  [planet scatter steps sun-light x view-direction sun-direction]
  (let [height-of-x  (height planet x)
        scatter-at-x #(mul (scattering % height-of-x) (phase % (dot view-direction sun-direction)))
        sun-ray      #:sfsim25.ray{:origin x :direction sun-direction}]
    (if (surface-intersection planet sun-ray)
      (matrix [0 0 0])
      (mul sun-light
           (apply add (map scatter-at-x scatter))
           (transmittance planet scatter atmosphere-intersection steps x sun-direction)))))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  [planet scatter intersection steps point-scatter x view-direction sun-direction]
  (let [point (intersection planet #:sfsim25.ray{:origin x :direction view-direction})
        ray   #:sfsim25.ray{:origin x :direction (sub point x)}]
    (integral-ray ray steps 1.0 #(mul (transmittance planet scatter steps x %) (point-scatter % view-direction sun-direction)))))

(defn point-scatter
  "Compute in-scattering of light at a point and given direction in atmosphere (J) plus light received from surface (E)"
  [planet scatter ray-scatter surface-radiance sun-light sphere-steps ray-steps x view-direction sun-direction]
  (let [normal        (normalise (sub x (:sfsim25.sphere/centre planet)))
        height-of-x   (height planet x)
        scatter-at-x  #(mul (scattering %2 height-of-x) (phase %2 (dot view-direction %1)))]
    (integral-sphere sphere-steps
                     normal
                     (fn [omega]
                         (let [ray     #:sfsim25.ray{:origin x :direction omega}
                               point   (ray-extremity planet ray)
                               surface (surface-point? planet point)]
                           (mul (apply add (map (partial scatter-at-x omega) scatter))
                                (add (ray-scatter x omega sun-direction)
                                     (if surface
                                       (mul (transmittance planet scatter ray-steps x point)
                                            (div (::brightness planet) Math/PI)
                                            (surface-radiance point sun-direction))
                                       (matrix [0 0 0])))))))))

(defn surface-radiance
  "Integrate over half sphere to get surface radiance E(S) depending on ray scatter"
  [planet ray-scatter steps x sun-direction]
  (let [normal (normalise (sub x (:sfsim25.sphere/centre planet)))]
    (integral-half-sphere steps normal (fn [omega] (mul (ray-scatter x omega sun-direction) (dot omega normal))))))

(defn horizon-angle
  "Get angle of planet's horizon below the horizontal plane depending on the height of the observer"
  ^double [{:sfsim25.sphere/keys [^Vector centre ^double radius]} ^Vector point]
  (let [distance (norm (sub point centre))]
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
        pi2         (/ Math/PI 2)]
    (fn ^Vector [^double height ^double index]
        (if (<= index (+ (dec sky-size) 0.5))
          (let [angle (* index (/ pi2(dec sky-size)))]
            (matrix [(Math/cos angle) (Math/sin angle) 0]))
          (let [angle (+ pi2 (* (- index sky-size) (/ pi2 (dec ground-size))))]
            (matrix [(Math/cos angle) (Math/sin angle) 0]))))))

(defn- transmittance-forward
  "Forward transformation for interpolating transmittance function"
  [{:sfsim25.sphere/keys [centre radius]}]
  (fn [^Vector point ^Vector direction]
      (let [height        (- (norm (sub point centre)) radius)
            cos-elevation (/ (dot point direction) (norm point))
            elevation     (Math/acos cos-elevation)]
        [height elevation])))

(defn- transmittance-backward
  "Backward transformation for looking up transmittance values"
  [{:sfsim25.sphere/keys [centre radius]}]
  (fn [^double height ^double elevation]
      (let [point     (add centre (matrix [(+ radius height) 0 0]))
            direction (matrix [(Math/cos elevation) (Math/sin elevation) 0])]
        [point direction])))

(defn transmittance-space
  "Create transformations for interpolating transmittance function"
  [planet size]
  (let [shape   [size size]
        height  (:sfsim25.atmosphere/height planet)
        scaling (linear-space [0 0] [height Math/PI] shape)]
    (compose-space scaling #:sfsim25.interpolate{:forward (transmittance-forward planet)
                                                 :backward (transmittance-backward planet)})))

(def surface-radiance-space transmittance-space)

(defn- clip-angle [angle] (if (< angle (- Math/PI)) (+ angle (* 2 Math/PI)) (if (>= angle Math/PI) (- angle (* 2 Math/PI)) angle)))

(defn- ray-scatter-forward
  "Forward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [centre radius]}]
  (fn [^Vector point ^Vector direction ^Vector sun-direction]
      (let [radius-vector         (sub point centre)
            height                (- (norm radius-vector) radius)
            horizon               (transpose (oriented-matrix (normalise radius-vector)))
            direction-rotated     (mmul horizon direction)
            sun-direction-rotated (mmul horizon sun-direction)
            cos-elevation         (mget direction-rotated 0)
            cos-sun-elevation     (mget sun-direction-rotated 0)
            elevation             (Math/acos cos-elevation)
            sun-elevation         (Math/acos cos-sun-elevation)
            direction-azimuth     (Math/atan2 (mget direction-rotated 2) (mget direction-rotated 1))
            sun-direction-azimuth (Math/atan2 (mget sun-direction-rotated 2) (mget sun-direction-rotated 1))
            sun-heading           (Math/abs (clip-angle (- sun-direction-azimuth direction-azimuth)))]
        [height elevation sun-elevation sun-heading])))

(defn- ray-scatter-backward
  "Backward transformation for interpolating ray scatter function"
  [{:sfsim25.sphere/keys [centre radius]}]
  (fn [^double height ^double elevation ^double sun-elevation ^double sun-heading]
      (let [point             (matrix [(+ radius height) 0 0])
            direction         (matrix [(Math/cos elevation) (Math/sin elevation) 0])
            cos-sun-elevation (Math/cos sun-elevation)
            sin-sun-elevation (Math/sin sun-elevation)
            cos-sun-heading   (Math/cos sun-heading)
            sin-sun-heading   (Math/sin sun-heading)
            sun-direction (matrix [cos-sun-elevation (* sin-sun-elevation cos-sun-heading) (* sin-sun-elevation sin-sun-heading)])]
        [point direction sun-direction])))

(defn ray-scatter-space
  "Create transformations for interpolating ray scatter function"
  [planet size]
  (let [shape   [size size size size]
        height  (:sfsim25.atmosphere/height planet)
        scaling (linear-space [0 0 0 0] [height Math/PI Math/PI Math/PI] shape)]
    (compose-space scaling #:sfsim25.interpolate{:forward (ray-scatter-forward planet)
                                                 :backward (ray-scatter-backward planet)})))

(def point-scatter-space ray-scatter-space)

(set! *unchecked-math* false)
