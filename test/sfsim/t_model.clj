(ns sfsim.t-model
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [clojure.math :refer (to-radians sqrt PI)]
              [comb.template :as template]
              [sfsim.conftest :refer (roughly-matrix roughly-vector roughly-quaternion is-image)]
              [fastmath.matrix :refer (eye mulm inverse mat4x4)]
              [fastmath.vector :refer (vec3 normalize)]
              [sfsim.matrix :refer :all]
              [sfsim.texture :refer :all]
              [sfsim.render :refer :all]
              [sfsim.atmosphere :as atmosphere]
              [sfsim.clouds :as clouds]
              [sfsim.shaders :as shaders]
              [sfsim.model :refer :all :as model]
              [sfsim.quaternion :refer (->Quaternion)])
    (:import [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.model]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def cube (read-gltf "test/sfsim/fixtures/model/cube.gltf"))

(fact "Root of cube model"
      (:sfsim.model/name (:sfsim.model/root cube)) => "Cube")

(fact "Transformation of root node"
      (:sfsim.model/transform (:sfsim.model/root cube)) => (roughly-matrix (transformation-matrix (eye 3) (vec3 1 3 -2)) 1e-6))

(fact "Mesh indices of root node"
      (:sfsim.model/mesh-indices (:sfsim.model/root cube)) => [0])

(fact "Number of meshes"
      (count (:sfsim.model/meshes cube)) => 1)

(fact "Size of index buffer"
      (count (:sfsim.model/indices (first (:sfsim.model/meshes cube)))) => (* 12 3))

(fact "Index buffer should be a vector"
      (:sfsim.model/indices (first (:sfsim.model/meshes cube))) => vector?)

(fact "12 triangles in index buffer"
      (:sfsim.model/indices (first (:sfsim.model/meshes cube)))
      => [0 1 2, 0 2 3, 4 5 6, 4 6 7, 8 9 10, 8 10 11, 12 13 14, 12 14 15, 16 17 18, 16 18 19, 20 21 22, 20 22 23])

(fact "Size of vertex buffer"
      (count (:sfsim.model/vertices (first (:sfsim.model/meshes cube)))) => (* 24 6))

(fact "Vertex buffer should be a vector"
      (:sfsim.model/vertices (first (:sfsim.model/meshes cube))) => vector?)

(fact "Find vertices with normals in vertex buffer"
      (take (* 4 6) (:sfsim.model/vertices (first (:sfsim.model/meshes cube)))) =>
      [1.0  1.0 -1.0 0.0  1.0  0.0,
      -1.0  1.0 -1.0 0.0  1.0  0.0,
      -1.0  1.0  1.0 0.0  1.0  0.0,
       1.0  1.0  1.0 0.0  1.0  0.0])

(fact "Vertex attributes of textureless cube"
      (:sfsim.model/attributes (first (:sfsim.model/meshes cube))) => ["vertex" 3 "normal" 3])

(fact "Material index of cube"
      (:sfsim.model/material-index (first (:sfsim.model/meshes cube))) => 0)

(fact "Decode materials of cube cube"
      (count (:sfsim.model/materials cube)) => 2)

(fact "Materials of cube are returned in a vector"
      (:sfsim.model/materials cube) => vector?)

(fact "First material has a diffuse red color"
      (:sfsim.model/diffuse (first (:sfsim.model/materials cube))) => (roughly-vector (vec3 0.8 0.0 0.0) 1e-6))

(fact "Second material has a diffuse white color"
      (:sfsim.model/diffuse (second (:sfsim.model/materials cube))) => (roughly-vector (vec3 1.0 1.0 1.0) 1e-6))

(fact "Cube has no textures"
      (count (:sfsim.model/textures cube)) => 0)

(facts "Texture indices are nil"
      (:sfsim.model/color-texture-index (first (:sfsim.model/materials cube))) => nil
      (:sfsim.model/normal-texture-index (first (:sfsim.model/materials cube))) => nil)

(def vertex-cube
"#version 410 core
uniform mat4 projection;
uniform mat4 object_to_camera;
in vec3 vertex;
in vec3 normal;
out VS_OUT
{
  vec3 normal;
} vs_out;
void main()
{
  vs_out.normal = mat3(object_to_camera) * normal;
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
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
        (let [program         (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              opengl-scene    (load-scene-into-opengl (constantly program) cube)
              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5)))
              moved-scene     (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] (eye 4))]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) {:sfsim.render/camera-to-world camera-to-world} moved-scene
                        (fn [{:sfsim.model/keys [program camera-to-world transform diffuse]}]
                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                            (uniform-vector3 program "diffuse_color" diffuse)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/cube.png" 0.0))

(def cubes (read-gltf "test/sfsim/fixtures/model/cubes.gltf"))

(fact "Name of root node"
      (:sfsim.model/name (:sfsim.model/root cubes)) => "ROOT")

(fact "Children of root node"
      (count (:sfsim.model/children (:sfsim.model/root cubes))) => 2)

(fact "Names of child nodes"
      (set (map :sfsim.model/name (:sfsim.model/children (:sfsim.model/root cubes)))) => #{"Cube1" "Cube2"})

(fact "Render red and green cube"
      (offscreen-render 160 120
        (let [program         (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              opengl-scene    (load-scene-into-opengl (constantly program) cubes)
              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7)))
              moved-scene     (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] (eye 4))]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) {:sfsim.render/camera-to-world camera-to-world} moved-scene
                        (fn [{:sfsim.model/keys [program camera-to-world transform diffuse]}]
                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                            (uniform-vector3 program "diffuse_color" diffuse)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/cubes.png" 0.01))

(def dice (read-gltf "test/sfsim/fixtures/model/dice.gltf"))

(fact "Dice has one texture"
      (count (:sfsim.model/textures dice)) => 1)

(fact "Textures are returned in a vector"
      (:sfsim.model/textures dice) => vector?)

(facts "Size of texture"
       (:sfsim.image/width  (first (:sfsim.model/textures dice))) => 64
       (:sfsim.image/height (first (:sfsim.model/textures dice))) => 64)

(fact "Color texture index for dice material is zero"
      (:sfsim.model/color-texture-index (first (:sfsim.model/materials dice))) => 0)

(fact "Normal texture index for dice material is nil"
      (:sfsim.model/normal-texture-index (first (:sfsim.model/materials dice))) => nil)

(fact "Size of vertex buffer with texture coordinates"
      (count (:sfsim.model/vertices (first (:sfsim.model/meshes dice)))) => (* 24 8))

(fact "Vertex attributes of textured cube"
      (:sfsim.model/attributes (first (:sfsim.model/meshes dice))) => ["vertex" 3 "normal" 3 "texcoord" 2])

(fact "Find vertices with normals and texture coordinates in vertex buffer"
      (take (* 4 8) (:sfsim.model/vertices (first (:sfsim.model/meshes dice)))) =>
      [1.0  1.0 -1.0 0.0  1.0  0.0 0.625 0.5,
      -1.0  1.0 -1.0 0.0  1.0  0.0 0.875 0.5,
      -1.0  1.0  1.0 0.0  1.0  0.0 0.875 0.25,
       1.0  1.0  1.0 0.0  1.0  0.0 0.625 0.25])

(def vertex-dice
"#version 410 core
uniform mat4 projection;
uniform mat4 object_to_camera;
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
  vs_out.normal = mat3(object_to_camera) * normal;
  vs_out.texcoord = texcoord;
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
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
        (let [program         (make-program :sfsim.render/vertex [vertex-dice] :sfsim.render/fragment [fragment-dice])
              opengl-scene    (load-scene-into-opengl (constantly program) dice)
              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5)))
              moved-scene     (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] (eye 4))]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (uniform-sampler program "colors" 0)
          (render-scene (constantly program) {:sfsim.render/camera-to-world camera-to-world} moved-scene
                        (fn [{:sfsim.model/keys [program camera-to-world transform colors]}]
                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                            (use-textures {0 colors})))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/dice.png" 0.01))

(def bricks (read-gltf "test/sfsim/fixtures/model/bricks.gltf"))

(fact "Color texture index for bricks material is zero"
      (:sfsim.model/color-texture-index (first (:sfsim.model/materials bricks))) => 0)

(fact "Normal texture index for bricks material is one"
      (:sfsim.model/normal-texture-index (first (:sfsim.model/materials bricks))) => 1)

(def vertex-bricks
"#version 410 core
uniform mat4 projection;
uniform mat4 object_to_camera;
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
  vs_out.surface = mat3(object_to_camera) * mat3(tangent, bitangent, normal);
  vs_out.texcoord = texcoord;
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
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
        (let [program         (make-program :sfsim.render/vertex [vertex-bricks] :sfsim.render/fragment [fragment-bricks])
              opengl-scene    (load-scene-into-opengl (constantly program) bricks)
              camera-to-world (inverse (transformation-matrix (rotation-x 1.8) (vec3 0 0 -3)))
              moved-scene     (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] (eye 4))]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 0 -3 1)))
          (uniform-sampler program "colors" 0)
          (uniform-sampler program "normals" 1)
          (render-scene (constantly program) {:sfsim.render/camera-to-world camera-to-world} moved-scene
                        (fn [{:sfsim.model/keys [program camera-to-world transform colors normals]}]
                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                            (use-textures {0 colors 1 normals})))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/bricks.png" 0.01))

(defn cube-material-type [{:sfsim.model/keys [color-texture-index]}]
  (if color-texture-index
    :textured
    :colored))

(defmulti render-cube cube-material-type)

(defmethod render-cube :colored [{:sfsim.model/keys [program camera-to-world transform diffuse]}]
  (use-program program)
  (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
  (uniform-vector3 program "diffuse_color" diffuse))

(defmethod render-cube :textured [{:sfsim.model/keys [program camera-to-world transform colors]}]
  (use-program program)
  (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
  (use-textures {0 colors}))

(def cube-and-dice (read-gltf "test/sfsim/fixtures/model/cube-and-dice.gltf"))

(fact "Render uniformly colored cube and textured cube"
      (offscreen-render 160 120
        (let [program-cube      (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              program-dice      (make-program :sfsim.render/vertex [vertex-dice] :sfsim.render/fragment [fragment-dice])
              program-selection (comp {:colored program-cube :textured program-dice} cube-material-type)
              opengl-scene      (load-scene-into-opengl program-selection cube-and-dice)
              camera-to-world   (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7)))
              moved-scene       (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] (eye 4))]
          (clear (vec3 0 0 0) 0.0)
          (doseq [program [program-cube program-dice]]
                 (use-program program)
                 (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                 (uniform-vector3 program "light" (normalize (vec3 1 2 3))))
          (uniform-sampler program-dice "colors" 0)
          (render-scene program-selection {:sfsim.render/camera-to-world camera-to-world} moved-scene render-cube)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program-dice)
          (destroy-program program-cube))) => (is-image "test/sfsim/fixtures/model/cube-and-dice.png" 0.01))

(def translation (read-gltf "test/sfsim/fixtures/model/translation.gltf"))

(fact "Number of animations"
      (count (:sfsim.model/animations translation)) => 1)

(def translation-animation ((:sfsim.model/animations translation) "CubeAction"))

(fact "Duration of animation in seconds"
      (:sfsim.model/duration translation-animation) => (roughly (/ 100.0 24.0) 1e-6))

(fact "Number of channels of animation"
      (count (:sfsim.model/channels translation-animation)) => 1)

(def translation-channel ((:sfsim.model/channels translation-animation) "Cube"))

(facts "Number of key frames for position, rotation, and scale"
       (count (:sfsim.model/position-keys translation-channel)) => 101
       (count (:sfsim.model/rotation-keys translation-channel)) => 1
       (count (:sfsim.model/scaling-keys translation-channel)) => 1)

(facts "Get time stamps from different position key frames"
       (:sfsim.model/time (first (:sfsim.model/position-keys translation-channel))) => (roughly 0.0 1e-6)
       (:sfsim.model/time (second (:sfsim.model/position-keys translation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:sfsim.model/time (last (:sfsim.model/position-keys translation-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get position from different position key frames"
       (:sfsim.model/position (first (:sfsim.model/position-keys translation-channel))) => (roughly-vector (vec3 2 0 0) 1e-6)
       (:sfsim.model/position (last (:sfsim.model/position-keys translation-channel))) => (roughly-vector (vec3 5 0 0) 1e-6))

(def rotation (read-gltf "test/sfsim/fixtures/model/rotation.gltf"))

(def rotation-animation ((:sfsim.model/animations rotation) "CubeAction"))
(def rotation-channel ((:sfsim.model/channels rotation-animation) "Cube"))

(facts "Get time stamps from different rotation key frames"
       (:sfsim.model/time (first (:sfsim.model/rotation-keys rotation-channel))) => (roughly 0.0 1e-6)
       (:sfsim.model/time (second (:sfsim.model/rotation-keys rotation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:sfsim.model/time (last (:sfsim.model/rotation-keys rotation-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get rotation from different rotation key frames"
       (:sfsim.model/rotation (first (:sfsim.model/rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6)
       (:sfsim.model/rotation (last (:sfsim.model/rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 0 0 -1 0) 1e-6))

(def scaling (read-gltf "test/sfsim/fixtures/model/scaling.gltf"))

(def scaling-animation ((:sfsim.model/animations scaling) "CubeAction"))
(def scaling-channel ((:sfsim.model/channels scaling-animation) "Cube"))

(facts "Get time stamps from different scaling key frames"
       (:sfsim.model/time (first (:sfsim.model/scaling-keys scaling-channel))) => (roughly 0.0 1e-6)
       (:sfsim.model/time (second (:sfsim.model/scaling-keys scaling-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:sfsim.model/time (last (:sfsim.model/scaling-keys scaling-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get scale from different scaling key frames"
       (:sfsim.model/scaling (first (:sfsim.model/scaling-keys scaling-channel))) => (roughly-vector (vec3 2 1 1) 1e-6)
       (:sfsim.model/scaling (last (:sfsim.model/scaling-keys scaling-channel))) => (roughly-vector (vec3 5 1 1) 1e-6))

(facts "Interpolate between position frames assuming constant sampling interval"
       (interpolate-position [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 0 0 0)}] 0.0) => (roughly-vector (vec3 0 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 2 3 5)}] 0.0) => (roughly-vector (vec3 2 3 5) 1e-6)
       (interpolate-position [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 1.0 :sfsim.model/position (vec3 3 0 0)}] 0.0)
       => (roughly-vector (vec3 2 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 1.0 :sfsim.model/position (vec3 3 0 0)}] 1.0)
       => (roughly-vector (vec3 3 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 1.0 :sfsim.model/position (vec3 3 0 0)}] 0.5)
       => (roughly-vector (vec3 2.5 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 3.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 3.5 :sfsim.model/position (vec3 3 0 0)}] 3.25)
       => (roughly-vector (vec3 2.5 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 1.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 2.0 :sfsim.model/position (vec3 3 0 0)}] 1.25)
       => (roughly-vector (vec3 2.25 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 1.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 2.0 :sfsim.model/position (vec3 3 0 0)}
                              {:sfsim.model/time 3.0 :sfsim.model/position (vec3 4 0 0)}] 2.25)
       => (roughly-vector (vec3 3.25 0 0) 1e-6)
       (interpolate-position [{:sfsim.model/time 1.0 :sfsim.model/position (vec3 2 0 0)}
                              {:sfsim.model/time 2.0 :sfsim.model/position (vec3 3 0 0)}
                              {:sfsim.model/time 3.0 :sfsim.model/position (vec3 4 0 0)}] 1.25)
       => (roughly-vector (vec3 2.25 0 0) 1e-6))

(fact "Interpolate between scaling frames assuming constant sampling interval"
      (interpolate-scaling [{:sfsim.model/time 0.0 :sfsim.model/scaling (vec3 2 0 0)}
                            {:sfsim.model/time 1.0 :sfsim.model/scaling (vec3 3 0 0)}] 0.25)
      => (roughly-vector (vec3 2.25 0 0) 1e-6))

(fact "Interpolate between rotation frames assuming constant sampling interval"
      (interpolate-rotation [{:sfsim.model/time 0.0 :sfsim.model/rotation (->Quaternion 2 0 0 0)}
                             {:sfsim.model/time 1.0 :sfsim.model/rotation (->Quaternion 3 0 0 0)}] 0.25)
      => (roughly-quaternion (->Quaternion 2.25 0 0 0) 1e-6))

(fact "Handle negative quaternion with same rotation"
      (interpolate-rotation [{:sfsim.model/time 0.0 :sfsim.model/rotation (->Quaternion 1 0 0 0)}
                             {:sfsim.model/time 1.0 :sfsim.model/rotation (->Quaternion -1 0 0 0)}] 0.5)
      => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6))

(facts "Create key frame for given channel"
       (interpolate-transformation {:sfsim.model/position-keys [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 2 3 5)}]
                                    :sfsim.model/rotation-keys [{:sfsim.model/time 0.0 :sfsim.model/rotation (->Quaternion 1 0 0 0)}]
                                    :sfsim.model/scaling-keys [{:sfsim.model/time 0.0 :sfsim.model/scaling (vec3 1 1 1)}]} 0.0)
       => (roughly-matrix (transformation-matrix (eye 3) (vec3 2 3 5)) 1e-6)
       (interpolate-transformation {:sfsim.model/position-keys [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 0 0 0)}]
                                    :sfsim.model/rotation-keys [{:sfsim.model/time 0.0 :sfsim.model/rotation (->Quaternion 0 1 0 0)}]
                                    :sfsim.model/scaling-keys [{:sfsim.model/time 0.0 :sfsim.model/scaling (vec3 1 1 1)}]} 0.0)
       => (roughly-matrix (transformation-matrix (rotation-x PI) (vec3 0 0 0)) 1e-6)
       (interpolate-transformation {:sfsim.model/position-keys [{:sfsim.model/time 0.0 :sfsim.model/position (vec3 0 0 0)}]
                                    :sfsim.model/rotation-keys [{:sfsim.model/time 0.0 :sfsim.model/rotation (->Quaternion (sqrt 0.5) (sqrt 0.5) 0 0)}]
                                    :sfsim.model/scaling-keys [{:sfsim.model/time 0.0 :sfsim.model/scaling (vec3 2 3 5)}]} 0.0)
       => (roughly-matrix (mat4x4 2 0 0 0, 0 0 -5 0, 0 3 0 0, 0 0 0 1) 1e-6))

(facts "Determine updates for model"
       (animations-frame {:sfsim.model/animations {}} {}) => {}
       (with-redefs [model/interpolate-transformation
                     (fn [channel t] (facts channel => :mock-channel-data t => 1.0) :mock-transform)]
         (animations-frame {:sfsim.model/animations {"Animation" {:sfsim.model/channels {"Object" :mock-channel-data}}}} {"Animation" 1.0}))
       => {"Object" :mock-transform})

(facts "Apply transformation updates to model"
       (apply-transforms {:sfsim.model/root {:sfsim.model/name "Cube" :sfsim.model/transform :mock :sfsim.model/children []}} {})
       => {:sfsim.model/root {:sfsim.model/name "Cube" :sfsim.model/transform :mock :sfsim.model/children []}}
       (apply-transforms {:sfsim.model/root {:sfsim.model/name "Cube" :sfsim.model/transform :mock :sfsim.model/children []}}
                         {"Cube" :mock-changed})
       => {:sfsim.model/root {:sfsim.model/name "Cube" :sfsim.model/transform :mock-changed :sfsim.model/children []}}
       (apply-transforms {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/transform :mock :sfsim.model/children
                                             [{:sfsim.model/name "Cube" :sfsim.model/transform :mock :sfsim.model/children []}]}}
                         {"Cube" :mock-changed})
       => {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/transform :mock :sfsim.model/children
                              [{:sfsim.model/name "Cube" :sfsim.model/transform :mock-changed :sfsim.model/children []}]}})

(def bump (read-gltf "test/sfsim/fixtures/model/bump.gltf"))

(def cloud-planet-mock
"#version 410 core
uniform vec3 origin;
vec4 cloud_planet(vec3 point)
{
  float dist = distance(origin, point);
  float transparency = exp(-dist / 10.0);
  return vec4(0.5, 0.5, 0.5, 1 - transparency);
}")

(def transmittance-outer-mock
"#version 410 core
uniform float transmittance;
vec3 transmittance_outer(vec3 point, vec3 direction)
{
  return vec3(transmittance, transmittance, transmittance);
}")

(def above-horizon-mock
"#version 410 core
uniform int above;
bool is_above_horizon(vec3 point, vec3 direction)
{
  return above > 0;
}")

(def surface-radiance-mock
"#version 410 core
uniform float ambient;
vec3 surface_radiance_function(vec3 point, vec3 light_direction)
{
  return vec3(ambient, ambient, ambient);
}")

(def overall-shadow-mock
"#version 410 core
uniform float shadow;
float overall_shadow(vec4 point)
{
  return shadow;
}")

(def ray-sphere-mock
"#version 410 core
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  return vec2(0, 10);
}")

(def attenuation-mock
"#version 410 core
uniform float attenuation;
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return incoming * attenuation;
}")

(def model-shader-mocks [cloud-planet-mock transmittance-outer-mock above-horizon-mock surface-radiance-mock overall-shadow-mock
                         ray-sphere-mock attenuation-mock shaders/phong (last atmosphere/attenuation-point)
                         (last (clouds/direct-light 3))])

(tabular "Render red cube with fog and atmosphere"
  (with-redefs [model/fragment-model (fn [textured bump num-steps perlin-octaves cloud-octaves]
                                         (conj model-shader-mocks (template/eval (slurp "resources/shaders/model/fragment.glsl")
                                                                                 {:textured textured :bump bump})))
                model/setup-model-static-uniforms (fn [program data]
                                                      (use-program program)
                                                      (uniform-float program "albedo" 3.14159265358)
                                                      (uniform-float program "amplification" 1.0)
                                                      (uniform-float program "specular" 1.0)
                                                      (uniform-vector3 program "origin" (vec3 0 0 5))
                                                      (uniform-matrix4 program "projection"
                                                                       (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                                                      (uniform-vector3 program "light_direction" (normalize (vec3 1 2 3)))
                                                      (uniform-float program "transmittance" ?transmittance)
                                                      (uniform-float program "ambient" ?ambient)
                                                      (uniform-float program "shadow" ?shadow)
                                                      (uniform-float program "attenuation" ?attenuation)
                                                      (uniform-float program "radius" 1000.0)
                                                      (uniform-float program "max_height" 100.0)
                                                      (uniform-int program "above" ?above))]
    (fact
      (offscreen-render 160 120
                        (let [data             {:sfsim.opacity/data {:sfsim.opacity/num-steps 3}
                                                :sfsim.clouds/data {:sfsim.clouds/perlin-octaves []
                                                                    :sfsim.clouds/cloud-octaves []}}
                              renderer         (make-model-renderer data)
                              opengl-scene     (load-scene-into-opengl (comp renderer material-type) ?model)
                              camera-to-world  (transformation-matrix (eye 3) (vec3 1 0 0))
                              object-to-world  (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 1 0 -5))
                              moved-scene      (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] object-to-world)]
                          (clear (vec3 0.5 0.5 0.5) 0.0)
                          (use-program (:sfsim.model/program-textured-flat renderer))
                          (uniform-sampler (:sfsim.model/program-textured-flat renderer) "colors" 0)
                          (use-program (:sfsim.model/program-colored-bump renderer))
                          (uniform-sampler (:sfsim.model/program-colored-bump renderer) "normals" 0)
                          (use-program (:sfsim.model/program-textured-bump renderer))
                          (uniform-sampler (:sfsim.model/program-textured-bump renderer) "colors" 0)
                          (uniform-sampler (:sfsim.model/program-textured-bump renderer) "normals" 1)
                          (render-scene (comp renderer material-type) {:sfsim.render/camera-to-world camera-to-world} moved-scene
                                        render-mesh)
                          (unload-scene-from-opengl opengl-scene)
                          (destroy-model-renderer renderer))) => (is-image (str "test/sfsim/fixtures/model/" ?result) 0.01)))
  ?model ?transmittance ?above ?ambient ?shadow ?attenuation ?result
  cube   1.0            1      0.0      1.0     1.0          "cube-fog.png"
  cube   0.5            1      0.0      1.0     1.0          "cube-dark.png"
  cube   1.0            0      0.0      1.0     1.0          "cube-sunset.png"
  cube   1.0            0      1.0      1.0     1.0          "cube-ambient.png"
  cube   1.0            1      0.0      0.5     1.0          "cube-shadow.png"
  cube   1.0            1      0.0      1.0     0.5          "cube-attenuation.png"
  dice   1.0            1      0.0      1.0     1.0          "dice-fog.png"
  dice   0.5            1      0.0      1.0     1.0          "dice-dark.png"
  dice   1.0            0      0.0      1.0     1.0          "dice-sunset.png"
  dice   1.0            0      1.0      1.0     1.0          "dice-ambient.png"
  dice   1.0            1      0.0      0.5     1.0          "dice-shadow.png"
  dice   1.0            1      0.0      1.0     0.5          "dice-attenuation.png"
  bump   1.0            1      0.0      1.0     1.0          "bump-fog.png"
  bump   0.5            1      0.0      1.0     1.0          "bump-dark.png"
  bump   1.0            0      0.0      1.0     1.0          "bump-sunset.png"
  bump   1.0            0      1.0      1.0     1.0          "bump-ambient.png"
  bump   1.0            1      0.0      0.5     1.0          "bump-shadow.png"
  bump   1.0            1      0.0      1.0     0.5          "bump-attenuation.png"
  bricks 1.0            1      0.0      1.0     1.0          "bricks-fog.png"
  bricks 0.5            1      0.0      1.0     1.0          "bricks-dark.png"
  bricks 1.0            0      0.0      1.0     1.0          "bricks-sunset.png"
  bricks 1.0            0      1.0      1.0     1.0          "bricks-ambient.png"
  bricks 1.0            1      0.0      0.5     1.0          "bricks-shadow.png"
  bricks 1.0            1      0.0      1.0     0.5          "bricks-attenuation.png")

(GLFW/glfwTerminate)
