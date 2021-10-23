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

(defn height
  "Determine height above surface of sphere"
  ^double [{:sfsim25.atmosphere/keys [centre radius]} ^Vector point]
  (- (length (sub point centre)) radius))

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

(defn transmittance
  "Compute transmission of light between two points x and x0 given extinction caused by different scattering effects"
  [sphere scatter steps x x0]
  (let [fun (fn [point] (apply add (map #(extinction % (height sphere point)) scatter)))]
    (exp (sub (integral-ray #:sfsim25.ray{:origin x :direction (sub x0 x)} steps 1.0 fun)))))

(defn ray-extremity
  "Return the intersection of the ray with the fringe of the atmosphere or the surface of the planet"
  [{:sfsim25.sphere/keys [centre radius] height ::height} {:sfsim25.ray/keys [origin direction]}]
  (let [{:keys [distance length]} (ray-sphere-intersection #:sfsim25.sphere{:centre centre :radius radius}
                                                           #:sfsim25.ray{:origin origin :direction direction})]
    (if (and (> length 0) (< (dot (sub origin centre) direction) 0))
      (add origin (mul distance direction))
      (let [{:keys [length]} (ray-sphere-intersection #:sfsim25.sphere{:centre centre :radius (+ radius height)}
                                                      #:sfsim25.ray{:origin origin :direction direction})]
        (add origin (mul length direction))))))

(defn epsilon0
  "Compute scatter-free radiation emitted from surface of planet (depends on position of sun) or fringe of atmosphere (zero)"
  [planet sun-light x0 sun-direction]
  (let [radial-vector (sub x0 (::centre planet))
        vector-length (length radial-vector)
        normal        (div radial-vector vector-length)]
    (if (> (* 2 vector-length) (+ (* 2 (::radius planet)) (::height planet)))
      (matrix [0.0 0.0 0.0])
      (mul (max 0 (dot normal sun-direction)) sun-light))))

(defn orthogonal
  "Create orthogonal vector to specified 3D vector"
  [n]
  (let [b (first (sort-by #(abs (dot n %)) (identity-matrix 3)))]
    (normalise (cross n b))))

(defn oriented-matrix
  "Create an isometry with given normal vector as first column"
  [n]
  (let [o1 (orthogonal n)
        o2 (cross n o1)]
    (transpose (matrix [n o1 o2]))))

(defn integrate-circle
  "Numerically integrate function in the range from zero to pi"
  [steps fun]
  (let [samples (map #(* 2 Math/PI (/ (+ 0.5 %) steps)) (range steps))
        weight  (/ (* 2 Math/PI) steps)]
    (mul (reduce add (map fun samples)) weight)))

(defn- spherical-integral
  "Integrate over specified range of sphere"
  [theta-steps phi-steps theta-range normal fun]
  (let [samples (map #(* theta-range (/ (+ 0.5 %) theta-steps)) (range theta-steps))
        delta2  (/ theta-range theta-steps 2)
        mat     (oriented-matrix normal)]
    (reduce add
      (map (fn [theta]
        (let [factor    (- (Math/cos (- theta delta2)) (Math/cos (+ theta delta2)))
              ringsteps (int (ceil (* (Math/sin theta) phi-steps)))
              cos-theta (Math/cos theta)
              sin-theta (Math/sin theta)]
          (mul (integrate-circle
                 ringsteps
                 (fn [phi]
                   (let [x cos-theta
                         y (* sin-theta (Math/cos phi))
                         z (* sin-theta (Math/sin phi))]
                     (fun (mmul mat (matrix [x y z]))))))
               factor)))
        samples))))

(defn integral-half-sphere
  "Integrate over half unit sphere oriented along normal"
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 2) steps (/ Math/PI 2) normal fun))

(defn integral-sphere
  "Integrate over a full unit sphere"
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 1) steps Math/PI normal fun))

(set! *unchecked-math* false)
