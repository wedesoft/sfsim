(ns sfsim25.vector3
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]))

(set! *unchecked-math* true)

(defrecord Vector3 [^double x ^double y ^double z])

(set! *warn-on-reflection* true)

(defn +
  "Add 3D vectors"
  (^Vector3 [^Vector3 a] a)
  (^Vector3 [^Vector3 a ^Vector3 b] (->Vector3 (c/+ (:x a) (:x b)) (c/+ (:y a) (:y b)) (c/+ (:z a) (:z b))))
  (^Vector3 [^Vector3 a ^Vector3 b & other] (apply + (+ a b) other)))

(defn -
  "Subtract 3D vectors"
  (^Vector3 [^Vector3 a] (->Vector3 (c/- (:x a)) (c/- (:y a)) (c/- (:z a))))
  (^Vector3 [^Vector3 a ^Vector3 b] (->Vector3 (c/- (:x a) (:x b)) (c/- (:y a) (:y b)) (c/- (:z a) (:z b)))))

(defn *
  "Scale a 3D vector"
  ^Vector3 [^double s ^Vector3 v]
  (->Vector3 (c/* s (:x v)) (c/* s (:y v)) (c/* s (:z v))))

(defn inner-product
  "Dot product of two vectors"
  ^double [^Vector3 a ^Vector3 b]
  (c/+ (c/* (:x a) (:x b)) (c/* (:y a) (:y b))  (c/* (:z a) (:z b))))

(defn norm2
  "Squared norm of vector"
  ^double [^Vector3 v]
  (inner-product v v))

(defn norm
  "Norm of vector"
  ^double [^Vector3 v]
  (Math/sqrt (norm2 v)))

(defn normalize
  "Normalize the vector"
  ^Vector3 [^Vector3 v]
  (* (/ 1.0 (norm v)) v))

(defn cross-product
  "Cross-product of two vectors"
  ^Vector3 [^Vector3 a ^Vector3 b]
  (->Vector3 (c/- (c/* (:y a) (:z b)) (c/* (:z a) (:y b)))
             (c/- (c/* (:z a) (:x b)) (c/* (:x a) (:z b)))
             (c/- (c/* (:x a) (:y b)) (c/* (:y a) (:x b)))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
