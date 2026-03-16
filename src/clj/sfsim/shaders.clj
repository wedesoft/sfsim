;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.shaders
  "Module with functions to use in shaders"
  (:require
    [comb.template :as template]))


(def ray-hypersphere
  "Shader function for computing intersection of ray with hypersphere"
  (template/fn [method-name vector-type] (slurp "resources/shaders/core/ray-hypersphere.glsl")))


(def ray-circle
  "Shader function for computing intersection of ray with circle"
  (ray-hypersphere "ray_circle" "vec2"))


(def ray-sphere
  "Shader function for computing intersection of ray with sphere"
  (ray-hypersphere "ray_sphere" "vec3"))


(def convert-1d-index
  "Convert 1D index to 1D texture lookup index avoiding clamping region"
  (slurp "resources/shaders/core/convert-1d-index.glsl"))


(def convert-2d-index
  "Convert 2D index to 2D texture lookup index avoiding clamping region"
  (slurp "resources/shaders/core/convert-2d-index.glsl"))


(def convert-3d-index
  "Convert 3D index to 3D texture lookup index avoiding clamping region"
  (slurp "resources/shaders/core/convert-3d-index.glsl"))


(def convert-cubemap-index
  "Convert cubemap index to avoid clamping regions"
  (slurp "resources/shaders/core/convert-cubemap-index.glsl"))


(def convert-shadow-index
  "Move shadow index out of clamping region"
  (slurp "resources/shaders/core/convert-shadow-index.glsl"))


(def shrink-shadow-index
  "Shrink sampling index to cover full NDC space"
  (slurp "resources/shaders/core/shrink-shadow-index.glsl"))


(def grow-shadow-index
  "Grow sampling index to cover full NDC space"
  (slurp "resources/shaders/core/grow-shadow-index.glsl"))


(defn shadow-lookup
  "Perform lookup in a shadow map including moving shadow index out of clamping region"
  {:malli/schema [:=> [:cat :string :string] [:vector :string]]}
  [method-name shadow-size]
  [convert-shadow-index
   (template/eval (slurp "resources/shaders/core/shadow-lookup.glsl") {:method-name method-name :shadow-size shadow-size})])


(def shadow-cascade-lookup
  "Perform shadow lookup in cascade of shadow maps"
  (template/fn [n base-function] (slurp "resources/shaders/core/shadow-cascade-lookup.glsl")))


(def percentage-closer-filtering
  "Local averaging of shadow to reduce aliasing"
  (template/fn [method-name base-function-name shadow-size parameters]
    (slurp "resources/shaders/core/percentage-closer-filtering.glsl")))


(def make-2d-index-from-4d
  "Convert 4D index to 2D indices for part-manual interpolation"
  (slurp "resources/shaders/core/make-2d-index-from-4d.glsl"))


(def interpolate-2d
  "Perform 2D color interpolation"
  [convert-2d-index (slurp "resources/shaders/core/interpolate-2d.glsl")])


(def interpolate-3d
  "Perform 3D float interpolation"
  [convert-3d-index (slurp "resources/shaders/core/interpolate-3d.glsl")])


(defn interpolate-cubemap
  "Perform interpolation on cubemap avoiding seams"
  {:malli/schema [:=> [:cat :string :string :string] [:vector :string]]}
  [result-type method-name selector]
  [convert-cubemap-index
   (template/eval (slurp "resources/shaders/core/interpolate-cubemap.glsl")
                  {:result-type result-type :method-name method-name :selector selector})])


(def interpolate-float-cubemap
  "Perform floating-point interpolation on cubemap avoiding seams"
  (interpolate-cubemap "float" "interpolate_float_cubemap" "r"))


(def interpolate-vector-cubemap
  "Perform floating-point interpolation on cubemap avoiding seams"
  (interpolate-cubemap "vec3" "interpolate_vector_cubemap" "xyz"))


(def interpolate-4d
  "Perform 4D float interpolation"
  [make-2d-index-from-4d (slurp "resources/shaders/core/interpolate-4d.glsl")])


(def is-above-horizon
  "Check whether a ray hits the ground or stays in the sky"
  (slurp "resources/shaders/core/is-above-horizon.glsl"))


(def ray-box
  "Shader function for computing intersection of ray with box"
  (slurp "resources/shaders/core/ray-box.glsl"))


(def lookup-3d
  "Perform lookup on a floating-point 3D texture"
  (template/fn [method-name sampler] (slurp "resources/shaders/core/lookup-3d.glsl")))


(def lookup-3d-lod
  "Perform lookup on a floating-point 3D texture with level-of-detail"
  (template/fn [method-name sampler] (slurp "resources/shaders/core/lookup-3d-lod.glsl")))


(def ray-shell
  "Shader function for computing intersections of ray with a shell"
  [ray-sphere (slurp "resources/shaders/core/ray-shell.glsl")])


(def limit-interval
  "Shader function to limit clipping interval with a scalar value"
  (slurp "resources/shaders/core/limit-interval.glsl"))


(def clip-interval
  "Shader function to apply clipping interval to input interval"
  (slurp "resources/shaders/core/clip-interval.glsl"))


(def clip-shell-intersections
  "Clip the intersection information of ray and shell using given limit"
  [clip-interval (slurp "resources/shaders/core/clip-shell-intersections.glsl")])


(def horizon-distance
  "Distance from point with specified radius to horizon of planet"
  (slurp "resources/shaders/core/horizon-distance.glsl"))


(def height-to-index
  "Shader for converting height to index"
  [horizon-distance (slurp "resources/shaders/core/height-to-index.glsl")])


(def sun-elevation-to-index
  "Shader for converting sun elevation to index"
  (slurp "resources/shaders/core/sun-elevation-to-index.glsl"))


(def sun-angle-to-index
  "Shader for converting sun angle to index"
  (slurp "resources/shaders/core/sun-angle-to-index.glsl"))


(def limit-quot
  "Shader for computing quotient and keeping it between bounds"
  (slurp "resources/shaders/core/limit-quot.glsl"))


(def elevation-to-index
  "Shader function to convert elevation angle to an index for texture lookup"
  [limit-quot horizon-distance (slurp "resources/shaders/core/elevation-to-index.glsl")])


(def transmittance-forward
  "Convert point and direction to 2D lookup index in transmittance table"
  [height-to-index elevation-to-index (slurp "resources/shaders/core/transmittance-forward.glsl")])


(def surface-radiance-forward
  "Convert point and direction to 2D lookup index in surface radiance table"
  [height-to-index sun-elevation-to-index (slurp "resources/shaders/core/surface-radiance-forward.glsl")])


(def ray-scatter-forward
  "Get 4D lookup index for ray scattering"
  [height-to-index elevation-to-index sun-elevation-to-index sun-angle-to-index
   (slurp "resources/shaders/core/ray-scatter-forward.glsl")])


(def noise-octaves
  "Shader function to sum octaves of noise"
  (template/fn [method-name base-function octaves] (slurp "resources/shaders/core/noise-octaves.glsl")))


(def noise-octaves-lod
  "Shader function to sum octaves of noise with level-of-detail"
  (template/fn [method-name base-function octaves] (slurp "resources/shaders/core/noise-octaves-lod.glsl")))


(def cubemap-vectors
  "Shader functions to convert cubemap face texture coordinates to 3D cubemap vectors"
  (slurp "resources/shaders/core/cubemap-vectors.glsl"))


(def gradient-3d
  "Shader template for 3D gradients"
  (template/fn [method-name function-name epsilon] (slurp "resources/shaders/core/gradient-3d.glsl")))


(def orthogonal-vector
  "Shader for generating an orthogonal vector"
  (slurp "resources/shaders/core/orthogonal-vector.glsl"))


(def oriented-matrix
  "Shader for creating isometry with given normal vector as first row"
  [orthogonal-vector (slurp "resources/shaders/core/oriented-matrix.glsl")])


(def project-vector
  "Shader to project vector x onto vector n"
  (slurp "resources/shaders/core/project-vector.glsl"))


(def rotate-vector
  "Shader for rotating vector around specified axis"
  [oriented-matrix (slurp "resources/shaders/core/rotate-vector.glsl")])


(def rotation-x
  "Shader for creating matrix for rotation around x axis"
  (slurp "resources/shaders/core/rotation-x.glsl"))


(def rotation-y
  "Shader for creating matrix for rotation around y axis"
  (slurp "resources/shaders/core/rotation-y.glsl"))


(def rotation-z
  "Shader for creating matrix for rotation around z axis"
  (slurp "resources/shaders/core/rotation-z.glsl"))


(def vertex-passthrough
  "Vertex shader to simply pass vertex through"
  (slurp "resources/shaders/core/vertex-passthrough.glsl"))


(def scale-noise
  "Shader for calling a noise function with a scaled vector"
  (template/fn [method-name factor noise] (slurp "resources/shaders/core/scale-noise.glsl")))


(def remap
  "Shader for mapping linear range to a new linear range"
  (slurp "resources/shaders/core/remap.glsl"))


(defn shadow-lookup-shaders
  "Shaders for performing lookups in the cascaded shadow map"
  {:malli/schema [:=> [:cat :int] [:vector :string]]}
  [num-steps]
  [(shadow-cascade-lookup num-steps "average_shadow") (shadow-lookup "shadow_lookup" "shadow_size") convert-shadow-index
   (percentage-closer-filtering "average_shadow" "shadow_lookup" "shadow_size" [["sampler2DShadow" "shadow_map"]])])


(def phong
  "Shader for phong shading (ambient, diffuse, and specular lighting)"
  (slurp "resources/shaders/core/phong.glsl"))


(def sdf-circle
  "Shader for computing signed distance function of a circle"
  (slurp "resources/shaders/core/sdf-circle.glsl"))


(def sdf-rectangle
  "Shader for computing signed distance function of a rectangle"
  (slurp "resources/shaders/core/sdf-rectangle.glsl"))


(def hermite-interpolate
  "Shader for performing cubic Hermite interpolation"
  (slurp "resources/shaders/core/hermite-interpolate.glsl"))


(def interpolate-function
  "Shader to interpolate a function using only samples from whole coordinates"
  (template/fn [method-name interpolation base-function] (slurp "resources/shaders/core/interpolate-function.glsl")))


(def hash3d
  "Shader function to create random noise"
  (slurp "resources/shaders/core/hash3d.glsl"))


(def noise3d
  "Shader function to create continuous 3D noise"
  [(interpolate-function "noise3d" "hermite_interpolate" "hash3d") hermite-interpolate hash3d])


(def subtract-interval
  "Shader function to subtract two intervals"
  (slurp "resources/shaders/core/subtract-interval.glsl"))
