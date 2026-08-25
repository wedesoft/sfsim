;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-decal
  (:require
    [clojure.math :refer (to-radians)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (is-image)]
    [fastmath.vector :refer (vec3)]
    [fastmath.matrix :refer (inverse mulm eye rotation-matrix-3d-x)]
    [sfsim.matrix :as matrix]
    [sfsim.shaders :as shaders]
    [sfsim.render :refer (with-invisible-window clear render-to-image) :as render]
    [sfsim.lighting :as lighting]
    [sfsim.model :as model])
  (:import
    (org.lwjgl.glfw
      GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def fragment-lighting-mock
"#version 450 core
uniform mat4 camera_to_world;
uniform sampler2D camera_point;
uniform sampler2D camera_normal;
uniform sampler2D diffuse_material;
uniform sampler2D metallic_material;
uniform sampler2D emissive_material;
uniform vec3 light;
uniform int width;
uniform int height;
out vec3 fragColor;
void main()
{
  vec2 uv = vec2(gl_FragCoord.x / width, gl_FragCoord.y / height);
  vec4 point = texture(camera_point, uv);
  vec3 diffuse_color = texture(diffuse_material, uv).rgb;
  if (point.w > 0.0)
    fragColor = diffuse_color;
  else
    fragColor = vec3(0.0, 0.0, 1.0);
}")


(def vertex-decal
"#version 450 core
uniform mat4 projection;
uniform mat4 object_to_camera;
in vec3 point;
void main()
{
  vec4 camera_point = object_to_camera * vec4(point, 1);
  gl_Position = projection * camera_point;
}")


(def fragment-decal
"#version 450 core
layout (location = 0) out vec4 diffuse_material;
uniform sampler2D camera_point;
uniform mat4 camera_to_object;
void main()
{
  vec2 uv = vec2(gl_FragCoord.x / 160, gl_FragCoord.y / 120);
  vec4 cam_point = texture(camera_point, uv);
  vec4 point = camera_to_object * cam_point;
  if (point.w > 0.0 && abs(point.x) <= 0.5 && abs(point.z) <= 0.5) {
    diffuse_material = vec4(1, 0, 0, 1);
  } else
    discard;
}")


(def decal-indices
  [4 5 7 6    ; front (+z)
   1 0 2 3    ; back  (-z)
   0 4 6 2    ; left  (-x)
   5 1 3 7    ; right (+x)
   2 6 7 3    ; top   (+y)
   0 1 5 4])  ; bottom (-y)


(def decal-vertices
  [-0.5 -0.2 -0.5
    0.5 -0.2 -0.5
   -0.5  0.2 -0.5
    0.5  0.2 -0.5
   -0.5 -0.2  0.5
    0.5 -0.2  0.5
   -0.5  0.2  0.5
    0.5  0.2  0.5])


(fact "Render a square with a decal"
      (with-invisible-window
        (let [geometry-renderer (model/make-scene-geometry-renderer true)
              plane             (model/read-gltf "test/clj/sfsim/fixtures/decal/plane.glb")
              scene             (model/load-scene-into-opengl (model/geometry-program-selection geometry-renderer) plane)
              object-to-camera  (matrix/transformation-matrix (rotation-matrix-3d-x (to-radians 45)) (vec3 0 0 -3))
              camera-to-world   (inverse object-to-camera)
              projection        (matrix/projection-matrix 160 120 0.1 10.0 (to-radians 60))
              lighting-program  (render/make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                                     :sfsim.render/fragment [fragment-lighting-mock])
              program-decal     (render/make-program :sfsim.render/vertex [vertex-decal] :sfsim.render/fragment [fragment-decal])
              decal             (render/make-vertex-array-object program-decal decal-indices decal-vertices ["point" 3])
              geometry-buffers  (model/make-geometry-buffers 160 120)]
          (model/render-geometry geometry-buffers
                                 (clear)
                                 (model/render-scene-geometry2 geometry-renderer projection
                                                               {:sfsim.render/camera-to-world camera-to-world}
                                                               scene))
          (render/framebuffer-render 160 120 :sfsim.render/cullfront nil [(:sfsim.model/diffuse-texture geometry-buffers)]
                                     (render/use-program program-decal)
                                     (render/uniform-sampler program-decal "camera_point" 0)
                                     (render/uniform-matrix4 program-decal "projection" projection)
                                     (render/uniform-matrix4 program-decal "object_to_camera" object-to-camera)
                                     (render/uniform-matrix4 program-decal "camera_to_object" (inverse object-to-camera))
                                     (render/use-textures {0 (:sfsim.model/point-texture geometry-buffers)})
                                     (render/render-quads decal))
          (render-to-image 160 120 false
                           (model/render-lighting geometry-buffers lighting-program 0))
          => (is-image "test/clj/sfsim/fixtures/decal/uniform.png" 0.5)
          (model/destroy-geometry-buffers geometry-buffers)
          (render/destroy-vertex-array-object decal)
          (render/destroy-program program-decal)
          (render/destroy-program lighting-program)
          (model/destroy-scene scene)
          (model/destroy-scene-geometry-renderer geometry-renderer))))


(GLFW/glfwTerminate)
