(ns sfsim25.sphere
  "Functions dealing with spheres"
  (:require [clojure.core.matrix :refer :all]
            [sfsim25.util :refer :all]))

(defn ray-sphere-intersection
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap
  [{:sfsim25.sphere/keys [centre radius]} {:sfsim25.ray/keys [origin direction]}]
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
