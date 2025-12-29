;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-plume
    (:require
      [clojure.math :refer (PI to-radians)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [fastmath.vector :refer (vec3)]
      [fastmath.matrix :refer (mulm)]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test is-image)]
      [midje.sweet :refer :all]
      [sfsim.render :refer :all]
      [sfsim.plume :refer :all]
      [sfsim.matrix :refer (projection-matrix rotation-x rotation-y)]
      [sfsim.shaders :as shaders])
    (:import
      (org.lwjgl.glfw
        GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def limit-probe
(template/fn [pressure]
"#version 450 core
out vec3 fragColor;
float plume_limit(float pressure);
void main()
{
  float result = plume_limit(<%= pressure %>);
  fragColor = vec3(result, 0, 0);
}"))

(def limit-test (shader-test (fn [program min-limit] (uniform-float program "min_limit" min-limit)) limit-probe
                             (plume-limit "plume_limit" "min_limit")))


(tabular "Shader function to get extent of plume"
         (fact ((limit-test [?min-limit] [?pressure]) 0) => (roughly ?result 1e-3))
         ?min-limit ?pressure ?result
         0.25       1.0       0.25
         0.25       0.25      0.5
         1.0        0.0       1000.0)


(def rcs-bulge-probe
(template/fn [pressure x]
"#version 450 core
out vec3 fragColor;
float rcs_bulge(float pressure, float x);
void main()
{
  float result = rcs_bulge(<%= pressure %>, <%= x %>);
  fragColor = vec3(result, 0, 0);
}"))


(def rcs-bulge-test (apply shader-test (fn [program nozzle min-limit max-slope]
                                           (uniform-float program "rcs_nozzle" nozzle)
                                           (uniform-float program "rcs_min_limit" min-limit)
                                           (uniform-float program "rcs_max_slope" max-slope))
                           rcs-bulge-probe rcs-bulge))


(tabular "Shader function to determine shape of rocket exhaust plume"
         (fact ((rcs-bulge-test [?nozzle ?min-limit ?max-slope] [?pressure ?x]) 0) => (roughly ?result 1e-3))
         ?nozzle ?min-limit ?max-slope ?pressure  ?x    ?result
         0.25    0.5        1.0        1.0          0.0   0.25
         0.25    0.5        1.0        1.0        100.0   0.5
         0.25    1.0        1.0        0.0          1.0   1.25
         0.25    1.0        1.0        0.25         1.0   0.930)


(def plume-bulge-probe
  (template/fn [pressure x]
"#version 450 core
out vec3 fragColor;
float plume_bulge(float pressure, float x);
void main()
{
  float result = plume_bulge(<%= pressure %>, <%= x %>);
  fragColor = vec3(result, 0, 0);
}"))


(def plume-bulge-test (apply shader-test (fn [program nozzle plume-min-limit plume-max-slope]
                                             (uniform-float program "plume_nozzle" nozzle)
                                             (uniform-float program "plume_min_limit" plume-min-limit)
                                             (uniform-float program "plume_max_slope" plume-max-slope)
                                             (uniform-float program "omega_factor" PI))
                             plume-bulge-probe plume-bulge))


(tabular "Shader function to determine shape of rocket exhaust plume"
         (fact ((plume-bulge-test [?nozzle ?min-limit ?max-slope] [?pressure ?x]) 0) => (roughly ?result 1e-3))
          ?nozzle   ?min-limit ?max-slope ?pressure  ?x    ?result
          0.5       2.0        1.0         1.0          0.0  0.5
          0.5       3.0        1.0         1.0        100.0  3.0
          0.5       0.2        1.0         0.01       100.0  2.0
          1.0       1.0        1.0         0.000001     0.1  1.1
          1.0       0.1        1.0         0.01        10.0  1.0
          1.0       0.5        1.0         1.0          0.0  0.5
          1.0       0.5        1.0         1.0          1.0  1.0
          1.0       0.5        1.0         1.0          2.0  0.5
          1.0       0.5        1.0         1.0          3.0  1.0
          1.0       0.75       1.0         1.0          0.0  0.75
          1.0       0.75       1.0         1.0          2.0  1.0)

(def plume-phase-probe
  (template/fn [x limit]
"#version 450 core
out vec3 fragColor;
float plume_phase(float x, float limit);
void main()
{
  float result = plume_phase(<%= x %>, <%= limit %>);
  fragColor = vec3(result, 0, 0);
}"))

(def plume-phase-test (shader-test (fn [program nozzle]
                                       (uniform-float program "plume_nozzle" nozzle)
                                       (uniform-float program "omega_factor" 10.0))
                                   plume-phase-probe plume-phase))

(tabular "Shader function to determine phase of rocket exhaust plume"
         (fact ((plume-phase-test [?nozzle] [?x ?limit]) 0) => (roughly ?result 1e-6))
          ?nozzle   ?x    ?limit  ?result
          1.0       0.0   0.5      0.0
          1.0       1.0   0.5      5.0
          1.0       2.0   0.5     10.0
          1.0       1.0   0.0     10.0)

(def diamond-phase-probe
  (template/fn [x limit]
"#version 450 core
out vec3 fragColor;
float plume_phase(float x, float limit)
{
  return x * (1.0 - limit);
}
float diamond_phase(float x, float limit);
void main()
{
  float result = diamond_phase(<%= x %>, <%= limit %>);
  fragColor = vec3(result, 0, 0);
}"))

(def diamond-phase-test (shader-test (fn [_program]) diamond-phase-probe (last diamond-phase)))

(tabular "Shader function to determine phase of Mach cones"
         (fact ((diamond-phase-test [] [?x ?limit]) 0) => (roughly ?result 1e-6))
          ?x         ?limit   ?result
          0.0        0.0      0.0
          (* 0.2 PI) 0.0      (*  0.2 PI)
          (* 0.4 PI) 0.0      (* -0.6 PI)
          PI         0.0      0.0
          (* 1.2 PI) 0.0      (*  0.2 PI)
          (* 1.4 PI) 0.0      (* -0.6 PI)
          (* 0.4 PI) 0.5      (*  0.2 PI))

(def diamond-probe
  (template/fn [pressure x y]
"#version 450 core
out vec3 fragColor;
float plume_limit(float pressure)
{
  return 0.5 / pressure;
}
float plume_omega(float limit)
{
  return 2.0 * limit;
}
float diamond_phase(float x, float limit)
{
  return mod(plume_omega(limit) * x + 1.0, 2.0) - 1.0;
}
float diamond(float pressure, vec2 uv);
void main()
{
  float result = diamond(<%= pressure %>, vec2(<%= x %>, <%= y %>));
  fragColor = vec3(result, 0, 0);
}"))

(def diamond-test (shader-test (fn [program strength]
                                   (uniform-float program "diamond_strength" strength)
                                   (uniform-float program "plume_nozzle" 1.0))
                               diamond-probe (last (diamond 0.05))))

(tabular "Shader function for volumetric Mach diamonds"
         (fact (first (diamond-test [?strength] [?pressure ?x ?y])) => (roughly ?result 1e-3))
          ?pressure ?strength ?x   ?y    ?result
          1.0       1.0       0.0  0.0   0.5
          1.0       0.5       0.0  0.0   0.25
          1.0       1.0       0.0  2.0   0.0
          1.0       1.0       0.0 -2.0   0.0
          1.0       1.0       0.0  0.475 0.25
          1.0       1.0       1.0  0.0   0.0
          1.0       1.0       2.0  0.45  0.5
          1.0       1.0       1.5  0.15  0.386
          1.0       1.0       0.5  0.15  0.0
          0.25      1.0       0.0  0.0   0.0)


(def rcs-transfer-probe
  (template/fn [x y step value alpha radius noise]
"#version 450 core
out vec3 fragColor;
float rcs_bulge(float pressure, float x)
{
  if (x < 0.0)
    return 0.0;
  else
    return <%= radius %>;
}
float noise3d(vec3 coordinates)
{
  return <%= noise %>;
}
vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, 0);
  float rcs_step = <%= step %>;
  vec4 scatter = vec4(<%= value %>, <%= value %>, <%= value %>, <%= alpha %>);
  vec4 result = rcs_transfer(point, rcs_step, scatter);
  fragColor = result.rga;
}"))


(def rcs-transfer-test (shader-test (fn [program throttle]
                                        (uniform-float program "pressure" 1.0)
                                        (uniform-float program "rcs_throttle" throttle))
                                    rcs-transfer-probe (last (rcs-transfer 1.0))))


(tabular "Shader function for light transfer in RCS thruster plume"
         (fact (rcs-transfer-test [?throttle] [?x ?y ?step ?value ?alpha ?radius ?noise]) => (roughly-vector (vec3 ?rg ?rg ?a) 1e-3))
          ?x            ?y         ?step ?throttle ?value ?alpha ?radius ?noise ?rg  ?a
          0.0           2.0        1.0   1.0       0.0    1.0    1.0     1.0    0.0  1.0
          0.0           2.0        1.0   1.0       0.5    0.75   1.0     1.0    0.5  0.75
          0.0           0.0        1.0   1.0       0.0    1.0    1.0     1.0    1.0  0.368
          0.0           0.0        0.1   1.0       0.0    1.0    1.0     1.0    0.1  0.905
          0.0           0.0        0.1   1.0       0.5    0.5    1.0     1.0    0.6  0.452
          rcs-end       0.0        1.0   1.0       0.0    1.0    1.0     1.0    0.0  1.0
          (/ rcs-end 2) 0.0        1.0   1.0       0.0    1.0    1.0     1.0    0.5  0.607
          0.0           0.0        1.0   1.0       0.0    1.0    2.0     1.0    0.25 0.779
          0.0           0.0        1.0   1.0       0.0    1.0    1.0     0.0    0.7  0.368
          (/ rcs-end 2) 0.0        1.0   0.5       0.0    1.0    1.0     1.0    0.0  1.0)

(def plume-segment-probe
  (template/fn [outer origin-x x size strength]
"#version 450 core
out vec3 fragColor;
vec4 sample_plume_outer(vec3 object_origin, vec3 object_direction);
vec4 sample_plume_point(vec3 object_origin, vec3 object_direction, float dist);
vec4 plume_transfer(vec3 point, float plume_step, vec4 plume_scatter)
{
  float x = plume_step * <%= strength %>;
  return plume_scatter + vec4(x, x, x, -x);
}
vec2 plume_box(vec3 origin, vec3 direction)
{
  return vec2(-0.5 * <%= size %> - origin.x, <%= size %>);
}
float sampling_offset()
{
  return 0.5;
}
void main()
{
  vec3 origin = vec3(<%= origin-x %>, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec3 object_point = vec3(<%= x %>, 0, 0);
<% (if outer %>
  vec4 plume = sample_plume_outer(origin, direction);
<% %>
  vec4 plume = sample_plume_point(origin, direction, distance(object_point, origin));
<% ) %>
  fragColor = plume.rga;
}"))


(defn plume-segment-test
  [outer]
  (shader-test (fn [program plume-step]
                   (uniform-float program "plume_step" plume-step))
               plume-segment-probe (last (sample-plume-segment outer)) shaders/limit-interval))


(tabular "Shader function to determine rocket plume contribution"
         (fact ((plume-segment-test ?outer) [?plume-step] [?outer ?o-x ?x ?size ?strength])
               => (roughly-vector (vec3 ?result ?result ?alpha) 1e-3))
         ?plume-step ?outer ?o-x  ?x   ?size ?strength ?result ?alpha
         0.1          true   -10.0  0.0  0.0   0.1       0.0    0.0
         0.1          true   -10.0  0.0  2.0   0.1       0.2    0.2
         0.1          false  -10.0 10.0  2.0   0.1       0.2    0.2
         0.1          false  -10.0 -5.0  2.0   0.1       0.0    0.0)


(def rcs-segment-probe
  (template/fn [outer origin-x x size strength]
"#version 450 core
out vec3 fragColor;
vec4 sample_rcs_outer(vec3 object_origin, vec3 object_direction);
vec4 sample_rcs_point(vec3 object_origin, vec3 object_direction, float dist);
vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter)
{
  float x = rcs_step * <%= strength %>;
  return rcs_scatter + vec4(x, x, x, -x);
}
vec2 rcs_box(vec3 origin, vec3 direction)
{
  return vec2(-0.5 * <%= size %> - origin.x, <%= size %>);
}
float sampling_offset()
{
  return 0.5;
}
void main()
{
  vec3 origin = vec3(<%= origin-x %>, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec3 object_point = vec3(<%= x %>, 0, 0);
<% (if outer %>
  vec4 rcs = sample_rcs_outer(origin, direction);
<% %>
  vec4 rcs = sample_rcs_point(origin, direction, distance(object_point, origin));
<% ) %>
  fragColor = rcs.rga;
}"))


(defn rcs-segment-test
  [outer]
  (shader-test (fn [program rcs-step]
                   (uniform-float program "rcs_step" rcs-step))
               rcs-segment-probe (last (sample-rcs-segment outer)) shaders/limit-interval))


(tabular "Shader function to determine RCS plume contribution"
         (fact ((rcs-segment-test ?outer) [?rcs-step] [?outer ?o-x ?x ?size ?strength])
               => (roughly-vector (vec3 ?result ?result ?alpha) 1e-3))
         ?rcs-step ?outer ?o-x  ?x   ?size ?strength ?result ?alpha
         0.1       true   -10.0  0.0  0.0   0.1       0.0    0.0
         0.1       true   -10.0  0.0  2.0   0.1       0.2    0.2
         0.1       false  -10.0 10.0  2.0   0.1       0.2    0.2
         0.1       false  -10.0 -5.0  2.0   0.1       0.0    0.0)


(def plume-box-probe
  (template/fn [limit x-range y-range z-range]
"#version 450 core
out vec3 fragColor;
float plume_limit(float pressure)
{
  return <%= limit %>;
}
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
<% (if x-range %>
  return vec2(box_min.x, box_max.x);
<% ) %>
<% (if y-range %>
  return vec2(box_min.y, box_max.y);
<% ) %>
<% (if z-range %>
  return vec2(box_min.z, box_max.z);
<% ) %>
}
vec2 plume_box(vec3 origin, vec3 direction);
void main()
{
  vec3 origin = vec3(-5, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  fragColor = vec3(plume_box(origin, direction), 0.0);
}"))


(def plume-box-test
  (shader-test (fn [program nozzle throttle max-slope]
                   (uniform-float program "plume_nozzle" nozzle)
                   (uniform-float program "plume_throttle" throttle)
                   (uniform-float program "max_slope" max-slope))
               plume-box-probe [(last plume-box-size) (last plume-box)]))


(tabular "Shader for bounding box computation of plume"
         (fact (plume-box-test [?nozzle ?throttle ?slope] [?limit ?x-range ?y-range ?z-range]) => (roughly-vector (vec3 ?a ?b 0) 1e-4))
         ?limit ?nozzle ?throttle ?slope ?x-range ?y-range ?z-range ?a                   ?b
           0.5  1.0     1.0       100.0  true     false    false    plume-end            0.0
           0.5  1.0     0.0       100.0  true     false    false    0.0                  0.0
           0.5  1.0     1.0       100.0  false    true     false    (- plume-width-2)    plume-width-2
           2.0  1.0     1.0       100.0  false    true     false    (- -1 plume-width-2) (+ 1 plume-width-2)
           0.5  1.0     1.0       100.0  false    false    true     (- plume-width-2)    plume-width-2
         100.0  1.0     0.0         0.5  false    false    true     (- plume-width-2)    plume-width-2
         100.0  1.0     1.0         0.5  false    true     false    (- (+ plume-width-2 (* (- plume-end) 0.5)))
                                                                    (+ (+ plume-width-2 (* (- plume-end) 0.5))))


(def rcs-box-probe
  (template/fn [limit x-range y-range z-range]
"#version 450 core
out vec3 fragColor;
float rcs_limit(float pressure)
{
  return <%= limit %>;
}
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
<% (if x-range %>
  return vec2(box_min.x, box_max.x);
<% ) %>
<% (if y-range %>
  return vec2(box_min.y, box_max.y);
<% ) %>
<% (if z-range %>
  return vec2(box_min.z, box_max.z);
<% ) %>
}
vec2 rcs_box(vec3 origin, vec3 direction);
void main()
{
  vec3 origin = vec3(-5, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  fragColor = vec3(rcs_box(origin, direction), 0.0);
}"))


(def rcs-box-test
  (shader-test (fn [program nozzle throttle max-slope]
                   (uniform-float program "rcs_nozzle" nozzle)
                   (uniform-float program "rcs_throttle" throttle)
                   (uniform-float program "max_slope" max-slope))
               rcs-box-probe [(last rcs-box-size) (last rcs-box)]))


(tabular "Shader for bounding box computation of RCS plume"
         (fact (rcs-box-test [?nozzle ?throttle ?slope] [?limit ?x-range ?y-range ?z-range]) => (roughly-vector (vec3 ?a ?b 0) 1e-4))
         ?limit ?nozzle ?throttle ?slope ?x-range ?y-range ?z-range  ?a                   ?b
           2.0  1.0     1.0       100.0  true     false    false     rcs-end              0.0
           2.0  1.0     0.0       100.0  true     false    false     0.0                  0.0
           2.0  1.0     1.0       100.0  false    true     false    -2.0                  2.0
         100.0  1.0     0.0         0.5  false    false    true     -1.0                  1.0
         100.0  1.0     1.0         0.5  false    true     false    (- (+ 1.0 (* (- rcs-end) 0.5)))
                                                                    (+ (+ 1.0 (* (- rcs-end) 0.5))))


(def plume-outer-probe
  (template/fn [x object-x outer]
"#version 450 core
out vec3 fragColor;
uniform float object_distance;
vec4 sample_plume_outer(vec3 object_origin, vec3 object_direction)
{
  return vec4(object_origin.x, 0.0, 0.0, 0.5);
}
vec4 sample_plume_point(vec3 object_origin, vec3 object_direction, float dist)
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
vec4 plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);
vec4 plume_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
void main()
{
  vec3 origin = vec3(<%= x %>, 0, 0);
  vec3 object_origin = vec3(<%= object-x %>, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec3 object_direction = vec3(1, 0, 0);
  vec3 point = origin + vec3(object_distance, 0, 0);
  vec3 object_point = object_origin + vec3(object_distance, 0, 0);
<% (if outer %>
  fragColor = plume_outer(origin, direction, object_origin, object_direction).rga;
<% %>
  fragColor = plume_point(origin, direction, object_origin, object_direction, object_distance).rga;
<% ) %>
}"))


(def plume-outer-test
  (shader-test (fn [program object-distance]
                   (uniform-float program "radius" 2.0)
                   (uniform-float program "max_height" 1.0)
                   (uniform-float program "object_distance" object-distance))
               plume-outer-probe (last plume-outer) (last plume-point) shaders/limit-interval))


(tabular "Shader for combining plume and atmospheric attenuation"
         (fact (plume-outer-test [?object-distance] [?x ?object-x ?outer]) => (roughly-vector (vec3 ?r ?g ?a) 1e-6))
         ?x ?object-x ?object-distance ?outer ?r  ?g  ?a
         5.0     0.0  2.0              true   0.0 0.0 0.5
         5.0     1.0  2.0              true   1.0 0.0 0.5
         2.0     1.0  2.0              true   0.5 0.0 0.5
        -4.0     1.0  2.0              true   0.5 0.0 0.5
         5.0     0.0  2.0              false  0.0 0.0 0.5
         5.0     1.0  2.0              false  1.0 0.0 0.5
         2.0     1.0  2.0              false  0.5 0.0 0.5
        -4.0     1.0  2.0              false  0.5 0.0 0.5)


(def rcs-outer-probe
  (template/fn [x object-x outer]
"#version 450 core
out vec3 fragColor;
uniform float object_distance;
vec4 sample_rcs_outer(vec3 object_origin, vec3 object_direction)
{
  return vec4(object_origin.x, 0.0, 0.0, 0.5);
}
vec4 sample_rcs_point(vec3 object_origin, vec3 object_direction, float dist)
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
vec4 rcs_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);
vec4 rcs_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
void main()
{
  vec3 origin = vec3(<%= x %>, 0, 0);
  vec3 object_origin = vec3(<%= object-x %>, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec3 object_direction = vec3(1, 0, 0);
  vec3 point = origin + vec3(object_distance, 0, 0);
  vec3 object_point = object_origin + vec3(object_distance, 0, 0);
<% (if outer %>
  fragColor = rcs_outer(origin, direction, object_origin, object_direction).rga;
<% %>
  fragColor = rcs_point(origin, direction, object_origin, object_direction, object_distance).rga;
<% ) %>
}"))

(def rcs-outer-test
  (shader-test (fn [program object-distance]
                   (uniform-float program "radius" 2.0)
                   (uniform-float program "max_height" 1.0)
                   (uniform-float program "object_distance" object-distance))
               rcs-outer-probe (last rcs-outer) (last rcs-point) shaders/limit-interval))

(tabular "Shader for combining RCS and atmospheric attenuation"
         (fact (rcs-outer-test [?object-distance] [?x ?object-x ?outer]) => (roughly-vector (vec3 ?r ?g ?a) 1e-6))
         ?x ?object-x ?object-distance ?outer ?r  ?g  ?a
         5.0     0.0  2.0              true   0.0 0.0 0.5
         5.0     1.0  2.0              true   1.0 0.0 0.5
         2.0     1.0  2.0              true   0.5 0.0 0.5
        -4.0     1.0  2.0              true   0.5 0.0 0.5
         5.0     0.0  2.0              false  0.0 0.0 0.5
         5.0     1.0  2.0              false  1.0 0.0 0.5
         2.0     1.0  2.0              false  0.5 0.0 0.5
        -4.0     1.0  2.0              false  0.5 0.0 0.5)


(def vertex-rotate
"#version 450 core
uniform mat3 rotation;
uniform mat4 projection;
in vec3 point;
void main(void)
{
  gl_Position = projection * vec4(rotation * point + vec3(0, 0, -5), 1.0);
}")


(def fragment-white
  "#version 450 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(1.0, 1.0, 1.0);
}")


(tabular "Vertex array object for rendering plume"
         (fact
           (offscreen-render 64 64
                             (let [program    (make-program :sfsim.render/vertex [vertex-rotate]
                                                            :sfsim.render/fragment [fragment-white])
                                   projection (projection-matrix 64 64 0.1 10.0 (to-radians 90))
                                   vao        (make-vertex-array-object program plume-indices plume-vertices ["point" 3])]
                               (use-program program)
                               (uniform-matrix4 program "projection" projection)
                               (uniform-matrix3 program "rotation" (mulm (rotation-x (to-radians ?alpha))
                                                                         (rotation-y (to-radians ?beta))))
                               (render-quads vao)
                               (destroy-vertex-array-object vao)
                               (destroy-program program)))
                             => (is-image "test/clj/sfsim/fixtures/plume/box.png" 0.0))
         ?alpha ?beta
           0      0
         180      0
           0     90
           0    -90
          90      0
         -90      0)


(GLFW/glfwTerminate)
