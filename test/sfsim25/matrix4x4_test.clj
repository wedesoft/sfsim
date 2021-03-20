(ns sfsim25.matrix4x4-test
  (:refer-clojure :exclude [*])
  (:require [clojure.core :as c]
            [clojure.test :refer :all]
            [sfsim25.matrix4x4 :refer :all]
            [sfsim25.matrix3x3 :refer (->Matrix3x3)]
            [sfsim25.vector3 :refer (->Vector3 norm) :as v]
            [sfsim25.vector4 :refer (->Vector4 project)]))

(deftest display-test
  (testing "Display 4x4 matrix"
    (is (= "(matrix4x4 1.0 2.0 3.0 4.0 2.0 3.0 4.0 5.0 3.0 4.0 5.0 6.0 4.0 5.0 6.0 7.0)"
           (str (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7))))))

(deftest equality-test
  (testing "Equality of 4x4 matrices"
    (is (= (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7) (->Matrix4x4 1 2 3 4 2 3 4 5 3 4 5 6 4 5 6 7)))))

(deftest matrix3x3->matrix4x4-test
  (testing "Creating a 4x4 matrix from a 3x3 matrix and a translation vector"
    (is (= (->Matrix4x4 1 2 3 4 5 6 7 8 9 10 11 12 0 0 0 1)
           (matrix3x3->matrix4x4 (->Matrix3x3 1 2 3 5 6 7 9 10 11) (->Vector3 4 8 12))))))

(deftest matrix-vector-dot-test
  (testing "4D matrix-vector multiplication")
    (is (= (->Vector4 30 40 50 60) (* (->Matrix4x4 1 2 3 4, 2 3 4 5, 3 4 5 6, 4 5 6 7) (->Vector4 1 2 3 4)))))

(def pi Math/PI)

(deftest projection-matrix-test
  (testing "OpenGL projection matrix"
    (let [m (projection-matrix 640 480 5.0 1000.0 (c/* 0.5 pi))]
      (is (< (norm (v/- (->Vector3 0 0 -1) (project (* m (->Vector4 0    0    -5 1))))) 1e-6))
      (is (< (norm (v/- (->Vector3 0 0  1) (project (* m (->Vector4 0    0 -1000 1))))) 1e-6))
      (is (< (norm (v/- (->Vector3 1 0 -1) (project (* m (->Vector4 5    0    -5 1))))) 1e-6))
      (is (< (norm (v/- (->Vector3 0 1 -1) (project (* m (->Vector4 0 3.75    -5 1))))) 1e-6)))))
