;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.plume
    "Module with shader functions for plume rendering"
    (:require
      [comb.template :as template]
      [sfsim.shaders :as shaders]))


(def plume-phase
  "Shader function for phase function of mach cone positions"
  (slurp "resources/shaders/plume/plume-phase.glsl"))


(def diamond-phase
  "Shader function to determine phase of Mach diamonds in rocket exhaust plume"
  [plume-phase (slurp "resources/shaders/plume/diamond-phase.glsl")])


(def plume-limit
  "Shader function to get extent of rocket plume"
  (slurp "resources/shaders/plume/limit.glsl"))


(def bulge
  "Shader function to determine shape of rocket exhaust plume"
  [plume-limit plume-phase (slurp "resources/shaders/plume/bulge.glsl")])


(defn diamond
  "Shader function for volumetric Mach diamonds"
  [fringe]
  [plume-limit diamond-phase plume-phase (template/eval (slurp "resources/shaders/plume/diamond.glsl") {:fringe fringe})])


(def plume-point  ; TODO: implement this
"#version 410 core
vec2 ray_box_copy(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 factors1 = (box_min - origin) / direction;
  vec3 factors2 = (box_max - origin) / direction;
  vec3 intersections1 = min(factors1, factors2);
  vec3 intersections2 = max(factors1, factors2);
  float near = max(max(max(intersections1.x, intersections1.y), intersections1.z), 0);
  float far = min(min(intersections2.x, intersections2.y), intersections2.z);
  return vec2(near, max(far - near, 0));
}
vec4 plume_point(vec3 object_origin, vec3 object_direction, vec3 object_point)
{
  return vec4(0, 0, 0, 0);
  vec2 intersection = ray_box_copy(vec3(-30), vec3(30), object_origin, object_direction);
  intersection.t = min(intersection.t, distance(object_point, object_origin) - intersection.s);
  float transparency = pow(0.98, max(intersection.t, 0.0));
  return vec4(1.0 - transparency);
}")


(defn cloud-plume-segment
  "Shader function to compute cloud and plume RGBA values for segment around plume in space"
  [model-point planet-point]
  [(template/eval (slurp "resources/shaders/plume/cloud-plume-segment.glsl") {:model-point model-point :planet-point planet-point})])
