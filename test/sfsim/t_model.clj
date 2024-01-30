(ns sfsim.t-model
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [clojure.math :refer (to-radians sqrt PI)]
              [sfsim.conftest :refer (roughly-matrix roughly-vector roughly-quaternion is-image)]
              [fastmath.matrix :refer (eye mulm mat4x4)]
              [fastmath.vector :refer (vec3 normalize)]
              [sfsim.matrix :refer :all]
              [sfsim.render :refer :all]
              [sfsim.model :refer :all :as model]
              [sfsim.quaternion :refer (->Quaternion)])
    (:import [org.lwjgl.glfw GLFW]))

(mi/collect! {:ns ['sfsim.model]})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def cube (read-gltf "test/sfsim/fixtures/model/cube.gltf"))

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
      => [0 1 2, 0 2 3, 4 5 6, 4 6 7, 8 9 10, 8 10 11, 12 13 14, 12 14 15, 16 17 18, 16 18 19, 20 21 22, 20 22 23])

(fact "Size of vertex buffer"
      (count (:vertices (first (:meshes cube)))) => (* 24 6))

(fact "Vertex buffer should be a vector"
      (:vertices (first (:meshes cube))) => vector?)

(fact "Find vertices with normals in vertex buffer"
      (take (* 4 6) (:vertices (first (:meshes cube)))) =>
      [1.0  1.0 -1.0 0.0  1.0  0.0,
      -1.0  1.0 -1.0 0.0  1.0  0.0,
      -1.0  1.0  1.0 0.0  1.0  0.0,
       1.0  1.0  1.0 0.0  1.0  0.0])

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
        (let [program      (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              opengl-scene (load-scene-into-opengl (constantly program) cube)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform diffuse]}]
                            (uniform-matrix4 program "transform" transform)
                            (uniform-vector3 program "diffuse_color" diffuse)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/cube.png" 0.0))

(def cubes (read-gltf "test/sfsim/fixtures/model/cubes.gltf"))

(fact "Name of root node"
      (:name (:root cubes)) => "ROOT")

(fact "Children of root node"
      (count (:children (:root cubes))) => 2)

(fact "Names of child nodes"
      (set (map :name (:children (:root cubes)))) => #{"Cube1" "Cube2"})

(fact "Render red and green cube"
      (offscreen-render 160 120
        (let [program      (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              opengl-scene (load-scene-into-opengl (constantly program) cubes)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform diffuse]}]
                            (uniform-matrix4 program "transform" transform)
                            (uniform-vector3 program "diffuse_color" diffuse)))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/cubes.png" 0.01))

(def dice (read-gltf "test/sfsim/fixtures/model/dice.gltf"))

(fact "Dice has one texture"
      (count (:textures dice)) => 1)

(fact "Textures are returned in a vector"
      (:textures dice) => vector?)

(facts "Size of texture"
       (:sfsim.image/width  (first (:textures dice))) => 64
       (:sfsim.image/height (first (:textures dice))) => 64)

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
      [1.0  1.0 -1.0 0.0  1.0  0.0 0.625 0.5,
      -1.0  1.0 -1.0 0.0  1.0  0.0 0.875 0.5,
      -1.0  1.0  1.0 0.0  1.0  0.0 0.875 0.25,
       1.0  1.0  1.0 0.0  1.0  0.0 0.625 0.25])

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
        (let [program      (make-program :sfsim.render/vertex [vertex-dice] :sfsim.render/fragment [fragment-dice])
              opengl-scene (load-scene-into-opengl (constantly program) dice)
              transform    (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -5))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 1 2 3)))
          (uniform-sampler program "colors" 0)
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform colors]}]
                            (uniform-matrix4 program "transform" transform)
                            (use-textures {0 colors})))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/dice.png" 0.01))

(def bricks (read-gltf "test/sfsim/fixtures/model/bricks.gltf"))

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
        (let [program      (make-program :sfsim.render/vertex [vertex-bricks] :sfsim.render/fragment [fragment-bricks])
              opengl-scene (load-scene-into-opengl (constantly program) bricks)
              transform    (transformation-matrix (rotation-x 1.8) (vec3 0 0 -3))
              moved-scene  (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0.0)
          (use-program program)
          (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
          (uniform-vector3 program "light" (normalize (vec3 0 -3 1)))
          (uniform-sampler program "colors" 0)
          (uniform-sampler program "normals" 1)
          (render-scene (constantly program) moved-scene
                        (fn [{:keys [transform colors normals]}]
                            (uniform-matrix4 program "transform" transform)
                            (use-textures {0 colors 1 normals})))
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program))) => (is-image "test/sfsim/fixtures/model/bricks.png" 0.01))

(defmulti render-model (fn [{:keys [color-texture-index]}] (type color-texture-index)))

(defmethod render-model nil [{:keys [program transform diffuse]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (uniform-vector3 program "diffuse_color" diffuse))

(defmethod render-model Number [{:keys [program transform colors]}]
  (use-program program)
  (uniform-matrix4 program "transform" transform)
  (use-textures {0 colors}))

(def cube-and-dice (read-gltf "test/sfsim/fixtures/model/cube-and-dice.gltf"))

(fact "Render uniformly colored cube and textured cube"
      (offscreen-render 160 120
        (let [program-cube      (make-program :sfsim.render/vertex [vertex-cube] :sfsim.render/fragment [fragment-cube])
              program-dice      (make-program :sfsim.render/vertex [vertex-dice] :sfsim.render/fragment [fragment-dice])
              program-selection (fn [material] (if (:color-texture-index material) program-dice program-cube))
              opengl-scene      (load-scene-into-opengl program-selection cube-and-dice)
              transform         (transformation-matrix (mulm (rotation-x 0.5) (rotation-y -0.4)) (vec3 0 0 -7))
              moved-scene       (assoc-in opengl-scene [:root :transform] transform)]
          (clear (vec3 0 0 0) 0.0)
          (doseq [program [program-cube program-dice]]
                 (use-program program)
                 (uniform-matrix4 program "projection" (projection-matrix 160 120 0.1 10.0 (to-radians 60)))
                 (uniform-vector3 program "light" (normalize (vec3 1 2 3))))
          (uniform-sampler program-dice "colors" 0)
          (render-scene program-selection moved-scene render-model)
          (unload-scene-from-opengl opengl-scene)
          (destroy-program program-dice)
          (destroy-program program-cube))) => (is-image "test/sfsim/fixtures/model/cube-and-dice.png" 0.01))

(def translation (read-gltf "test/sfsim/fixtures/model/translation.gltf"))

(fact "Number of animations"
      (count (:animations translation)) => 1)

(def translation-animation ((:animations translation) "CubeAction"))

(fact "Duration of animation in seconds"
      (:duration translation-animation) => (roughly (/ 100.0 24.0) 1e-6))

(fact "Number of channels of animation"
      (count (:channels translation-animation)) => 1)

(def translation-channel ((:channels translation-animation) "Cube"))

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

(def rotation (read-gltf "test/sfsim/fixtures/model/rotation.gltf"))

(def rotation-animation ((:animations rotation) "CubeAction"))
(def rotation-channel ((:channels rotation-animation) "Cube"))

(facts "Get time stamps from different rotation key frames"
       (:time (first (:rotation-keys rotation-channel))) => (roughly 0.0 1e-6)
       (:time (second (:rotation-keys rotation-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:time (last (:rotation-keys rotation-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get rotation from different rotation key frames"
       (:rotation (first (:rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6)
       (:rotation (last (:rotation-keys rotation-channel))) => (roughly-quaternion (->Quaternion 0 0 -1 0) 1e-6))

(def scaling (read-gltf "test/sfsim/fixtures/model/scaling.gltf"))

(def scaling-animation ((:animations scaling) "CubeAction"))
(def scaling-channel ((:channels scaling-animation) "Cube"))

(facts "Get time stamps from different scaling key frames"
       (:time (first (:scaling-keys scaling-channel))) => (roughly 0.0 1e-6)
       (:time (second (:scaling-keys scaling-channel))) => (roughly (/ 1.0 24.0) 1e-6)
       (:time (last (:scaling-keys scaling-channel))) => (roughly (/ 100.0 24.0) 1e-6))

(facts "Get scale from different scaling key frames"
       (:scaling (first (:scaling-keys scaling-channel))) => (roughly-vector (vec3 2 1 1) 1e-6)
       (:scaling (last (:scaling-keys scaling-channel))) => (roughly-vector (vec3 5 1 1) 1e-6))

(facts "Interpolate between position frames assuming constant sampling interval"
       (interpolate-position [{:time 0.0 :position (vec3 0 0 0)}] 0.0) => (roughly-vector (vec3 0 0 0) 1e-6)
       (interpolate-position [{:time 0.0 :position (vec3 2 3 5)}] 0.0) => (roughly-vector (vec3 2 3 5) 1e-6)
       (interpolate-position [{:time 0.0 :position (vec3 2 0 0)} {:time 1.0 :position (vec3 3 0 0)}] 0.0)
       => (roughly-vector (vec3 2 0 0) 1e-6)
       (interpolate-position [{:time 0.0 :position (vec3 2 0 0)} {:time 1.0 :position (vec3 3 0 0)}] 1.0)
       => (roughly-vector (vec3 3 0 0) 1e-6)
       (interpolate-position [{:time 0.0 :position (vec3 2 0 0)} {:time 1.0 :position (vec3 3 0 0)}] 0.5)
       => (roughly-vector (vec3 2.5 0 0) 1e-6)
       (interpolate-position [{:time 3.0 :position (vec3 2 0 0)} {:time 3.5 :position (vec3 3 0 0)}] 3.25)
       => (roughly-vector (vec3 2.5 0 0) 1e-6)
       (interpolate-position [{:time 1.0 :position (vec3 2 0 0)} {:time 2.0 :position (vec3 3 0 0)}] 1.25)
       => (roughly-vector (vec3 2.25 0 0) 1e-6)
       (interpolate-position [{:time 1.0 :position (vec3 2 0 0)} {:time 2.0 :position (vec3 3 0 0)}
                              {:time 3.0 :position (vec3 4 0 0)}] 2.25)
       => (roughly-vector (vec3 3.25 0 0) 1e-6)
       (interpolate-position [{:time 1.0 :position (vec3 2 0 0)} {:time 2.0 :position (vec3 3 0 0)}
                              {:time 3.0 :position (vec3 4 0 0)}] 1.25)
       => (roughly-vector (vec3 2.25 0 0) 1e-6))

(fact "Interpolate between scaling frames assuming constant sampling interval"
      (interpolate-scaling [{:time 0.0 :scaling (vec3 2 0 0)} {:time 1.0 :scaling (vec3 3 0 0)}] 0.25)
      => (roughly-vector (vec3 2.25 0 0) 1e-6))

(fact "Interpolate between rotation frames assuming constant sampling interval"
      (interpolate-rotation [{:time 0.0 :rotation (->Quaternion 2 0 0 0)} {:time 1.0 :rotation (->Quaternion 3 0 0 0)}] 0.25)
      => (roughly-quaternion (->Quaternion 2.25 0 0 0) 1e-6))

(fact "Handle negative quaternion with same rotation"
      (interpolate-rotation [{:time 0.0 :rotation (->Quaternion 1 0 0 0)} {:time 1.0 :rotation (->Quaternion -1 0 0 0)}] 0.5)
      => (roughly-quaternion (->Quaternion 1 0 0 0) 1e-6))

(facts "Create key frame for given channel"
       (interpolate-transformation {:position-keys [{:time 0.0 :position (vec3 2 3 5)}]
                                    :rotation-keys [{:time 0.0 :rotation (->Quaternion 1 0 0 0)}]
                                    :scaling-keys [{:time 0.0 :scaling (vec3 1 1 1)}]} 0.0)
       => (roughly-matrix (transformation-matrix (eye 3) (vec3 2 3 5)) 1e-6)
       (interpolate-transformation {:position-keys [{:time 0.0 :position (vec3 0 0 0)}]
                                    :rotation-keys [{:time 0.0 :rotation (->Quaternion 0 1 0 0)}]
                                    :scaling-keys [{:time 0.0 :scaling (vec3 1 1 1)}]} 0.0)
       => (roughly-matrix (transformation-matrix (rotation-x PI) (vec3 0 0 0)) 1e-6)
       (interpolate-transformation {:position-keys [{:time 0.0 :position (vec3 0 0 0)}]
                                    :rotation-keys [{:time 0.0 :rotation (->Quaternion (sqrt 0.5) (sqrt 0.5) 0 0)}]
                                    :scaling-keys [{:time 0.0 :scaling (vec3 2 3 5)}]} 0.0)
       => (roughly-matrix (mat4x4 2 0 0 0, 0 0 -5 0, 0 3 0 0, 0 0 0 1) 1e-6))

(facts "Determine updates for model"
       (animations-frame {:animations {}} {}) => {}
       (with-redefs [model/interpolate-transformation
                     (fn [channel t] (facts channel => :mock-channel-data t => 1.0) :mock-transform)]
         (animations-frame {:animations {"Animation" {:channels {"Object" :mock-channel-data}}}} {"Animation" 1.0}))
       => {"Object" :mock-transform})

(facts "Apply transformation updates to model"
       (apply-transforms {:root {:name "Cube" :transform :mock :children []}} {})
       => {:root {:name "Cube" :transform :mock :children []}}
       (apply-transforms {:root {:name "Cube" :transform :mock :children []}} {"Cube" :mock-changed})
       => {:root {:name "Cube" :transform :mock-changed :children []}}
       (apply-transforms {:root {:name "ROOT" :transform :mock :children [{:name "Cube" :transform :mock :children []}]}}
                         {"Cube" :mock-changed})
       => {:root {:name "ROOT" :transform :mock :children [{:name "Cube" :transform :mock-changed :children []}]}})

(GLFW/glfwTerminate)
