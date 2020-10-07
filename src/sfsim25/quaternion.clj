(ns sfsim25.quaternion
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]))

(set! *unchecked-math* true)

(deftype Quaternion [^double a ^double b ^double c ^double d]
  Object
  (equals [this other] (and (instance? Quaternion other) (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other))))
  (toString [this] (str "(quaternion " a \space b \space c \space d ")")))

(defn make-quaternion ^Quaternion [^double a ^double b ^double c ^double d]
  "Construct a quaternion"
  (Quaternion. a b c d))

(defn + ^Quaternion [^Quaternion p ^Quaternion q]
  "Add two quaternions"
  (Quaternion. (c/+ (.a p) (.a q)) (c/+ (.b p) (.b q)) (c/+ (.c p) (.c q)) (c/+ (.d p) (.d q))))

(defn - ^Quaternion [^Quaternion p ^Quaternion q]
  "Subtract two quaternions"
  (Quaternion. (c/- (.a p) (.a q)) (c/- (.b p) (.b q)) (c/- (.c p) (.c q)) (c/- (.d p) (.d q))))

(defn * ^Quaternion [^Quaternion p ^Quaternion q]
  "Multiply two quaternions"
  (make-quaternion
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
  (let [factor (/ 1.0 (norm q))]
    (make-quaternion (c/* (.a q) factor) (c/* (.b q) factor) (c/* (.c q) factor) (c/* (.d q) factor))))

(set! *unchecked-math* false)
