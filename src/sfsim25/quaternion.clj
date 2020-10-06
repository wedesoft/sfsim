(ns sfsim25.quaternion
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]))

(deftype Quaternion [^double a ^double b ^double c ^double d]
  Object
  (equals [this other] (and (instance? Quaternion other) (= a (.a other)) (= b (.b other)) (= c (.c other)) (= d (.d other))))
  (toString [this] (str "(quaternion " a \space b \space c \space d ")")))

(defn make-quaternion [^double a ^double b ^double c ^double d]
  "Construct a quaternion"
  (Quaternion. a b c d))

(defn + [^Quaternion p ^Quaternion q]
  "Add two quaternions"
  (Quaternion. (c/+ (.a p) (.a q)) (c/+ (.b p) (.b q)) (c/+ (.c p) (.c q)) (c/+ (.d p) (.d q))))

(defn - [^Quaternion p ^Quaternion q]
  "Subtract two quaternions"
  (Quaternion. (c/- (.a p) (.a q)) (c/- (.b p) (.b q)) (c/- (.c p) (.c q)) (c/- (.d p) (.d q))))

(defn * [^Quaternion p ^Quaternion q]
  "Multiply two quaternions"
  (make-quaternion
    (c/- (c/* (.a p) (.a q)) (c/* (.b p) (.b q)) (c/* (.c p) (.c q)) (c/* (.d p) (.d q)))
    (c/- (c/+ (c/* (.a p) (.b q)) (c/* (.b p) (.a q)) (c/* (.c p) (.d q))) (c/* (.d p) (.c q)))
    (c/+ (c/- (c/* (.a p) (.c q)) (c/* (.b p) (.d q))) (c/* (.c p) (.a q)) (c/* (.d p) (.b q)))
    (c/+ (c/- (c/+ (c/* (.a p) (.d q)) (c/* (.b p) (.c q))) (c/* (.c p) (.b q))) (c/* (.d p) (.a q)))))
