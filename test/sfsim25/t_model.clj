(ns sfsim25.t-model
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix)]
              [fastmath.matrix :refer (eye)]
              [fastmath.vector :refer (vec3)]
              [sfsim25.matrix :refer :all]
              [sfsim25.model :refer :all]))

(set! *unchecked-math* true)

(def scene (read-gltf "test/sfsim25/fixtures/model/cube.gltf"))

(fact "Root of cube model"
      (:name (:root scene)) => "Cube")

(fact "Transformation of root node"
      (:transform (:root scene)) => (roughly-matrix (transformation-matrix (eye 3) (vec3 1 3 -2)) 1e-6))

(set! *unchecked-math* false)
