;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-shockwave
    (:require
      [clojure.math :refer (sqrt exp)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [midje.sweet :refer :all]
      [fastmath.vector :refer (vec3 vec4)]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test is-image)]
      [sfsim.render :refer (offscreen-render make-program uniform-float make-vertex-array-object clear use-program uniform-int
                            render-quads destroy-vertex-array-object destroy-program with-invisible-window framebuffer-render)]
      [sfsim.texture :refer (make-empty-texture-2d destroy-texture rgba-texture->vectors4)]
      [sfsim.image :refer (get-vector4)]
      [sfsim.shaders :refer (vertex-passthrough)]
      [sfsim.shockwave :refer :all])
    (:import
      (org.lwjgl.glfw
        GLFW)
      (org.lwjgl.opengl
        GL30)))

(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(def shockfront-probe
  (template/fn [radial-distance curvature-radius]
"#version 450 core
out vec3 fragColor;
float shockfront(float radial_distance, float curvature_radius);
void main()
{
  float result = shockfront(<%= radial-distance  %>, <%= curvature-radius %>);
  fragColor = vec3(result);
}"))


(def shockfront-test (shader-test (fn [program mach] (uniform-float program "mach" mach))
                                  shockfront-probe shockfront))


(tabular "Shockfront shape depending on curvature radius"
         (fact (first (shockfront-test [?mach] [?y ?radius])) => (roughly ?result 1e-6))
         ?mach    ?y  ?radius ?result
         (sqrt 2) 0.0 1.0     0.722592
         (sqrt 2) 1.0 1.0     0.630982
         (sqrt 2) 2.0 1.0     0.364871
         10.0     0.0 1.0     0.147709
         10.0     1.0 1.0    -0.272397
         10.0     0.1 0.0    -0.994987)


(def sphere-normal
"#version 450 core
vec4 normal_source(vec2 uv)
{
  vec2 offset = uv * 2.0 - 1.0;
  if (length(offset) < 1.0)
    return vec4(offset, sqrt(1.0 - length(offset)), 0.0);
  else
    return vec4(0.0, 0.0, 0.0, 0.0);
}")


(def sphere-depth
"#version 450 core
float depth_source(vec2 uv)
{
  vec2 offset = uv * 2.0 - 1.0;
  if (length(offset) < 1.0)
    return sqrt(1.0 - length(offset));
  else
    return -1.0;
}")


(def fragment-curvature-test
"#version 450 core
#define MAX_RADIUS 1.0
out vec3 fragColor;
uniform int size;
vec4 normal_source(vec2 uv);
float curvature(vec4 normal, float max_result);
void main()
{
  vec2 uv = gl_FragCoord.xy / size;
  vec4 normal = normal_source(uv);
  float scale = 2.0 / size;
  float c = curvature(normal, MAX_RADIUS * scale) / scale;
  fragColor = vec3(c);
}")


(fact "Estimate curvature off surface given normals"
      (offscreen-render
        256 256
        (let [indices  [0 1 3 2]
              vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
              program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                     :sfsim.render/fragment [fragment-curvature-test curvature sphere-normal])
              vao      (make-vertex-array-object program indices vertices ["point" 3])]
          (clear (vec3 0.0 0.0 0.0))
          (use-program program)
          (uniform-int program "size" 256)
          (render-quads vao)
          (destroy-vertex-array-object vao)
          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/shockwave/curvature.png" 0.1))


(def vertex-texture
"#version 450 core
in vec3 point;
in vec2 uv;
out vec2 uv_fragment;
void main()
{
  gl_Position = vec4(point, 1);
  uv_fragment = uv;
}")


(def jump-flooding-init-fragment
"#version 450 core
uniform float scale;
uniform float max_curvature_radius;
uniform int size;
in vec2 uv_fragment;
float depth_source(vec2 uv);
vec4 normal_source(vec2 uv);
layout (location = 0) out vec4 point;
float curvature(vec4 normal, float max_result);
void main()
{
  vec2 position = (gl_FragCoord.xy) * scale;
  float depth = depth_source(uv_fragment);
  vec4 normal = normal_source(uv_fragment);
  float curvature_ = curvature(normal, max_curvature_radius * scale) / scale;
  point = vec4(position, depth, curvature_);
}")

(facts "Initial step of Jump Flooding Algorithm"
       (with-invisible-window
         (let [size     256
               tex      (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F size size)
               program  (make-program :sfsim.render/vertex [vertex-texture]
                                      :sfsim.render/fragment [jump-flooding-init-fragment sphere-depth sphere-normal curvature])
               indices  [0 1 3 2]
               vertices [-1.0 -1.0 0.5 0.0 0.0, 1.0 -1.0 0.5 1.0 0.0, -1.0 1.0 0.5 0.0 1.0, 1.0 1.0 0.5 1.0 1.0]
               vao      (make-vertex-array-object program indices vertices ["point" 3 "uv" 2])]
           (framebuffer-render size size :sfsim.render/cullback nil [tex]
                               (use-program program)
                               (uniform-int program "size" size)
                               (uniform-float program "scale" (/ 2.0 size))
                               (uniform-float program "max_curvature_radius" 2.0)
                               (uniform-float program "max_curvature_radius" 1.0)
                               (render-quads vao))
           (let [img (rgba-texture->vectors4 tex)]
             (get-vector4 img 128 128) => (roughly-vector (vec4  1.0  1.0  1.0  1.0) 1e-2)
             (get-vector4 img   0   0) => (roughly-vector (vec4  0.0  0.0 -1.0  0.0) 1e-2)
             (get-vector4 img  64 128) => (roughly-vector (vec4  1.0  0.5  0.71 0.71) 1e-2)
             (get-vector4 img 128  64) => (roughly-vector (vec4  0.5  1.0  0.71 0.71) 1e-2))
           (destroy-vertex-array-object vao)
           (destroy-program program)
           (destroy-texture tex))))


(GLFW/glfwTerminate)
