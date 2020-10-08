(ns sfsim25.vector3)

(set! *unchecked-math* true)

(deftype Vector3 [^double x ^double y ^double z]
  Object
  (toString [this] (str "(vector3 " x \space y \space z ")")))

(set! *warn-on-reflection* true)

(defn make-vector3 ^Vector3 [^double x ^double y ^double z]
  "Construct a 3D vector"
  (Vector3. x y z))

