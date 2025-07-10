;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
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
    [fastmath.vector :refer (vec3 mag mult cross dot)]
    [malli.core :as mc]
    [sfsim.util :refer (sinc sqr)])
  (:import
    [fastmath.vector
     Vec3]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defrecord Quaternion
  [^double real ^double imag ^double jmag ^double kmag])


(def fvec3 (mc/schema [:tuple :double :double :double]))
(def quaternion (mc/schema [:map [:real :double] [:imag :double] [:jmag :double] [:kmag :double]]))


(defn +
  "Add two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  ^Quaternion [^Quaternion p ^Quaternion q]
  (->Quaternion (c/+ ^double (:real p) ^double (:real q))
                (c/+ ^double (:imag p) ^double (:imag q))
                (c/+ ^double (:jmag p) ^double (:jmag q))
                (c/+ ^double (:kmag p) ^double (:kmag q))))


(defn -
  "Negate quaternion or subtract two quaternions"
  {:malli/schema [:=> [:cat quaternion [:? quaternion]] quaternion]}
  ([^Quaternion p]
   (->Quaternion (c/- ^double (:real p)) (c/- ^double (:imag p)) (c/- ^double (:jmag p)) (c/- ^double (:kmag p))))
  ([^Quaternion p ^Quaternion q]
   (->Quaternion (c/- ^double (:real p) ^double (:real q))
                 (c/- ^double (:imag p) ^double (:imag q))
                 (c/- ^double (:jmag p) ^double (:jmag q))
                 (c/- ^double (:kmag p) ^double (:kmag q)))))


(defn *
  "Multiply two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  ^Quaternion [^Quaternion p ^Quaternion q]
  (let [p-real (:real p)
        p-imag (:imag p)
        p-jmag (:jmag p)
        p-kmag (:kmag p)
        q-real (:real q)
        q-imag (:imag q)
        q-jmag (:jmag q)
        q-kmag (:kmag q)]
    (->Quaternion
      (c/- (c/* ^double p-real ^double q-real) (c/* ^double p-imag ^double q-imag)
           (c/* ^double p-jmag ^double q-jmag) (c/* ^double p-kmag ^double q-kmag))
      (c/- (c/+ (c/* ^double p-real ^double q-imag) (c/* ^double p-imag ^double q-real)
                (c/* ^double p-jmag ^double q-kmag)) (c/* ^double p-kmag ^double q-jmag))
      (c/+ (c/- (c/* ^double p-real ^double q-jmag) (c/* ^double p-imag ^double q-kmag))
           (c/* ^double p-jmag ^double q-real) (c/* ^double p-kmag ^double q-imag))
      (c/+ (c/- (c/+ (c/* ^double p-real ^double q-kmag) (c/* ^double p-imag ^double q-jmag))
                (c/* ^double p-jmag ^double q-imag)) (c/* ^double p-kmag ^double q-real)))))


(defn scale
  "Multiply quaternion with real number"
  ^Quaternion [^Quaternion q ^double s]
  (->Quaternion (c/* ^double (:real q) s) (c/* ^double (:imag q) s) (c/* ^double (:jmag q) s) (c/* ^double (:kmag q) s)))


(defn norm2
  "Compute square of norm of quaternion"
  ^double [^Quaternion q]
  (c/+ (sqr (:real q))
       (sqr (:imag q))
       (sqr (:jmag q))
       (sqr (:kmag q))))


(defn norm
  "Compute norm of quaternion"
  ^double [^Quaternion q]
  (sqrt (norm2 q)))


(defn normalize
  "Normalize quaternion to create unit quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion [^Quaternion q]
  (let [factor (/ 1.0 (norm q))]
    (->Quaternion (c/* ^double (:real q) factor)
                  (c/* ^double (:imag q) factor)
                  (c/* ^double (:jmag q) factor)
                  (c/* ^double (:kmag q) factor))))


(defn conjugate
  "Return conjugate of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion[^Quaternion q]
  (->Quaternion (:real q) (c/- ^double (:imag q)) (c/- ^double (:jmag q)) (c/- ^double (:kmag q))))


(defn inverse
  "Return inverse of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion[^Quaternion q]
  (let [factor (/ 1.0 (norm2 q))]
    (->Quaternion (c/* ^double (:real q) factor)
                  (c/* ^double (c/- ^double (:imag q)) factor)
                  (c/* ^double (c/- ^double (:jmag q)) factor)
                  (c/* ^double (c/- ^double (:kmag q)) factor))))


(defn vector->quaternion
  "Convert 3D vector to quaternion"
  {:malli/schema [:=> [:cat fvec3] quaternion]}
  [v]
  (apply ->Quaternion 0.0 v))


(defn quaternion->vector
  "Convert quaternion to 3D vector"
  {:malli/schema [:=> [:cat quaternion] fvec3]}
  [q]
  (vec3 (:imag q) (:jmag q) (:kmag q)))


(defn exp
  "Exponentiation of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  ^Quaternion [^Quaternion q]
  (let [scale      (m/exp (:real q))
        rotation   (mag (quaternion->vector q))
        cos-scale  (c/* scale (cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (->Quaternion cos-scale
                  (c/* sinc-scale ^double (:imag q))
                  (c/* sinc-scale ^double (:jmag q))
                  (c/* sinc-scale ^double (:kmag q)))))


(defn rotation
  "Generate quaternion to represent rotation"
  ^Quaternion [^double theta ^Vec3 v]
  (let [scale (c/* theta 0.5)]
    (exp (vector->quaternion (mult v scale)))))


(defn rotate-vector
  "Rotate a vector with a rotation represented by a quaternion"
  {:malli/schema [:=> [:cat quaternion fvec3] fvec3]}
  [q v]
  (quaternion->vector (* (* q (vector->quaternion v)) (conjugate q))))


(defn vector-to-vector-rotation
  "Create quaternion for rotating u to v"
  {:malli/schema [:=> [:cat fvec3 fvec3] quaternion]}
  [u v]
  (let [axis (cross u v)
        w    (c/+ (c/* (mag u) (mag v)) (dot u v))]
    (normalize (->Quaternion w (axis 0) (axis 1) (axis 2)))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
