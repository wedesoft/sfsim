(ns sfsim25.t-model
    (:require [midje.sweet :refer :all]
              [clojure.math :refer (to-radians)]
              [sfsim25.conftest :refer (roughly-matrix roughly-vector roughly-quaternion record-image is-image)]
              [fastmath.matrix :refer (eye mulm)]
              [fastmath.vector :refer (vec3 normalize)]
              [sfsim25.matrix :refer :all]
              [sfsim25.render :refer :all]
              [sfsim25.model :refer :all]
              [sfsim25.quaternion :refer (->Quaternion)])
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

(facts "Texture indices are nil"
      (:color-texture-index (first (:materials cube))) => nil
      (:normal-texture-index (first (:materials cube))) => nil)

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
  vs_out.normal = mat3(transform) * normal;
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
              opengl-scene (load-scene-into-opengl (constantly program) cube)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform diffuse]}]
                            (uniform-matrix4 program "transform" transform)
                            (uniform-vector3 program "diffuse_color" diffuse)))
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
              opengl-scene (load-scene-into-opengl (constantly program) cubes)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform diffuse]}]
                            (uniform-matrix4 program "transform" transform)
                            (uniform-vector3 program "diffuse_color" diffuse)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/cubes.png" 0.01))

(def dice (read-gltf "test/sfsim25/fixtures/model/dice.gltf"))

(fact "Dice has one texture"
      (count (:textures dice)) => 1)

(fact "Textures are returned in a vector"
      (:textures dice) => vector?)

(facts "Size of texture"
       (:width  (first (:textures dice))) => 64
       (:height (first (:textures dice))) => 64)

(fact "Color texture index for dice material is zero"
      (:color-texture-index (first (:materials dice))) => 0)

(fact "Normal texture index for dice material is nil"
      (:normal-texture-index (first (:materials dice))) => nil)

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
  vs_out.normal = mat3(transform) * normal;
  vs_out.texcoord = texcoord;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-dice
"#version 410 core
uniform vec3 light;
uniform sampler2D colors;
in VS_OUT
{
  vec3 normal;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 color = texture(colors, fs_in.texcoord).rgb;
  fragColor = color * max(0, dot(light, fs_in.normal));
}")

(fact "Render textured cube"
      (offscreen-render 160 120
        (let [program      (make-program :vertex [vertex-dice] :fragment [fragment-dice])
              opengl-scene (load-scene-into-opengl (constantly program) dice)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (uniform-sampler program "colors" 0)
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform colors]}]
                            (uniform-matrix4 program "transform" transform)
                            (use-textures colors)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/dice.png" 0.01))

(def bricks (read-gltf "test/sfsim25/fixtures/model/bricks.gltf"))

(fact "Color texture index for bricks material is zero"
      (:color-texture-index (first (:materials bricks))) => 0)

(fact "Normal texture index for bricks material is one"
      (:normal-texture-index (first (:materials bricks))) => 1)

(def vertex-bricks
"#version 410 core
uniform mat4 projection;
uniform mat4 transform;
in vec3 vertex;
in vec3 tangent;
in vec3 bitangent;
in vec3 normal;
in vec2 texcoord;
out VS_OUT
{
  mat3 surface;
  vec2 texcoord;
} vs_out;
void main()
{
  vs_out.surface = mat3(transform) * mat3(tangent, bitangent, normal);
  vs_out.texcoord = texcoord;
  gl_Position = projection * transform * vec4(vertex, 1);
}")

(def fragment-bricks
"#version 410 core
uniform vec3 light;
uniform sampler2D colors;
uniform sampler2D normals;
in VS_OUT
{
  mat3 surface;
  vec2 texcoord;
} fs_in;
out vec3 fragColor;
void main()
{
  vec3 normal = 2.0 * texture(normals, fs_in.texcoord).xyz - 1.0;
  vec3 color = texture(colors, fs_in.texcoord).rgb;
  float brightness = 0.2 + 0.8 * max(0, dot(light, fs_in.surface * normal));
  fragColor = color * brightness;
}")

(fact "Render brick wall"
      (offscreen-render 160 120
        (let [program      (make-program :vertex [vertex-bricks] :fragment [fragment-bricks])
              opengl-scene (load-scene-into-opengl (constantly program) bricks)
              transform    (transformation-matrix (rotation-x 1.8) (vec3 0 0 -3))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 0 -3 1)))
          (uniform-sampler program "colors" 0)
          (uniform-sampler program "normals" 1)
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform colors normals]}]
                            (uniform-matrix4 program "transform" transform)
                            (use-textures colors normals)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim25/fixtures/model/bricks.png" 0.01))

(defmulti render-model (fn [{:keys [color-texture-index]}] (type color-texture-index)))

(defmethod render-model nil [{:keys [program transform diffuse]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (uniform-vector3 program "diffuse_color" diffuse))

(defmethod render-model Number [{:keys [program transform colors]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (use-textures colors))

(def cube-and-dice (read-gltf "test/sfsim25/fixtures/model/cube-and-dice.gltf"))

(fact "Render uniformly colored cube and textured cube"
      (offscreen-render 160 120
        (let [program-cube      (make-program :vertex [vertex-cube] :fragment [fragment-cube])
              program-dice      (make-program :vertex [vertex-dice] :fragment [fragment-dice])
              program-selection (fn [material] (if (:color-texture-index material) program-dice program-cube))
              opengl-scene      (load-scene-into-opengl program-selection cube-and-dice)
              transform         (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7))
              moved-scene       (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0)
          (doseq [program [program-cube program-dice]]
                 (use-program program)
                 (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10 (to-radians 60)))
                 (uniform-vector3 program "light" (normalize (vec3 1 2 3))))
          (uniform-sampler program-dice "colors" 0)
          (render-scene program-selection moved-scene render-model)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program-dice)
          (destroy-program program-cube))) => (is-image "test/sfsim25/fixtures/model/cube-and-dice.png" 0.01))

(def translation (read-gltf "test/sfsim25/fixtures/model/translation.gltf"))

(fact "Number of animations"
      (count (:animations translation)) => 1)

(def translation-animation (first (:animations translation)))

(fact "Name of action for animation"
      (:name translation-animation) => "CubeAction")

(fact "Duration of animation in seconds"
      (:duration translation-animation) => (roughly (/ 100.0 24.0) 1e-6))

(fact "Number of channels of animation"
      (count (:channels translation-animation)) => 1)

(def translation-channel (first (:channels (first (:animations translation)))))

(fact "Target object of animation channel"
      (:node-name translation-channel) => "Cube")

(facts "Number of key frames for position, rotation, and scale"
       (count (:position-keys translation-channel)) => 101
       (count (:rotation-keys translation-channel)) => 1
       (count (:scaling-keys translation-channel)) => 1)

(facts "Get time stamps from different position key frames"
       (:time (first (:position-keys translation-channel))) => (roughly 0.0 1e-6)
       (:time (second (:position-keys translation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:time (last (:position-keys translation-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get position from different position key frames"
       (:position (first (:position-keys translation-channel))) => (roughly-vector (vec3 2 0 0) 1e-6)
       (:position (last (:position-keys translation-channel))) => (roughly-vector (vec3 5 0 0) 1e-6))

(def rotation (read-gltf "test/sfsim25/fixtures/model/rotation.gltf"))

(def rotation-animation (first (:animations rotation)))
(def rotation-channel (first (:channels (first (:animations rotation)))))

(facts "Get time stamps from different rotation key frames"
       (:time (first (:rotation-keys rotation-channel))) => (roughly 0.0 1e-6)
       (:time (second (:rotation-keys rotation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:time (last (:rotation-keys rotation-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get rotation from different rotation key frames"
       (:rotation (first (:rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6)
       (:rotation (last (:rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 0 0 -1 0) 1e-6))

(def scaling (read-gltf "test/sfsim25/fixtures/model/scaling.gltf"))

(def scaling-animation (first (:animations scaling)))
(def scaling-channel (first (:channels (first (:animations scaling)))))

(facts "Get time stamps from different scaling key frames"
       (:time (first (:scaling-keys scaling-channel))) => (roughly 0.0 1e-6)
       (:time (second (:scaling-keys scaling-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:time (last (:scaling-keys scaling-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get scale from different scaling key frames"
       (:scaling (first (:scaling-keys scaling-channel))) => (roughly-vector (vec3 2 1 1) 1e-6)
       (:scaling (last (:scaling-keys scaling-channel))) => (roughly-vector (vec3 5 1 1) 1e-6))

(GLFW/glfwTerminate)
