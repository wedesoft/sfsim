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

(def cube (read-gltf "test/sfsim25/fixtures/model/cube.gltf"))

(fact "Root of cube model"
      (:name (:root cube)) => "Cube")

(fact "Transformation of root node"
      (:transform (:root cube)) => (roughly-matrix (transformation-matrix (eye 3) (vec3 1 3 -2)) 1e-6))

(fact "Mesh indices of root node"
      (:mesh-indices (:root cube)) => [0])

(fact "Number of meshes"
      (count (:meshes cube)) => 1)

(fact "Size of index buffer"
      (count (:indices (first (:meshes cube)))) => (* 12 3))

(fact "Index buffer should be a vector"
      (:indices (first (:meshes cube))) => vector?)

(fact "12 triangles in index buffer"
      (:indices (first (:meshes cube)))
      => [1 14 20, 1 20 7, 10 6 19, 10 19 23, 21 18 12, 21 12 15, 16 3 9, 16 9 22, 5 2 8, 5 8 11, 17 13, 0 17 0 4])

(fact "Size of vertex buffer"
      (count (:vertices (first (:meshes cube)))) => (* 24 6))

(fact "Vertex buffer should be a vector"
      (:vertices (first (:meshes cube))) => vector?)

(fact "Find vertices with normals in vertex buffer"
      (take (* 4 6) (:vertices (first (:meshes cube)))) =>
      [1.0  1.0 -1.0 0.0  0.0 -1.0,
       1.0  1.0 -1.0 0.0  1.0  0.0,
       1.0  1.0 -1.0 1.0  0.0  0.0,
       1.0 -1.0 -1.0 0.0 -1.0  0.0])

(fact "Vertex attributes of textureless cube"
      (:attributes (first (:meshes cube))) => ["vertex" 3 "normal" 3])

(fact "Material index of cube"
      (:material-index (first (:meshes cube))) => 0)

(fact "Decode materials of cube cube"
      (count (:materials cube)) => 2)

(fact "Materials of cube are returned in a vector"
      (:materials cube) => vector?)

(fact "First material has a diffuse red color"
      (:diffuse (first (:materials cube))) => (roughly-vector (vec3 0.8 0.0 0.0) 1e-6))

(fact "Second material has a diffuse white color"
      (:diffuse (second (:materials cube))) => (roughly-vector (vec3 1.0 1.0 1.0) 1e-6))

(fact "Cube has no textures"
      (count (:textures cube)) => 0)

(fact "Texture index is nil"
      (:texture-index (first (:materials cube))) => nil)

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
              opengl-scene (load-scene-into-opengl program cube)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene program moved-scene)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/cube.png" 0.0))

(def cubes (read-gltf "test/sfsim25/fixtures/model/cubes.gltf"))

(fact "Name of root node"
      (:name (:root cubes)) => "ROOT")

(fact "Children of root node"
      (count (:children (:root cubes))) => 2)

(fact "Names of child nodes"
      (set (map :name (:children (:root cubes)))) => #{"Cube1" "Cube2"})

(fact "Render red and green cube"
      (offscreen-render 160 120
        (let [program      (make-program :vertex [vertex-cube] :fragment [fragment-cube])
              opengl-scene (load-scene-into-opengl program cubes)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene program moved-scene)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/cubes.png" 0.0))

(def dice (read-gltf "test/sfsim25/fixtures/model/dice.gltf"))

(fact "Dice has one texture"
      (count (:textures dice)) => 1)

(fact "Textures are returned in a vector"
      (:textures dice) => vector?)

(facts "Size of texture"
       (:width  (first (:textures dice))) => 64
       (:height (first (:textures dice))) => 64)

(fact "Texture index for dice material is zero"
      (:texture-index (first (:materials dice))) => 0)

(fact "Size of vertex buffer with texture coordinates"
      (count (:vertices (first (:meshes dice)))) => (* 24 8))

(fact "Vertex attributes of textured cube"
      (:attributes (first (:meshes dice))) => ["vertex" 3 "normal" 3 "texcoord" 2])

(fact "Find vertices with normals and texture coordinates in vertex buffer"
      (take (* 4 8) (:vertices (first (:meshes dice)))) =>
      [1.0  1.0 -1.0 0.0  0.0 -1.0 0.625 0.5,
       1.0  1.0 -1.0 0.0  1.0 -0.0 0.625 0.5,
       1.0  1.0 -1.0 1.0  0.0 -0.0 0.625 0.5,
       1.0 -1.0 -1.0 0.0 -1.0 -0.0 0.375 0.5])

(def vertex-dice
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 normal;
in vec2 texcoord;
out VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} vs_out;
void main()
{
  vs_out.normal = normal;
  vs_out.texcoord = texcoord;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-dice
"#version 410 core
uniform vec3 light;
uniform sampler2D tex;
in VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 color = texture(tex, fs_in.texcoord).rgb;
  fragColor = color * max(0, dot(light, fs_in.normal));
}")

(fact "Render textured cube"
      (offscreen-render 160 120
        (let [program      (make-program :vertex [vertex-dice] :fragment [fragment-dice])
              opengl-scene (load-scene-into-opengl program dice)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene program moved-scene)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/dice.png" 0.0))

(GLFW/glfwTerminate)
