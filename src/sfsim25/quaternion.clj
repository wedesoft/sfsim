(ns sfsim25.quaternion
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]
            [sfsim25.util :refer [sinc]]
            [sfsim25.vector3 :refer [vector3] :as v])
  (:import [sfsim25.vector3 Vector3]))

(set! *unchecked-math* true)

(deftype Quaternion [^double a ^double b ^double c ^double d]
  Object
  (equals [this other] (and (instance? Quaternion other) (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other))))
  (toString [this] (str "(quaternion " a \space b \space c \space d ")")))

(set! *warn-on-reflection* true)

(defn quaternion ^Quaternion [^double a ^double b ^double c ^double d]
  "Construct a quaternion"
  (Quaternion. a b c d))

(defn real ^double [^Quaternion q] (.a q))
(defn imag ^double [^Quaternion q] (.b q))
(defn jmag ^double [^Quaternion q] (.c q))
(defn kmag ^double [^Quaternion q] (.d q))

(defn + ^Quaternion [^Quaternion p ^Quaternion q]
  "Add two quaternions"
  (Quaternion. (c/+ (.a p) (.a q)) (c/+ (.b p) (.b q)) (c/+ (.c p) (.c q)) (c/+ (.d p) (.d q))))

(defn - ^Quaternion [^Quaternion p ^Quaternion q]
  "Subtract two quaternions"
  (Quaternion. (c/- (.a p) (.a q)) (c/- (.b p) (.b q)) (c/- (.c p) (.c q)) (c/- (.d p) (.d q))))

(defn * ^Quaternion [^Quaternion p ^Quaternion q]
  "Multiply two quaternions"
  (quaternion
    (c/- (c/* (.a p) (.a q)) (c/* (.b p) (.b q)) (c/* (.c p) (.c q)) (c/* (.d p) (.d q)))
    (c/- (c/+ (c/* (.a p) (.b q)) (c/* (.b p) (.a q)) (c/* (.c p) (.d q))) (c/* (.d p) (.c q)))
    (c/+ (c/- (c/* (.a p) (.c q)) (c/* (.b p) (.d q))) (c/* (.c p) (.a q)) (c/* (.d p) (.b q)))
    (c/+ (c/- (c/+ (c/* (.a p) (.d q)) (c/* (.b p) (.c q))) (c/* (.c p) (.b q))) (c/* (.d p) (.a q)))))

(defn norm2 ^double [^Quaternion q]
  "Compute square of norm of quaternion"
  (c/+ (c/* (.a q) (.a q)) (c/* (.b q) (.b q)) (c/* (.c q) (.c q)) (c/* (.d q) (.d q))))

(defn norm ^double [^Quaternion q]
  "Compute norm of quaternion"
  (Math/sqrt (norm2 q)))

(defn normalize ^Quaternion [^Quaternion q]
  "Normalize quaternion to create unit quaternion"
  (let [factor (/ 1.0 (norm q))]
    (quaternion (c/* (.a q) factor) (c/* (.b q) factor) (c/* (.c q) factor) (c/* (.d q) factor))))

(defn conjugate ^Quaternion [^Quaternion q]
  "Return conjugate of quaternion"
  (quaternion (.a q) (c/- (.b q)) (c/- (.c q)) (c/- (.d q))))

(defn inverse ^Quaternion [^Quaternion q]
  "Return inverse of quaternion"
  (let [factor (/ 1.0 (norm2 q))]
    (quaternion (c/* (.a q) factor) (c/* (c/- (.b q)) factor) (c/* (c/- (.c q)) factor) (c/* (c/- (.d q)) factor))))

(defn vector3->quaternion ^Quaternion [^Vector3 v]
  "Convert 3D vector to quaternion"
  (quaternion 0.0 (.x v) (.y v) (.z v)))

(defn quaternion->vector3 ^Vector3 [^Quaternion q]
  "Convert quaternion to 3D vector"
  (vector3 (.b q) (.c q) (.d q)))

(defn exp ^Quaternion [^Quaternion q]
  "Exponentiation of quaternion"
  (let [scale      (Math/exp (.a q))
        rotation   (v/norm (quaternion->vector3 q))
        cos-scale  (c/* scale (Math/cos rotation))
        sinc-scale (c/* scale (sinc rotation))]
    (quaternion cos-scale (c/* sinc-scale (.b q)) (c/* sinc-scale (.c q)) (c/* sinc-scale (.d q)))))

(defn rotation ^Quaternion [^double theta ^Vector3 v]
  "Generate quaternion to represent rotation"
  (let [scale (/ theta 2)]
    (exp (vector3->quaternion (vector3 (c/* scale (.x v)) (c/* scale (.y v)) (c/* scale (.z v)))))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
