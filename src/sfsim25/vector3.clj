(ns sfsim25.vector3
  (:refer-clojure :exclude [+ - *])
  (:require [clojure.core :as c]))

(set! *unchecked-math* true)

(deftype Vector3 [^double x ^double y ^double z]
  Object
  (equals [this other] (and (instance? Vector3 other) (= x (.x other)) (= y (.y other)) (= z (.z other))))
  (toString [this] (str "(vector3 " x \space y \space z ")")))

(set! *warn-on-reflection* true)

(defn vector3
  "Construct a 3D vector"
  ^Vector3 [^double x ^double y ^double z]
  (Vector3. x y z))

(defn x ^double [^Vector3 v] (.x v))
(defn y ^double [^Vector3 v] (.y v))
(defn z ^double [^Vector3 v] (.z v))

(defn +
  "Add 3D vectors"
  (^Vector3 [^Vector3 a] a)
  (^Vector3 [^Vector3 a ^Vector3 b] (Vector3. (c/+ (.x a) (.x b)) (c/+ (.y a) (.y b)) (c/+ (.z a) (.z b))))
  (^Vector3 [^Vector3 a ^Vector3 b & other] (apply + (+ a b) other)))

(defn -
  "Subtract 3D vectors"
  (^Vector3 [^Vector3 a] (Vector3. (c/- (.x a)) (c/- (.y a)) (c/- (.z a))))
  (^Vector3 [^Vector3 a ^Vector3 b] (Vector3. (c/- (.x a) (.x b)) (c/- (.y a) (.y b)) (c/- (.z a) (.z b)))))

(defn *
  "Scale a 3D vector"
  ^Vector3 [^double s ^Vector3 v]
  (Vector3. (c/* s (.x v)) (c/* s (.y v)) (c/* s (.z v))))

(defn norm2
  "Squared norm of vector"
  ^double [^Vector3 v]
  (c/+ (c/* (.x v) (.x v)) (c/* (.y v) (.y v)) (c/* (.z v) (.z v))))

(defn norm
  "Norm of vector"
  ^double [^Vector3 v]
  (Math/sqrt (norm2 v)))

(defn normalize
  "Normalize the vector"
  ^Vector3 [^Vector3 v]
  (* (/ 1.0 (norm v)) v))

(defn cross-product
  "Cross-product of two vectors"
  ^Vector3 [^Vector3 a ^Vector3 b]
  (Vector3. (c/- (c/* (.y a) (.z b)) (c/* (.z a) (.y b)))
            (c/- (c/* (.z a) (.x b)) (c/* (.x a) (.z b)))
            (c/- (c/* (.x a) (.y b)) (c/* (.y a) (.x b)))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
