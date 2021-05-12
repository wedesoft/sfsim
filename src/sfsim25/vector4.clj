(ns sfsim25.vector4
  "Homogeneous 3D vectors."
  (:require [sfsim25.vector3 :refer (->Vector3)])
  (:import [sfsim25.vector3 Vector3]))

(set! *unchecked-math* true)

(defrecord Vector4 [^double x ^double y ^double z ^double l])

(defn project
  "Project homogeneous coordinate to cartesian"
  ^Vector3 [^Vector4 v]
  (let [l (:l v)]
    (->Vector3 (/ (:x v) l) (/ (:y v) l) (/ (:z v) l))))

(set! *unchecked-math* false)
