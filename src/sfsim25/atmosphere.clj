(ns sfsim25.atmosphere
  "Functions for computing the atmosphere"
  (:require [clojure.core.matrix :refer :all]
            [sfsim25.ray :refer :all]
            [sfsim25.sphere :refer :all]
            [sfsim25.util :refer :all])
  (:import [mikera.vectorz Vector]))

(set! *unchecked-math* true)

(defn air-density
  "Compute density of atmosphere at specified height"
  ^double [^double height ^double base ^double scale]
  (* base (Math/exp (- (/ height scale)))))

(defn air-density-table
  "Create a lookup table for air density values for different heights"
  ^floats [^double base ^long size ^double max-height ^double scale]
  (float-array (map #(air-density (* % (/ max-height (dec size))) base scale) (range size))))

(defn air-density-at-point
  "Determine the atmospheric density at a given 3D point"
  ^double [^Vector point ^double base ^double radius ^double scale]
  (air-density (- (length point) radius) base scale))

(defn optical-depth
  "Return optical depth of atmosphere at different points and for different directions"
  [point direction base radius max-height scale num-points]
  (let [ray-length (:length (ray-sphere-intersection #:sfsim25.sphere{:centre (matrix [0 0 0]) :radius (+ radius max-height)}
                                                     #:sfsim25.ray{:origin point :direction direction}))
        step-size  (/ ray-length num-points)
        nth-point  #(add point (mul (+ 0.5 %) step-size direction))]
    (reduce + (map #(-> % nth-point (air-density-at-point base radius scale) (* step-size))
                   (range num-points)))))

(defn optical-depth-table
  "Create lookup table for optical density for different directions (rows) and heights (columns)"
  [width height base radius max-height scale num-points]
  (let [data (for [j (range height) i (range width)]
               (let [dist      (+ radius (* i (/ max-height (dec width))))
                     point     (matrix [0 dist 0])
                     cos-angle (- (* j (/ 2.0 (dec height))) 1)
                     sin-angle (Math/sqrt (- 1 (* cos-angle cos-angle)))
                     direction (matrix [sin-angle cos-angle 0])]
                 (optical-depth point direction base radius max-height scale num-points)))]
    {:width width :height height :data (float-array data)}))

(defn scattering
  "Compute scattering or absorption amount in atmosphere"
  ^Vector [{:sfsim25.atmosphere/keys [scatter-base scatter-scale]} ^double height]
  (mul scatter-base (Math/exp (- (/ height scatter-scale)))))

(defn extinction
  "Compute Mie extinction for given atmosphere and height (Rayleigh extinction equals Rayleigh scattering)"
  ^Vector [scattering-type ^double height]
  (div (scattering scattering-type height) (or (::scatter-quotient scattering-type) 1)))

(defn phase
  "Mie scattering phase function by Cornette and Shanks depending on assymetry g and mu = cos(theta)"
  [{:sfsim25.atmosphere/keys [scatter-g] :or {scatter-g 0}} mu]
  (/ (* 3 (- 1 (sqr scatter-g)) (+ 1 (sqr mu)))
     (* 8 Math/PI (+ 2 (sqr scatter-g)) (Math/pow (- (+ 1 (sqr scatter-g)) (* 2 scatter-g mu)) 1.5))))

(defn ray-extremity
  "Return the intersection of the ray with the fringe of the atmosphere or the surface of the planet"
  [{:sfsim25.sphere/keys [centre radius] height ::height} {:sfsim25.ray/keys [origin direction]}]
  (let [{:keys [distance length]} (ray-sphere-intersection #:sfsim25.sphere{:centre centre :radius radius}
                                                           #:sfsim25.ray{:origin origin :direction direction})]
    (if (and (> length 0) (< (dot (sub origin centre) direction) 0))
      {::point (add origin (mul distance direction)) ::surface true}
      (let [{:keys [length]} (ray-sphere-intersection #:sfsim25.sphere{:centre centre :radius (+ radius height)}
                                                      #:sfsim25.ray{:origin origin :direction direction})]
        {::point (add origin (mul length direction)) ::surface false}))))

(defn transmittance
  "Compute transmission of light between two points x and x0 given extinction caused by different scattering effects"
  ([sphere scatter steps x x0]
   (let [fun (fn [point] (apply add (map #(extinction % (height sphere point)) scatter)))]
     (exp (sub (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 fun)))))
  ([planet scatter steps ray]
   (transmittance planet scatter steps (:sfsim25.ray/origin ray) (::point (ray-extremity planet ray)))))

(defn surface-radiance-base
  "Compute scatter-free radiation emitted from surface of planet (E) depending on position of sun"
  [planet scatter steps sun-light x sun-direction]
  (let [radial-vector (sub x (:sfsim25.sphere/centre planet))
        vector-length (length radial-vector)
        normal        (div radial-vector vector-length)]
    (mul (max 0 (dot normal sun-direction))
         (transmittance planet scatter steps #:sfsim25.ray{:origin x :direction sun-direction}) sun-light)))

(defn point-scatter-base
  "Compute single-scatter in-scattering of light at a point and given direction in atmosphere (J0)"
  [planet scatter steps sun-light x view-direction sun-direction]
  (let [height-of-x  (height planet x)
        scatter-at-x #(mul (scattering % height-of-x) (phase % (dot view-direction sun-direction)))
        sun-ray      #:sfsim25.ray{:origin x :direction sun-direction}]
    (mul sun-light (apply add (map scatter-at-x scatter)) (transmittance planet scatter steps sun-ray))))

(defn ray-scatter
  "Compute in-scattering of light from a given direction (S) using point scatter function (J)"
  [planet scatter steps point-scatter x view-direction sun-direction]
  (let [x0  (::point (ray-extremity planet #:sfsim25.ray{:origin x :direction view-direction}))
        ray #:sfsim25.ray{:origin x :direction (sub x0 x)}]
    (integral-ray ray steps 1.0 #(mul (transmittance planet scatter steps x %) (point-scatter % view-direction sun-direction)))))

(defn point-scatter; TODO: radiance of surface
  "Compute in-scattering of light at a point and given direction in atmosphere (J)"
  [planet scatter ray-scatter steps x view-direction sun-direction]
  (let [radial-vector (sub x (:sfsim25.sphere/centre planet))
        height-of-x   (height planet x)
        scatter-at-x  #(mul (scattering %2 height-of-x) (phase %2 (dot view-direction %1)))]
    (integral-sphere steps
                     (normalise radial-vector)
                     (fn [omega] (mul (apply add (map (partial scatter-at-x omega) scatter)) (ray-scatter x omega sun-direction))))))

(set! *unchecked-math* false)
