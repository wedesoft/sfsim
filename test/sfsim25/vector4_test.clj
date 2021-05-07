(ns sfsim25.vector4-test
  (:require [midje.sweet :refer :all]
            [sfsim25.vector4 :refer :all]
            [sfsim25.vector3 :refer (->Vector3)]))

(fact "Equality of 4D vectors"
  (->Vector4 2 3 5 7) => (->Vector4 2 3 5 7))

(fact "Convert vector to sequence"
  (vals (->Vector4 2 3 5 7)) => [2.0 3.0 5.0 7.0])

(fact "Project homogeneous coordinate to cartesian"
  (project (->Vector4 4 6 10 2)) => (->Vector3 2 3 5))
