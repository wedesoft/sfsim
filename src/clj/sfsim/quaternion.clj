;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.quaternion
  "Complex algebra implementation."
  (:refer-clojure :exclude [+ - *])
  (:require
   [clojure.core :as c]
   [clojure.math :refer (cos sqrt) :as m]
   [fastmath.vector :refer (mag mult cross dot)]
   [malli.core :as mc]
   [sfsim.util :refer (sinc sqr)])
  (:import
   [fastmath.vector
    Vec3]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defrecord Quaternion
    [^double real ^double imag ^double jmag ^double kmag])


(def fvec3 (mc/schema [:tuple :double :double :double]))
(def quaternion (mc/schema [:map [:real :double] [:imag :double] [:jmag :double] [:kmag :double]]))


(defn +
  "Add two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  ^Quaternion [^Quaternion p ^Quaternion q]
  (Quaternion. (c/+ (.real p) (.real q))
               (c/+ (.imag p) (.imag q))
               (c/+ (.jmag p) (.jmag q))
               (c/+ (.kmag p) (.kmag q))))


(defn -
  "Negate quaternion or subtract two quaternions"
  {:malli/schema [:=> [:cat quaternion [:? quaternion]] quaternion]}
  ([^Quaternion p]
   (Quaternion. (c/- (.real p)) (c/- (.imag p)) (c/- (.jmag p)) (c/- (.kmag p))))
  ([^Quaternion p ^Quaternion q]
   (Quaternion. (c/- (.real p) (.real q))
                (c/- (.imag p) (.imag q))
                (c/- (.jmag p) (.jmag q))
                (c/- (.kmag p) (.kmag q)))))


(defn *
  "Multiply two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  ^Quaternion [^Quaternion p ^Quaternion q]
  (let [p-real (.real p)
        p-imag (.imag p)
        p-jmag (.jmag p)
        p-kmag (.kmag p)
        q-real (.real q)
        q-imag (.imag q)
        q-jmag (.jmag q)
        q-kmag (.kmag q)]
    (Quaternion.
     (c/- (c/* p-real q-real) (c/* p-imag q-imag)
          (c/* p-jmag q-jmag) (c/* p-kmag q-kmag))
     (c/- (c/+ (c/* p-real q-imag) (c/* p-imag q-real)
               (c/* p-jmag q-kmag)) (c/* p-kmag q-jmag))
     (c/+ (c/- (c/* p-real q-jmag) (c/* p-imag q-kmag))
          (c/* p-jmag q-real) (c/* p-kmag q-imag))
     (c/+ (c/- (c/+ (c/* p-real q-kmag) (c/* p-imag q-jmag))
               (c/* p-jmag q-imag)) (c/* p-kmag q-real)))))


(defn scale
  "Multiply quaternion with real number"
  ^Quaternion [^Quaternion q ^double s]
  (Quaternion. (c/* (.real q) s) (c/* (.imag q) s) (c/* (.jmag q) s) (c/* (.kmag q) s)))


(defn norm2
  "Compute square of norm of quaternion"
  ^double [^Quaternion q]
  (c/+ (sqr (.real q)) (sqr (.imag q)) (sqr (.jmag q)) (sqr (.kmag q))))


(defn norm
  "Compute norm of quaternion"
  ^double [^Quaternion q]
  (sqrt (norm2 q)))


(defn normalize
  "Normalize quaternion to create unit quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion [^Quaternion q]
  (scale q (/ 1.0 (norm q))))


(defn conjugate
  "Return conjugate of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion[^Quaternion q]
  (Quaternion. (.real q) (c/- (.imag q)) (c/- (.jmag q)) (c/- (.kmag q))))


(defn inverse
  "Return inverse of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion[^Quaternion q]
  (let [factor (/ 1.0 (norm2 q))]
    (Quaternion. (c/* (.real q) factor)
                 (c/* (c/- (.imag q)) factor)
                 (c/* (c/- (.jmag q)) factor)
                 (c/* (c/- (.kmag q)) factor))))


(defn vector->quaternion
  "Convert 3D vector to quaternion"
  {:malli/schema [:=> [:cat fvec3] quaternion]}
  [v]
  (if (instance? Vec3 v)
    (let [v ^Vec3 v]
      (Quaternion. 0.0 (.x v) (.y v) (.z v)))
    (Quaternion. 0.0 (nth v 0) (nth v 1) (nth v 2))))


(defn quaternion->vector
  "Convert quaternion to 3D vector"
  {:malli/schema [:=> [:cat quaternion] fvec3]}
  [^Quaternion q]
  (Vec3. (.imag q) (.jmag q) (.kmag q)))


(defn exp
  "Exponentiation of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion [^Quaternion q]
  (let [scale   (m/exp (.real q))
        rotation  (mag (quaternion->vector q))
        cos-scale (c/* scale (cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (Quaternion. cos-scale
                 (c/* sinc-scale (.imag q))
                 (c/* sinc-scale (.jmag q))
                 (c/* sinc-scale (.kmag q)))))


(defn rotation
  "Generate quaternion to represent rotation"
  ^Quaternion [^double theta ^Vec3 v]
  (exp (vector->quaternion (mult v (c/* theta 0.5)))))


(defn rotate-vector
  "Rotate a vector with a rotation represented by a quaternion"
  {:malli/schema [:=> [:cat quaternion fvec3] fvec3]}
  [q v]
  (quaternion->vector (* (* q (vector->quaternion v)) (conjugate q))))


(defn vector-to-vector-rotation
  "Create quaternion for rotating u to v"
  {:malli/schema [:=> [:cat fvec3 fvec3] quaternion]}
  ^Quaternion [u v]
  (let [axis (cross u v)
        w    (c/+ (c/* (mag u) (mag v)) (dot u v))]
    (normalize (Quaternion. w (nth axis 0) (nth axis 1) (nth axis 2)))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
