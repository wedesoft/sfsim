(ns sfsim.quaternion
  "Complex algebra implementation."
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]
            [clojure.math :refer (cos sqrt) :as m]
            [malli.core :as mc]
            [fastmath.vector :refer (vec3 mag mult)]
            [sfsim.util :refer (sinc sqr)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defrecord Quaternion [^double a ^double b ^double c ^double d])

(def fvec3 (mc/schema [:tuple :double :double :double]))
(def quaternion (mc/schema [:map [:a :double] [:b :double] [:c :double] [:d :double]]))

(defn +
  "Add two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  [p q]
  (->Quaternion (c/+ (:a p) (:a q)) (c/+ (:b p) (:b q)) (c/+ (:c p) (:c q)) (c/+ (:d p) (:d q))))

(defn -
  "Negate quaternion or subtract two quaternions"
  {:malli/schema [:=> [:cat quaternion [:? quaternion]] quaternion]}
  ([p]
   (->Quaternion (c/- (:a p)) (c/- (:b p)) (c/- (:c p)) (c/- (:d p))))
  ([p q]
   (->Quaternion (c/- (:a p) (:a q)) (c/- (:b p) (:b q)) (c/- (:c p) (:c q)) (c/- (:d p) (:d q)))))

(defn *
  "Multiply two quaternions"
  {:malli/schema [:=> [:cat quaternion quaternion] quaternion]}
  [p q]
  (->Quaternion
    (c/- (c/* (:a p) (:a q)) (c/* (:b p) (:b q)) (c/* (:c p) (:c q)) (c/* (:d p) (:d q)))
    (c/- (c/+ (c/* (:a p) (:b q)) (c/* (:b p) (:a q)) (c/* (:c p) (:d q))) (c/* (:d p) (:c q)))
    (c/+ (c/- (c/* (:a p) (:c q)) (c/* (:b p) (:d q))) (c/* (:c p) (:a q)) (c/* (:d p) (:b q)))
    (c/+ (c/- (c/+ (c/* (:a p) (:d q)) (c/* (:b p) (:c q))) (c/* (:c p) (:b q))) (c/* (:d p) (:a q)))))

(defn scale
  "Multiply quaternion with real number"
  {:malli/schema [:=> [:cat quaternion :double] quaternion]}
  [q s]
  (->Quaternion (c/* (:a q) s) (c/* (:b q) s) (c/* (:c q) s) (c/* (:d q) s)))

(defn norm2
  "Compute square of norm of quaternion"
  {:malli/schema [:=> [:cat quaternion] :double]}
  [q]
  (c/+ (sqr (:a q)) (sqr (:b q)) (sqr (:c q)) (sqr (:d q))))

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
    (->Quaternion (c/* (:a q) factor) (c/* (:b q) factor) (c/* (:c q) factor) (c/* (:d q) factor))))

(defn conjugate
  "Return conjugate of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (->Quaternion (:a q) (c/- (:b q)) (c/- (:c q)) (c/- (:d q))))

(defn inverse
  "Return inverse of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (let [factor (/ 1.0 (norm2 q))]
    (->Quaternion (c/* (:a q) factor) (c/* (c/- (:b q)) factor) (c/* (c/- (:c q)) factor) (c/* (c/- (:d q)) factor))))

(defn vector->quaternion
  "Convert 3D vector to quaternion"
  {:malli/schema [:=> [:cat fvec3] quaternion]}
  [v]
  (apply ->Quaternion 0.0 v))

(defn quaternion->vector
  "Convert quaternion to 3D vector"
  {:malli/schema [:=> [:cat quaternion] fvec3]}
  [q]
  (vec3 (:b q) (:c q) (:d q)))

(defn exp
  "Exponentiation of quaternion"
  {:malli/schema [:=> [:cat quaternion] quaternion]}
  [q]
  (let [scale      (m/exp (:a q))
        rotation   (mag (quaternion->vector q))
        cos-scale  (c/* scale (cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (->Quaternion cos-scale (c/* sinc-scale (:b q)) (c/* sinc-scale (:c q)) (c/* sinc-scale (:d q)))))

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

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
