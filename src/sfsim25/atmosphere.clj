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

(defn ray-sphere
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap [^Vector centre ^double radius ^Vector origin ^Vector direction]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)
        discriminant  (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr radius))))]
    (if (> discriminant 0)
      (let [length2 (/ (Math/sqrt discriminant) direction-sqr)
            middle  (- (/ (dot direction offset) direction-sqr))]
        (if (< middle length2)
          {:distance 0.0 :length (max 0.0 (+ middle length2))}
          {:distance (- middle length2) :length (* 2 length2)}))
      {:distance 0.0 :length 0.0})))

(defn ray-ellipsoid
  "Compute intersection of line with ellipsoid"
  [centre radius1 radius2 origin direction]
  (let [factor (/ radius1 radius2)
        scale  (fn [v] (matrix [(mget v 0) (mget v 1) (* factor (mget v 2))]))]
  (ray-sphere (scale centre) radius1 (scale origin) (scale direction))))

(defn optical-depth
  "Return optical depth of atmosphere at different points and for different directions"
  [point direction base radius max-height scale num-points]
  (let [ray-length (:length (ray-sphere (matrix [0 0 0]) (+ radius max-height) point direction))
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
  ^Vector [^Vector base ^double scale ^double height]
  (mul base (Math/exp (- (/ height scale)))))

(defn scattering-rayleigh
  "Compute Rayleigh scattering for given atmosphere and height"
  ^Vector [{:atmosphere/keys [rayleigh-base rayleigh-scale]} height]
  (scattering rayleigh-base rayleigh-scale height))

(defn scattering-mie
  "Compute Mie scattering for given atmosphere and height"
  ^Vector [{:atmosphere/keys [mie-base mie-scale]} height]
  (scattering mie-base mie-scale height))

(defn phase-rayleigh
  "Rayleigh scattering phase function depending on mu = cos(theta) where theta is angle between incident and scattering direction"
  [mu]
  (/ (* 3 (+ 1 (sqr mu))) (* 16 Math/PI)))

(defn phase-mie
  "Mie scattering phase function by Cornette and Shanks depending on assymetry g and mu = cos(theta)"
  [{:atmosphere/keys [g]} mu]
  (/ (* 3 (- 1 (sqr g)) (+ 1 (sqr mu))) (* 8 Math/PI (+ 2 (sqr g)) (Math/pow (- (+ 1 (sqr g)) (* 2 g mu)) 1.5))))

(set! *unchecked-math* false)
