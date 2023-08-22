(ns sfsim25.t-model
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix roughly-vector)]
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

(fact "12 triangles in index buffer"
      (:indices (first (:meshes scene)))
      => [1 14 20, 1 20 7, 10 6 19, 10 19 23, 21 18 12, 21 12 15, 16 3 9, 16 9 22, 5 2 8, 5 8 11, 17 13, 0 17 0 4])

(fact "Size of vertex buffer"
      (count (:vertices (first (:meshes scene)))) => (* 24 6))

(fact "Vertex buffer should be a vector"
      (:vertices (first (:meshes scene))) => vector?)

(fact "24 vertices with normals in vertex buffer"
      (take (* 4 6) (:vertices (first (:meshes scene)))) =>
      [1.0  1.0 -1.0 0.0  0.0 -1.0,
       1.0  1.0 -1.0 0.0  1.0  0.0,
       1.0  1.0 -1.0 1.0  0.0  0.0,
       1.0 -1.0 -1.0 0.0 -1.0  0.0])

(fact "Vertex attributes of textureless cube"
      (:attributes (first (:meshes scene))) => ["vertex" 3 "normal" 3])

(fact "Material index of cube"
      (:material-index (first (:meshes scene))) => 0)

(fact "Decode materials of cube scene"
      (count (:materials scene)) => 2)

(fact "Materials of cube are returned in a vector"
      (:materials scene) => vector?)

(fact "First material has a diffuse red color"
      (:diffuse (first (:materials scene))) => (roughly-vector (vec3 0.8 0.0 0.0) 1e-6))

(fact "Second material has a diffuse white color"
      (:diffuse (second (:materials scene))) => (roughly-vector (vec3 1.0 1.0 1.0) 1e-6))

(set! *unchecked-math* false)
