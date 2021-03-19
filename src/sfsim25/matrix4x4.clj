(ns sfsim25.matrix4x4)

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
                                      m41 \space m42 \space m43 \space m44 \))))
(set! *unchecked-math* false)
