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

(fact "Number of meshes"
      (count (:meshes scene)) => 1)

(fact "Size of index buffer"
      (count (:indices (first (:meshes scene)))) => (* 12 3))

(fact "Index buffer should be a vector"
      (:indices (first (:meshes scene))) => vector?)

(fact "Content of index buffer"
      (:indices (first (:meshes scene)))
      => [1 14 20, 1 20 7, 10 6 19, 10 19 23, 21 18 12, 21 12 15, 16 3 9, 16 9 22, 5 2 8, 5 8 11, 17 13, 0 17 0 4])

(set! *unchecked-math* false)
