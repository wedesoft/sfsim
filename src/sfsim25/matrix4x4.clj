(ns sfsim25.matrix4x4
  (:refer-clojure :exclude [*])
  (:require [clojure.core :as c]
            [sfsim25.vector3 :refer (->Vector3)]
            [sfsim25.vector4 :refer (->Vector4)]
            [sfsim25.matrix3x3 :refer (->Matrix3x3)])
  (:import [sfsim25.vector3 Vector3]
           [sfsim25.vector4 Vector4]
           [sfsim25.matrix3x3 Matrix3x3]))

(set! *unchecked-math* true)

(deftype Matrix4x4 [^double m11 ^double m12 ^double m13 ^double m14
                    ^double m21 ^double m22 ^double m23 ^double m24
                    ^double m31 ^double m32 ^double m33 ^double m34
                    ^double m41 ^double m42 ^double m43 ^double m44]
  Object
  (equals [this other] (and (instance? Matrix4x4 other)
                            (= m11 (.m11 other)) (= m12 (.m12 other)) (= m13 (.m13 other)) (= m14 (.m14 other))
                            (= m21 (.m21 other)) (= m22 (.m22 other)) (= m23 (.m23 other)) (= m24 (.m24 other))
                            (= m31 (.m31 other)) (= m32 (.m32 other)) (= m33 (.m33 other)) (= m34 (.m34 other))
                            (= m41 (.m41 other)) (= m42 (.m42 other)) (= m43 (.m43 other)) (= m44 (.m44 other))))
  (toString [this] (str "(matrix4x4 " m11 \space m12 \space m13 \space m14 \space
                                      m21 \space m22 \space m23 \space m24 \space
                                      m31 \space m32 \space m33 \space m34 \space
                                      m41 \space m42 \space m43 \space m44 \)))
  clojure.lang.Seqable
  (seq [this] (list m11 m12 m13 m14 m21 m22 m23 m24 m31 m32 m33 m34 m41 m42 m43 m44)))

(defn matrix3x3->matrix4x4
  "Create homogeneous 4x4 transformation matrix from 3x3 rotation matrix and translation vector"
  ^Matrix4x4 [^Matrix3x3 m ^Vector3 v]
  (->Matrix4x4 (.m11 m) (.m12 m) (.m13 m) (.x v)
               (.m21 m) (.m22 m) (.m23 m) (.y v)
               (.m31 m) (.m32 m) (.m33 m) (.z v)
                      0        0        0      1))

(defn *
  "4D matrix-vector multiplication"
  ^Vector4 [^Matrix4x4 m ^Vector4 v]
  (->Vector4 (+ (c/* (.m11 m) (.x v)) (c/* (.m12 m) (.y v)) (c/* (.m13 m) (.z v)) (c/* (.m14 m) (.l v)))
             (+ (c/* (.m21 m) (.x v)) (c/* (.m22 m) (.y v)) (c/* (.m23 m) (.z v)) (c/* (.m24 m) (.l v)))
             (+ (c/* (.m31 m) (.x v)) (c/* (.m32 m) (.y v)) (c/* (.m33 m) (.z v)) (c/* (.m34 m) (.l v)))
             (+ (c/* (.m41 m) (.x v)) (c/* (.m42 m) (.y v)) (c/* (.m43 m) (.z v)) (c/* (.m44 m) (.l v)))))

(defn projection-matrix
  "Compute OpenGL projection matrix (frustum)"
  [width height near far field-of-view]
  (let [dx (/ 1 (Math/tan (/ field-of-view 2)))
        dy (-> dx (c/* width) (/ height))
        c1 (/ (+ near far) (- near far))
        c2 (/ (c/* 2 near far) (- near far))]
    (->Matrix4x4 dx  0  0  0
                  0 dy  0  0
                  0  0 c1 c2
                  0  0 -1  0)))

(set! *unchecked-math* false)
