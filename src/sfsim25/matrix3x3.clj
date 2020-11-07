(ns sfsim25.matrix3x3
  (:refer-clojure :exclude [*])
  (:require [clojure.core :as c]
            [sfsim25.vector3 :refer (vector3)])
  (:import [sfsim25.vector3 Vector3]))

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
                                      m31 \space m32 \space m33 ")")))

(set! *warn-on-reflection* true)

(defn matrix3x3 ^Matrix3x3 [m11 m12 m13 m21 m22 m23 m31 m32 m33]
  "Construct a 3x3 matrix"
  (Matrix3x3. m11 m12 m13 m21 m22 m23 m31 m32 m33))

(defn * ^Vector3 [^Matrix3x3 m ^Vector3 v]
  "Matrix-vector multiplication"
  (vector3 (+ (c/* (.m11 m) (.x v)) (c/* (.m12 m) (.y v)) (c/* (.m13 m) (.z v)))
           (+ (c/* (.m21 m) (.x v)) (c/* (.m22 m) (.y v)) (c/* (.m23 m) (.z v)))
           (+ (c/* (.m31 m) (.x v)) (c/* (.m32 m) (.y v)) (c/* (.m33 m) (.z v)))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
