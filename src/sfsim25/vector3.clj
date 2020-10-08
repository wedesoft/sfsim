(ns sfsim25.vector3)

(set! *unchecked-math* true)

(deftype Vector3 [^double x ^double y ^double z]
  Object
  (equals [this other] (and (instance? Vector3 other) (= x (.x other)) (= y (.y other)) (= z (.z other))))
  (toString [this] (str "(vector3 " x \space y \space z ")")))

(set! *warn-on-reflection* true)

(defn vector3 ^Vector3 [^double x ^double y ^double z]
  "Construct a 3D vector"
  (Vector3. x y z))

(defn x ^double [^Vector3 v] (.x v))
(defn y ^double [^Vector3 v] (.y v))
(defn z ^double [^Vector3 v] (.z v))

(defn norm ^double [^Vector3 v]
  "Norm of vector"
  (Math/sqrt (+ (* (.x v) (.x v)) (* (.y v) (.y v)) (* (.z v) (.z v)))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
