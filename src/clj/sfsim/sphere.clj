;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.sphere
  "Functions dealing with spheres"
  (:require
    [clojure.math :refer (cos sin ceil sqrt PI)]
    [fastmath.matrix :refer (transpose mulv)]
    [fastmath.vector :refer (vec3 add sub mag dot mult)]
    [malli.core :as m]
    [sfsim.matrix :refer (oriented-matrix fvec3)]
    [sfsim.ray :refer (ray)]
    [sfsim.util :refer (sqr N)])
  (:import
    (fastmath.vector
      Vec3)))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)

(def sphere (m/schema [:map [::centre fvec3] [::radius :double]]))


(defn height
  "Determine height above surface of sphere"
  ^double [{::keys [^Vec3 centre ^double radius]} ^Vec3 point]
  (- (mag (sub point centre)) radius))


(defn- ray-sphere-determinant
  "Get determinant for intersection of ray with sphere"
  {:malli/schema [:=> [:cat fvec3 :double fvec3 fvec3] :double]}
  ^double [^Vec3 centre ^double radius ^Vec3 origin ^Vec3 direction]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)]
    (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr radius))))))


(defn ray-intersects-sphere?
  "Check whether the ray intersects the sphere"
  {:malli/schema [:=> [:cat sphere ray] :boolean]}
  [{::keys [centre radius]} {:sfsim.ray/keys [origin direction]}]
  (> (ray-sphere-determinant centre radius origin direction) 0))


(def intersection (m/schema [:map [:sfsim.intersection/distance :double] [:sfsim.intersection/length :double]]))


(defn ray-sphere-intersection
  "Compute intersection of line with sphere or closest point with sphere"
  {:malli/schema [:=> [:cat sphere ray] intersection]}
  [{::keys [centre radius]} {:sfsim.ray/keys [origin direction]}]
  (let [offset        (sub origin centre)
        direction-sqr (dot direction direction)
        discriminant  (ray-sphere-determinant centre radius origin direction)
        middle        (- (/ (dot direction offset) direction-sqr))]
    (if (> discriminant 0)
      (let [length2 (/ (sqrt discriminant) direction-sqr)]
        (if (< middle length2)
          {:sfsim.intersection/distance 0.0 :sfsim.intersection/length (max 0.0 (+ middle length2))}
          {:sfsim.intersection/distance (- middle length2) :sfsim.intersection/length (* 2 length2)}))
      {:sfsim.intersection/distance (max 0.0 middle) :sfsim.intersection/length 0.0})))


(defn ray-pointing-downwards
  "Check whether ray points towards centre of sphere"
  {:malli/schema [:=> [:cat sphere ray] :boolean]}
  [{::keys [centre]} {:sfsim.ray/keys [origin direction]}]
  (< (dot direction (sub origin centre)) 0))


(defn integrate-circle
  "Numerically integrate function in the range from zero to two pi"
  [^long steps fun]
  (let [samples (mapv #(* 2 PI (/ (+ 0.5 ^long %) steps)) (range steps))
        weight  (/ (* 2 PI) steps)]
    (mult (reduce add (mapv fun samples)) weight)))


(defn- spherical-integral
  "Integrate over specified range of sphere"
  {:malli/schema [:=> [:cat N N :double :vec3 fn?]]}
  [theta-steps phi-steps theta-range normal fun]
  (let [samples (mapv #(* ^double theta-range (/ (+ 0.5 ^long %) ^long theta-steps)) (range theta-steps))
        delta2  (/ ^double theta-range ^long theta-steps 2)
        mat     (transpose (oriented-matrix normal))]
    (reduce add
            (mapv (fn sample-circle
                    [^double theta]
                    (let [factor    (- (cos (- theta ^double delta2)) (cos (+ theta ^double delta2)))
                          ringsteps (int (ceil (* (sin theta) ^long phi-steps)))
                          cos-theta (cos theta)
                          sin-theta (sin theta)]
                      (mult (integrate-circle
                              ringsteps
                              (fn sample-point
                                [^double phi]
                                (let [x cos-theta
                                      y (* sin-theta (cos phi))
                                      z (* sin-theta (sin phi))]
                                  (fun (mulv mat (vec3 x y z))))))
                            factor)))
                  samples))))


(defn integral-half-sphere
  "Integrate over half unit sphere oriented along normal"
  [^long steps ^Vec3 normal fun]
  (spherical-integral (bit-shift-right steps 2) steps (/ PI 2) normal fun))


(defn integral-sphere
  "Integrate over a full unit sphere"
  [^long steps ^Vec3 normal fun]
  (spherical-integral (bit-shift-right steps 1) steps PI normal fun))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
