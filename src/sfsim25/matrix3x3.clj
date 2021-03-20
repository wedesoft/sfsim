(ns sfsim25.matrix3x3
  (:refer-clojure :exclude [* -])
  (:require [clojure.core :as c]
            [sfsim25.vector3 :refer (->Vector3)]
            [sfsim25.quaternion :refer (rotate-vector)])
  (:import [sfsim25.vector3 Vector3]
           [sfsim25.quaternion Quaternion]))

(set! *unchecked-math* true)

(deftype Matrix3x3 [^double m11 ^double m12 ^double m13
                    ^double m21 ^double m22 ^double m23
                    ^double m31 ^double m32 ^double m33]
  Object
  (equals [this other] (and (instance? Matrix3x3 other)
                            (= m11 (.m11 other)) (= m12 (.m12 other)) (= m13 (.m13 other))
                            (= m21 (.m21 other)) (= m22 (.m22 other)) (= m23 (.m23 other))
                            (= m31 (.m31 other)) (= m32 (.m32 other)) (= m33 (.m33 other))))
  (toString [this] (str "(matrix3x3 " m11 \space m12 \space m13 \space
                                      m21 \space m22 \space m23 \space
                                      m31 \space m32 \space m33 \))))

(set! *warn-on-reflection* true)

(defn -
  "Negate matrix or subtract matrices"
  (^Matrix3x3 [^Matrix3x3 a]
    (->Matrix3x3 (c/- (.m11 a)) (c/- (.m12 a)) (c/- (.m13 a))
                 (c/- (.m21 a)) (c/- (.m22 a)) (c/- (.m23 a))
                 (c/- (.m31 a)) (c/- (.m32 a)) (c/- (.m33 a))))
  (^Matrix3x3 [^Matrix3x3 a ^Matrix3x3 b]
    (->Matrix3x3 (c/- (.m11 a) (.m11 b)) (c/- (.m12 a) (.m12 b)) (c/- (.m13 a) (.m13 b))
                 (c/- (.m21 a) (.m21 b)) (c/- (.m22 a) (.m22 b)) (c/- (.m23 a) (.m23 b))
                 (c/- (.m31 a) (.m31 b)) (c/- (.m32 a) (.m32 b)) (c/- (.m33 a) (.m33 b)))))

(defn *
  "3D matrix-vector multiplication"
  ^Vector3 [^Matrix3x3 m ^Vector3 v]
  (->Vector3 (+ (c/* (.m11 m) (.x v)) (c/* (.m12 m) (.y v)) (c/* (.m13 m) (.z v)))
             (+ (c/* (.m21 m) (.x v)) (c/* (.m22 m) (.y v)) (c/* (.m23 m) (.z v)))
             (+ (c/* (.m31 m) (.x v)) (c/* (.m32 m) (.y v)) (c/* (.m33 m) (.z v)))))

(defn norm2
  "Compute square of norm of matrix"
  ^double [^Matrix3x3 m]
  (+ (c/* (.m11 m) (.m11 m)) (c/* (.m12 m) (.m12 m)) (c/* (.m13 m) (.m13 m))
     (c/* (.m21 m) (.m21 m)) (c/* (.m22 m) (.m22 m)) (c/* (.m23 m) (.m23 m))
     (c/* (.m31 m) (.m31 m)) (c/* (.m32 m) (.m32 m)) (c/* (.m33 m) (.m33 m))))

(defn norm
  "Compute Frobenius norm of matrix"
  ^double [^Matrix3x3 m]
  (Math/sqrt (norm2 m)))

(defn rotation-x
  "Rotation matrix around x-axis"
  ^Matrix3x3 [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (->Matrix3x3 1 0 0 0 ca (c/- sa) 0 sa ca)))

(defn rotation-y
  "Rotation matrix around y-axis"
  ^Matrix3x3 [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (->Matrix3x3 ca 0 sa 0 1 0 (c/- sa) 0 ca)))

(defn rotation-z
  "Rotation matrix around z-axis"
  ^Matrix3x3 [^double angle]
  (let [ca (Math/cos angle) sa (Math/sin angle)]
    (->Matrix3x3 ca (c/- sa) 0 sa ca 0 0 0 1)))

(defn quaternion->matrix
  "Convert rotation quaternion to rotation matrix"
  ^Matrix3x3 [^Quaternion q]
  (let [a (rotate-vector q (->Vector3 1 0 0))
        b (rotate-vector q (->Vector3 0 1 0))
        c (rotate-vector q (->Vector3 0 0 1))]
    (->Matrix3x3 (.x a) (.x b) (.x c)
                 (.y a) (.y b) (.y c)
                 (.z a) (.z b) (.z c))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
