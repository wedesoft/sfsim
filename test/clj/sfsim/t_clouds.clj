;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-clouds
  (:require
    [clojure.math :refer (exp log sin cos asin atan to-radians PI E)]
    [comb.template :as template]
    [fastmath.matrix :refer (mat3x3 eye inverse)]
    [fastmath.vector :refer (vec3 vec4)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.atmosphere :refer (vertex-atmosphere)]
    [sfsim.clouds :refer :all]
    [sfsim.conftest :refer (roughly-vector shader-test is-image)]
    [sfsim.image :refer (get-vector3 get-vector4 get-float-3d get-float)]
    [sfsim.matrix :refer (transformation-matrix projection-matrix shadow-matrix-cascade)]
    [sfsim.planet :refer (vertex-planet tess-control-planet tess-evaluation-planet geometry-planet make-cube-map-tile-vertices)]
    [sfsim.model :refer (read-gltf
                         load-scene-into-opengl destroy-scene material-type make-joined-geometry-renderer render-joined-geometry
                         destroy-joined-geometry-renderer)]
    [sfsim.render :refer :all]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer :all]
    [sfsim.util :refer (slurp-floats)]
    [sfsim.worley :refer (worley-size)])
  (:import
    (org.lwjgl.glfw
      GLFW)
   (org.lwjgl.opengl
      GL11 GL30)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(def cloud-noise-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
uniform sampler3D worley;
float cloud_octaves(vec3 idx, float lod)
{
  return texture(worley, idx).r;
}
float cloud_noise(vec3 point, float lod);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_noise(point, 0.0);
  fragColor = vec3(result, 0, 0);
}"))


(defn cloud-noise-test
  [scale octaves x y z]
  (with-invisible-window
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data     (cons 1.0 (repeat (dec (* 2 2 2)) 0.0))
          worley   (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat #:sfsim.image{:width 2 :height 2 :depth 2 :data (float-array data)})
          program  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                 :sfsim.render/fragment [(cloud-noise-probe x y z) (last (cloud-noise []))])
          vao      (make-vertex-array-object program indices vertices ["point" 3])
          tex      (texture-render-color 1 1 true
                                         (use-program program)
                                         (uniform-sampler program "worley" 0)
                                         (uniform-float program "detail_scale" scale)
                                         (use-textures {0 worley})
                                         (render-quads vao))
          img      (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture worley)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Shader to sample 3D cloud noise texture"
         (fact ((cloud-noise-test ?scale ?octaves ?x ?y ?z) 0) => (roughly ?result 1e-5))
         ?scale ?octaves ?x   ?y   ?z   ?result
         1.0    [1.0]    0.25 0.25 0.25 1.0
         1.0    [1.0]    0.25 0.75 0.25 0.0
         2.0    [1.0]    0.5  0.5  0.5  1.0)


(def lod-at-distance-probe
  (template/fn [distance lod-offset]
"#version 450 core
out vec3 fragColor;
float lod_at_distance(float dist, float lod_offset);
void main()
{
  fragColor = vec3(lod_at_distance(<%= distance %>, <%= lod-offset %>), 0, 0);
}"))


(def lod-at-distance-test
  (shader-test
    (fn [program])
    lod-at-distance-probe
    lod-at-distance))


(tabular "Shader function for determining level of detail at a distance"
         (fact ((lod-at-distance-test [] [?distance ?lod-offset]) 0) => (roughly ?result 1e-5))
         ?distance ?lod-offset ?result
         1.0       0.0         0.0
         1.0       3.0         3.0
         2.0       3.0         4.0)


(def sampling-probe
  (template/fn [distance stepsize]
    "#version 450 core
out vec3 fragColor;
float update_stepsize(float dist, float stepsize);
void main()
{
  fragColor = vec3(update_stepsize(<%= distance %>, <%= stepsize %>), 0, 0);
}"))


(def linear-sampling-test
  (shader-test
    (fn [program])
    sampling-probe
    linear-sampling))


(tabular "Trivial shader functions for updating linear sampling stepsize"
         (fact ((linear-sampling-test [] [?distance ?stepsize]) 0) => (roughly ?result 1e-5))
         ?distance ?stepsize ?result
         100.0       2.0       2.0
         100.0       5.0       5.0)


(def exponential-sampling-test
  (shader-test
    (fn [program linear-range stepsize-factor]
        (uniform-float program "linear_range" linear-range)
        (uniform-float program "stepsize_factor" stepsize-factor))
    sampling-probe
    exponential-sampling))


(tabular "Shader functions for updating exponential sampling stepsize"
         (fact ((exponential-sampling-test [?linear-range ?stepsize-factor] [?distance ?stepsize]) 0) => (roughly ?result 1e-5))
         ?linear-range ?stepsize-factor ?distance ?stepsize ?result
         100.0         2.0              50.0      10.0       10.0
         100.0         2.0              120.0     10.0       20.0)


(def ray-shell-mock
  "#version 450 core
uniform int num_shell_intersections;
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  if (num_shell_intersections > 1)
    return vec4(origin.z - outer_radius + abs(origin.x),
                outer_radius - inner_radius,
                origin.z + inner_radius,
                outer_radius - inner_radius);
  else
    return vec4(origin.z - outer_radius,
                2 * outer_radius,
                0,
                0);
}")


(def cloud-density-mock
  "#version 450 core
uniform float radius;
uniform float density_start;
uniform float cloud_multiplier;
float cloud_density(vec3 point, float lod)
{
  if (point.z <= density_start)
    return cloud_multiplier * pow(0.5, lod);
  else
    return -1.0;
}")


(defn setup-opacity-fragment-static-uniforms
  [program]
  (uniform-int program "shadow_size" 3)
  (uniform-float program "radius" 1000.0)
  (uniform-float program "cloud_bottom" 100.0)
  (uniform-float program "cloud_top" 200.0))


(defn setup-opacity-fragment-dynamic-uniforms
  [program shadow-ndc-to-world light-direction shells multiplier scatter
   depth opacitystep start lod]
  (uniform-matrix4 program "shadow_ndc_to_world" shadow-ndc-to-world)
  (uniform-vector3 program "light_direction" light-direction)
  (uniform-int program "num_shell_intersections" shells)
  (uniform-float program "cloud_multiplier" multiplier)
  (uniform-float program "scatter_amount" scatter)
  (uniform-float program "depth" depth)
  (uniform-float program "opacity_step" opacitystep)
  (uniform-float program "density_start" start)
  (uniform-float program "level_of_detail" lod))


(defn render-deep-opacity-map
  [program vao opacity-layers depth z shells multiplier scatter start opacitystep lod]
  (let [shadow-ndc-to-world   (transformation-matrix (mat3x3 1 1 depth) (vec3 0 0 (- z depth)))]
    (framebuffer-render 3 3 :sfsim.render/cullback nil [opacity-layers]
                        (use-program program)
                        (setup-opacity-fragment-static-uniforms program)
                        (setup-opacity-fragment-dynamic-uniforms program shadow-ndc-to-world (vec3 0 0 1) shells
                                                                 multiplier scatter depth opacitystep start lod)
                        (render-quads vao))))


(tabular "Compute deep opacity map offsets and layers"
         (fact
           (with-invisible-window
             (let [indices         [0 1 3 2]
                   vertices        [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
                   program         (make-program :sfsim.render/vertex [opacity-vertex]
                                                 :sfsim.render/fragment [(last (opacity-fragment 7 [] [])) ray-shell-mock
                                                                         cloud-density-mock linear-sampling])
                   vao             (make-vertex-array-object program indices vertices ["point" 2])
                   opacity-layers  (make-empty-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp 3 3 8)
                   index           ({:offset 0 :layer (inc ?layer)} ?selector)]
               (render-deep-opacity-map program vao opacity-layers ?depth ?z ?shells ?multiplier ?scatter ?start ?opacitystep ?lod)
               (get-float-3d (float-texture-3d->floats opacity-layers) index 1 ?px) => (roughly ?result 1e-6)
               (destroy-texture opacity-layers)
               (destroy-vertex-array-object vao)
               (destroy-program program))))
         ?shells ?px ?depth  ?opacitystep ?scatter ?start  ?multiplier ?lod ?z   ?selector ?layer ?result
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1200 :offset   0      1
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1400 :offset   0      (- 1 0.2)
         2       0    1000.0  50.0        0.0       1200.0 0.02        0.0  1400 :offset   0      (- 1 0.201)
         2       1    1000.0  50.0        0.0       1150.0 0.02        0.0  1200 :offset   0      (- 1 0.05)
         2       1   10000.0  50.0        0.0      -9999.0 0.02        0.0  1200 :offset   0      0.0
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1200 :layer    0      1.0
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1200 :layer    1      (exp -1)
         2       1    1000.0  50.0        0.0       1200.0 0.02        1.0  1200 :layer    1      (exp -0.5)
         2       1    1000.0  50.0        0.5       1200.0 0.02        0.0  1200 :layer    1      (exp -0.5)
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1400 :layer    1      (exp -1)
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1200 :layer    2      (exp -2)
         2       1    1000.0  50.0        0.0       1200.0 0.02        0.0  1200 :layer    3      (exp -2)
         2       1   10000.0  50.0        0.0          0.0 0.02        0.0  1200 :layer    0      1.0)


(def opacity-lookup-probe
  (template/fn [x y z depth]
    "#version 450 core
out vec3 fragColor;
uniform sampler3D opacity_layers;
float opacity_lookup(sampler3D layers, float depth, vec4 opacity_map_coords);
void main()
{
  vec4 opacity_map_coords = vec4(<%= x %>, <%= y %>, <%= z %>, 1.0);
  float result = opacity_lookup(opacity_layers, <%= depth %>, opacity_map_coords);
  fragColor = vec3(result, result, result);
}"))


(defn opacity-lookup-test
  [offset step depth x y z]
  (with-invisible-window
    (let [indices         [0 1 3 2]
          vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program         (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                        :sfsim.render/fragment [(opacity-lookup-probe x y z depth) opacity-lookup])
          vao             (make-vertex-array-object program indices vertices ["point" 3])
          zeropad         (fn [x] [0 x 0 0])
          offset-data     (zeropad offset)
          opacity-data    (flatten (map (partial repeat 4) [1.0 0.9 0.8 0.7 0.6 0.5 0.4]))
          data            (concat offset-data opacity-data)
          opacity-layers  (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp
                                                 #:sfsim.image{:width 2 :height 2 :depth 8 :data (float-array data)})
          tex             (texture-render-color 1 1 true
                                                (use-program program)
                                                (uniform-sampler program "opacity_layers" 0)
                                                (uniform-float program "opacity_step" step)
                                                (uniform-int program "num_opacity_layers" 7)
                                                (uniform-int program "shadow_size" 2)
                                                (use-textures {0 opacity-layers})
                                                (render-quads vao))
          img             (rgb-texture->vectors3 tex)]
      (destroy-texture opacity-layers)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Lookup values from deep opacity map taking into account offsets"
         (fact ((opacity-lookup-test ?offset ?step ?depth ?x ?y ?z) 0) => (roughly ?result 1e-6))
         ?offset ?step ?depth ?x    ?y ?z        ?result
         1.0     1.0    6      1     0  1         1.0
         1.0     1.0    6      1     0  0         0.4
         0.0     1.0    6      1     0  0         1.0
         1.0     1.0    12     1     0  0.5       0.4
         1.0     1.0    6      1     0  (/ 5.0 6) 0.9
         1.0     2.0    12     1     0  (/ 5.0 6) 0.9
         1.0     1.0    6      0.75  0  0.5       0.85)


(def opacity-lookup-mock
  "#version 450 core
uniform int select;
float opacity_lookup(sampler3D layers, float depth, vec4 opacity_map_coords)
{
  if (select == 0)
    return texture(layers, vec3(0.5, 0.5, 0.75)).r;
  else
    return opacity_map_coords.x;
}")


(def opacity-cascade-lookup-probe
  (template/fn [z]
    "#version 450 core
out vec3 fragColor;
float opacity_cascade_lookup(vec4 point);
void main()
{
  vec4 point = vec4(0, 0, <%= z %>, 1);
  float result = opacity_cascade_lookup(point);
  fragColor = vec3(result, 0, 0);
}"))


(defn opacity-cascade-lookup-test
  [n z shift-z opacities offsets bias select]
  (with-invisible-window
    (let [indices         [0 1 3 2]
          vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          world-to-camera (transformation-matrix (eye 3) (vec3 0 0 shift-z))
          program         (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                        :sfsim.render/fragment [(opacity-cascade-lookup-probe z)
                                                                (opacity-cascade-lookup n "opacity_lookup")
                                                                opacity-lookup-mock])
          vao             (make-vertex-array-object program indices vertices ["point" 3])
          opacity-texs    (map #(make-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp
                                                       #:sfsim.image{:width 1 :height 1 :depth 1 :data (float-array [%1 %2])})
                               opacities offsets)
          tex             (texture-render-color 1 1 true
                                                (use-program program)
                                                (uniform-matrix4 program "world_to_camera" world-to-camera)
                                                (uniform-vector3 program "light_direction" (vec3 1 0 0))
                                                (doseq [idx (range n)]
                                                  (uniform-sampler program (str "opacity" idx) idx)
                                                  (uniform-float program (str "depth" idx) 200.0)
                                                  (uniform-float program (str "bias" idx) (* bias idx)))
                                                (doseq [idx (range n)]
                                                  (uniform-matrix4 program (str "world_to_shadow_map" idx)
                                                                   (transformation-matrix (eye 3)
                                                                                          (vec3 (inc idx) 0 0))))
                                                (doseq [idx (range (inc n))]
                                                  (uniform-float program (str "split" idx)
                                                                 (+ 10.0 (/ (* 30.0 idx) n))))
                                                (uniform-int program "select" ({:opacity 0 :coord 1} select))
                                                (use-textures (zipmap (range) opacity-texs))
                                                (render-quads vao))
          img             (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (doseq [tex opacity-texs] (destroy-texture tex))
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform opacity (transparency) lookup in cascade of deep opacity maps"
         (fact ((opacity-cascade-lookup-test ?n ?z ?shift-z ?opacities ?offsets ?bias ?select) 0) => (roughly ?result 1e-6))
         ?n ?z  ?shift-z ?opacities ?offsets ?bias ?select  ?result
         1  -10  0       [0.75]     [0]      0.0   :opacity 0.75
         2  -40  0       [0.75 0.5] [0 0]    0.0   :opacity 0.5
         2  -50 10       [0.75 0.5] [0 0]    0.0   :opacity 0.5
         2  -50  0       [0.75 0.5] [0 0]    0.0   :opacity 1.0
         1  -10  0       [1.0]      [0]      0.0   :coord   1.0
         2  -10  0       [1.0]      [0]      0.0   :coord   1.0
         2  -40  0       [1.0]      [0]      0.0   :coord   2.0
         2  -10  0       [1.0]      [0]      1.0   :coord   1.0
         2  -40  0       [1.0]      [0]      1.0   :coord   3.0)


(def cubemap-probe
  (template/fn [x y z]
    "#version 450 core
uniform samplerCube cubemap;
out vec3 fragColor;
vec3 convert_cubemap_index(vec3 idx, int size);
void main()
{
  vec3 idx = convert_cubemap_index(vec3(<%= x %>, <%= y %>, <%= z %>), 15);
  fragColor = texture(cubemap, idx).rgb;
}"))


(defn identity-cubemap-test
  [x y z]
  (with-invisible-window
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                 :sfsim.render/fragment [(cubemap-probe x y z) shaders/convert-cubemap-index])
          vao      (make-vertex-array-object program indices vertices ["point" 3])
          cubemap  (identity-cubemap 15)
          tex      (texture-render-color 1 1 true
                                         (use-program program)
                                         (uniform-sampler program "cubemap" 0)
                                         (use-textures {0 cubemap})
                                         (render-quads vao))
          img      (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture cubemap)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Create identity cubemap"
         (fact (identity-cubemap-test ?x ?y ?z) => (roughly-vector (vec3 ?x ?y ?z) 1e-6))
         ?x ?y  ?z
         1  0   0
         -1  0   0
         0  1   0
         0 -1   0
         0  0   1
         0  0  -1
         1  0.5 0.25)


(def curl-field-mock
  "#version 450 core
uniform float x;
uniform float y;
uniform float z;
vec3 curl_field_mock(vec3 point)
{
  if (point.x >= 0)
    return vec3(x, y, z);
  else
    return vec3(0, 0, 0);
}")


(defn update-cubemap
  [program current scale x y z]
  (iterate-cubemap 15 scale program
                   (uniform-sampler program "current" 0)
                   (uniform-float program "x" x)
                   (uniform-float program "y" y)
                   (uniform-float program "z" z)
                   (use-textures {0 current})))


(defn iterate-cubemap-warp-test
  [n scale px py pz x y z]
  (with-invisible-window
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                 :sfsim.render/fragment [(cubemap-probe px py pz) shaders/convert-cubemap-index])
          vao      (make-vertex-array-object program indices vertices ["point" 3])
          cubemap  (atom (identity-cubemap 15))
          update   (make-iterate-cubemap-warp-program "current" "curl_field_mock" [curl-field-mock])]
      (dotimes [i n]
        (let [updated (update-cubemap update @cubemap scale x y z)]
          (destroy-texture @cubemap)
          (reset! cubemap updated)))
      (let [tex (texture-render-color 1 1 true
                                      (use-program program)
                                      (uniform-sampler program "cubemap" 0)
                                      (use-textures {0 @cubemap})
                                      (render-quads vao))
            img (rgb-texture->vectors3 tex)]
        (destroy-texture tex)
        (destroy-texture @cubemap)
        (destroy-program update)
        (destroy-vertex-array-object vao)
        (destroy-program program)
        (get-vector3 img 0 0)))))


(tabular "Update normalised cubemap warp vectors using specified vectors"
         (fact (iterate-cubemap-warp-test ?n ?scale ?px ?py ?pz ?x ?y ?z) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-3))
         ?n ?scale ?px ?py ?pz ?x  ?y  ?z   ?rx   ?ry   ?rz
         0  1.0    1   0   0   0.0 0.0 0.0  1     0     0
         0  1.0   -1   0   0   0.0 0.0 0.0 -1     0     0
         0  1.0    0   1   0   0.0 0.0 0.0  0     1     0
         0  1.0    0  -1   0   0.0 0.0 0.0  0    -1     0
         0  1.0    0   0   1   0.0 0.0 0.0  0     0     1
         0  1.0    0   0  -1   0.0 0.0 0.0  0     0    -1
         1  1.0    1   0   0   2.0 0.0 0.0  3     0     0
         1  1.0    1   0   0   0.0 1.0 0.0  1     1     0
         1  1.0   -1   0   0   0.0 1.0 0.0 -1     0     0
         1  0.5    1   0   0   2.0 0.0 0.0  2     0     0
         2  1.0    1   0   0   0.0 1.0 0.0  0.707 1.707 0)


(def lookup-mock
  "#version 450 core
uniform int selector;
float lookup_mock(vec3 point)
{
  if (selector == 0)
    return point.x;
  if (selector == 1)
    return point.y;
  if (selector == 2)
    return point.z;
  return 2.0;
}")


(defn cubemap-warp-test
  [px py pz selector]
  (with-invisible-window
    (let [indices  [0 1 3 2]
          vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          program  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                 :sfsim.render/fragment [(cubemap-probe px py pz) shaders/convert-cubemap-index])
          vao      (make-vertex-array-object program indices vertices ["point" 3])
          vectors  [[1 0 0] [0 2 0] [0 0 4] [-1 0 0] [0 -1 0] [0 0 -1]]
          to-data  (fn [v] (float-array (flatten (repeat 9 v))))
          current  (make-vector-cubemap :sfsim.texture/linear :sfsim.texture/clamp (mapv (fn [v] #:sfsim.image{:width 3 :height 3 :data (to-data v)}) vectors))
          warp     (make-cubemap-warp-program "current" "lookup_mock" [lookup-mock])
          warped   (cubemap-warp 3 warp
                                 (uniform-sampler warp "current" 0)
                                 (uniform-int warp "selector" selector)
                                 (use-textures {0 current}))
          tex      (texture-render-color 1 1 true
                                         (use-program program)
                                         (uniform-sampler program "cubemap" 0)
                                         (use-textures {0 warped})
                                         (render-quads vao))
          img      (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture warped)
      (destroy-texture current)
      (destroy-program warp)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      ((get-vector3 img 0 0) 0))))


(tabular "Lookup floating-point values using cubemap warp vector field"
         (fact (cubemap-warp-test ?px ?py ?pz ?selector) => (roughly ?result 1e-3))
         ?px ?py ?pz ?selector ?result
         +1   0   0   0         1
         +1   0   0   1         0
         +1   0   0   2         0
         -1   0   0   1         1
         +0   1   0   2         1
         +0  -1   0   0        -1
         +0   0   1   1        -1
         +0   0  -1   2        -1)


(def noise-mock
  "#version 450 core
uniform float dx;
uniform float dy;
uniform float dz;
float noise_mock(vec3 point)
{
  return dot(point, vec3(dx, dy, dz));
}")


(def curl-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec3 curl(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  fragColor = curl(point);
}"))


(def curl-test
  (shader-test
    (fn [program epsilon dx dy dz]
      (uniform-float program "epsilon" epsilon)
      (uniform-float program "dx" dx)
      (uniform-float program "dy" dy)
      (uniform-float program "dz" dz))
    curl-probe
    (curl-vector "curl" "gradient")
    (shaders/gradient-3d "gradient" "noise_mock" "epsilon")
    noise-mock))


(tabular "Shader for computing curl vectors from noise function"
         (fact (curl-test [0.125 ?dx ?dy ?dz] [?x ?y ?z]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-3))
         ?dx ?dy ?dz ?x ?y ?z ?rx ?ry ?rz
         0.0 0.0 0.0 1  0  0  0   0   0
         0.0 0.1 0.0 1  0  0  0   0   0.1
         0.0 0.0 0.1 1  0  0  0  -0.1 0
         0.2 0.1 0.0 1  0  0  0   0   0.1)


(def flow-field-probe
  (template/fn [north south x y z]
    "#version 450 core
out vec3 fragColor;
float octaves_north(vec3 idx)
{
  return <%= north %> + idx.z;
}
float octaves_south(vec3 idx)
{
  return <%= south %>;
}
float flow_field(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = flow_field(point);
  fragColor = vec3(result, 0, 0);
}"))


(def flow-field-test
  (shader-test
    (fn [program curl-scale prevailing whirl]
      (uniform-float program "curl_scale" curl-scale)
      (uniform-float program "prevailing" prevailing)
      (uniform-float program "whirl" whirl))
    flow-field-probe
    (last (flow-field []))))


(tabular "Shader to create potential field for generating curl noise for global cloud cover"
         (fact ((flow-field-test [?curl-scale ?prevailing ?whirl] [?north ?south ?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?curl-scale ?prevailing ?whirl ?north ?south ?x                     ?y                     ?z ?result
         1.0         0.0         0.0    0.0    0.0    1                      0                      0  0
         1.0         1.0         0.0    0.0    0.0    0                     -1                      0  1
         1.0         1.0         0.0    0.0    0.0    1                      0                      0  0
         1.0         1.0         0.0    0.0    0.0    (cos (/ (asin 0.5) 3)) (sin (/ (asin 0.5) 3)) 0  0.5
         1.0         0.0         1.0    1.0    0.3    0                     -1                      0  1
         1.0         0.0         1.0    0.3    1.0    0                      1                      0 -1
         1.0         0.0         0.5    1.0    0.0    0                     -1                      0  0.5
         1.0         0.0         0.5    0.0    1.0    0                      1                      0 -0.5
         1.0         0.0         1.0    0.0    0.0    0                      0                      1  0.25
         2.0         0.0         1.0    0.0    0.0    0                      0                      1  0.125)


(def cover-vertex
  "#version 450 core
in vec3 point;
out VS_OUT
{
  vec3 point;
  vec3 object_direction;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")


(def cover-fragment
  "#version 450 core
uniform samplerCube cubemap;
uniform float threshold;
uniform float multiplier;
in VS_OUT
{
  vec3 point;
  vec3 object_direction;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec4 fragColor;
void main()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = vec3(fs_in.point.xy, -1 + intersection.x);
    float value = texture(cubemap, p).r;
    value = (value - threshold) * multiplier;
    fragColor = vec4(value, value, 1.0, 1.0);
  } else
    fragColor = vec4(0, 0, 0, 1.0);
}")


(fact "Program to generate planetary cloud cover using curl noise"
      (with-invisible-window
        (let [worley-size  8
              worley-north (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                                  #:sfsim.image{:width worley-size :height worley-size :depth worley-size
                                                                :data (slurp-floats "test/clj/sfsim/fixtures/clouds/worley-north.raw")})
              worley-south (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                                  #:sfsim.image{:width worley-size :height worley-size :depth worley-size
                                                                :data (slurp-floats "test/clj/sfsim/fixtures/clouds/worley-south.raw")})
              worley-cover (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                                  #:sfsim.image{:width worley-size :height worley-size :depth worley-size
                                                                :data (slurp-floats "test/clj/sfsim/fixtures/clouds/worley-cover.raw")})
              program      (make-program :sfsim.render/vertex [cover-vertex]
                                         :sfsim.render/fragment [cover-fragment shaders/ray-sphere])
              indices      [0 1 3 2]
              vertices     [-1 -1 0, 1 -1 0, -1 1 0, 1 1 0]
              vao          (make-vertex-array-object program indices vertices ["point" 3])
              cubemap      (cloud-cover-cubemap :sfsim.clouds/size 256
                                                :sfsim.clouds/worley-size worley-size
                                                :sfsim.clouds/worley-south worley-south
                                                :sfsim.clouds/worley-north worley-north
                                                :sfsim.clouds/worley-cover worley-cover
                                                :sfsim.clouds/flow-octaves [0.5 0.25 0.125]
                                                :sfsim.clouds/cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                                                :sfsim.clouds/whirl 2.0
                                                :sfsim.clouds/prevailing 0.1
                                                :sfsim.clouds/curl-scale 1.0
                                                :sfsim.clouds/cover-scale 2.0
                                                :sfsim.clouds/num-iterations 50
                                                :sfsim.clouds/flow-scale 1.5e-3)
              tex          (texture-render-color 128 128 false
                                                 (clear (vec3 1 0 0))
                                                 (use-program program)
                                                 (uniform-sampler program "cubemap" 0)
                                                 (uniform-float program "threshold" 0.3)
                                                 (uniform-float program "multiplier" 4.0)
                                                 (use-textures {0 cubemap})
                                                 (render-quads vao))
              img          (texture->image tex)]
          (destroy-texture cubemap)
          (destroy-vertex-array-object vao)
          (destroy-program program)
          (destroy-texture worley-cover)
          (destroy-texture worley-south)
          (destroy-texture worley-north)
          (destroy-texture tex)
          img))
      => (is-image "test/clj/sfsim/fixtures/clouds/cover.png" 0.13))


(def cloud-profile-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float cloud_profile(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_profile(point);
  fragColor = vec3(result, 0, 0);
}"))


(def cloud-profile-test
  (shader-test
    (fn [program radius cloud-bottom cloud-top]
      (uniform-float program "radius" radius)
      (uniform-float program "cloud_bottom" cloud-bottom)
      (uniform-float program "cloud_top" cloud-top))
    cloud-profile-probe cloud-profile))


(tabular "Shader for creating vertical cloud profile"
         (fact ((cloud-profile-test [?radius ?bottom ?top] [?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?radius ?bottom ?top ?x    ?y  ?z ?result
         100.0   10.0    18.0 110   0   0  0.0
         100.0   10.0    18.0 110.5 0   0  0.5
         100.0   10.0    18.0 111   0   0  1.0
         100.0   10.0    18.0 113.5 0   0  0.7
         100.0   10.0    18.0 116   0   0  0.4
         100.0   10.0    18.0 117   0   0  0.2
         100.0   10.0    18.0 118   0   0  0.0)


(def sphere-noise-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float base_noise(vec3 point)
{
  return point.x;
}
float sphere_noise(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float noise = sphere_noise(point);
  fragColor = vec3(noise, 0, 0);
}"))


(def sphere-noise-test
  (shader-test
    (fn [program radius cloud-scale]
      (uniform-float program "radius" radius)
      (uniform-float program "cloud_scale" cloud-scale))
    sphere-noise-probe
    (sphere-noise "base_noise")))


(tabular "Sample 3D noise on the surface of a sphere"
         (fact ((sphere-noise-test [?radius ?cloud-scale] [?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?radius ?cloud-scale ?x  ?y  ?z  ?result
         100.0   100.0        1.0 0.0 0.0   1.0
         100.0    10.0        1.0 0.0 0.0  10.0
         100.0    10.0       -1.0 0.0 0.0 -10.0
         100.0    10.0        2.0 0.0 0.0  10.0)


(def cloud-base-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float cloud_cover(vec3 point)
{
  return point.x >= 0.0 ? 1.0 : 0.0;
}
float sphere_noise(vec3 point)
{
  return point.y >= 0.0 ? 0.4 : 0.0;
}
float cloud_profile(vec3 point)
{
  return max(1.0 - 0.01 * abs(length(point) - 1000), 0.0);
}
float cloud_base(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_base(point);
  fragColor = vec3(result, 0, 0);
}"))


(def cloud-base-test
  (shader-test
    (fn [program cover clouds threshold]
      (uniform-float program "cover_multiplier" cover)
      (uniform-float program "cloud_multiplier" clouds)
      (uniform-float program "cloud_threshold" threshold))
    cloud-base-probe
    (last (cloud-base []))))


(tabular "Shader for determining cloud density at specified point"
         (fact ((cloud-base-test [?cover ?clouds ?threshold] [?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?cover ?clouds ?threshold ?x   ?y ?z ?result
         1.0     1.0     0.0       1000 -1  0  1.0
         1.0     1.0     0.0      -1000 -1  0  0.0
         0.5     1.0     0.0       1000 -1  0  0.5
         0.0     1.0     0.0       1000  1  0  0.4
         0.0     0.5     0.0       1000  1  0  0.2
         1.0     1.0     0.5       1000  1  0  0.9
         1.0     1.0     0.5        950  1  0  0.45
         1.0     1.0     3.0       1000 -1  0 -1.0
         1.0     1.0     1.5        950  1  0  0.0)


(def cloud-cover-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float cloud_cover(vec3 idx);
void main()
{
  float result = cloud_cover(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))


(defn cloud-cover-test
  [x y z]
  (with-invisible-window
    (let [indices     [0 1 3 2]
          vertices    [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          datas       [[0 1 0 1] [2 2 2 2] [3 3 3 3] [4 4 4 4] [5 5 5 5] [6 6 6 6]]
          data->image (fn [data] #:sfsim.image{:width 2 :height 2 :data (float-array data)})
          cube        (make-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp (mapv data->image datas))
          program     (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                    :sfsim.render/fragment [(cloud-cover-probe x y z) cloud-cover])
          vao         (make-vertex-array-object program indices vertices ["point" 3])
          tex         (texture-render-color
                        1 1 true
                        (use-program program)
                        (uniform-sampler program "cover" 0)
                        (uniform-int program "cover_size" 2)
                        (use-textures {0 cube})
                        (render-quads vao))
          img         (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture cube)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform cloud cover lookup in cube map"
         (fact ((cloud-cover-test ?x ?y ?z) 0) => ?result)
         ?x   ?y ?z  ?result
         1    0   0   0.5
         1    0  -1   1.0
         1    0   0.5 0.25)


(def cloud-density-probe
  (template/fn [lod x y z]
    "#version 450 core
out vec3 fragColor;
float cloud_base(vec3 point)
{
  return point.x;
}
float cloud_noise(vec3 point, float lod)
{
  return point.y - lod;
}
float cloud_density(vec3 point, float lod);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_density(point, <%= lod %>);
  fragColor = vec3(result, 0, 0);
}"))


(def cloud-density-test
  (shader-test
    (fn [program cap]
      (uniform-float program "cap" cap))
    cloud-density-probe
    (last (cloud-density [] []))
    shaders/remap))


(tabular "Compute cloud density at given point"
         (fact ((cloud-density-test [?cap] [?lod ?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?cap ?lod ?x  ?y   ?z  ?result
         1.0  0.0  0.0 0.0  0.0 0.0
         1.0  0.0  1.0 1.0  0.0 1.0
         1.0  0.0  1.0 0.5  0.0 0.5
         1.0  0.0  0.5 0.75 0.0 0.5
         0.6  0.0  1.0 1.0  0.0 0.6
         1.0  0.0  2.0 0.5  0.0 0.75
         1.0  0.0  0.5 0.25 0.0 0.0
         1.0  0.5  1.0 1.0  0.0 0.5)


(def cloud-transfer-probe
  (template/fn [red alpha density stepsize selector scatter]
    "#version 450 core
uniform float shadow;
uniform float transmittance;
uniform float atmosphere;
uniform float in_scatter;
uniform float amplification;
float planet_and_cloud_shadows(vec4 point)
{
  return shadow;
}
vec3 transmittance_outer(vec3 point, vec3 direction)
{
  return direction * transmittance;
}
vec3 transmittance_track(vec3 p, vec3 q)
{
  return (q - p) * atmosphere;
}
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  return (q - p) * in_scatter;
}
vec4 attenuate(vec3 light_direction, vec3 start, vec3 point, vec4 incoming)
{
  vec3 transmittance = transmittance_track(start, point);
  vec3 in_scatter = ray_scatter_track(light_direction, start, point) * amplification;
  return vec4(incoming.rgb * transmittance + in_scatter * incoming.a, incoming.a);
}
float powder (float d)
{
  return 1.0;
}
vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density);
out vec3 fragColor;
void main()
{
  vec3 start = vec3(1, 0, 0);
  vec3 point = vec3(2, 0, 0);
  vec4 cloud_scatter = vec4(<%= red %>, 0, 0, <%= alpha %>);
  fragColor.r = cloud_transfer(start, point, <%= scatter %>, <%= stepsize %>, cloud_scatter, <%= density %>).<%= selector %>;
}"))


(def cloud-transfer-test
  (shader-test
    (fn [program shadow transmittance atmosphere in-scatter amplification]
      (uniform-float program "shadow" shadow)
      (uniform-float program "transmittance" transmittance)
      (uniform-float program "atmosphere" atmosphere)
      (uniform-float program "in_scatter" in-scatter)
      (uniform-float program "amplification" amplification)
      (uniform-vector3 program "light_direction" (vec3 1 0 0)))
    cloud-transfer-probe
    (last (cloud-transfer 3))))


(tabular "Shader function to increment scattering caused by clouds"
         (fact ((cloud-transfer-test [?shadow ?transmit ?atmos ?inscatter ?amp]
                                     [?red ?alpha ?density ?stepsize ?selector ?scatter]) 0)
               => (roughly ?result 1e-6))
         ?shadow ?transmit ?atmos ?inscatter ?amp ?red ?alpha ?density  ?stepsize ?scatter ?selector ?result
         1.0     1.0       1.0    0.0        1.0  0.0  1.0    0.0       1.0       1.0      "r"       0.0
         1.0     1.0       1.0    0.0        1.0  0.5  1.0    0.0       1.0       1.0      "r"       0.5
         1.0     1.0       1.0    0.0        1.0  1.0  0.5    0.0       1.0       1.0      "a"       0.5
         1.0     1.0       1.0    0.0        1.0  1.0  1.0    0.0       1.0       1.0      "a"       1.0
         1.0     1.0       1.0    0.0        1.0  1.0  0.5    (log 2.0) 1.0       1.0      "a"       0.25
         1.0     1.0       1.0    0.0        1.0  1.0  0.5    (log 2.0) 2.0       1.0      "a"       0.125
         1.0     1.0       1.0    0.0        1.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       0.5
         1.0     1.0       1.0    0.0        1.0  0.0  1.0    (log 2.0) 1.0       0.5      "r"       0.25
         0.5     1.0       1.0    0.0        1.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       0.25
         1.0     0.5       1.0    0.0        1.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       0.25
         1.0     1.0       1.0    0.0        1.0  0.0  0.5    (log 2.0) 1.0       1.0      "r"       0.25
         1.0     1.0       0.5    0.0        1.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       0.25
         1.0     1.0       1.0    0.5        1.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       0.75
         1.0     1.0       1.0    0.5        1.0  0.0  0.5    (log 2.0) 1.0       1.0      "r"       0.375
         1.0     1.0       1.0    0.5        2.0  0.0  1.0    (log 2.0) 1.0       1.0      "r"       1.0)


(def sample-cloud-probe
  (template/fn [cloud-begin cloud-length density red alpha selector]
    "#version 450 core
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);
out vec3 fragColor;
float sampling_offset()
{
  return 0.5;
}
float phase(float g, float mu)
{
  return 1.0;
}
float cloud_density(vec3 point, float lod)
{
  return <%= density %>;
}
vec4 cloud_transfer(vec3 start, vec3 point, float scatter_amount, float stepsize, vec4 cloud_scatter, float density)
{
  float transparency = pow(0.5, density * stepsize);
  cloud_scatter.a *= transparency;
  return cloud_scatter;
}
void main()
{
  vec3 origin = vec3(3, 0, 0);
  vec3 start = vec3(5, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  vec2 cloud_shell = vec2(<%= cloud-begin %>, <%= cloud-length %>);
  vec4 cloud_scatter = vec4(<%= red %>, 0, 0, <%= alpha %>);
  fragColor.r = sample_cloud(origin, start, direction, cloud_shell, cloud_scatter).<%= selector %>;
}"))


(def sample-cloud-test
  (shader-test
    (fn [program cloud-step opacity-cutoff]
      (uniform-float program "cloud_step" cloud-step)
      (uniform-float program "lod_offset" 0.0)
      (uniform-float program "anisotropic" 0.5)
      (uniform-vector3 program "light_direction" (vec3 0 1 0))
      (uniform-float program "opacity_cutoff" opacity-cutoff))
    sample-cloud-probe
    (last (sample-cloud 3 [] []))
    linear-sampling lod-at-distance))


(tabular "Shader to sample the cloud layer and apply cloud scattering update steps"
         (fact ((sample-cloud-test [?step ?cutoff] [?begin ?length ?density ?red ?alpha ?selector]) 0)
               => (roughly ?result 1e-6))
         ?begin ?length ?step ?density ?red ?alpha ?selector ?cutoff ?result
         2.0    0.0     1.0   1.0      0.0  1.0    "r"       0.0     0.0
         2.0    0.0     1.0   1.0      1.0  1.0    "r"       0.0     1.0
         2.0    0.0     1.0   1.0      1.0  1.0    "a"       0.0     1.0
         2.0    0.0     1.0   1.0      1.0  0.8    "a"       0.0     0.8
         2.0    1.0     1.0   1.0      0.0  1.0    "a"       0.0     0.5
         2.0    2.0     1.0   1.0      0.0  1.0    "a"       0.0     0.25
         2.0    2.0     2.0   1.0      0.0  1.0    "a"       0.0     0.25
         2.0    2.0     1.0   0.5      0.0  1.0    "a"       0.0     0.5
         2.0    1.0     1.0   0.0      0.0  1.0    "a"       0.0     1.0
         2.0    2.0     1.0   1.0      0.0  1.0    "a"       0.5     0.5)


(def cloud-point-probe
  (template/fn [z skip selector]
    "#version 450 core
uniform vec3 origin;
uniform float radius;
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  float start = max(origin.z - radius, 0);
  float end = max(origin.z + radius, 0);
  return vec2(start, (start - end) / direction.z);
}
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  float start = max(origin.z - outer_radius, 0);
  float end = max(origin.z - inner_radius, 0);
  return vec4(start, (start - end) / direction.z, 0, 0);
}
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter)
{
  return vec4(start.z, cloud_shell.t, 0, (1 - cloud_shell.t) * cloud_scatter.a);
}
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment);
void main()
{
  vec3 point = vec3(0, 0, <%= z %>);
  vec3 direction = normalize(point - origin);
  vec2 segment = vec2(<%= skip %>, distance(point, origin));
  vec4 result = cloud_point(origin, direction, segment);
  fragColor.r = result.<%= selector %>;
}"))


(def cloud-point-test
  (shader-test
    (fn [program origin depth]
      (uniform-float program "radius" 10.0)
      (uniform-float program "max_height" 3.0)
      (uniform-float program "cloud_bottom" 1.0)
      (uniform-float program "cloud_top" 2.0)
      (uniform-float program "depth" depth)
      (uniform-vector3 program "origin" (vec3 0 0 origin)))
    cloud-point-probe shaders/clip-interval shaders/limit-interval
    (last shaders/clip-shell-intersections)
    (last (cloud-point 3 [] []))))


(tabular "Shader to compute pixel of cloud foreground overlay for planet"
         (fact ((cloud-point-test [?origin ?depth] [?z ?skip ?selector]) 0) => (roughly ?result 1e-6))
         ?origin ?z ?skip ?depth ?selector ?result
         11      10 0     100.0  "g"        0.0
         11      10 0     100.0  "a"        0.0
         12      10 0     100.0  "g"        1.0
         12      10 0     100.0  "a"        1.0
         12      10 0     100.0  "r"       12.0
         14      10 0     100.0  "r"       13.0
         13      10 0     100.0  "a"        1.0
         13      10 0       1.5  "a"        0.5
         12      10 1     100.0  "g"        0.0
         12      10 1     100.0  "a"        0.0)


(def cloud-outer-probe
  (template/fn [dz skip selector]
    "#version 450 core
uniform vec3 origin;
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  if (direction.z > 0) {
    float start = 0;
    float end = max(radius - origin.z, 0);
    return vec2(start, end - start);
  } else {
    float start = max(origin.z - radius, 0);
    float end = max(radius + origin.z, 0);
    return vec2(start, end - start);
  }
}
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  if (direction.z > 0) {
    float start = max(inner_radius - origin.z, 0);
    float end = max(outer_radius - origin.z, 0);
    return vec4(start, end - start, 0, 0);
  } else {
    float start1 = max(origin.z - outer_radius, 0);
    float end1 = max(origin.z - inner_radius, 0);
    float start2 = max(origin.z + inner_radius, 0);
    float end2 = max(origin.z + outer_radius, 0);
    return vec4(start1, end1 - start1, start2, end2 - start2);
  }
}
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter)
{
  return vec4(start.z, cloud_scatter.g + cloud_shell.t, 0, (1 - cloud_shell.t) * cloud_scatter.a);
}
vec4 cloud_outer(vec3 origin, vec3 direction, float skip);
void main()
{
  vec3 direction = vec3(0, 0, <%= dz %>);
  vec4 result = cloud_outer(origin, direction, <%= skip %>);
  fragColor.r = result.<%= selector %>;
}"))


(def cloud-outer-test
  (shader-test
    (fn [program z]
      (uniform-float program "radius" 10.0)
      (uniform-float program "max_height" 3.0)
      (uniform-float program "cloud_bottom" 1.0)
      (uniform-float program "cloud_top" 2.0)
      (uniform-vector3 program "origin" (vec3 0 0 z)))
    cloud-outer-probe
    shaders/clip-interval
    (last shaders/clip-shell-intersections)
    (last (cloud-outer 3 [] []))))


(tabular "Shader to compute pixel of cloud foreground overlay for atmosphere"
         (fact ((cloud-outer-test [?z] [?dz ?skip ?selector]) 0) => (roughly ?result 1e-6))
         ?z   ?dz ?skip ?selector ?result
         14    1  0     "g"        0.0
         14    1  0     "a"        0.0
         11    1  0     "g"        1.0
         11    1  0     "a"        1.0
         11.5  1  0     "g"        0.5
         11.5  1  0     "a"        0.5
         13   -1  0     "g"        2.0
         12   -1  0     "r"       12.0
         15   -1  0     "r"       13.0
         11    1  3     "g"        0.0
         11    1  3     "a"        0.0
         15   -1  3     "r"       12.0)


(def fragment-planet-clouds
  "#version 450 core
uniform vec3 origin;
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
  vec3 object_point;
  vec4 camera_point;
} fs_in;
out vec4 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  return vec2(origin.z - radius, 2 * radius);
}
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  return vec4(origin.z - outer_radius, outer_radius - inner_radius, 0.0, 0.0);
}
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter)
{
  return vec4(cloud_shell.y, 0, 0, 0.25);
}
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment);
void main()
{
  vec2 segment = vec2(0, distance(fs_in.point, origin));
  vec3 direction = normalize(fs_in.point - origin);
  fragColor = cloud_point(origin, direction, segment);
}")


(def fragment-atmosphere-clouds-mock
  "#version 450 core
uniform vec3 origin;
in VS_OUT
{
  vec3 direction;
  vec3 object_direction;
} fs_in;
out vec4 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction)
{
  if (length(direction.xy) <= 0.5)
    return vec2(origin.z - radius, 2 * radius);
  else
    return vec2(0, 0);
}
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter)
{
  return vec4(0, cloud_shell.y, 0, 1 - cloud_shell.y / 2);
}
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction)
{
  return vec4(origin.z - outer_radius, outer_radius - inner_radius, 0.0, 0.0);
}
vec4 cloud_outer(vec3 origin, vec3 direction, float skip);
void main()
{
  fragColor = cloud_outer(origin, normalize(fs_in.direction), 0.0);
}")


(fact "Test rendering of cloud overlay image"
      (with-invisible-window
        (let [width           160
              height          120
              fov             (to-radians 60.0)
              z-near          1.0
              z-far           100.0
              tilesize        33
              data            (flatten (for [y (range -1.0 1.0625 0.0625) x (range -1.0 1.0625 0.0625)] [x y 5.0]))
              surface         (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                      #:sfsim.image{:width tilesize :height tilesize :data (float-array data)})
              projection      (projection-matrix width height z-near (+ z-far 1) fov)
              origin          (vec3 0 0 10)
              camera-to-world (transformation-matrix (eye 3) origin)
              planet          (make-program :sfsim.render/vertex [vertex-planet]
                                            :sfsim.render/tess-control [tess-control-planet]
                                            :sfsim.render/tess-evaluation [(tess-evaluation-planet 0)]
                                            :sfsim.render/geometry [(geometry-planet 0)]
                                            :sfsim.render/fragment [fragment-planet-clouds shaders/clip-interval
                                                                    shaders/limit-interval
                                                                    (last shaders/clip-shell-intersections)
                                                                    (last (cloud-point 3 [] []))])
              indices         [0 1 3 2]
              vertices        [-1 -1 5 0 0 0 0, 1 -1 5 1 0 1 0, -1 1 5 0 1 0 1, 1 1 5 1 1 1 1]
              tile            (make-vertex-array-object planet indices vertices ["point" 3 "surfacecoord" 2 "colorcoord" 2])
              atmosphere      (make-program :sfsim.render/vertex [vertex-atmosphere]
                                            :sfsim.render/fragment [fragment-atmosphere-clouds-mock
                                                                    shaders/clip-interval
                                                                    (last shaders/clip-shell-intersections)
                                                                    (last (cloud-outer 3 [] []))])
              indices         [0 1 3 2]
              vertices        [-1 -1, 1 -1, -1 1, 1 1]
              vao             (make-vertex-array-object atmosphere indices vertices ["ndc" 2])
              tex             (texture-render-color-depth width height true
                                                          (clear (vec3 0 0 0) 0.0)
                                                          (use-program planet)
                                                          (uniform-sampler planet "surface" 0)
                                                          (uniform-matrix4 planet "projection" projection)
                                                          (uniform-vector3 planet "tile_center" (vec3 0 0 0))
                                                          (uniform-matrix4 planet "tile_to_camera" (inverse camera-to-world))
                                                          (uniform-vector3 planet "origin" origin)
                                                          (uniform-vector3 planet "light_direction" (vec3 1 0 0))
                                                          (uniform-float planet "radius" 5.0)
                                                          (uniform-float planet "max_height" 4.0)
                                                          (uniform-float planet "cloud_bottom" 1.0)
                                                          (uniform-float planet "cloud_top" 2.0)
                                                          (uniform-float planet "depth" 3.0)
                                                          (uniform-int planet "neighbours" 15)
                                                          (uniform-int planet "high_detail" (dec tilesize))
                                                          (uniform-int planet "low_detail" (quot (dec tilesize) 2))
                                                          (use-textures {0 surface})
                                                          (render-patches tile)
                                                          (use-program atmosphere)
                                                          (uniform-matrix4 atmosphere "inverse_projection" (inverse projection))
                                                          (uniform-matrix4 atmosphere "camera_to_world" camera-to-world)
                                                          (uniform-vector3 atmosphere "origin" origin)
                                                          (uniform-float atmosphere "radius" 5.0)
                                                          (uniform-float atmosphere "max_height" 4.0)
                                                          (uniform-float atmosphere "cloud_bottom" 1.0)
                                                          (uniform-float atmosphere "cloud_top" 2.0)
                                                          (render-quads vao))
              img        (texture->image tex)]
          (destroy-texture tex)
          (destroy-vertex-array-object vao)
          (destroy-program atmosphere)
          (destroy-vertex-array-object tile)
          (destroy-program planet)
          (destroy-texture surface)
          img)) => (is-image "test/clj/sfsim/fixtures/clouds/overlay.png" 0.0))


(def opacity-cascade-mocks
  "#version 450 core
float cloud_density(vec3 point, float lod)
{
  if (abs(point.x) < 0.5 && abs(point.y) < 0.5)
    return 3.0;
  else
    return 0.0;
}")


(def vertex-render-opacity
  "#version 450 core
uniform mat4 projection;
uniform mat4 world_to_camera;
in vec3 point;
out vec4 pos;
void main()
{
  pos = vec4(point, 1);
  gl_Position = projection * world_to_camera * pos;
}")


(def fragment-render-opacity
  "#version 450 core
uniform mat4 world_to_camera;
in vec4 pos;
out vec4 fragColor;
float opacity_cascade_lookup(vec4 point);
void main()
{
  float value = 0.9 * opacity_cascade_lookup(pos) + 0.1;
  fragColor = vec4(value, value, value, 1.0);
}")


(fact "Render cascade of deep opacity maps"
      (with-invisible-window
        (let [indices         [0 1 3 2]
              vertices        [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
              num-steps       1
              num-layers      7
              shadow-size     128
              z-near          1.0
              z-far           6.0
              projection      (projection-matrix 320 240 z-near z-far (to-radians 45))
              camera-to-world (transformation-matrix (eye 3) (vec3 0 0 4))
              light           (vec3 0 0 1)
              shadow-data     #:sfsim.opacity{:num-steps num-steps :mix 0.5 :depth 5.0}
              render-vars     #:sfsim.render{:projection projection :camera-to-world camera-to-world :light-direction light
                                             :z-near z-near :z-far z-far}
              shadow-mats     (shadow-matrix-cascade shadow-data render-vars)
              program-opac    (make-program :sfsim.render/vertex [opacity-vertex shaders/grow-shadow-index]
                                            :sfsim.render/fragment [(last (opacity-fragment num-layers [] [])) shaders/ray-shell
                                                                    shaders/ray-sphere linear-sampling opacity-cascade-mocks])
              vao             (make-vertex-array-object program-opac indices vertices ["point" 2])
              opacity-maps    (opacity-cascade shadow-size num-layers shadow-mats 1.0 program-opac
                                               (uniform-vector3 program-opac "light_direction" light)
                                               (uniform-float program-opac "radius" 0.25)
                                               (uniform-float program-opac "cloud_bottom" 0.25)
                                               (uniform-float program-opac "cloud_top" 0.75)
                                               (uniform-float program-opac "opacity_step" 0.1)
                                               (uniform-float program-opac "scatter_amount" 0.5)
                                               (render-quads vao))
              tex             (texture-render-color-depth 320 240 false
                                                          (let [indices   [0 1 3 2]
                                                                vertices  [-1.0 -1.0 0, 1.0 -1.0 0, -1.0 1.0 0, 1.0 1.0 0]
                                                                program   (make-program :sfsim.render/vertex [vertex-render-opacity]
                                                                                        :sfsim.render/fragment [fragment-render-opacity
                                                                                                                (opacity-cascade-lookup num-steps
                                                                                                                                        "opacity_lookup")
                                                                                                                opacity-lookup])
                                                                vao       (make-vertex-array-object program indices vertices ["point" 3])]
                                                            (clear (vec3 0 0 0) 0.0)
                                                            (use-program program)
                                                            (uniform-sampler program "opacity0" 0)
                                                            (uniform-float program "opacity_step" 0.1)
                                                            (uniform-int program "shadow_size" shadow-size)
                                                            (uniform-int program "num_opacity_layers" num-layers)
                                                            (uniform-matrix4 program "projection" projection)
                                                            (uniform-matrix4 program "world_to_camera" (inverse camera-to-world))
                                                            (uniform-float program "split0" z-near)
                                                            (uniform-float program "split1" z-far)
                                                            (uniform-matrix4 program "world_to_shadow_map0" (:sfsim.matrix/world-to-shadow-map (shadow-mats 0)))
                                                            (uniform-float program "depth0" (:sfsim.matrix/depth (shadow-mats 0)))
                                                            (use-textures (zipmap (range) opacity-maps))
                                                            (render-quads vao)
                                                            (destroy-vertex-array-object vao)
                                                            (destroy-program program)))]
          (texture->image tex) => (is-image "test/clj/sfsim/fixtures/clouds/cascade.png" 0.01)
          (destroy-texture tex)
          (doseq [opacity-map opacity-maps]
            (destroy-texture opacity-map))
          (destroy-vertex-array-object vao)
          (destroy-program program-opac))))


(def planet-and-cloud-shadows-probe
  (template/fn []
    "#version 450 core
uniform float opacity;
uniform float shadow;
out vec3 fragColor;
float opacity_cascade_lookup(vec4 point)
{
  return opacity;
}
float shadow_cascade_lookup(vec4 point)
{
  return shadow;
}
float planet_and_cloud_shadows(vec4 point);
void main()
{
  float result = planet_and_cloud_shadows(vec4(1, 2, 4, 1));
  fragColor = vec3(result, result, result);
}"))


(def planet-and-cloud-shadows-test
  (shader-test
    (fn [program opacity shadow]
      (uniform-float program "opacity" opacity)
      (uniform-float program "shadow" shadow))
    planet-and-cloud-shadows-probe
    (last (planet-and-cloud-shadows 3))))


(fact "Multiply shadows to get overall shadow"
      ((planet-and-cloud-shadows-test [2.0 3.0] []) 0) => 6.0)


(facts "Test level-of-detail offset computation"
       (lod-offset {:sfsim.render/fov (/ PI 2)} {:sfsim.clouds/detail-scale worley-size} {:sfsim.render/window-width 1})
       => (roughly 1.0 1e-5)
       (lod-offset {:sfsim.render/fov (/ PI 2)} {:sfsim.clouds/detail-scale worley-size} {:sfsim.render/window-width 1024})
       => (roughly -9.0 1e-5)
       (lod-offset {:sfsim.render/fov (/ PI 2)} {:sfsim.clouds/detail-scale (/ worley-size 2)}
                   {:sfsim.render/window-width 1})
       => (roughly 2.0 1e-5)
       (lod-offset {:sfsim.render/fov (* 2 (atan 2))} {:sfsim.clouds/detail-scale worley-size}
                   {:sfsim.render/window-width 1})
       => (roughly 2.0 1e-5))


(def environmental-shading-probe
  (template/fn [z]
    "#version 450 core
out vec3 fragColor;
bool is_above_horizon(vec3 point, vec3 direction)
{
  return direction.z > 0.0;
}
float planet_and_cloud_shadows(vec4 point)
{
  return point.z >= 1.0 ? 1.0 : 0.0;
}
vec3 transmittance_point(vec3 point)
{
  float result = point.z >= 3.0 ? 1.0 : 0.5;
  return vec3(result, result, result);
}
vec3 environmental_shading(vec3 point);
void main()
{
  vec3 point = vec3(0, 0, <%= z %>);
  fragColor = environmental_shading(point);
}"))


(def environmental-shading-test
  (shader-test
    (fn [program light-dir]
      (uniform-vector3 program "light_direction" (vec3 0 0 light-dir)))
    environmental-shading-probe (last (environmental-shading 3))))


(tabular "Shader function for determining direct light left after atmospheric scattering and shadows"
         (fact ((environmental-shading-test [?light-dir] [?z]) 0) => (roughly ?result 1e-5))
         ?z  ?light-dir ?result
         0.0  1.0       0.0
         5.0  1.0       1.0
         2.0  1.0       0.5
         5.0 -1.0       0.0)


(def overall-shading-probe
  (template/fn [x y z sx sy sz]
"#version 450 core
out vec3 fragColor;
vec3 environmental_shading(vec3 point)
{
  return point.xxx;
}
float test_shadow(sampler2DShadow shadow_map, vec4 shadow_pos)
{
  return shadow_pos.x;
}
vec3 overall_shading(vec3 point, vec4 object_shadow_pos_1);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec4 shadow_pos = vec4(<%= sx %>, <%= sy %>, <%= sz %>, 1.0);
  fragColor = overall_shading(point, shadow_pos);
}"))


(def overall-shading-test
  (shader-test (fn [program]) overall-shading-probe (last (overall-shading 3 [["test_shadow" "shadow_map"]]))))


(tabular "Overall shadow composed of environmental and scene shadows"
         (fact ((overall-shading-test [] [?x ?y ?z ?sx ?sy ?sz]) 0) => (roughly ?result 1e-5))
         ?x  ?y ?z ?sx ?sy ?sz ?result
         1   0  0  1   0   0   1.0
         0   0  0  1   0   0   0.0
         1   0  0  0   0   0   0.0
         0.5 0  0  0.5 0   0   0.25)


(def powder-probe
  (template/fn [d]
"#version 450 core
out vec3 fragColor;
float powder(float d);
void main()
{
  fragColor = vec3(powder(<%= d %>), 0, 0);
}"))


(def powder-test
  (shader-test
    (fn [program powder-decay]
        (uniform-float program "powder_decay" powder-decay))
    powder-probe
    powder-shader))


(tabular "Shader function for making low density areas of clouds darker"
         (fact ((powder-test [?powder-decay] [?d]) 0) => (roughly ?result 1e-5))
         ?powder-decay ?d  ?result
         1.0           0.0 0.0
         1.0           1.0 (- 1 (/ 1 E))
         1.0           2.0 (- 1 (/ 1 E E))
         2.0           1.0 (- 1 (/ 1 E E)))


(def cube (read-gltf "test/clj/sfsim/fixtures/model/cube.glb"))


(tabular "Render joined geometry of model, planet, and atmosphere"
         (facts
           (with-invisible-window
             (let [data                {:sfsim.planet/config {:sfsim.planet/tilesize 3}}
                   renderer            (make-joined-geometry-renderer data)
                   scene-program       (:sfsim.model/programs (:sfsim.model/scene-renderer renderer))
                   planet-program      (:sfsim.planet/program (:sfsim.model/planet-renderer renderer))
                   opengl-scene        (load-scene-into-opengl (comp scene-program material-type) cube)
                   moved-scene         (assoc-in opengl-scene [:sfsim.model/root :sfsim.model/transform]
                                                 (transformation-matrix (eye 3) (vec3 0 0 (or ?model 0))))
                   indices             [0 2 3 1]
                   vertices            (make-cube-map-tile-vertices :sfsim.cubemap/face0 0 0 0 3 3)
                   vao                 (make-vertex-array-object planet-program indices vertices
                                                                 ["point" 3 "surfacecoord" 2 "colorcoord" 2])
                   data                [-1  1 0, 0  1 0, 1  1 0,
                                        -1  0 0, 0  0 0, 1  0 0,
                                        -1 -1 0, 0 -1 0, 1 -1 0]
                   surface             (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                                               #:sfsim.image{:width 3 :height 3 :data (float-array data)})
                   tree                {:sfsim.planet/vao vao
                                        :sfsim.planet/surf-tex surface
                                        :sfsim.quadtree/center (vec3 0 0 (or ?planet 0))}
                   camera-to-world     (transformation-matrix (eye 3) (vec3 0 0 0))
                   model-render-vars   #:sfsim.render{:sfsim.render/camera-to-world camera-to-world
                                                      :sfsim.render/z-near 0.1
                                                      :sfsim.render/overlay-projection (projection-matrix 160 120 0.1 10.0 (to-radians 60))
                                                      :sfsim.render/overlay-width 160
                                                      :sfsim.render/overlay-height 120}
                   planet-render-vars  (assoc model-render-vars :sfsim.render/z-near ?planet-z-near)
                   geometry            (render-joined-geometry renderer model-render-vars planet-render-vars moved-scene tree)]
               (nth (get-vector4 (rgba-texture->vectors4 (:sfsim.clouds/points geometry)) 60 80) 2)
               => (roughly ?coordinate 1e-3)
               (get-float (float-texture-2d->floats (:sfsim.clouds/distance geometry)) 60 80)
               => (roughly ?distance 1e-3)
               (destroy-cloud-geometry geometry)
               (destroy-texture surface)
               (destroy-vertex-array-object vao)
               (destroy-scene opengl-scene)
               (destroy-joined-geometry-renderer renderer))))
         ?model ?planet ?planet-z-near ?coordinate ?distance
         nil    nil     0.1            -1.0        0.0
        -4.0    nil     0.1            -1.0        3.0
         nil   -5.0     0.1            -1.0        5.0
        -4.0   -5.0     0.1            -1.0        3.0
        -7.0   -5.0     0.1            -1.0        5.0
        -7.0   -5.0     0.5            -1.0        6.0)


(def fragment-mock-geometry
"#version 450 core
layout (location = 0) out vec4 camera_point;
layout (location = 1) out float dist;
uniform vec3 point;
void main()
{
  camera_point = vec4(normalize(point), 0.0);
  dist = length(point);
}")


(def cloud-shader-mock
"#version 450 core
uniform sampler2D camera_point;
uniform sampler2D dist;
uniform int overlay_width;
uniform int overlay_height;
vec4 geometry_point()
{
  vec2 uv = vec2(gl_FragCoord.x / overlay_width, gl_FragCoord.y / overlay_height);
  return texture(camera_point, uv);
}
float geometry_distance()
{
  vec2 uv = vec2(gl_FragCoord.x / overlay_width, gl_FragCoord.y / overlay_height);
  return texture(dist, uv).r;
}
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment)
{
  float opacity = 1.0 - pow(0.5, segment.t);
  return vec4(opacity, 0, 0, opacity);
}
vec4 cloud_outer(vec3 origin, vec3 direction, float skip)
{
  float opacity = 1.0 - pow(0.5, 3.0 - skip);
  return vec4(opacity, 0, 0, opacity);
}
vec4 plume_outer(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction)
{
  return vec4(0.0, 0.5, 0.0, 0.5);
}
vec4 plume_point(vec3 origin, vec3 direction, vec3 object_origin, vec3 object_direction, float dist)
{
  return vec4(0, 0.25, 0, 0.25);
}")


(defn mock-geometry
  [x y z stencil]
  (let [geometry-program (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                       :sfsim.render/fragment [fragment-mock-geometry])
        indices          [0 1 3 2]
        vertices         [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        vao              (make-vertex-array-object geometry-program indices vertices ["point" 3])
        result           (render-cloud-geometry 1 1
                                                (use-program geometry-program)
                                                (uniform-vector3 geometry-program "point" (vec3 x y z))
                                                (clear (vec3 0.0 0.0 0.0) 0.0 0)
                                                (with-stencils  ; 0x4: model, 0x2: planet, 0x1: atmosphere
                                                  (with-stencil-op-ref-and-mask GL11/GL_ALWAYS stencil stencil
                                                    (render-quads vao))))]
    (destroy-program geometry-program)
    (assoc result :vao vao)))


(defn setup-geometry-uniforms
  [program]
  (use-program program)
  (uniform-sampler program "camera_point" 0)
  (uniform-sampler program "dist" 1))


(defn make-cloud-renderer
  []
  (let [programs (into {} (map (fn [[k v]]
                                   [k (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                                                    :sfsim.render/fragment [cloud-shader-mock v])])
                               cloud-fragment-shaders))]
    (doseq [program (vals programs)] (setup-geometry-uniforms program))
    {:sfsim.clouds/programs programs}))


(defn destroy-cloud-renderer
  [{:sfsim.clouds/keys [programs]}]
  (doseq [program (vals programs)] (destroy-program program)))


(defn render-cloud-overlay
  [{:sfsim.clouds/keys [programs]} vao geometry obj-dist front plume back]
  (let [overlay (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F 1 1)]
    (doseq [program (vals programs)]
           (use-program program)
           (uniform-int program "overlay_width" 1)
           (uniform-int program "overlay_height" 1)
           (uniform-vector3 program "origin" (vec3 0 0 0))
           (uniform-vector3 program "object_origin" (vec3 (- obj-dist) 0 0))
           (uniform-matrix4 program "camera_to_world" (eye 4))
           (uniform-matrix4 program "camera_to_object" (eye 4))
           (uniform-float program "object_distance" obj-dist)
           (use-textures {0 (:sfsim.clouds/points geometry) 1 (:sfsim.clouds/distance geometry)}))
    (framebuffer-render 1 1 :sfsim.render/cullback (:sfsim.clouds/depth-stencil geometry) [overlay]
                        (clear (vec3 0.0 0.0 0.0) 0.0)
                        (with-stencils
                          (when front
                            (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                              (use-program (:sfsim.clouds/atmosphere-front programs))
                              (render-quads vao))
                            (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                              (use-program (:sfsim.clouds/planet-front programs))
                              (render-quads vao))
                            (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
                              (use-program (:sfsim.clouds/scene-front programs))
                              (render-quads vao)))
                          (with-underlay-blending
                            (when plume
                              (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                                (use-program (:sfsim.clouds/plume-outer programs))
                                (render-quads vao))
                              (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                                (use-program (:sfsim.clouds/plume-point programs))
                                (render-quads vao))
                              (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
                                (use-program (:sfsim.clouds/plume-point programs))
                                (render-quads vao)))
                            (when back
                              (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                                (use-program (:sfsim.clouds/atmosphere-back programs))
                                (render-quads vao))
                              (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                                (use-program (:sfsim.clouds/planet-back programs))
                                (render-quads vao))))))
    overlay))


(tabular "Use geometry buffer to render clouds"
         (facts
           (with-invisible-window
             (let [geometry         (mock-geometry ?x ?y ?z ?stencil)
                   vao              (:vao geometry)
                   cloud-renderer   (make-cloud-renderer)
                   overlay          (render-cloud-overlay cloud-renderer vao geometry ?obj-dist ?front ?plume ?back)]
               (get-vector4 (rgba-texture->vectors4 overlay) 0 0)
               => (roughly-vector (vec4 ?r ?g ?b ?a) 1e-3)
               (destroy-texture overlay)
               (destroy-cloud-geometry geometry)
               (destroy-vertex-array-object vao)
               (destroy-cloud-renderer cloud-renderer))))
         ?stencil ?x  ?y  ?z  ?front ?plume ?back ?obj-dist ?r    ?g    ?b  ?a
         0x1      1.0 0.0 0.0 false  false  false 2.0       0.0   0.0   0.0 0.0
         0x1      1.0 0.0 0.0 true   false  false 2.0       0.75  0.0   0.0 0.75
         0x1      1.0 0.0 0.0 false  false  true  2.0       0.5   0.0   0.0 0.5
         0x1      1.0 0.0 0.0 true   false  true  2.0       0.875 0.0   0.0 0.875
         0x2      4.0 0.0 0.0 true   false  false 2.0       0.75  0.0   0.0 0.75
         0x2      2.0 0.0 0.0 true   false  false 3.0       0.75  0.0   0.0 0.75
         0x2      3.0 0.0 0.0 false  false  true  2.0       0.5   0.0   0.0 0.5
         0x2      2.0 0.0 0.0 false  false  true  3.0       0.0   0.0   0.0 0.0
         0x2      3.0 0.0 0.0 true   false  true  2.0       0.875 0.0   0.0 0.875
         0x4      2.0 0.0 0.0 true   false  false 3.0       0.75  0.0   0.0 0.75
         0x4      2.0 0.0 0.0 false  false  true  3.0       0.0   0.0   0.0 0.0
         0x1      1.0 0.0 0.0 false  true   false 2.0       0.0   0.5   0.0 0.5
         0x1      1.0 0.0 0.0 true   true   false 2.0       0.75  0.125 0.0 0.875
         0x2      3.0 0.0 0.0 false  true   false 2.0       0.0   0.25  0.0 0.25
         0x4      2.0 0.0 0.0 false  true   false 2.0       0.0   0.25  0.0 0.25)


(GLFW/glfwTerminate)
