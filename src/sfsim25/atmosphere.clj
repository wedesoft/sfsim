(ns sfsim25.atmosphere
  "Functions for computing the atmosphere"
  (:require [clojure.core.matrix :refer :all]
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
  ^double [{:sfsim25.atmosphere/keys [sphere-centre sphere-radius]} ^Vector point]
  (- (length (sub point sphere-centre)) sphere-radius))

(defn ray-sphere
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap [{:sfsim25.atmosphere/keys [sphere-centre sphere-radius]} ^Vector origin ^Vector direction]
  (let [offset        (sub origin sphere-centre)
        direction-sqr (dot direction direction)
        discriminant  (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr sphere-radius))))]
    (if (> discriminant 0)
      (let [length2 (/ (Math/sqrt discriminant) direction-sqr)
            middle  (- (/ (dot direction offset) direction-sqr))]
        (if (< middle length2)
          {:distance 0.0 :length (max 0.0 (+ middle length2))}
          {:distance (- middle length2) :length (* 2 length2)}))
      {:distance 0.0 :length 0.0})))

(defn ray-ellipsoid
  "Compute intersection of line with ellipsoid"
  [{:sfsim25.atmosphere/keys [ellipsoid-centre ellipsoid-radius1 ellipsoid-radius2]} origin direction]
  (let [factor (/ ellipsoid-radius1 ellipsoid-radius2)
        scale  (fn [v] (matrix [(mget v 0) (mget v 1) (* factor (mget v 2))]))]
  (ray-sphere {::sphere-centre (scale ellipsoid-centre) ::sphere-radius ellipsoid-radius1} (scale origin) (scale direction))))

(defn optical-depth
  "Return optical depth of atmosphere at different points and for different directions"
  [point direction base radius max-height scale num-points]
  (let [ray-length (:length (ray-sphere {::sphere-centre (matrix [0 0 0]) ::sphere-radius (+ radius max-height)} point direction))
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
  (let [samples     (map #(/ (+ 0.5 %) steps) (range steps))
        stepsize    (/ (length (sub x0 x)) steps)
        interpolate (fn [s] (add (mul (- 1 s) x) (mul s x0)))
        scatter-sum (fn [h] (apply add (map #(extinction % h) scatter)))]
    (exp (sub (apply add (map #(->> % interpolate (height sphere) scatter-sum (mul stepsize)) samples))))))

(defn ray-extremity
  "Return the intersection of the ray with the fringe of the atmosphere or the surface of the planet"
  [{:sfsim25.atmosphere/keys [sphere-centre sphere-radius height]} point direction]
  (let [{:keys [distance length]} (ray-sphere {::sphere-centre sphere-centre ::sphere-radius sphere-radius} point direction)]
    (if (and (> length 0) (< (dot (sub point sphere-centre) direction) 0))
      (add point (mul distance direction))
      (let [{:keys [length]} (ray-sphere {::sphere-centre sphere-centre ::sphere-radius (+ sphere-radius height)} point direction)]
        (add point (mul length direction))))))

(defn epsilon0
  "Compute scatter-free radiation emitted from surface of planet (depends on position of sun) or fringe of atmosphere (zero)"
  [planet sun-light x0 sun-direction]
  (let [radial-vector (sub x0 (::sphere-centre planet))
        vector-length (length radial-vector)
        normal        (div radial-vector vector-length)]
    (if (> (* 2 vector-length) (+ (* 2 (::sphere-radius planet)) (::height planet)))
      (matrix [0 0 0])
      (mul (max 0 (dot normal sun-direction)) sun-light))))

(defn integrate-circle
  "Numerically integrate function in the range from zero to pi"
  [steps fun]
  (let [samples (map #(* 2 Math/PI (/ (+ 0.5 %) steps)) (range steps))
        weight  (/ (* 2 Math/PI) steps)]
    (* (reduce + (map fun samples)) weight)))

(defn integral-half-sphere
  "Integrate over half unit sphere oriented along normal"
  [steps normal fun]
  (let [steps4  (bit-shift-right steps 2)
        samples (map #(* (/ Math/PI 2) (/ (+ 0.5 %) steps4)) (range steps4))
        delta2  (/ Math/PI steps4 4)]
    (reduce +
      (map (fn [theta]
        (let [factor    (- (Math/cos (- theta delta2)) (Math/cos (+ theta delta2)))
              ringsteps (int (ceil (* (Math/sin theta) steps)))
              cos-theta (Math/cos theta)
              sin-theta (Math/sin theta)]
          (* (integrate-circle
               ringsteps
               (fn [phi]
                 (let [x cos-theta
                       y (* sin-theta (Math/cos phi))
                       z (* sin-theta (Math/sin phi))]
                   (fun (matrix [x y z])))))
             factor)))
        samples))))

(set! *unchecked-math* false)
