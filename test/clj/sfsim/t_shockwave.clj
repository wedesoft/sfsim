;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-shockwave
    (:require
      [clojure.math :refer (sqrt)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [midje.sweet :refer :all]
      [fastmath.vector :refer (vec3 vec4)]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test is-image)]
      [sfsim.render :refer :all]
      [sfsim.texture :refer (destroy-texture rgba-texture->vectors4 make-vector-texture-2d)]
      [sfsim.image :refer (get-vector4 set-vector4!)]
      [sfsim.shaders :refer (vertex-passthrough) :as shaders]
      [sfsim.clouds :as clouds]
      [sfsim.shockwave :refer :all])
    (:import
      (org.lwjgl.glfw
        GLFW)
      (org.lwjgl.opengl
        GL11)))

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


(def normal-mock
"#version 450 core
vec4 normal_source(vec2 uv)
{
  vec2 offset = uv * 2.0 - 1.0;
  if (length(offset) < 1.0)
    return vec4(offset, sqrt(1.0 - length(offset)), 0.0);
  else
    return vec4(0.0, 0.0, 0.0, 0.0);
}")


(def depth-mock
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


(def shockfront-mock
"#version 450 core
float shockfront(float radial_distance, float curvature_radius)
{
  return -radial_distance * curvature_radius;
}")


(fact "Estimate curvature off surface given normals"
      (offscreen-render
        256 256
        (let [indices  [0 1 3 2]
              vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
              program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                     :sfsim.render/fragment [fragment-curvature-test curvature normal-mock])
              vao      (make-vertex-array-object program indices vertices ["point" 3])]
          (clear (vec3 0.0 0.0 0.0))
          (use-program program)
          (uniform-int program "size" 256)
          (render-quads vao)
          (destroy-vertex-array-object vao)
          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/shockwave/curvature.png" 0.1))


(facts "Initial step of Jump Flooding Algorithm"
       (with-invisible-window
         (let [renderer (make-shockwave-renderer depth-mock normal-mock shockfront-mock 256 {:sfsim.texture/width 256} 1.0 1.0)
               tex      (jump-flooding-initialisation renderer identity)]
           (let [img (rgba-texture->vectors4 tex)]
             (get-vector4 img 128 128) => (roughly-vector (vec4  0.0  0.0  2.0  0.91) 1e-2)
             (get-vector4 img   0   0) => (roughly-vector (vec4 -1.0 -1.0 -2.0  0.0 ) 1e-2)
             (get-vector4 img  64 128) => (roughly-vector (vec4  0.0 -0.5  1.42 0.58) 1e-2)
             (get-vector4 img 128  64) => (roughly-vector (vec4 -0.5  0.0  1.42 0.58) 1e-2))
           (destroy-texture tex)
           (destroy-shockwave-renderer renderer))))


(facts "Jump flood algorithm"
       (with-invisible-window
         (let [size     256
               image    {:sfsim.image/width size :sfsim.image/height size :sfsim.image/data (float-array (* size size 4))
                         :sfsim.image/channels 4}
               renderer (make-shockwave-renderer depth-mock normal-mock shockfront-mock 256 {:sfsim.texture/width 256} 1.0 1.0)]
           (set-vector4! image 128  64 (vec4 -0.5 0.0 1.0 1.0))
           (set-vector4! image 128 192 (vec4  0.5 0.0 1.0 1.0))
           (let [flood  (make-vector-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp image)
                 flood  (reduce (jump-flooding-step renderer identity) flood [128 64 32 16 8 4 2 1])
                 result (rgba-texture->vectors4 flood)]
             (get-vector4 result 128  64) => (vec4 -0.5 0.0 1.0 1.0)
             (get-vector4 result 128 192) => (vec4  0.5 0.0 1.0 1.0)
             (get-vector4 result 128  96) => (vec4 -0.5 0.0 1.0 1.0)
             (get-vector4 result 128 160) => (vec4  0.5 0.0 1.0 1.0)
             (destroy-texture flood)
             (destroy-shockwave-renderer renderer)))))


(facts "Test halving sequence"
       (halving 1) => []
       (halving 2) => [1]
       (halving 4) => [2 1]
       (halving 8) => [4 2 1])


(def shockwave-outer-probe
  (template/fn [x object-x outer]
"#version 450 core
out vec3 fragColor;
uniform float object_distance;
vec4 sample_shockwave_outer(vec3 object_origin, vec3 object_direction)
{
  return vec4(object_origin.x, 0.0, 0.0, 0.5);
}
vec4 sample_shockwave_point(vec3 object_origin, vec3 object_direction, float dist)
{
  return vec4(object_origin.x, 0.0, 0.0, 0.5);
}
vec2 ray_sphere(vec3 center, float radius, vec3 origin, vec3 direction)
{
  float start = max(0.0, - radius - origin.x);
  float end = radius - origin.x;
  return vec2(start, end - start);
}
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming)
{
  return incoming * pow(0.5, max(segment.y, 0.0));
}
vec4 shockwave_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);
vec4 shockwave_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
void main()
{
  vec3 origin = vec3(<%= x %>, 0, 0);
  vec3 object_origin = vec3(<%= object-x %>, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec3 object_direction = vec3(1, 0, 0);
  vec3 point = origin + vec3(object_distance, 0, 0);
  vec3 object_point = object_origin + vec3(object_distance, 0, 0);
<% (if outer %>
  fragColor = shockwave_outer(origin, direction, object_origin, object_direction).rga;
<% %>
  fragColor = shockwave_point(origin, direction, object_origin, object_direction, object_distance).rga;
<% ) %>
}"))


(def shockwave-outer-test
  (shader-test (fn [program object-distance]
                   (uniform-float program "radius" 2.0)
                   (uniform-float program "max_height" 1.0)
                   (uniform-float program "object_distance" object-distance))
               shockwave-outer-probe (last shockwave-outer) (last shockwave-point) shaders/limit-interval))


(tabular "Shader for combining shockwave and atmospheric attenuation"
         (fact (shockwave-outer-test [?object-distance] [?x ?object-x ?outer]) => (roughly-vector (vec3 ?r ?g ?a) 1e-6))
         ?x ?object-x ?object-distance ?outer ?r  ?g  ?a
         5.0     0.0  2.0              true   0.0 0.0 0.5
         5.0     1.0  2.0              true   1.0 0.0 0.5
         2.0     1.0  2.0              true   0.5 0.0 0.5
        -4.0     1.0  2.0              true   0.5 0.0 0.5
         5.0     0.0  2.0              false  0.0 0.0 0.5
         5.0     1.0  2.0              false  1.0 0.0 0.5
         2.0     1.0  2.0              false  0.5 0.0 0.5
        -4.0     1.0  2.0              false  0.5 0.0 0.5)


(def fragment-mock-geometry
"#version 450 core
layout (location = 0) out vec4 camera_point;
layout (location = 1) out float dist;
uniform vec3 point;
void main()
{
  camera_point = vec4(point, 1.0);
  dist = length(point);
}")


(defn mock-geometry
  [x y z stencil]
  (let [geometry-program (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                       :sfsim.render/fragment [fragment-mock-geometry])
        indices          [0 1 3 2]
        vertices         [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        vao              (make-vertex-array-object geometry-program indices vertices ["point" 3])
        result           (clouds/render-cloud-geometry 1 1
                                                       (use-program geometry-program)
                                                       (uniform-vector3 geometry-program "point" (vec3 x y z))
                                                       (clear (vec3 0.0 0.0 0.0) 0.0 0)
                                                       (with-stencils  ; 0x4: model, 0x2: planet, 0x1: atmosphere
                                                         (with-stencil-op-ref-and-mask GL11/GL_ALWAYS stencil stencil
                                                           (render-quads vao))))]
    (destroy-program geometry-program)
    (assoc result :vao vao)))


(tabular "Use geometry buffer to render shockwave overlays"
         (facts
           (with-redefs []
             (with-invisible-window
               (let [geometry (mock-geometry ?x ?y ?z ?stencil)]
                 (clouds/destroy-cloud-geometry geometry)))))
         ?stencil ?x   ?y  ?z
         0x1      1.0 0.0 0.0)


(GLFW/glfwTerminate)
