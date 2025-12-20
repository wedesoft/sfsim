;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-model
  (:require
    [clojure.math :refer (to-radians sqrt PI)]
    [comb.template :as template]
    [fastmath.matrix :refer (eye mulm inverse mat4x4)]
    [fastmath.vector :refer (vec3 vec4 normalize)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.clouds :as clouds]
    [sfsim.conftest :refer (roughly-matrix roughly-vector roughly-quaternion is-image)]
    [sfsim.image :refer (floats->image get-vector4 get-float)]
    [sfsim.matrix :refer :all]
    [sfsim.model :refer :all :as model]
    [sfsim.plume :as plume]
    [sfsim.quaternion :refer (->Quaternion) :as q]
    [sfsim.render :refer :all]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer :all])
  (:import
    (org.lwjgl.glfw
      GLFW)
    (org.lwjgl.opengl
      GL30)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def cube (read-gltf "test/clj/sfsim/fixtures/model/cube.glb"))
(def cube-moved (read-gltf "test/clj/sfsim/fixtures/model/cube-moved.glb"))


(fact "Root of cube scene"
      (:sfsim.model/name (:sfsim.model/root cube)) => "Cube")


(fact "Transformation of root node"
      (let [blender-x 1
            blender-y 2
            blender-z 3]
        (:sfsim.model/transform (:sfsim.model/root cube-moved))
        => (roughly-matrix (transformation-matrix (eye 3) (vec3 blender-x blender-z (- blender-y))) 1e-6)))


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
      [+1.0  1.0 -1.0 0.0  1.0  0.0,
       -1.0  1.0 -1.0 0.0  1.0  0.0,
       -1.0  1.0  1.0 0.0  1.0  0.0,
       +1.0  1.0  1.0 0.0  1.0  0.0])


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
  "#version 450 core
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
  "#version 450 core
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
                              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5)))]
                          (clear (vec3 0 0 0) 0.0)
                          (use-program program)
                          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
                          (render-scene (constantly program) 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene
                                        (fn [{:sfsim.model/keys [diffuse]} {:sfsim.model/keys [program transform] :as render-vars}]
                                          (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
                                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                                            (uniform-vector3 program "diffuse_color" diffuse))))
                          (destroy-scene opengl-scene)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/model/cube.png" 0.0))


(def cubes (read-gltf "test/clj/sfsim/fixtures/model/cubes.gltf"))


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
                              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7)))]
                          (clear (vec3 0 0 0) 0.0)
                          (use-program program)
                          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
                          (render-scene (constantly program) 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene
                                        (fn [{:sfsim.model/keys [diffuse]} {:sfsim.model/keys [program transform] :as render-vars}]
                                          (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
                                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                                            (uniform-vector3 program "diffuse_color" diffuse))))
                          (destroy-scene opengl-scene)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/model/cubes.png" 0.01))


(def dice (read-gltf "test/clj/sfsim/fixtures/model/dice.gltf"))


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
      [+1.0  1.0 -1.0 0.0  1.0  0.0 0.625 0.5,
       -1.0  1.0 -1.0 0.0  1.0  0.0 0.875 0.5,
       -1.0  1.0  1.0 0.0  1.0  0.0 0.875 0.25,
       +1.0  1.0  1.0 0.0  1.0  0.0 0.625 0.25])


(def vertex-dice
  "#version 450 core
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
  "#version 450 core
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
                              camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5)))]
                          (clear (vec3 0 0 0) 0.0)
                          (use-program program)
                          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
                          (uniform-sampler program "colors" 0)
                          (render-scene (constantly program) 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene
                                        (fn [{:sfsim.model/keys [colors]} {:sfsim.model/keys [program transform] :as render-vars}]
                                          (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
                                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                                            (use-textures {0 colors}))))
                          (destroy-scene opengl-scene)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/model/dice.png" 0.10))


(def bricks (read-gltf "test/clj/sfsim/fixtures/model/bricks.gltf"))


(fact "Color texture index for bricks material is zero"
      (:sfsim.model/color-texture-index (first (:sfsim.model/materials bricks))) => 0)


(fact "Normal texture index for bricks material is one"
      (:sfsim.model/normal-texture-index (first (:sfsim.model/materials bricks))) => 1)


(def vertex-bricks
  "#version 450 core
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
  "#version 450 core
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
                              camera-to-world (inverse (transformation-matrix (rotation-x 1.8) (vec3 0 0 -3)))]
                          (clear (vec3 0 0 0) 0.0)
                          (use-program program)
                          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                          (uniform-vector3 program "light" (normalize (vec3 0 -3 1)))
                          (uniform-sampler program "colors" 0)
                          (uniform-sampler program "normals" 1)
                          (render-scene (constantly program) 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene
                                        (fn [{:sfsim.model/keys [colors normals]} {:sfsim.model/keys [program transform] :as render-vars}]
                                          (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
                                            (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world) transform))
                                            (use-textures {0 colors 1 normals}))))
                          (destroy-scene opengl-scene)
                          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/model/bricks.png" 0.10))


(defn cube-material-type
  [{:sfsim.model/keys [color-texture-index]}]
  (if color-texture-index
    :textured
    :colored))


(defmulti render-cube (fn [material render-vars] (cube-material-type material)))


(defmethod render-cube :colored [{:sfsim.model/keys [diffuse]} {:sfsim.model/keys [program transform] :as render-vars}]
  (use-program program)
  (uniform-matrix4 program "object_to_camera" (mulm (inverse (:sfsim.render/camera-to-world render-vars)) transform))
  (uniform-vector3 program "diffuse_color" diffuse))


(defmethod render-cube :textured [{:sfsim.model/keys [colors]} {:sfsim.model/keys [program transform] :as render-vars}]
  (use-program program)
  (uniform-matrix4 program "object_to_camera" (mulm (inverse (:sfsim.render/camera-to-world render-vars)) transform))
  (use-textures {0 colors}))


(def cube-and-dice (read-gltf "test/clj/sfsim/fixtures/model/cube-and-dice.gltf"))


(fact "Render uniformly colored cube and textured cube"
      (offscreen-render 160 120
                        (let [program-cube      (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
                              program-dice      (make-program :sfsim.render/vertex [vertex-dice] :sfsim.render/fragment [fragment-dice])
                              program-selection (comp {:colored program-cube :textured program-dice} cube-material-type)
                              opengl-scene      (load-scene-into-opengl program-selection cube-and-dice)
                              camera-to-world   (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7)))]
                          (clear (vec3 0 0 0) 0.0)
                          (doseq [program [program-cube program-dice]]
                            (use-program program)
                            (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                            (uniform-vector3 program "light" (normalize (vec3 1 2 3))))
                          (uniform-sampler program-dice "colors" 0)
                          (render-scene program-selection 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene render-cube)
                          (destroy-scene opengl-scene)
                          (destroy-program program-dice)
                          (destroy-program program-cube))) => (is-image "test/clj/sfsim/fixtures/model/cube-and-dice.png" 0.04))


(def translation (read-gltf "test/clj/sfsim/fixtures/model/translation.gltf"))


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


(def rotation (read-gltf "test/clj/sfsim/fixtures/model/rotation.gltf"))

(def rotation-animation ((:sfsim.model/animations rotation) "CubeAction"))
(def rotation-channel ((:sfsim.model/channels rotation-animation) "Cube"))


(facts "Get time stamps from different rotation key frames"
       (:sfsim.model/time (first (:sfsim.model/rotation-keys rotation-channel))) => (roughly 0.0 1e-6)
       (:sfsim.model/time (second (:sfsim.model/rotation-keys rotation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:sfsim.model/time (last (:sfsim.model/rotation-keys rotation-channel))) => (roughly (/ 100.0 24.0) 1e-6))


(facts "Get rotation from different rotation key frames"
       (:sfsim.model/rotation (first (:sfsim.model/rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6)
       (:sfsim.model/rotation (last (:sfsim.model/rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 0 0 -1 0) 1e-6))


(def scaling (read-gltf "test/clj/sfsim/fixtures/model/scaling.gltf"))

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


(facts "Determine updates for scene"
       (animations-frame {:sfsim.model/animations {}} {}) => {}
       (with-redefs [model/interpolate-transformation
                     (fn [channel t] (facts channel => :mock-channel-data t => 1.0) :mock-transform)]
         (animations-frame {:sfsim.model/animations {"Animation" {:sfsim.model/channels {"Object" :mock-channel-data}}}}
                           {"Animation" 1.0}))
       => {"Object" :mock-transform})


(facts "Apply transformation updates to scene"
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


(def bump (read-gltf "test/clj/sfsim/fixtures/model/bump.gltf"))


(def cloud-overlay-mock
  "#version 450 core
uniform vec3 origin;
uniform float object_distance;
vec4 cloud_overlay(float depth)
{
  float transparency = exp(-object_distance / 10.0);
  return vec4(0.5, 0.5, 0.5, 1 - transparency);
}")


(def transmittance-point-mock
  "#version 450 core
uniform float transmittance;
vec3 transmittance_point(vec3 point)
{
  return vec3(transmittance, transmittance, transmittance);
}")


(def above-horizon-mock
  "#version 450 core
uniform int above;
bool is_above_horizon(vec3 point, vec3 direction)
{
  return above > 0;
}")


(def surface-radiance-mock
  "#version 450 core
uniform float ambient;
vec3 surface_radiance_function(vec3 point, vec3 light_direction)
{
  return vec3(ambient, ambient, ambient);
}")


(def planet-and-cloud-shadows-mock
  "#version 450 core
uniform float shadow;
float planet_and_cloud_shadows(vec4 point)
{
  return shadow;
}")


(def ray-sphere-mock
  "#version 450 core
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  return vec2(0, 10);
}")


(def attenuation-mock
  "#version 450 core
uniform float attenuation;
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming)
{
  return vec4(incoming.rgb * attenuation, incoming.a);
}")


(def model-shader-mocks
  [cloud-overlay-mock transmittance-point-mock above-horizon-mock surface-radiance-mock
   planet-and-cloud-shadows-mock ray-sphere-mock attenuation-mock shaders/phong shaders/limit-interval
   (last atmosphere/attenuation-point) (last (clouds/environmental-shading 3))
   (last (clouds/overall-shading 3 []))])


(tabular "Render red cube with fog and atmosphere"
         (with-redefs [model/fragment-scene (fn [textured bump num-steps num-scene-shadows perlin-octaves cloud-octaves]
                                              (conj model-shader-mocks
                                                    (template/eval (slurp "resources/shaders/model/fragment.glsl")
                                                                   {:textured textured :bump bump :num-scene-shadows 0})))
                       plume/setup-static-plume-uniforms (fn [_program _model-data])
                       model/setup-scene-static-uniforms (fn [program texture-offset num-scene-shadows textured bump data]
                                                           (use-program program)
                                                           (setup-scene-samplers program 0 0 textured bump)
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
                               (let [data             {:sfsim.opacity/data {:sfsim.opacity/num-steps 3 :sfsim.opacity/scene-shadow-counts [0]}
                                                       :sfsim.clouds/data {:sfsim.clouds/perlin-octaves [] :sfsim.clouds/cloud-octaves []}
                                                       :sfsim.model/data {:sfsim.model/object-radius 30.0}}
                                     renderer         (make-scene-renderer data)
                                     opengl-scene     (load-scene-into-opengl (comp (:sfsim.model/programs renderer) material-and-shadow-type) ?model)
                                     camera-to-world  (transformation-matrix (eye 3) (vec3 1 0 0))
                                     object-to-world  (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 1 0 -5))
                                     moved-scene      (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] object-to-world)]
                                 (clear (vec3 0.5 0.5 0.5) 0.0)
                                 (render-scene (comp (:sfsim.model/programs renderer) material-and-shadow-type)
                                               0
                                               {:sfsim.render/camera-to-world camera-to-world}
                                               []
                                               moved-scene
                                               render-mesh)
                                 (destroy-scene opengl-scene)
                                 (destroy-scene-renderer renderer))) => (is-image (str "test/clj/sfsim/fixtures/model/" ?result) 0.22)))
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


(facts "Create hashmap with render variables for rendering a scene outside the atmosphere"
       (let [render-config   #:sfsim.render{:fov 0.5 :min-z-near 1.0 :cloud-subsampling 2 }
             pos1            (vec3 0 0 0)
             pos2            (vec3 0 0 -20)
             pos3            (vec3 0 0 -100)
             orientation1    (q/rotation 0.0 (vec3 0 0 1))
             orientation2    (q/rotation 0.5 (vec3 0 0 1))
             orientation3    (q/rotation 0.0 (vec3 0 0 1))
             light-direction (vec3 1 0 0)
             obj-pos         (vec3 0 0 -100)
             obj-orient      (q/rotation 0.0 (vec3 0 0 1))
             model-data      #:sfsim.model{:object-radius 10.0
                                           :nozzle 2.7549
                                           :min-limit 1.2
                                           :max-slope 1.0
                                           :omega-factor 0.2
                                           :diamond-strength 0.4
                                           :engine-step 0.2}
             model-vars      {:sfsim.model/time 0.0 :sfsim.model/pressure 1.0 :sfsim.model/throttle 0.0}
             render-vars1    (make-scene-render-vars render-config 640 480 pos1 orientation1 light-direction obj-pos obj-orient
                                                     model-data model-vars)
             render-vars2    (make-scene-render-vars render-config 640 480 pos2 orientation2 light-direction obj-pos obj-orient
                                                     model-data model-vars)
             render-vars3    (make-scene-render-vars render-config 640 480 pos3 orientation3 light-direction obj-pos obj-orient
                                                     model-data model-vars)]
         (:sfsim.render/origin render-vars1) => pos1
         (:sfsim.render/camera-to-world render-vars1) => (eye 4)
         (:sfsim.render/camera-to-world render-vars2) => (transformation-matrix (quaternion->matrix orientation2) (vec3 0 0 -20))
         (:sfsim.render/z-near render-vars1) => 90.0
         (:sfsim.render/z-far render-vars1) => 110.0
         (:sfsim.render/z-near render-vars2) => 70.0
         (:sfsim.render/z-far render-vars2) => 90.0
         (:sfsim.render/z-near render-vars3) => 1.0
         (:sfsim.render/z-far render-vars3) => 21.0))


(tabular "Render shadow map for an object"
         (fact
           (with-invisible-window
             (let [renderer        (make-scene-shadow-renderer 256 ?object-radius)
                   light-direction (vec3 0 0 1)
                   scene           (load-scene-into-opengl (comp (:sfsim.model/programs renderer) material-type) ?model)
                   object-to-world (transformation-matrix (mulm (rotation-x ?angle-x) (rotation-y ?angle-y)) (vec3 100 200 300))
                   moved-scene     (assoc-in scene [:sfsim.model/root :sfsim.model/transform] object-to-world)
                   object-shadow   (scene-shadow-map renderer light-direction moved-scene)
                   depth           (depth-texture->floats (:sfsim.model/shadows object-shadow))
                   img             (floats->image depth)]
               (destroy-scene-shadow-map object-shadow)
               (destroy-scene scene)
               (destroy-scene-shadow-renderer renderer)
               img)) => (is-image (str "test/clj/sfsim/fixtures/model/" ?result) 0.01))
         ?model ?object-radius ?angle-x ?angle-y ?result
         cube   1.75           0.5      -0.4     "shadow-map-cube.png"
         dice   1.75           0.5      -0.4     "shadow-map-dice.png"
         bump   1.75           0.5      -0.4     "shadow-map-bump.png"
         bricks 1.75           0.5      -0.4     "shadow-map-bricks.png"
         cubes  4.0            0.1      -1.0     "shadow-map-cubes.png")


(def torus (read-gltf "test/clj/sfsim/fixtures/model/torus.gltf"))


(def vertex-torus
  "#version 450 core
uniform mat4 projection;
uniform mat4 object_to_world;
uniform mat4 object_to_camera;
uniform mat4 object_to_shadow_map;
in vec3 vertex;
in vec3 normal;
out VS_OUT
{
  vec4 object_shadow_pos;
  vec3 normal;
} vs_out;
void main()
{
  vs_out.object_shadow_pos = object_to_shadow_map * vec4(vertex, 1);
  vs_out.normal = mat3(object_to_world) * normal;
  gl_Position = projection * object_to_camera * vec4(vertex, 1);
}")


(def fragment-torus
  "#version 450 core
uniform sampler2DShadow shadow_map;
uniform vec3 light_direction;
uniform vec3 diffuse_color;
in VS_OUT
{
  vec4 object_shadow_pos;
  vec3 normal;
} fs_in;
out vec4 fragColor;
float average_scene_shadow(sampler2DShadow shadow_map, vec4 shadow_pos);
void main()
{
  float shadow = average_scene_shadow(shadow_map, fs_in.object_shadow_pos);
  float cos_incidence = dot(light_direction, fs_in.normal);
  fragColor = vec4(diffuse_color * max(cos_incidence * shadow, 0.125), 1.0);
}")


(tabular "Render objects with self-shadowing"
         (fact
           (with-invisible-window
             (let [program         (make-program :sfsim.render/vertex [vertex-torus]
                                                 :sfsim.render/fragment [fragment-torus
                                                                         (shaders/percentage-closer-filtering "average_scene_shadow"
                                                                                                              "scene_shadow_lookup"
                                                                                                              "scene_shadow_size"
                                                                                                              [["sampler2DShadow"
                                                                                                                "shadow_map"]])
                                                                         (shaders/shadow-lookup "scene_shadow_lookup"
                                                                                                "scene_shadow_size")])
                   opengl-scene    (load-scene-into-opengl (constantly program) ?model)
                   light-direction (normalize (vec3 5 2 1))
                   shadow-size     64
                   camera-to-world (inverse (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 (- ?distance))))
                   shadow-renderer (make-scene-shadow-renderer shadow-size ?object-radius)
                   object-shadow   (scene-shadow-map shadow-renderer light-direction opengl-scene)
                   tex             (texture-render-color-depth 160 120 false
                                                               (clear (vec3 0 0 0) 0.0)
                                                               (use-program program)
                                                               (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                                                               (uniform-matrix4 program "object_to_world" (eye 4))
                                                               (uniform-vector3 program "light_direction" light-direction)
                                                               (uniform-int program "scene_shadow_size" shadow-size)
                                                               (uniform-float program "shadow_bias" 1e-6)
                                                               (uniform-sampler program "shadow_map" 0)
                                                               (use-textures {0 (:sfsim.model/shadows object-shadow)})
                                                               (render-scene (constantly program) 0 {:sfsim.render/camera-to-world camera-to-world} [] opengl-scene
                                                                             (fn [{:sfsim.model/keys [diffuse]}
                                                                                  {:sfsim.model/keys [program transform] :as render-vars}]
                                                                               (let [camera-to-world (:sfsim.render/camera-to-world render-vars)]
                                                                                 (uniform-matrix4 program "object_to_shadow_map"
                                                                                                  (mulm (-> object-shadow
                                                                                                            :sfsim.model/matrices
                                                                                                            :sfsim.matrix/object-to-shadow-map)
                                                                                                        transform))
                                                                                 (uniform-matrix4 program "object_to_camera" (mulm (inverse camera-to-world)
                                                                                                                                   transform))
                                                                                 (uniform-vector3 program "diffuse_color" diffuse)))))
                   result            (texture->image tex)]
               (destroy-texture tex)
               (destroy-scene-shadow-map object-shadow)
               (destroy-scene opengl-scene)
               (destroy-scene-shadow-renderer shadow-renderer)
               (destroy-program program)
               result)) => (is-image (str "test/clj/sfsim/fixtures/model/" ?result) 0.11))
         ?model ?object-radius ?distance ?result
         torus  1.5            3         "torus-shadow.png"
         cubes  4.0            7         "cubes-shadow.png")


(def model-shadow-mocks
  "#version 450 core
vec3 environmental_shading(vec3 point)
{
  return vec3(1, 1, 1);
}
vec4 attenuation_point(vec3 point, vec4 incoming)
{
  return incoming;
}
vec3 surface_radiance_function(vec3 point, vec3 light_direction)
{
  return vec3(0.2, 0.2, 0.2);
}
vec4 cloud_overlay(float depth)
{
  return vec4(0, 0, 0, 0);
}")


(tabular "Integration of model's self-shading"
         (fact
           (with-invisible-window
             (with-redefs [model/fragment-scene (fn [textured bump num-steps num-scene-shadows perlin-octaves cloud-octaves]
                                                  (conj [model-shadow-mocks shaders/phong
                                                         (last (clouds/overall-shading 3 (repeat num-scene-shadows
                                                                                                 ["average_scene_shadow"
                                                                                                  "scene_shadow_map_1"])))
                                                         (shaders/percentage-closer-filtering "average_scene_shadow"
                                                                                              "scene_shadow_lookup"
                                                                                              "scene_shadow_size"
                                                                                              [["sampler2DShadow"
                                                                                                "shadow_map"]])
                                                         (shaders/shadow-lookup "scene_shadow_lookup" "scene_shadow_size")]
                                                        (template/eval (slurp "resources/shaders/model/fragment.glsl")
                                                                       {:textured textured
                                                                        :bump bump
                                                                        :num-scene-shadows num-scene-shadows})))
                           plume/setup-static-plume-uniforms (fn [_program _model-data])
                           model/setup-scene-static-uniforms (fn [program texture-offset num-scene-shadows textured bump data]
                                                               (use-program program)
                                                               (setup-scene-samplers program 0 0 textured bump)
                                                               (uniform-sampler program "scene_shadow_map_1" 0)
                                                               (uniform-float program "albedo" 3.14159265358)
                                                               (uniform-float program "amplification" 1.0)
                                                               (uniform-float program "specular" 1.0)
                                                               (uniform-int program "scene_shadow_size" 256)
                                                               (uniform-matrix4 program "projection"
                                                                                (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                                                               (uniform-vector3 program "light_direction" (normalize (vec3 5 2 1))))]
               (let [data             {:sfsim.opacity/data {:sfsim.opacity/num-steps 3 :sfsim.opacity/scene-shadow-counts [0 1]}
                                       :sfsim.clouds/data {:sfsim.clouds/perlin-octaves [] :sfsim.clouds/cloud-octaves []}
                                       :sfsim.model/data {:sfsim.model/object-radius 30.0}}
                     renderer         (make-scene-renderer data)
                     shadow-size      256
                     object-radius    4.0
                     light-direction  (normalize (vec3 5 2 1))
                     shadow-renderer  (make-scene-shadow-renderer shadow-size object-radius)
                     opengl-scene     (load-scene-into-opengl (comp (:sfsim.model/programs renderer) material-and-shadow-type) ?model)
                     camera-to-world  (transformation-matrix (eye 3) (vec3 1 0 0))
                     object-to-world  (transformation-matrix (mulm (rotation-x ?angle-x) (rotation-y ?angle-y)) (vec3 1 0 (- ?dist)))
                     moved-scene      (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform] object-to-world)
                     object-shadow    (scene-shadow-map shadow-renderer light-direction opengl-scene)
                     tex              (texture-render-color-depth 160 120 false
                                                                  (clear (vec3 0.5 0.5 0.5) 0.0)
                                                                  (use-textures {0 (:sfsim.model/shadows object-shadow)})
                                                                  (render-scene (comp (:sfsim.model/programs renderer) material-and-shadow-type) 1
                                                                                {:sfsim.render/camera-to-world camera-to-world}
                                                                                [(:sfsim.matrix/object-to-shadow-map (:sfsim.model/matrices object-shadow))]
                                                                                moved-scene render-mesh))
                     result           (texture->image tex)]
                 (destroy-texture tex)
                 (destroy-scene-shadow-map object-shadow)
                 (destroy-scene opengl-scene)
                 (destroy-scene-shadow-renderer shadow-renderer)
                 (destroy-scene-renderer renderer)
                 result))) => (is-image (str "test/clj/sfsim/fixtures/model/" ?result) 0.01))
         ?model ?dist ?angle-x ?angle-y ?result
         torus  3.0   0.5      -0.4     "torus-integration.png"
         cubes  6.0   0.1      -1.0     "cubes-integration.png")


(def cube-with-hull (read-gltf "test/clj/sfsim/fixtures/model/cube-with-hull.glb"))
(def hull-with-offset (read-gltf "test/clj/sfsim/fixtures/model/hull-with-offset.glb"))
(def cube-with-incomplete-hull (read-gltf "test/clj/sfsim/fixtures/model/cube-with-incomplete-hull.glb"))
(def cube-with-points (read-gltf "test/clj/sfsim/fixtures/model/cube-with-points.glb"))



(fact "Filter out empty children"
      (:sfsim.model/name (:sfsim.model/root (remove-empty-meshes cube))) => "Cube"
      (map :sfsim.model/name (:sfsim.model/children (:sfsim.model/root (remove-empty-meshes cube-with-hull)))) => ["Cube"])


(facts "Convert empty meshes to points of for convex hulls"
       (map :sfsim.model/name (:sfsim.model/children (empty-meshes-to-points cube-with-hull))) => ["Hull"]
       (:sfsim.model/children (first (:sfsim.model/children (empty-meshes-to-points cube-with-hull))))
       => [(vec3 1 1 -1) (vec3 -1 1 -1) (vec3 -1 1 1) (vec3 1 1 1)
           (vec3 1 -1 -1) (vec3 -1 -1 -1) (vec3 -1 -1 1) (vec3 1 -1 1) (vec3 0 0 0)]
       (:sfsim.model/transform (first (:sfsim.model/children (empty-meshes-to-points hull-with-offset))))
       => (mat4x4 1 0 0 1, 0 1 0 0, 0 0 1 0, 0 0 0 1)
       (:sfsim.model/children (first (:sfsim.model/children (empty-meshes-to-points hull-with-offset))))
       => [(vec3 1 0 0) (vec3 1 0 0) (vec3 1 0 0) (vec3 0 0 0)]
       (:sfsim.model/children (empty-meshes-to-points cube-with-points)) => [])


(fact "Remove hulls with less than four points"
      (:sfsim.model/children (empty-meshes-to-points cube-with-incomplete-hull)) => [])


(facts "Get path in model to node with given name"
       (get-node-path {:sfsim.model/root {:sfsim.model/name "ROOT"}} "ROOT")
       => [:sfsim.model/root]
       (get-node-path {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/children [{:sfsim.model/name "Node"}]}} "Node")
       => [:sfsim.model/root :sfsim.model/children 0]
       (get-node-path {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/children [{:sfsim.model/name "Node"}]}} "Node")
       => [:sfsim.model/root :sfsim.model/children 0]
       (get-node-path {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/children [{:sfsim.model/name "Node"}]}} "Other")
       => nil
       (get-node-path {:sfsim.model/root {:sfsim.model/name "ROOT"
                                          :sfsim.model/children [{:sfsim.model/name "Node1"} {:sfsim.model/name "Node2"}]}}
                      "Node2")
       => [:sfsim.model/root :sfsim.model/children 1])


(facts "Get global transform of a node"
       (let [translation (translation-matrix (vec3 1 2 3))
             rotation    (rotation-matrix (rotation-x (to-radians 90)))]
         (get-node-transform {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/transform translation}}
                             "ROOT")
         => translation
         (get-node-transform {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/transform translation
                                                 :sfsim.model/children [{:sfsim.model/name "Node"
                                                                         :sfsim.model/transform rotation}]}}
                             "Node")
         => (mulm translation rotation)
         (get-node-transform {:sfsim.model/root {:sfsim.model/name "ROOT" :sfsim.model/transform translation
                                                 :sfsim.model/children []}}
                             "Node")
         => nil))


(facts "Create model configuration"
       (let [model-vars (model/make-model-vars 1234.0 0.5 0.8)]
         (:sfsim.model/time model-vars) => 1234.0
         (:sfsim.model/pressure model-vars) => 0.5
         (:sfsim.model/throttle model-vars) => 0.8))


(fact "Render camera points to frame buffer"
      (with-invisible-window
        (let [renderer        (make-scene-geometry-renderer)
              opengl-scene    (load-scene-into-opengl (comp (:sfsim.model/programs renderer) material-type) cube)
              camera-to-world (transformation-matrix (eye 3) (vec3 0 0 5))
              render-vars     {:sfsim.render/camera-to-world camera-to-world
                               :sfsim.render/overlay-projection (projection-matrix 160 120 0.1 10.0 (to-radians 60))}
              geometry        (clouds/render-cloud-geometry 160 120 (render-scene-geometry renderer render-vars opengl-scene))]
          (get-vector4 (rgba-texture->vectors4 (:sfsim.clouds/points geometry)) 60 80)
          => (roughly-vector (vec4 0.004 0.004 -1.0 0.0) 1e-3)
          (get-float (float-texture-2d->floats (:sfsim.clouds/distance geometry)) 60 80)
          => (roughly 4.0 1e-3)
          (clouds/destroy-cloud-geometry geometry)
          (destroy-scene opengl-scene)
          (destroy-scene-geometry-renderer renderer))))


(GLFW/glfwTerminate)
