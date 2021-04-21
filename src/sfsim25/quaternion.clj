(ns sfsim25.quaternion
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]
            [sfsim25.util :refer (sinc)]
            [sfsim25.vector3 :refer (->Vector3) :as v])
  (:import [sfsim25.vector3 Vector3]))

(set! *unchecked-math* true)

(defrecord Quaternion [^double a ^double b ^double c ^double d])

(set! *warn-on-reflection* true)

(defn +
  "Add two quaternions"
  ^Quaternion [^Quaternion p ^Quaternion q]
  (->Quaternion (c/+ (:a p) (:a q)) (c/+ (:b p) (:b q)) (c/+ (:c p) (:c q)) (c/+ (:d p) (:d q))))

(defn -
  "Subtract two quaternions"
  ^Quaternion [^Quaternion p ^Quaternion q]
  (->Quaternion (c/- (:a p) (:a q)) (c/- (:b p) (:b q)) (c/- (:c p) (:c q)) (c/- (:d p) (:d q))))

(defn *
  "Multiply two quaternions"
  ^Quaternion [^Quaternion p ^Quaternion q]
  (->Quaternion
    (c/- (c/* (:a p) (:a q)) (c/* (:b p) (:b q)) (c/* (:c p) (:c q)) (c/* (:d p) (:d q)))
    (c/- (c/+ (c/* (:a p) (:b q)) (c/* (:b p) (:a q)) (c/* (:c p) (:d q))) (c/* (:d p) (:c q)))
    (c/+ (c/- (c/* (:a p) (:c q)) (c/* (:b p) (:d q))) (c/* (:c p) (:a q)) (c/* (:d p) (:b q)))
    (c/+ (c/- (c/+ (c/* (:a p) (:d q)) (c/* (:b p) (:c q))) (c/* (:c p) (:b q))) (c/* (:d p) (:a q)))))

(defn norm2
  "Compute square of norm of quaternion"
  ^double [^Quaternion q]
  (c/+ (c/* (:a q) (:a q)) (c/* (:b q) (:b q)) (c/* (:c q) (:c q)) (c/* (:d q) (:d q))))

(defn norm
  "Compute norm of quaternion"
  ^double [^Quaternion q]
  (Math/sqrt (norm2 q)))

(defn normalize
  "Normalize quaternion to create unit quaternion"
  ^Quaternion [^Quaternion q]
  (let [factor (/ 1.0 (norm q))]
    (->Quaternion (c/* (:a q) factor) (c/* (:b q) factor) (c/* (:c q) factor) (c/* (:d q) factor))))

(defn conjugate
  "Return conjugate of quaternion"
  ^Quaternion [^Quaternion q]
  (->Quaternion (:a q) (c/- (:b q)) (c/- (:c q)) (c/- (:d q))))

(defn inverse
  "Return inverse of quaternion"
  ^Quaternion [^Quaternion q]
  (let [factor (/ 1.0 (norm2 q))]
    (->Quaternion (c/* (:a q) factor) (c/* (c/- (:b q)) factor) (c/* (c/- (:c q)) factor) (c/* (c/- (:d q)) factor))))

(defn vector3->quaternion
  "Convert 3D vector to quaternion"
  ^Quaternion [^Vector3 v]
  (->Quaternion 0.0 (:x v) (:y v) (:z v)))

(defn quaternion->vector3
  "Convert quaternion to 3D vector"
  ^Vector3 [^Quaternion q]
  (->Vector3 (:b q) (:c q) (:d q)))

(defn exp
  "Exponentiation of quaternion"
  ^Quaternion [^Quaternion q]
  (let [scale      (Math/exp (:a q))
        rotation   (v/norm (quaternion->vector3 q))
        cos-scale  (c/* scale (Math/cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (->Quaternion cos-scale (c/* sinc-scale (:b q)) (c/* sinc-scale (:c q)) (c/* sinc-scale (:d q)))))

(defn rotation
  "Generate quaternion to represent rotation"
  ^Quaternion [^double theta ^Vector3 v]
  (let [scale (/ theta 2)]
    (exp (vector3->quaternion (->Vector3 (c/* scale (:x v)) (c/* scale (:y v)) (c/* scale (:z v)))))))

(defn rotate-vector
  "Rotate a vector with a rotation represented by a quaternion"
  ^Vector3 [^Quaternion q ^Vector3 v]
  (quaternion->vector3 (* (* q (vector3->quaternion v)) (conjugate q))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
