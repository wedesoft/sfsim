(ns sfsim25.t-model
    (:require [midje.sweet :refer :all]
              [clojure.math :refer (to-radians)]
              [sfsim25.conftest :refer (roughly-matrix roughly-vector record-image is-image)]
              [fastmath.matrix :refer (eye mulm)]
              [fastmath.vector :refer (vec3 normalize)]
              [sfsim25.matrix :refer :all]
              [sfsim25.render :refer :all]
              [sfsim25.model :refer :all])
    (:import [org.lwjgl.glfw GLFW]))

(GLFW/glfwInit)

(def scene (read-gltf "test/sfsim25/fixtures/model/cube.gltf"))

(fact "Root of cube model"
      (:name (:root scene)) => "Cube")

(fact "Transformation of root node"
      (:transform (:root scene)) => (roughly-matrix (transformation-matrix (eye 3) (vec3 1 3 -2)) 1e-6))

(fact "Mesh indices of root node"
      (:mesh-indices (:root scene)) => [0])

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

(def vertex-cube
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 normal;
out VS_OUT
{
  vec3 normal;
} vs_out;
void main()
{
  vs_out.normal = normal;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-cube
"#version 410 core
uniform vec3 light;
uniform vec3 diffuse_color;
in VS_OUT
{
  vec3 normal;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = diffuse_color * max(0, dot(light, fs_in.normal));
}")

(fact "Render red cube"
      (offscreen-render 160 120
        (let [program      (make-program :vertex [vertex-cube] :fragment [fragment-cube])
              opengl-scene (load-scene-into-opengl program scene)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene program moved-scene)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/cube.png" 0.0))

(GLFW/glfwTerminate)
