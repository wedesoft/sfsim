(ns sfsim25.rgb
  (:refer-clojure :exclude [+ *])
  (:require [clojure.core :as c]))

(set! *unchecked-math* true)

(deftype RGB [^double r ^double g ^double b]
  Object
  (equals [this other] (and (instance? RGB other) (= r (.r other)) (= g (.g other)) (= b (.b other))))
  (toString [this] (str "(rgb " r \space g \space b ")")))

(set! *warn-on-reflection* true)

(defn rgb ^RGB [^double r ^double g ^double b]
  "Construct an RGB value"
  (RGB. r g b))

(defn r ^double [^RGB v] (.r v))
(defn g ^double [^RGB v] (.g v))
(defn b ^double [^RGB v] (.b v))

(defn +
  "Add two RGB values"
  (^RGB [^RGB a] a)
  (^RGB [^RGB a ^RGB b] (RGB. (c/+ (.r a) (.r b)) (c/+ (.g a) (.g b)) (c/+ (.b a) (.b b))))
  (^RGB [^RGB a ^RGB b & other] (apply + (+ a b) other)))

(defn * ^RGB [^double s ^RGB v]
  "Scale an RGB value"
  (RGB. (c/* s (.r v)) (c/* s (.g v)) (c/* s (.b v))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
