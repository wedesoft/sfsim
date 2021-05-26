(ns sfsim25.t-matrix4x4
  (:refer-clojure :exclude [*])
  (:require [clojure.core :as c]
            [midje.sweet :refer :all]
            [sfsim25.matrix4x4 :refer :all]
            [sfsim25.matrix3x3 :refer (->Matrix3x3)]
            [sfsim25.vector3 :refer (->Vector3 norm) :as v]
            [sfsim25.vector4 :refer (->Vector4 project)]))

(fact "Equality of 4x4 matrices"
   (->Matrix4x4 1 2 3 4, 2 3 4 5, 3 4 5 6, 4 5 6 7) => (->Matrix4x4 1 2 3 4, 2 3 4 5, 3 4 5 6, 4 5 6 7))

(fact "Convert 4x4 matrix to sequence"
  (vals (->Matrix4x4 1 2 3 4, 2 3 4 5, 3 4 5 6, 4 5 6 7)) => [1.0 2.0 3.0 4.0 2.0 3.0 4.0 5.0 3.0 4.0 5.0 6.0 4.0 5.0 6.0 7.0])

(fact "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
  (matrix3x3->matrix4x4 (->Matrix3x3 1 2 3 5 6 7 9 10 11) (->Vector3 4 8 12)) => (->Matrix4x4 1 2 3 4 5 6 7 8 9 10 11 12 0 0 0 1))

(fact "4D matrix-vector multiplication"
  (* (->Matrix4x4 1 2 3 4, 2 3 4 5, 3 4 5 6, 4 5 6 7) (->Vector4 1 2 3 4)) => (->Vector4 30 40 50 60))

(defn roughly-vector [y] (fn [x] (< (v/norm (v/- y x)) 1e-6)))

(fact "OpenGL projection matrix"
  (let [m (projection-matrix 640 480 5.0 1000.0 (c/* 0.5 Math/PI))]
    (project (* m (->Vector4 0    0    -5 1))) => (roughly-vector (->Vector3 0 0 -1))
    (project (* m (->Vector4 0    0 -1000 1))) => (roughly-vector (->Vector3 0 0  1))
    (project (* m (->Vector4 5    0    -5 1))) => (roughly-vector (->Vector3 1 0 -1))
    (project (* m (->Vector4 0 3.75    -5 1))) => (roughly-vector (->Vector3 0 1 -1))))

(fact "Multiply two 4x4 matrices"
  (x (->Matrix4x4 1 2 3 4, 5 6 7 8, 9 10 11 12, 13 14 15 16) (->Matrix4x4 2 3 4 5, 6 7 8 9, 10 11 12 13, 14 15 16 17))
  => (->Matrix4x4 100 110 120 130, 228 254 280 306, 356 398 440 482, 484 542 600 658))

(fact "Determinant of 4x4 matrix"
  (determinant4x4 (->Matrix4x4 2 3 5 7, 11 13 17 19, 23 29 31 37, 41 43 47 53)) => 880.0)
