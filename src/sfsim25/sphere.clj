(ns sfsim25.sphere
  "Functions dealing with spheres"
  (:require [clojure.math :refer (cos sin ceil sqrt PI)]
            [fastmath.matrix :refer (transpose mulv)]
            [fastmath.vector :refer (vec3 add sub mag dot mult)]
            [sfsim25.matrix :refer (oriented-matrix)]
            [sfsim25.util :refer (sqr)])
  (:import [fastmath.vector Vec3]))

(defn height
  "Determine height above surface of sphere"
  ^double [{:sfsim25.sphere/keys [centre radius]} ^Vec3 point]
  (- (mag (sub point centre)) radius))

(defn- ray-sphere-determinant
  "Get determinant for intersection of ray with sphere"
  ^double [^Vec3 centre ^Vec3 radius ^Vec3 origin ^Vec3 direction]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)]
    (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr radius))))))

(defn ray-intersects-sphere?
  "Check whether the ray intersects the sphere"
  [{:sfsim25.sphere/keys [centre radius]} {:sfsim25.ray/keys [origin direction]}]
  (> (ray-sphere-determinant centre radius origin direction) 0))

(defn ray-sphere-intersection
  "Compute intersection of line with sphere or closest point with sphere"
  ^clojure.lang.PersistentArrayMap
  [{:sfsim25.sphere/keys [centre radius]} {:sfsim25.ray/keys [origin direction]}]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)
        discriminant  (ray-sphere-determinant centre radius origin direction)
        middle        (- (/ (dot direction offset) direction-sqr))]
    (if (> discriminant 0)
      (let [length2 (/ (sqrt discriminant) direction-sqr)]
        (if (< middle length2)
          {:sfsim25.intersection/distance 0.0 :sfsim25.intersection/length (max 0.0 (+ middle length2))}
          {:sfsim25.intersection/distance (- middle length2) :sfsim25.intersection/length (* 2 length2)}))
      {:sfsim25.intersection/distance (max 0.0 middle) :sfsim25.intersection/length 0.0})))

(defn ray-pointing-downwards
  "Check whether ray points towards centre of sphere"
  [{:sfsim25.sphere/keys [centre]} {:sfsim25.ray/keys [origin direction]}]
  (< (dot direction (sub origin centre)) 0))

(defn integrate-circle
  "Numerically integrate function in the range from zero to two pi"
  [steps fun]
  (let [samples (map #(* 2 PI (/ (+ 0.5 %) steps)) (range steps))
        weight  (/ (* 2 PI) steps)]
    (mult (reduce add (map fun samples)) weight)))

(defn- spherical-integral
  "Integrate over specified range of sphere"
  [theta-steps phi-steps theta-range normal fun]
  (let [samples (map #(* theta-range (/ (+ 0.5 %) theta-steps)) (range theta-steps))
        delta2  (/ theta-range theta-steps 2)
        mat     (transpose (oriented-matrix normal))]
    (reduce add
      (map (fn [theta]
        (let [factor    (- (cos (- theta delta2)) (cos (+ theta delta2)))
              ringsteps (int (ceil (* (sin theta) phi-steps)))
              cos-theta (cos theta)
              sin-theta (sin theta)]
          (mult (integrate-circle
                  ringsteps
                  (fn [phi]
                      (let [x cos-theta
                            y (* sin-theta (cos phi))
                            z (* sin-theta (sin phi))]
                        (fun (mulv mat (vec3 x y z))))))
                factor)))
        samples))))

(defn integral-half-sphere
  "Integrate over half unit sphere oriented along normal"
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 2) steps (/ PI 2) normal fun))

(defn integral-sphere
  "Integrate over a full unit sphere"
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 1) steps PI normal fun))
