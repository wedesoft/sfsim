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

(fact "Render a square with a decal"
      (with-invisible-window
        (let [geometry-renderer (model/make-scene-geometry-renderer true)
              plane             (model/read-gltf "test/clj/sfsim/fixtures/decal/plane.glb")
              scene             (model/load-scene-into-opengl (model/geometry-program-selection geometry-renderer) plane)
              camera-to-world   (inverse (matrix/transformation-matrix (rotation-matrix-3d-x (to-radians 60)) (vec3 0 0 -3)))
              projection        (matrix/projection-matrix 160 120 0.1 10.0 (to-radians 60))
              lighting-program  (render/make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                                     :sfsim.render/fragment [fragment-lighting-mock])
              geometry-buffers  (model/make-geometry-buffers 160 120)]
          (model/render-geometry geometry-buffers
                                 (clear)
                                 (model/render-scene-geometry2 geometry-renderer projection
                                                               {:sfsim.render/camera-to-world camera-to-world}
                                                               scene))
          (render-to-image 160 120 false
                           (model/render-lighting geometry-buffers lighting-program 0))
          => (is-image "test/clj/sfsim/fixtures/decal/uniform.png" 0.5)
          (model/destroy-geometry-buffers geometry-buffers)
          (render/destroy-program lighting-program)
          (model/destroy-scene scene)
          (model/destroy-scene-geometry-renderer geometry-renderer))))


(GLFW/glfwTerminate)
