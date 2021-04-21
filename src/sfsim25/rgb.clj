(ns sfsim25.rgb
  (:refer-clojure :exclude [+ *])
  (:require [clojure.core :as c]))

(set! *unchecked-math* true)

(defrecord RGB [^double r ^double g ^double b])

(set! *warn-on-reflection* true)

(defn +
  "Add two RGB values"
  (^RGB [^RGB a] a)
  (^RGB [^RGB a ^RGB b] (->RGB (c/+ (:r a) (:r b)) (c/+ (:g a) (:g b)) (c/+ (:b a) (:b b))))
  (^RGB [^RGB a ^RGB b & other] (apply + (+ a b) other)))

(defn *
  "Scale an RGB value"
  ^RGB [^double s ^RGB v]
  (->RGB (c/* s (:r v)) (c/* s (:g v)) (c/* s (:b v))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
