(ns sfsim25.sphere
  "Functions dealing with spheres"
  (:require [clojure.math :refer (cos sin ceil sqrt PI)]
            [fastmath.matrix :refer (transpose mulv)]
            [fastmath.vector :refer (vec3 add sub mag dot mult)]
            [malli.core :as m]
            [sfsim25.matrix :as matrix]
            [sfsim25.ray :refer (ray)]
            [sfsim25.util :refer (sqr N)])
  (:import [fastmath.vector Vec3]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def sphere (m/schema [:map [::centre matrix/vec3] [::radius :double]]))

(defn height
  "Determine height above surface of sphere"
  {:malli/schema [:=> [:cat sphere matrix/vec3] :double]}
  [{:sfsim25.sphere/keys [centre radius]} point]
  (- (mag (sub point centre)) radius))

(defn- ray-sphere-determinant
  "Get determinant for intersection of ray with sphere"
  {:malli/schema [:=> [:cat matrix/vec3 :double matrix/vec3 matrix/vec3] :double]}
  [centre radius origin direction]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)]
    (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr radius))))))

(defn ray-intersects-sphere?
  "Check whether the ray intersects the sphere"
  {:malli/schema [:=> [:cat sphere ray] :boolean]}
  [{:sfsim25.sphere/keys [centre radius]} {:sfsim25.ray/keys [origin direction]}]
  (> (ray-sphere-determinant centre radius origin direction) 0))

(def intersection (m/schema [:map [:sfsim25.intersection/distance :double] [:sfsim25.intersection/length :double]]))

(defn ray-sphere-intersection
  "Compute intersection of line with sphere or closest point with sphere"
  {:malli/schema [:=> [:cat sphere ray] intersection]}
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
  {:malli/schema [:=> [:cat sphere ray] :boolean]}
  [{:sfsim25.sphere/keys [centre]} {:sfsim25.ray/keys [origin direction]}]
  (< (dot direction (sub origin centre)) 0))

(defn integrate-circle
  "Numerically integrate function in the range from zero to two pi"
  {:malli/schema [:=> [:cat N [:=> [:cat :double] [:vector :double]]] [:vector :double]]}
  [steps fun]
  (let [samples (map #(* 2 PI (/ (+ 0.5 %) steps)) (range steps))
        weight  (/ (* 2 PI) steps)]
    (mult (reduce add (map fun samples)) weight)))

(defn- spherical-integral
  "Integrate over specified range of sphere"
  {:malli/schema [:=> [:cat N N :double ]]}
  [theta-steps phi-steps theta-range normal fun]
  (let [samples (map #(* theta-range (/ (+ 0.5 %) theta-steps)) (range theta-steps))
        delta2  (/ theta-range theta-steps 2)
        mat     (transpose (matrix/oriented-matrix normal))]
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
  {:malli/schema [:=> [:cat N matrix/vec3 [:=> [:cat matrix/vec3] [:vector :double]]] [:vector :double]]}
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 2) steps (/ PI 2) normal fun))

(defn integral-sphere
  "Integrate over a full unit sphere"
  {:malli/schema [:=> [:cat N matrix/vec3 [:=> [:cat matrix/vec3] [:vector :double]]] [:vector :double]]}
  [steps normal fun]
  (spherical-integral (bit-shift-right steps 1) steps PI normal fun))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
