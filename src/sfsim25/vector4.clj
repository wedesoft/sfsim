(ns sfsim25.vector4
  (:require [sfsim25.vector3 :refer (->Vector3)])
  (:import [sfsim25.vector3 Vector3]))

(set! *unchecked-math* true)

(deftype Vector4 [^double x ^double y ^double z ^double l]
  Object
  (equals [this other] (and (instance? Vector4 other) (= x (.x other)) (= y (.y other)) (= z (.z other)) (= l (.l other))))
  (toString [this] (str "(vector4 " x \space y \space z \space l \)))
  clojure.lang.Seqable
  (seq [this] (list x y z l)))

(defn project
  "Project homogeneous coordinate to cartesian"
  ^Vector3 [^Vector4 v]
  (->Vector3 (/ (.x v) (.l v)) (/ (.y v) (.l v)) (/ (.z v) (.l v))))

(set! *unchecked-math* false)
