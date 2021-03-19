(ns sfsim25.vector4)

(set! *unchecked-math* true)

(deftype Vector4 [^double x ^double y ^double z ^double l]
  Object
  (equals [this other] (and (instance? Vector4 other) (= x (.x other)) (= y (.y other)) (= z (.z other)) (= l (.l other))))
  (toString [this] (str "(vector4 " x \space y \space z \space l \))))

(set! *unchecked-math* false)
