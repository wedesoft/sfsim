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
    [sfsim.util :refer (sinc sqr)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defrecord Quaternion
  [^double real ^double imag ^double jmag ^double kmag])


(def fvec3 (mc/schema [:tuple :double :double :double]))
(def quaternion (mc/schema [:map [:real :double] [:imag :double] [:jmag :double] [:kmag :double]]))


(defn +
  "Add two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  [p q]
  (->Quaternion (c/+ (:real p) (:real q)) (c/+ (:imag p) (:imag q)) (c/+ (:jmag p) (:jmag q)) (c/+ (:kmag p) (:kmag q))))


(defn -
  "Negate quaternion or subtract two quaternions"
  {:malli/schema [:=> [:cat quaternion [:? quaternion]] quaternion]}
  ([p]
   (->Quaternion (c/- (:real p)) (c/- (:imag p)) (c/- (:jmag p)) (c/- (:kmag p))))
  ([p q]
   (->Quaternion (c/- (:real p) (:real q)) (c/- (:imag p) (:imag q)) (c/- (:jmag p) (:jmag q)) (c/- (:kmag p) (:kmag q)))))


(defn *
  "Multiply two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  [p q]
  (->Quaternion
    (c/- (c/* (:real p) (:real q)) (c/* (:imag p) (:imag q)) (c/* (:jmag p) (:jmag q)) (c/* (:kmag p) (:kmag q)))
    (c/- (c/+ (c/* (:real p) (:imag q)) (c/* (:imag p) (:real q)) (c/* (:jmag p) (:kmag q))) (c/* (:kmag p) (:jmag q)))
    (c/+ (c/- (c/* (:real p) (:jmag q)) (c/* (:imag p) (:kmag q))) (c/* (:jmag p) (:real q)) (c/* (:kmag p) (:imag q)))
    (c/+ (c/- (c/+ (c/* (:real p) (:kmag q)) (c/* (:imag p) (:jmag q))) (c/* (:jmag p) (:imag q))) (c/* (:kmag p) (:real q)))))


(defn scale
  "Multiply quaternion with real number"
  {:malli/schema [:=> [:cat quaternion :double] quaternion]}
  [q s]
  (->Quaternion (c/* (:real q) s) (c/* (:imag q) s) (c/* (:jmag q) s) (c/* (:kmag q) s)))


(defn norm2
  "Compute square of norm of quaternion"
  {:malli/schema [:=> [:cat quaternion] :double]}
  [q]
  (c/+ (sqr (:real q)) (sqr (:imag q)) (sqr (:jmag q)) (sqr (:kmag q))))


(defn norm
  "Compute norm of quaternion"
  {:malli/schema [:=> [:cat quaternion] :double]}
  [q]
  (sqrt (norm2 q)))


(defn normalize
  "Normalize quaternion to create unit quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (let [factor (/ 1.0 (norm q))]
    (->Quaternion (c/* (:real q) factor) (c/* (:imag q) factor) (c/* (:jmag q) factor) (c/* (:kmag q) factor))))


(defn conjugate
  "Return conjugate of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (->Quaternion (:real q) (c/- (:imag q)) (c/- (:jmag q)) (c/- (:kmag q))))


(defn inverse
  "Return inverse of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (let [factor (/ 1.0 (norm2 q))]
    (->Quaternion (c/* (:real q) factor) (c/* (c/- (:imag q)) factor) (c/* (c/- (:jmag q)) factor) (c/* (c/- (:kmag q)) factor))))


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
  [q]
  (let [scale      (m/exp (:real q))
        rotation   (mag (quaternion->vector q))
        cos-scale  (c/* scale (cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (->Quaternion cos-scale (c/* sinc-scale (:imag q)) (c/* sinc-scale (:jmag q)) (c/* sinc-scale (:kmag q)))))


(defn rotation
  "Generate quaternion to represent rotation"
  {:malli/schema [:=> [:cat :double fvec3] quaternion]}
  [theta v]
  (let [scale (/ theta 2)]
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
