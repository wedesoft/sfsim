;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-plume
    (:require
      [clojure.math :refer (PI)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [fastmath.vector :refer (vec3)]
      [fastmath.matrix :refer (diagonal)]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test)]
      [midje.sweet :refer :all]
      [sfsim.render :refer :all]
      [sfsim.plume :refer :all]
      [sfsim.shaders :as shaders])
    (:import
      (org.lwjgl.glfw
        GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(def bulge-probe
  (template/fn [pressure x]
"#version 450 core
out vec3 fragColor;
float bulge(float pressure, float x);
void main()
{
  float result = bulge(<%= pressure %>, <%= x %>);
  fragColor = vec3(result, 0, 0);
}"))

(def bulge-test (apply shader-test (fn [program nozzle min-limit max-slope]
                                       (uniform-float program "nozzle" nozzle)
                                       (uniform-float program "min_limit" min-limit)
                                       (uniform-float program "max_slope" max-slope)
                                       (uniform-float program "omega_factor" PI))
                       bulge-probe bulge))

(tabular "Shader function to determine shape of rocket exhaust plume"
         (fact ((bulge-test [?nozzle ?min-limit ?max-slope] [?pressure ?x]) 0) => (roughly ?result 1e-3))
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
                                       (uniform-float program "nozzle" nozzle)
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
float limit(float pressure)
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
                                   (uniform-float program "nozzle" 1.0))
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

(def cloud-plume-segment-probe
  (template/fn [x plume model-point planet-point attenuation]
"#version 450 core
uniform vec3 origin;
uniform float radius;
out vec3 fragColor;
vec4 cloud_plume_cloud(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction);
vec4 cloud_plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);
vec4 cloud_plume_cloud_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  float start = max(0.0, -3.0 - origin.x);
  float end = 3.0 - origin.x;
  return vec2(start, end - start);
}
vec4 cloud_outer(vec3 origin, vec3 direction, float skip)
{
  vec3 point = origin + direction * skip;
  vec2 segment = ray_sphere(vec3(0, 0, 0), radius, point, direction);
  if (segment.t > 0) {
    float transmittance = pow(0.5, segment.t);
    return vec4(1.0 - transmittance, 0.0, 0.0, 1.0 - transmittance);
  } else
    return vec4(0, 0, 0, 0);
}
vec3 transmittance_track(vec3 p, vec3 q)
{
  float dist = distance(p, q);
  return vec3(pow(0.5, <%= attenuation %> * dist));
}
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment)
{
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius, origin, direction);
  if (atmosphere.s > segment.s) {
    segment.t += segment.s - atmosphere.s;
    segment.s = atmosphere.s;
  };
  if (atmosphere.s + atmosphere.t < segment.s + segment.t) {
    segment.t = atmosphere.s + atmosphere.t - segment.s;
  };
  if (segment.t > 0) {
    vec3 multiplier = vec3(1, 1, 1);
    if (atmosphere.s < segment.s)
      multiplier = transmittance_track(origin + direction * atmosphere.s, origin + direction * segment.s);
    float transmittance = pow(0.5, segment.t);
    return vec4(vec3(1.0 - transmittance, 0.0, 0.0) * multiplier, 1.0 - transmittance);
  } else
    return vec4(0, 0, 0, 0);
}
vec4 plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
{
  float plume = object_origin.y;
  return vec4(0.0, plume, 0.0, plume);
}
vec4 plume_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
{
  float plume = object_origin.y;
  return vec4(0.0, plume, 0.0, plume);
}
void main()
{
  vec3 point = vec3(<%= x %>, 0.0, 0.0);
  vec3 direction = vec3(1.0, 0.0, 0.0);
  vec3 object_origin = vec3(0.0, <%= plume %>, 0.0);
  float dist = distance(point, origin);
<% (if (and (not planet-point) (not model-point)) %>
  vec4 result = cloud_plume_cloud(origin, direction, object_origin, direction);
<% ) %>
<% (if (and planet-point (not model-point)) %>
  vec4 result = cloud_plume_cloud_point(origin, direction, object_origin, direction, dist);
<% ) %>
<% (if model-point %>
  vec4 result = cloud_plume_point(origin, direction, object_origin, direction, dist);
<% ) %>
  fragColor = result.rga;
}"))

(defn cloud-plume-segment-test
  [model-point planet-point]
  (shader-test (fn [program origin-x object-distance]
                   (uniform-float program "radius" 2.0)
                   (uniform-float program "max_height" 1.0)
                   (uniform-float program "opacity_cutoff" 0.0)
                   (uniform-float program "object_distance" object-distance)
                   (uniform-vector3 program "light_direction" (vec3 0 1 0))
                   (uniform-vector3 program "origin" (vec3 origin-x 0.0 0.0)))
               cloud-plume-segment-probe (last (cloud-plume-segment model-point planet-point))))

(tabular "Shader function to determine cloud and rocket plume contribution"
         (fact ((cloud-plume-segment-test ?model ?planet) [?ox ?d] [?x ?plume ?model ?planet ?attenuation])
               => (roughly-vector (vec3 ?r ?g ?a) 1e-3))
         ?ox  ?x  ?d  ?plume ?model ?planet ?attenuation ?r    ?g    ?a
         4.0  0.0 0.0 0.0    false  false   0.0          0.0   0.0   0.0
         1.0  0.0 2.0 0.0    false  false   0.0          0.75  0.0   0.75
         4.0  0.0 0.0 1.0    false  false   0.0          0.0   1.0   1.0
         2.0  0.0 1.0 1.0    false  false   0.0          0.5   0.5   1.0
         1.0  0.0 1.0 0.5    false  false   0.0          0.625 0.25  0.875
         2.0  0.0 2.0 0.0    false  false   0.0          0.5   0.0   0.5
         4.0  0.0 2.0 0.0    false  false   0.0          0.0   0.0   0.0
         2.0  0.0 2.0 0.0    false  false   0.0          0.5   0.0   0.5
         2.0  0.0 0.0 0.0    false  false   0.0          0.5   0.0   0.5
         2.0  0.0 1.0 0.0    false  false   0.0          0.5   0.0   0.5
         0.0  0.0 0.0 0.0    true   false   0.0          0.0   0.0   0.0
        -1.0  1.0 2.0 0.0    true   false   0.0          0.75  0.0   0.75
        -6.0 -5.0 1.0 1.0    true   false   0.0          0.0   1.0   1.0
        -1.0  0.0 1.0 1.0    true   false   0.0          0.5   0.5   1.0
        -1.0  1.0 1.0 0.5    false  true    0.0          0.625 0.25  0.875
        -4.0 -2.0 2.0 0.0    true   false   0.0          0.5   0.0   0.5
        -6.0 -4.0 2.0 0.0    true   false   0.0          0.0   0.0   0.0
         2.0  4.0 2.0 0.0    true   false   0.0          0.5   0.0   0.5
        -4.0 -2.0 0.0 0.0    false  true    0.0          0.5   0.0   0.5
         2.0  4.0 0.0 0.0    false  true    0.0          0.5   0.0   0.5
        -1.0  1.0 1.0 0.0    true   false   0.0          0.5   0.0   0.5
        -6.0 -5.0 1.0 1.0    false  true    0.0          0.0   1.0   1.0
        -6.0 -5.0 1.0 1.0    true   false   0.0          0.0   1.0   1.0
        -1.0  1.0 1.0 1.0    false  false   1.0          0.5   0.5   1.0
        -6.0 -5.0 1.0 1.0    false  false   1.0          0.0   1.0   1.0
        -4.0 -2.0 2.0 1.0    true   false   1.0          0.5   0.5   1.0
         2.0  4.0 2.0 1.0    true   false   1.0          0.5   0.5   1.0
        -1.0  1.0 3.0 0.5    false  true    1.0          0.75  0.125 0.875
         5.0  7.0 2.0 0.5    false  false   1.0          0.0   0.5   0.5)

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
  (shader-test (fn [program engine-step]
                   (uniform-float program "engine_step" engine-step))
               plume-segment-probe (last (sample-plume-segment outer)) shaders/limit-interval))

(tabular "Shader function to determine rocket plume contribution"
         (fact ((plume-segment-test ?outer) [?engine-step] [?outer ?o-x ?x ?size ?strength])
               => (roughly-vector (vec3 ?result ?result ?alpha) 1e-3))
         ?engine-step ?outer ?o-x  ?x   ?size ?strength ?result ?alpha
         0.1          true   -10.0  0.0  0.0   0.1       0.0    0.0
         0.1          true   -10.0  0.0  2.0   0.1       0.2    0.2
         0.1          false  -10.0 10.0  2.0   0.1       0.2    0.2
         0.1          false  -10.0 -5.0  2.0   0.1       0.0    0.0)

(def plume-box-probe
  (template/fn [limit x-range y-range z-range]
"#version 450 core
out vec3 fragColor;
float limit(float pressure)
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
  (shader-test (fn [program nozzle throttle]
                   (uniform-float program "nozzle" nozzle)
                   (uniform-float program "throttle" throttle))
               plume-box-probe (last plume-box)))

(tabular "Shader for bounding box computation of plume"
         (fact (plume-box-test [?nozzle ?throttle] [?limit ?x-range ?y-range ?z-range]) => (roughly-vector (vec3 ?a ?b 0) 1e-6))
         ?limit ?nozzle ?throttle ?x-range ?y-range ?z-range ?a                ?b
         0.5    1.0     1.0       true     false    false    plume-end            plume-start
         0.5    1.0     0.0       true     false    false    plume-start          plume-start
         0.5    1.0     1.0       false    true     false    (- plume-width-2)    plume-width-2
         2.0    1.0     1.0       false    true     false    (- -1 plume-width-2) (+ 1 plume-width-2)
         0.5    1.0     1.0       false    false    true     (- plume-width-2)    plume-width-2)

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


(GLFW/glfwTerminate)
