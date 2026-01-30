;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-shaders
  (:require
    [clojure.math :refer (PI sqrt)]
    [comb.template :as template]
    [fastmath.matrix :refer (eye)]
    [fastmath.vector :refer (vec2 vec3 div ediv dot mag cross)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector shader-test)]
    [sfsim.image :refer (get-vector3 convert-4d-to-2d)]
    [sfsim.matrix :refer (transformation-matrix)]
    [sfsim.quaternion :refer (orthogonal)]
    [sfsim.render :refer :all]
    [sfsim.shaders :refer :all]
    [sfsim.texture :refer :all])
  (:import
    (org.lwjgl.glfw
      GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(def ray-sphere-probe
  (template/fn [cx cy cz ox oy oz dx dy dz]
    "#version 450 core
out vec3 fragColor;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
void main()
{
  vec2 result = ray_sphere(vec3(<%= cx %>, <%= cy %>, <%= cz %>),
                           1,
                           vec3(<%= ox %>, <%= oy %>, <%= oz %>),
                           vec3(<%= dx %>, <%= dy %>, <%= dz %>));
  fragColor = vec3(result.x, result.y, 0);
}"))


(def ray-sphere-test (shader-test (fn [_program]) ray-sphere-probe ray-sphere))


(tabular "Shader for intersection of ray with sphere"
         (fact (ray-sphere-test [] [?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz]) => (vec3 ?ix ?iy 0))
         ?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz ?ix ?iy
         0   0   0   2   2  -1   0   0   1   0.0 0.0
         0   0   0   0   0  -2   0   0   1   1.0 2.0
         2   2   0   2   2  -2   0   0   1   1.0 2.0
         0   0   0   0   0  -2   0   0   2   0.5 1.0
         0   0   0   0   0   0   0   0   1   0.0 1.0
         0   0   0   0   0   2   0   0   1   0.0 0.0)


(def ray-circle-probe
  (template/fn [cx cy ox oy dx dy]
    "#version 450 core
out vec3 fragColor;
vec2 ray_circle(vec2 centre, float radius, vec2 origin, vec2 direction);
void main()
{
  vec2 result = ray_circle(vec2(<%= cx %>, <%= cy %>),
                           1,
                           vec2(<%= ox %>, <%= oy %>),
                           vec2(<%= dx %>, <%= dy %>));
  fragColor = vec3(result.x, result.y, 0);
}"))


(def ray-circle-test (shader-test (fn [_program]) ray-circle-probe ray-circle))


(tabular "Shader for intersection of ray with circle"
         (fact (ray-circle-test [] [?cx ?cy ?ox ?oy ?dx ?dy]) => (vec3 ?ix ?iy 0))
         ?cx ?cy ?ox ?oy ?dx ?dy ?ix ?iy
         0   0  -1    0   1   0   0.0 2.0
         0   0   0    0   1   0   0.0 1.0
         0   0  -2    0   1   0   1.0 2.0)


(def convert-1d-index-probe
  (template/fn [x]
    "#version 450 core
out vec3 fragColor;
float convert_1d_index(float idx, int size);
void main()
{
  float result = convert_1d_index(<%= x %>, 15);
  fragColor = vec3(result, 0, 0);
}"))


(def convert-1d-index-test (shader-test (fn [_program]) convert-1d-index-probe convert-1d-index))


(tabular "Convert 1D index to 1D texture lookup index"
         (fact ((convert-1d-index-test [] [?x]) 0) => (roughly (/ ?r 15) 1e-6))
         ?x  ?r
         0   0.5
         1  14.5)


(def convert-2d-index-probe
  (template/fn [x y]
    "#version 450 core
out vec3 fragColor;
vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
void main()
{
  fragColor.rg = convert_2d_index(vec2(<%= x %>, <%= y %>), 17, 15);
  fragColor.b = 0;
}"))


(def convert-2d-index-test (shader-test (fn [_program]) convert-2d-index-probe convert-2d-index))


(tabular "Convert 2D index to 2D texture lookup index"
         (fact (convert-2d-index-test [] [?x ?y]) => (roughly-vector (ediv (vec3 ?r ?g 0) (vec3 15 17 1)) 1e-6))
         ?x  ?y  ?r   ?g
         0   0   0.5  0.5
         1   0  14.5  0.5
         0   1   0.5 16.5)


(def convert-3d-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);
void main()
{
  fragColor = convert_3d_index(vec3(<%= x %>, <%= y %>, <%= z %>), 19, 17, 15);
}"))


(def convert-3d-index-test (shader-test (fn [_program]) convert-3d-index-probe convert-3d-index))


(tabular "Convert 2D index to 2D texture lookup index"
         (fact (convert-3d-index-test [] [?x ?y ?z]) => (roughly-vector (ediv (vec3 ?r ?g ?b) (vec3 15 17 19)) 1e-6))
         ?x  ?y  ?z  ?r   ?g   ?b
         0   0   0  0.5  0.5  0.5
         1   0   0 14.5  0.5  0.5
         0   1   0  0.5 16.5  0.5
         0   0   1  0.5  0.5 18.5)


(def convert-cubemap-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec3 convert_cubemap_index(vec3 idx, int size);
void main()
{
  fragColor = convert_cubemap_index(vec3(<%= x %>, <%= y %>, <%= z %>), 3);
}"))


(def convert-cubemap-index-test (shader-test (fn [_program]) convert-cubemap-index-probe convert-cubemap-index))


(tabular "Convert cubemap index to avoid clamping regions"
         (fact (convert-cubemap-index-test [] [?x ?y ?z]) => (roughly-vector (vec3 ?r ?g ?b) 1e-3))
         ?x     ?y     ?z     ?r   ?g   ?b
         +1.5    0.0    0.0    1.5  0.0  0.0
         +1.5    1.499  0.0    1.5  1.0  0.0
         -1.5    1.499  0.0   -1.5  1.0  0.0
         +1.5    0.0    1.499  1.5  0.0  1.0
         +1.499  1.5    0.0    1.0  1.5  0.0
         +1.499 -1.5    0.0    1.0 -1.5  0.0
         +0.0    1.5    1.499  0.0  1.5  1.0
         +1.499  1.499  1.5    1.0  1.0  1.5
         +1.499  1.499 -1.5    1.0  1.0 -1.5
         +0.000  1.499  1.5    0.0  1.0  1.5
         +0.000  1.499 -1.5    0.0  1.0 -1.5)


(def convert-shadow-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec4 convert_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = convert_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 4, 6).rgb;
}"))


(def convert-shadow-index-test
  (shader-test (fn [program bias] (uniform-float program "shadow_bias" bias))
               convert-shadow-index-probe convert-shadow-index))


(tabular "Move shadow index out of clamping region"
         (fact (convert-shadow-index-test [?bias] [?x ?y ?z]) => (roughly-vector (ediv (vec3 ?r ?g ?b) (vec3 6 4 1)) 1e-6))
         ?x  ?y  ?z  ?bias ?r   ?g   ?b
         0   0   0  0.0   0.5  0.5  0.0
         1   0   0  0.0   5.5  0.5  0.0
         0   1   0  0.0   0.5  3.5  0.0
         0   0   1  0.0   0.5  0.5  1.0
         0   0  -1  0.0   0.5  0.5  0.0
         0   0  -1  0.1   0.5  0.5  0.2)


(def shrink-shadow-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = shrink_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 2, 4).rgb;
}"))


(def shrink-shadow-index-test (shader-test (fn [_program]) shrink-shadow-index-probe shrink-shadow-index))


(tabular "Shrink sampling index to cover full NDC space"
         (fact (shrink-shadow-index-test [] [?x ?y ?z]) => (roughly-vector (vec3 ?r ?g ?b) 1e-6))
         ?x ?y ?z ?r    ?g   ?b
         -1 -1  0 -0.75 -0.5  0
         +1 -1  0  0.75 -0.5  0
         -1  1  0 -0.75  0.5  0
         -1 -1  1 -0.75 -0.5  1)


(def grow-shadow-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec4 grow_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = grow_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 2, 4).rgb;
}"))


(def grow-shadow-index-test (shader-test (fn [_program]) grow-shadow-index-probe grow-shadow-index))


(tabular "grow sampling index to cover full NDC space"
         (fact (grow-shadow-index-test [] [?x ?y ?z]) => (roughly-vector (vec3 ?r ?g ?b) 1e-6))
         ?x    ?y  ?z ?r ?g ?b
         -0.75 -0.5  0 -1 -1  0
         +0.75 -0.5  0  1 -1  0
         -0.75  0.5  0 -1  1  0
         -0.75 -0.5  1 -1 -1  1)


(def shadow-lookup-probe
  (template/fn [lookup-depth]
    "#version 450 core
uniform sampler2DShadow shadows;
out vec3 fragColor;
float shadow_lookup(sampler2DShadow shadow_map, vec4 shadow_pos);
void main()
{
  vec4 pos = vec4(0.5, 0.5, <%= lookup-depth %>, 1.0);
  float shade = shadow_lookup(shadows, pos);
  fragColor = vec3(shade, shade, shade);
}"))


(defn shadow-lookup-test
  [shadow-depth lookup-depth bias]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data      (repeat 4 shadow-depth)
          shadows   (make-depth-texture :sfsim.texture/linear :sfsim.texture/clamp #:sfsim.image{:width 2 :height 2 :data (float-array data)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(shadow-lookup-probe lookup-depth)
                                                          (shadow-lookup "shadow_lookup" "shadow_size")])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "shadows" 0)
                      (uniform-int program "shadow_size" 2)
                      (uniform-float program "shadow_bias" bias)
                      (use-textures {0 shadows})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture shadows)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Shrink sampling index to cover full NDC space"
         (fact ((shadow-lookup-test ?depth ?z ?bias) 0) => (roughly ?result 1e-6))
         ?depth ?z  ?bias ?result
         0.5    1.0 0.0    1.0
         0.5    0.0 0.0    0.0
         0.5    0.6 0.2    0.0)


(def shadow-lookup-mock
  "#version 450 core
uniform int selector;
float shadow_lookup(sampler2DShadow shadow_map, vec4 shadow_pos)
{
  if (selector == 0)
    return textureProj(shadow_map, vec4(0.5, 0.5, 0.5, 1));
  else
    return shadow_pos.x;
}")


(def shadow-cascade-lookup-probe
  (template/fn [z]
    "#version 450 core
out vec3 fragColor;
float shadow_cascade_lookup(vec4 point);
void main()
{
  vec4 point = vec4(0, 0, <%= z %>, 1);
  float result = shadow_cascade_lookup(point);
  fragColor = vec3(result, 0, 0);
}"))


(defn shadow-cascade-lookup-test
  [n z shift-z shadows selector]
  (with-invisible-window
    (let [indices         [0 1 3 2]
          vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          world-to-camera (transformation-matrix (eye 3) (vec3 0 0 shift-z))
          program         (make-program :sfsim.render/vertex [vertex-passthrough]
                                        :sfsim.render/fragment [(shadow-cascade-lookup-probe z)
                                                                (shadow-cascade-lookup n "shadow_lookup") shadow-lookup-mock])
          vao                (make-vertex-array-object program indices vertices ["point" 3])
          shadow-texs     (map #(make-depth-texture :sfsim.texture/linear :sfsim.texture/clamp
                                                    #:sfsim.image{:width 1 :height 1 :data (float-array [%])})
                               shadows)
          tex             (texture-render-color 1 1 true
                                                (use-program program)
                                                (uniform-matrix4 program "world_to_camera" world-to-camera)
                                                (uniform-int program "selector" selector)
                                                (doseq [idx (range n)]
                                                  (uniform-sampler program (str "shadow_map" idx) idx)
                                                  (uniform-matrix4 program (str "world_to_shadow_map" idx)
                                                                   (transformation-matrix (eye 3)
                                                                                          (vec3 (inc idx) 0 0))))
                                                (doseq [idx (range (inc n))]
                                                  (uniform-float program (str "split" idx)
                                                                 (+ 10.0 (/ (* 30.0 idx) n))))
                                                (use-textures (zipmap (range) shadow-texs))
                                                (render-quads vao))
          img             (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (doseq [tex shadow-texs] (destroy-texture tex))
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform shadow lookup in cascade of shadow maps"
         (fact ((shadow-cascade-lookup-test ?n ?z ?shift-z ?shadows ?selector) 0) => (roughly ?result 1e-6))
         ?n ?z ?shift-z ?shadows   ?selector ?result
         1  0    0       [0.0]     0          1.0
         1 -25   0       [1.0]     0          0.0
         1 -25   0       [0.0]     0          1.0
         1 -50   0       [1.0]     0          1.0
         1 -25   0       [1.0]     1          1.0
         2 -15   0       [1.0 1.0] 1          1.0
         2 -35   0       [1.0 1.0] 1          2.0
         1 -50  20       [1.0]     0          0.0
         2 -15   0       [0.0 1.0] 0          1.0
         2 -15   0       [1.0 1.0] 0          0.0
         2 -35   0       [1.0 0.0] 0          1.0
         2 -35   0       [1.0 1.0] 0          0.0)


(def percentage-closer-filtering-probe
  (template/fn [x]
    "#version 450 core
out vec3 fragColor;
float f(float scale, vec4 point)
{
  return max(point.x * scale, 0.0);
}
float averaged(float scale, vec4 point);
void main()
{
  vec4 point = vec4(<%= x %>, 0.0, 0.0, 1.0);
  float result = averaged(3.0, point);
  fragColor = vec3(result, result, result);
}"))


(def percentage-closer-filtering-test
  (shader-test
    (fn [program shadow-size]
      (uniform-int program "shadow_size" shadow-size))
    percentage-closer-filtering-probe (percentage-closer-filtering "averaged" "f" "shadow_size" [["float" "scale"]])))


(tabular "Local averaging of shadow to reduce aliasing"
         (fact ((percentage-closer-filtering-test [?size] [?x]) 0) => (roughly ?result 1e-6))
         ?size ?x ?result
         2     0.0 1.0
         3     0.0 0.5
         2     1.0 3.0)


(def make-2d-index-from-4d-probe
  (template/fn [x y z w selector]
    "#version 450 core
out vec3 fragColor;
vec4 make_2d_index_from_4d(vec4 idx, int size_w, int size_z, int size_y, int size_x);
void main()
{
  vec4 result = make_2d_index_from_4d(vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>), 3, 5, 7, 11);
  fragColor.rg = result.<%= selector %>;
  fragColor.b = 0;
}"))


(def make-2d-index-from-4d-test (shader-test (fn [_program]) make-2d-index-from-4d-probe make-2d-index-from-4d))


(tabular "Convert 4D index to 2D indices for part-manual interpolation"
         (fact (make-2d-index-from-4d-test [] [?x ?y ?z ?w ?selector])
               => (roughly-vector (div (vec3 ?r ?g 0) (* ?s2 ?s1)) 1e-6))
         ?x ?y ?z     ?w     ?s2 ?s1 ?selector ?r               ?g
         0  0   0.123  0.123 11  5   "st"      0.5              (+ 0.5 11)
         1  0   0.123  0.123 11  5   "st"      1.5              (+ 1.5 11)
         0  0   1.123  0.123 11  5   "st"      (+ 0.5 11)       (+ 0.5 (* 2 11))
         0  0   4.123  0.123 11  5   "st"      (+ 0.5 (* 4 11)) (+ 0.5 (* 4 11))
         0  0   0.123  0.123  7  3   "pq"      0.5              (+ 0.5 7)
         0  1   0.123  0.123  7  3   "pq"      1.5              (+ 1.5 7)
         0  0   0.123  1.123  7  3   "pq"      (+ 0.5 7)        (+ 0.5 (* 2 7))
         0  0   0.123  2.123  7  3   "pq"      (+ 0.5 (* 2 7))  (+ 0.5 (* 2 7)))


(def interpolate-2d-probe
  (template/fn [x y]
    "#version 450 core
out vec3 fragColor;
uniform sampler2D table;
vec3 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
void main()
{
  fragColor = interpolate_2d(table, 3, 2, vec2(<%= x %>, <%= y %>));
}"))


(defn interpolate-2d-test
  [x y]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data-2d   [[1 2] [3 4] [5 6]]
          data-flat (flatten (map (partial repeat 3) (flatten data-2d)))
          table     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp
                                            #:sfsim.image{:width 2 :height 3 :data (float-array data-flat)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(interpolate-2d-probe x y) interpolate-2d])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "table" 0)
                      (use-textures {0 table})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture table)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform 2d interpolation"
         (fact ((interpolate-2d-test ?x ?y) 0) => ?result)
         ?x   ?y ?result
         0    0  1.0
         0.25 0  1.25
         0    1  5.0
         1    1  6.0)


(def interpolate-3d-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
uniform sampler3D table;
float interpolate_3d(sampler3D table, int size_z, int size_y, int size_x, vec3 idx);
void main()
{
  float result = interpolate_3d(table, 2, 3, 4, vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, result, result);
}"))


(defn interpolate-3d-test
  [x y z]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data-3d   (range (* 2 3 4))
          table     (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp
                                           #:sfsim.image{:width 4 :height 3 :depth 2 :data (float-array data-3d)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(interpolate-3d-probe x y z) interpolate-3d])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "table" 0)
                      (use-textures {0 table})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture table)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform 3d interpolation"
         (fact ((interpolate-3d-test ?x ?y ?z) 0) => ?result)
         ?x   ?y ?z ?result
         0    0  0   0.0
         0.25 0  0   0.75
         1    0  0   3.0
         0    1  0   8.0
         0    0  1  12.0
         3    2  1  23.0)


(def interpolate-cubemap-probe
  (template/fn [method selector x y z]
    "#version 450 core
uniform samplerCube cube;
out vec3 fragColor;
float interpolate_float_cubemap(samplerCube cube, int size, vec3 idx);
vec3 interpolate_vector_cubemap(samplerCube cube, int size, vec3 idx);
void main()
{
  fragColor = vec3(0, 0, 0);
  fragColor.<%= selector %> = interpolate_<%= method %>_cubemap(cube, 2, vec3(<%= x %>, <%= y %>, <%= z %>));
}"))


(defn interpolate-cubemap-test
  [method selector x y z]
  (with-invisible-window
    (let [indices     [0 1 3 2]
          vertices    [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          datas       [[0 1 0 1] [2 2 2 2] [3 3 3 3] [4 4 4 4] [5 5 5 5] [6 6 6 6]]
          data->image (fn [data] #:sfsim.image{:width 2 :height 2 :data (float-array data)})
          cube        (make-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp (mapv data->image datas))
          program     (make-program :sfsim.render/vertex [vertex-passthrough]
                                    :sfsim.render/fragment [(interpolate-cubemap-probe method selector x y z)
                                                            interpolate-float-cubemap interpolate-vector-cubemap])
          vao         (make-vertex-array-object program indices vertices ["point" 3])
          tex         (texture-render-color
                        1 1 true
                        (use-program program)
                        (uniform-sampler program "cube" 0)
                        (use-textures {0 cube})
                        (render-quads vao))
          img         (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture cube)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform interpolation on cubemap avoiding seams"
         (fact ((interpolate-cubemap-test ?method ?selector ?x ?y ?z) 0) => ?result)
         ?method  ?selector ?x   ?y ?z  ?result
         "float"  "r"       1    0   0   0.5
         "float"  "r"       1    0  -1   1.0
         "float"  "r"       1    0   0.5 0.25
         "vector" "xyz"     1    0   0   0.5
         "vector" "xyz"     1    0  -1   1.0
         "vector" "xyz"     1    0   0.5 0.25)


(def interpolate-4d-probe
  (template/fn [x y z w]
    "#version 450 core
out vec3 fragColor;
uniform sampler2D table;
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
void main()
{
  fragColor = interpolate_4d(table, 2, 2, 2, 2, vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>)).rgb;
}"))


(defn interpolate-4d-test
  [x y z w]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data-4d   [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]]
          data-flat (flatten (map (partial repeat 3) (flatten (convert-4d-to-2d data-4d))))
          table     (make-vector-texture-2d :sfsim.texture/linear :sfsim.texture/clamp #:sfsim.image{:width 4 :height 4 :data (float-array data-flat)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(interpolate-4d-probe x y z w) interpolate-4d])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "table" 0)
                      (use-textures {0 table})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture table)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform 4D interpolation"
         (fact ((interpolate-4d-test ?x ?y ?z ?w) 0) => ?result)
         ?x   ?y ?z  ?w  ?result
         0    0  0   0   1.0
         0.25 0  0   0   1.25
         0    1  0   0   3.0
         0    0  0.5 0   3.0
         0    0  0   0.5 5.0
         0    0  0.5 0.5 7.0)


(def ray-box-probe
  (template/fn [ax ay az bx by bz ox oy oz dx dy dz]
    "#version 450 core
out vec3 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
void main()
{
  vec2 result = ray_box(vec3(<%= ax %>, <%= ay %>, <%= az %>), vec3(<%= bx %>, <%= by %>, <%= bz %>),
                        vec3(<%= ox %>, <%= oy %>, <%= oz %>), vec3(<%= dx %>, <%= dy %>, <%= dz %>));
  fragColor = vec3(result, 0);
}"))


(def ray-box-test (shader-test (fn [_program]) ray-box-probe ray-box))


(tabular "Shader for intersection of ray with axis-aligned box"
         (fact (ray-box-test [] [?ax ?ay ?az ?bx ?by ?bz ?ox ?oy ?oz ?dx ?dy ?dz]) => (vec3 ?ix ?iy 0))
         ?ax ?ay ?az ?bx ?by ?bz ?ox  ?oy  ?oz ?dx ?dy ?dz ?ix ?iy
         3   0   0   4   1   1  -2    0.5  0.5  1  0   0   5   1
         3   0   0   4   1   1  -2    0.5  0.5  2  0   0   2.5 0.5
         0   3   0   1   4   1   0.5 -2    0.5  0  1   0   5   1
         0   0   3   1   1   4   0.5  0.5 -2    0  0   1   5   1
         3   0   0   4   1   1   9    0.5  0.5 -1  0   0   5   1
         0   0   0   1   1   1   0.5  0.5  0.5  1  0   0   0   0.5
         0   0   0   1   1   1   1.5  0.5  0.5  1  0   0   0   0
         0   0   0   1   1   1   0.5  1.5  0.5  1  0   0   0   0
       -10  -1  -1  -5   1   1  -6    0   -2    0  0   1   1   2)


(def lookup-3d-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float lookup_3d(vec3 point);
void main()
{
  float result = lookup_3d(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))


(defn lookup-3d-test
  [x y z]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data-3d   [[[1 2] [3 4]] [[5 6] [7 8]]]
          data-flat (flatten data-3d)
          table     (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                           #:sfsim.image{:width 2 :height 2 :depth 2 :data (float-array data-flat)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(lookup-3d-probe x y z) (lookup-3d "lookup_3d" "table")])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "table" 0)
                      (use-textures {0 table})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture table)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform 3d texture lookup"
         (fact ((lookup-3d-test ?x ?y ?z) 0) => ?result)
         ?x    ?y   ?z   ?result
         0.25  0.25 0.25 1.0
         0.75  0.25 0.25 2.0
         0.25  0.75 0.25 3.0
         0.25  0.25 0.75 5.0)


(def lookup-3d-lod-probe
  (template/fn [x y z lod]
    "#version 450 core
out vec3 fragColor;
float lookup_3d_lod(vec3 point, float lod);
void main()
{
  float result = lookup_3d_lod(vec3(<%= x %>, <%= y %>, <%= z %>), <%= lod %>);
  fragColor = vec3(result, 0, 0);
}"))


(defn lookup-3d-lod-test
  [x y z lod]
  (with-invisible-window
    (let [indices   [0 1 3 2]
          vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
          data-3d   [[[1 2] [3 4]] [[5 6] [7 8]]]
          data-flat (flatten data-3d)
          table     (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat
                                           #:sfsim.image{:width 2 :height 2 :depth 2 :data (float-array data-flat)})
          program   (make-program :sfsim.render/vertex [vertex-passthrough]
                                  :sfsim.render/fragment [(lookup-3d-lod-probe x y z lod)
                                                          (lookup-3d-lod "lookup_3d_lod" "table")])
          vao       (make-vertex-array-object program indices vertices ["point" 3])
          tex       (texture-render-color
                      1 1 true
                      (use-program program)
                      (uniform-sampler program "table" 0)
                      (generate-mipmap table)
                      (use-textures {0 table})
                      (render-quads vao))
          img       (rgb-texture->vectors3 tex)]
      (destroy-texture tex)
      (destroy-texture table)
      (destroy-vertex-array-object vao)
      (destroy-program program)
      (get-vector3 img 0 0))))


(tabular "Perform 3d texture lookup with level-of-detail"
         (fact ((lookup-3d-lod-test ?x ?y ?z ?lod) 0) => ?result)
         ?x    ?y   ?z   ?lod ?result
         0.25  0.25 0.25 0.0  1.0
         0.75  0.25 0.25 0.0  2.0
         0.25  0.75 0.25 0.0  3.0
         0.25  0.25 0.75 0.0  5.0
         0.25  0.25 0.25 1.0  4.5)


(def ray-shell-probe
  (template/fn [cx cy cz radius1 radius2 ox oy oz dx dy dz selector]
    "#version 450 core
out vec3 fragColor;
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
void main()
{
  vec3 centre = vec3(<%= cx %>, <%= cy %>, <%= cz %>);
  vec3 origin = vec3(<%= ox %>, <%= oy %>, <%= oz %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec4 result = ray_shell(centre, <%= radius1 %>, <%= radius2 %>, origin, direction);
  fragColor.rg = result.<%= selector %>;
  fragColor.b = 0;
}"))


(def ray-shell-test (shader-test (fn [_program]) ray-shell-probe ray-shell))


(tabular "Shader for computing intersections of ray with a shell"
         (fact (ray-shell-test [] [?cx ?cy ?cz ?radius1 ?radius2 ?ox ?oy ?oz ?dx ?dy ?dz ?selector])
               => (roughly-vector (vec3 ?ix ?iy 0) 1e-5))
         ?cx ?cy ?cz ?radius1 ?radius2 ?ox ?oy ?oz ?dx ?dy ?dz ?selector ?ix  ?iy
         0   0   0   1        2        -10 0   0   0   1   0   "st"       0   0
         0   0   0   1        2        -10 0   0   0   1   0   "pq"       0   0
         0   0   0   1        5        -10 0   3   1   0   0   "st"       6   8
         0   0   0   1        5        -10 0   3   1   0   0   "pq"       0   0
         0   0   0   2        3        -10 0   0   1   0   0   "st"       7   1
         0   0   0   2        3        -10 0   0   1   0   0   "pq"      12   1
         0   0   0   2        3          0 0   0   1   0   0   "st"       2   1
         0   0   0   2        3          0 0   0   1   0   0   "pq"       0   0)


(def clip-interval-probe
  (template/fn [x l cx cl]
"#version 450 core
out vec3 fragColor;
vec2 clip_interval(vec2 interval, vec2 clip);
void main()
{
  vec2 result = clip_interval(vec2(<%= x %>, <%= l %>), vec2(<%= cx %>, <%= cl %>));
  fragColor = vec3(result, 0);
}"))


(def clip-interval-test (shader-test (fn [_program]) clip-interval-probe clip-interval))


(tabular "Clip the interval information of ray and shell using given limit"
         (fact (clip-interval-test [] [?x ?l ?cx ?cl]) => (roughly-vector (vec3 ?rx ?rl 0) 1e-6))
         ?x ?l ?cx ?cl ?rx ?rl
         2  3  0   8   2   3
         2  3  4   8   4   1
         2  3  0   3   2   1)


(def clip-shell-intersections-probe
  (template/fn [a b c d ca cb selector]
    "#version 450 core
out vec3 fragColor;
vec4 clip_shell_intersections(vec4 intersections, vec2 clip);
void main()
{
  vec4 result = clip_shell_intersections(vec4(<%= a %>, <%= b %>, <%= c %>, <%= d %>), vec2(<%= ca %>, <%= cb %>));
  fragColor.xy = result.<%= selector %>;
  fragColor.z = 0;
}"))


(def clip-shell-intersections-test (apply shader-test (fn [_program]) clip-shell-intersections-probe clip-shell-intersections))


(tabular "Clip the intersection information of ray and shell using given limit"
         (fact (clip-shell-intersections-test [] [?a ?b ?c ?d ?ca ?cb ?selector])
               => (roughly-vector (vec3 ?ix ?iy 0) 1e-6))
         ?a ?b ?c ?d ?ca ?cb ?selector ?ix ?iy
         2  3  6  2  0   9   "xy"      2   3
         2  3  6  2  0   9   "zw"      6   2
         2  3  6  2  0   7   "zw"      6   1
         2  3  6  2  0   5   "zw"      6  -1
         2  3  0  0  0   9   "zw"      0   0
         2  3  6  2  0   3   "xy"      2   1
         2  3  6  2  0   1   "xy"      2  -1)


(def height-to-index-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float height_to_index(vec3 point);
void main()
{
  float result = height_to_index(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))


(def height-to-index-test
  (shader-test
    (fn [program radius max-height]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height))
    height-to-index-probe height-to-index))


(tabular "Shader for converting height to index"
         (fact ((height-to-index-test [?radius ?max-height] [?x ?y ?z]) 0) => (roughly ?result 1e-6))
         ?radius ?max-height ?x    ?y ?z  ?result
         4.0     1.0         4     0  0   0.0
         4.0     1.0         5     0  0   1.0
         4.0     1.0         4.5   0  0   0.687184
         4.0     1.0         3.999 0  0   0.0)


(def sun-elevation-to-index-probe
  (template/fn [x y z dx dy dz]
    "#version 450 core
out vec3 fragColor;
float sun_elevation_to_index(vec3 point, vec3 light_direction);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  float result = sun_elevation_to_index(point, light_direction);
  fragColor = vec3(result, 0, 0);
}"))


(def sun-elevation-to-index-test
  (shader-test
    (fn [_program])
    sun-elevation-to-index-probe sun-elevation-to-index))


(tabular "Shader for converting sun elevation to index"
         (fact ((sun-elevation-to-index-test [] [?x ?y ?z ?dx ?dy ?dz]) 0) => (roughly ?result 1e-6))
         ?x ?y ?z ?dx ?dy      ?dz ?result
         4  0  0  1   0        0   1.0
         4  0  0  0   1        0   0.463863
         4  0  0 -0.2 0.979796 0   0.0
         4  0  0 -1   0        0   0.0)


(def sun-angle-to-index-probe
  (template/fn [dx dy dz lx ly lz]
    "#version 450 core
out vec3 fragColor;
float sun_angle_to_index(vec3 direction, vec3 light_direction);
void main()
{
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  float result = sun_angle_to_index(direction, light_direction);
  fragColor = vec3(result, 0, 0);
}"))


(def sun-angle-to-index-test
  (shader-test
    (fn [_program])
    sun-angle-to-index-probe sun-angle-to-index))


(tabular "Shader for converting sun angle to index"
         (fact ((sun-angle-to-index-test [] [?dx ?dy ?dz ?lx ?ly ?lz]) 0) => (roughly ?result 1e-6))
         ?dx ?dy ?dz ?lx ?ly ?lz ?result
         0   1   0   0   1   0   1.0
         0   1   0   0  -1   0   0.0
         0   1   0   0   0   1   0.5)


(def elevation-to-index-probe
  (template/fn [x y z dx dy dz above-horizon]
    "#version 450 core
out vec3 fragColor;
float elevation_to_index(vec3 point, vec3 direction, bool above_horizon);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  float result = elevation_to_index(point, direction, <%= above-horizon %>);
  fragColor = vec3(result, 0, 0);
}"))


(def elevation-to-index-test
  (shader-test
    (fn [program radius max-height]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height))
    elevation-to-index-probe elevation-to-index))


(tabular "Shader for converting view direction elevation to index"
         (fact ((elevation-to-index-test [?radius ?max-height] [?x ?y ?z ?dx ?dy ?dz ?above-horizon]) 0)
               => (roughly ?result 2e-4))
         ?radius ?max-height ?x ?y ?z  ?dx            ?dy        ?dz            ?above-horizon ?result
         4.0     1.0         4  0  0  -1              0          0              false          0.5
         4.0     1.0         5  0  0  -1              0          0              false          (/ 1 3)
         4.0     1.0         5  0  0   (- (sqrt 0.5)) (sqrt 0.5) 0              false          0.222549
         4.0     1.0         5  0  0  -0.6            0.8        0              false          0.0
         4.0     1.0         4  0  0   1              0          0              true           (/ 2 3)
         4.0     1.0         5  0  0   0              1          0              true           0.5
         4.0     1.0         5  0  0  -0.6            0.8        0              true           1.0
         4.0     1.0         4  0  0   0              1          0              true           1.0
         4.0     1.0         5  0  0  -1              0          0              true           1.0
         4.0     1.0         4  0  0   1              0          0              false          0.5)


(def transmittance-forward-probe
  (template/fn [x y z dx dy dz above]
    "#version 450 core
out vec3 fragColor;
vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  fragColor.rg = transmittance_forward(point, direction, <%= above %>);
  fragColor.b = 0;
}"))


(def transmittance-forward-test
  (shader-test
    (fn [program radius max-height]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height))
    transmittance-forward-probe transmittance-forward))


(tabular "Convert point and direction to 2D lookup index in transmittance table"
         (fact (transmittance-forward-test [6378000.0 100000.0] [?x ?y ?z ?dx ?dy ?dz ?above])
               => (roughly-vector (vec3 ?u ?v 0) 1e-3))
         ?x      ?y ?z  ?dx ?dy ?dz ?above ?u  ?v
         6378000 0  0   0   1   0   true   1   0
         6478000 0  0   0   1   0   true   0.5 1
         6378000 0  0  -1   0   0   false  0.5 0)


(def surface-radiance-forward-probe
  (template/fn [x y z lx ly lz]
    "#version 450 core
out vec3 fragColor;
vec2 surface_radiance_forward(vec3 point, vec3 light_direction);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor.rg = surface_radiance_forward(point, light_direction);
  fragColor.b = 0;
}"))


(def surface-radiance-forward-test
  (shader-test
    (fn [program radius max-height]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height))
    surface-radiance-forward-probe surface-radiance-forward))


(tabular "Convert point and direction to 2D lookup index in surface radiance table"
         (fact (surface-radiance-forward-test [6378000.0 100000.0] [?x ?y ?z ?lx ?ly ?lz])
               => (roughly-vector (vec3 ?u ?v 0) 1e-3))
         ?x      ?y ?z  ?lx ?ly   ?lz ?u    ?v
         6378000 0  0   1   0     0   1     0
         6478000 0  0   1   0     0   1     1
         6378000 0  0  -1   0     0   0     0
         6378000 0  0  -0.2 0.980 0   0     0
         6378000 0  0   0   1     0   0.464 0)


(def ray-scatter-forward-probe
  (template/fn [x y z dx dy dz lx ly lz above selector]
    "#version 450 core
out vec3 fragColor;
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec4 result = ray_scatter_forward(point, direction, light_direction, <%= above %>);
  fragColor.r = result.<%= selector %>;
  fragColor.g = 0;
  fragColor.b = 0;
}"))


(def ray-scatter-forward-test
  (shader-test
    (fn [program radius max-height]
      (uniform-float program "radius" radius)
      (uniform-float program "max_height" max-height))
    ray-scatter-forward-probe ray-scatter-forward))


(tabular "Get 4D lookup index for ray scattering"
         (fact ((ray-scatter-forward-test [6378000.0 100000.0] [?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?above ?selector]) 0)
               => (roughly ?result 1e-3))
         ?x      ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?above ?selector ?result
         6378000 0  0  1   0   0   1   0   0   true   "w"       0.0
         6478000 0  0  1   0   0   1   0   0   true   "w"       1.0
         6478000 0  0  1   0   0   1   0   0   true   "z"       0.5
         6378000 0  0  0   0   1   0   0   1   true   "z"       1.0
         6378000 0  0  0   0   1   0   0   1   false  "z"       0.5
         6378000 0  0  1   0   0   1   0   0   true   "y"       1.0
         6378000 0  0  1   0   0  -1   0   0   true   "y"       0.0
         6378000 0  0  0   1   0   0   1   0   true   "x"       1.0
         6378000 0  0  0   1   0   0  -1   0   true   "x"       0.0)


(def noise-octaves-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float octaves(vec3 idx);
float noise(vec3 idx)
{
  return idx.x == 1.0 ? 1.0 : 0.0;
}
void main()
{
  float result = octaves(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))


(defn noise-octaves-test
  [octaves x y z]
  ((shader-test (fn [_program]) noise-octaves-probe (noise-octaves "octaves" "noise" octaves)) [] [x y z]))


(tabular "Shader function to sum octaves of noise"
         (fact ((noise-octaves-test ?octaves ?x ?y ?z) 0) => ?result)
         ?x  ?y  ?z  ?octaves  ?result
         0.0 0.0 0.0 [1.0]     0.0
         1.0 0.0 0.0 [1.0]     1.0
         1.0 0.0 0.0 [0.5]     0.5
         0.5 0.0 0.0 [0.0 1.0] 1.0
         1.0 0.0 0.0 [1.0 0.0] 1.0)


(def noise-octaves-lod-probe
  (template/fn [x y z lod]
    "#version 450 core
out vec3 fragColor;
float octaves(vec3 idx, float lod);
float noise(vec3 idx, float lod)
{
  if (idx.y == 0.0)
    return idx.x == 1.0 ? 1.0 : 0.0;
  else
    return lod;
}
void main()
{
  float result = octaves(vec3(<%= x %>, <%= y %>, <%= z %>), <%= lod %>);
  fragColor = vec3(result, 0, 0);
}"))


(defn noise-octaves-lod-test
  [octaves x y z lod]
  ((shader-test (fn [_program]) noise-octaves-lod-probe (noise-octaves-lod "octaves" "noise" octaves)) [] [x y z lod]))


(tabular "Shader function to sum octaves of noise with level-of-detail"
         (fact ((noise-octaves-lod-test ?octaves ?x ?y ?z ?lod) 0) => ?result)
         ?x  ?y  ?z  ?lod ?octaves  ?result
         0.0 0.0 0.0 0.0  [1.0]     0.0
         1.0 0.0 0.0 0.0  [1.0]     1.0
         1.0 0.0 0.0 0.0  [0.5]     0.5
         0.5 0.0 0.0 0.0  [0.0 1.0] 1.0
         1.0 0.0 0.0 0.0  [1.0 0.0] 1.0
         0.0 1.0 0.0 0.0  [1.0]     0.0
         0.0 1.0 0.0 1.0  [1.0]     1.0
         0.0 1.0 0.0 1.0  [0.0 1.0] 2.0)


(def fragment-cubemap-vectors
  "#version 450 core
layout (location = 0) out vec3 output1;
layout (location = 1) out vec3 output2;
layout (location = 2) out vec3 output3;
layout (location = 3) out vec3 output4;
layout (location = 4) out vec3 output5;
layout (location = 5) out vec3 output6;
vec3 face1_vector(vec2 texcoord);
vec3 face2_vector(vec2 texcoord);
vec3 face3_vector(vec2 texcoord);
vec3 face4_vector(vec2 texcoord);
vec3 face5_vector(vec2 texcoord);
vec3 face6_vector(vec2 texcoord);
void main()
{
  vec2 x = (gl_FragCoord.xy - 0.5) / 31;
  output1 = face1_vector(x);
  output2 = face2_vector(x);
  output3 = face3_vector(x);
  output4 = face4_vector(x);
  output5 = face5_vector(x);
  output6 = face6_vector(x);
}")


(def face-vector-probe
  (template/fn [x y z]
    "#version 450 core
uniform samplerCube cubemap;
out vec3 fragColor;
vec3 convert_cubemap_index(vec3 idx, int size);
void main()
{
  vec3 idx = convert_cubemap_index(vec3(<%= x %>, <%= y %>, <%= z %>), 32);
  fragColor = texture(cubemap, idx).rgb;
}"))


(tabular "Convert cubemap face coordinate to 3D vector"
         (fact
           (with-invisible-window
             (let [cubemap  (make-empty-vector-cubemap :sfsim.texture/linear :sfsim.texture/clamp 32)
                   indices  [0 1 3 2]
                   vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                   program  (make-program :sfsim.render/vertex [vertex-passthrough]
                                          :sfsim.render/fragment [fragment-cubemap-vectors cubemap-vectors])
                   vao      (make-vertex-array-object program indices vertices ["point" 3])]
               (framebuffer-render 32 32 :sfsim.render/cullback nil [cubemap]
                                   (use-program program)
                                   (render-quads vao))
               (destroy-vertex-array-object vao)
               (destroy-program program)
               (let [program (make-program :sfsim.render/vertex [vertex-passthrough]
                                           :sfsim.render/fragment [(face-vector-probe ?x ?y ?z) convert-cubemap-index])
                     vao     (make-vertex-array-object program indices vertices ["point" 3])
                     tex     (texture-render-color 1 1 true
                                                   (use-program program)
                                                   (uniform-sampler program "cubemap" 0)
                                                   (use-textures {0 cubemap})
                                                   (render-quads vao))
                     img     (rgb-texture->vectors3 tex)]
                 (destroy-texture tex)
                 (destroy-vertex-array-object vao)
                 (destroy-program program)
                 (destroy-texture cubemap)
                 (get-vector3 img 0 0)))) => (roughly-vector (vec3 ?x ?y ?z) 1e-6))
         ?x    ?y    ?z
         +1.0   0.0   0.0
         +1.0   0.25  0.5
         -1.0   0.0   0.0
         -1.0   0.25  0.5
         +0.0   1.0   0.0
         +0.25  1.0   0.5
         +0.0  -1.0   0.0
         +0.25 -1.0   0.5
         +0.0   0.0   1.0
         +0.25  0.5   1.0
         +0.0   0.0  -1.0
         +0.25  0.5  -1.0)


(def gradient-3d-probe
  (template/fn [x y z c dx dy dz]
    "#version 450 core
out vec3 fragColor;
float f(vec3 point)
{
  return min(<%= c %> + point.x * <%= dx %> + point.y * <%= dy %> + point.z * <%= dz %>, 10);
}
vec3 gradient(vec3 point);
void main()
{
  fragColor = gradient(vec3(<%= x %>, <%= y %>, <%= z %>));
}"))


(def gradient-3d-test
  (shader-test
    (fn [program epsilon]
      (uniform-float program "epsilon" epsilon))
    gradient-3d-probe
    (gradient-3d "gradient" "f" "epsilon")))


(tabular "Shader template for 3D gradients"
         (fact (gradient-3d-test [0.1] [?x ?y ?z ?c ?dx ?dy ?dz]) => (roughly-vector (vec3 ?gx ?gy ?gz) 1e-6))
         ?x ?y ?z ?c ?dx ?dy ?dz ?gx ?gy ?gz
         0  0  0  0  0   0   0   0   0   0
         0  0  0  0  1   2   3   1   2   3
         3  3  3  2  1   2   3   0   0   0)


(def orthogonal-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
vec3 orthogonal_vector(vec3 n);
void main()
{
  fragColor = orthogonal_vector(vec3(<%= x %>, <%= y %>, <%= z %>));
}"))


(def orthogonal-test (shader-test (fn [_program]) orthogonal-probe orthogonal-vector))


(tabular "Shader for generating an orthogonal vector"
         (fact (orthogonal-test [] [?x ?y ?z]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-6))
         ?x  ?y  ?z ?rx  ?ry  ?rz
         1   0   0  0    0    1
         2   0   0  0    0    1
         0   1   0  0    0   -1
         0   2   0  0    0   -1
         0   0   1  0    1    0
         0   0   2  0    1    0
         0.6 0.8 0  0.8 -0.6  0
         3   4   0  0.8 -0.6  0)


(def oriented-matrix-probe
  (template/fn [x y z]
"#version 450 core
out vec3 fragColor;
mat3 oriented_matrix(vec3 n);
void main()
{
  fragColor = oriented_matrix(vec3(0.36, 0.48, 0.8)) * vec3(<%= x %>, <%= y %>, <%= z %>);
}"))

(def oriented-matrix-test (shader-test (fn [_program]) oriented-matrix-probe oriented-matrix))


(facts "Shader for creating isometry with given normal vector as first row"
       (let [n  (vec3 0.36 0.48 0.8)
             o1 (orthogonal n)
             o2 (cross n o1)]
         (oriented-matrix-test [] (vec n)) => (roughly-vector (vec3 1 0 0) 1e-6)
         (oriented-matrix-test [] (vec o1)) => (roughly-vector (vec3 0 1 0) 1e-6)
         (oriented-matrix-test [] (vec o2)) => (roughly-vector (vec3 0 0 1) 1e-6)))


(def project-vector-probe
  (template/fn [nx ny nz x y z]
    "#version 450 core
out vec3 fragColor;
vec3 project_vector(vec3 n, vec3 v);
void main()
{
  fragColor = project_vector(vec3(<%= nx %>, <%= ny %>, <%= nz %>), vec3(<%= x %>, <%= y %>, <%= z %>));
}"))


(def project-vector-test (shader-test (fn [_program]) project-vector-probe project-vector))


(tabular "Shader to project vector x onto vector n"
         (fact (project-vector-test [] [?nx ?ny ?nz ?x ?y ?z]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-6))
         ?nx ?ny ?nz ?x ?y ?z ?rx ?ry ?rz
         1   0   0   1  0  0  1   0   0
         2   0   0   1  0  0  1   0   0
         1   0   0   2  0  0  2   0   0
         1   0   0   1  2  3  1   0   0)


(def rotate-vector-probe
  (template/fn [ax ay az x y z angle]
    "#version 450 core
out vec3 fragColor;
vec3 rotate_vector(vec3 axis, vec3 v, float cos_angle, float sin_angle);
void main()
{
  vec3 axis = vec3(<%= ax %>, <%= ay %>, <%= az %>);
  vec3 v = vec3(<%= x %>, <%= y %>, <%= z %>);
  float angle = <%= angle %>;
  float cos_angle = cos(angle);
  float sin_angle = sin(angle);
  fragColor = rotate_vector(axis, v, cos_angle, sin_angle);
}"))


(def rotate-vector-test (shader-test (fn [_program]) rotate-vector-probe rotate-vector))


(tabular "Shader for rotating vector around specified axis"
         (fact (rotate-vector-test [] [?ax ?ay ?az ?x ?y ?z ?angle]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-6))
         ?ax ?ay ?az ?x ?y ?z ?angle    ?rx ?ry ?rz
         1   0   0   0  0  0  0         0   0   0
         1   0   0   1  2  3  0         1   2   3
         1   0   0   1  2  3  (/ PI 2)  1  -3   2
         0   0   1   1  2  3  (/ PI 2) -2   1   3
         0   0   1   1  0  0  (/ PI 2)  0   1   0)


(def rotation-matrix-probe
  (template/fn [x y z axis angle]
"#version 450 core
out vec3 fragColor;
mat3 rotation_<%= axis %>(float angle);
void main()
{
  vec3 v = vec3(<%= x %>, <%= y %>, <%= z %>);
  float angle = <%= angle %>;
  fragColor = rotation_<%= axis %>(angle) * v;
}"))

(def rotation-matrix-test (shader-test (fn [_program]) rotation-matrix-probe rotation-x rotation-y rotation-z))

(tabular "Shaders for creating rotation matrices"
         (fact (rotation-matrix-test [] [?x ?y ?z ?axis ?angle]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-6))
         ?x ?y ?z ?axis ?angle    ?rx ?ry ?rz
         2  3  5  "x"   0         2   3   5
         0  1  0  "x"   (/ PI 2)  0   0   1
         0  0  1  "x"   (/ PI 2)  0  -1   0
         2  3  5  "y"   0         2   3   5
         1  0  0  "y"   (/ PI 2)  0   0  -1
         0  0  1  "y"   (/ PI 2)  1   0   0
         2  3  5  "z"   0         2   3   5
         1  0  0  "z"   (/ PI 2)  0   1   0
         0  1  0  "z"   (/ PI 2) -1   0   0)

(def scale-noise-probe
  (template/fn [x y z]
    "#version 450 core
out vec3 fragColor;
float noise(vec3 point)
{
  return point.x;
}
float scale(vec3 point);
void main()
{
  float result = scale(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))


(def scale-noise-test
  (shader-test
    (fn [program factor] (uniform-float program "factor" factor))
    scale-noise-probe (scale-noise "scale" "factor" "noise")))


(tabular "Shader for calling a noise function with a scaled vector"
         (fact ((scale-noise-test [?scale] [?x ?y ?z]) 0) => ?result)
         ?scale ?x ?y ?z ?result
         1.0    2  3  5  2.0
         3.0    2  3  5  6.0)


(def remap-probe
  (template/fn [value original-min original-max new-min new-max]
    "#version 450 core
out vec3 fragColor;
float remap(float value, float original_min, float original_max, float new_min, float new_max);
void main()
{
  float result = remap(<%= value %>, <%= original-min %>, <%= original-max %>, <%= new-min %>, <%= new-max %>);
  fragColor = vec3(result, 0, 0);
}"))


(def remap-test (shader-test (fn [_program]) remap-probe remap))


(tabular "Shader for mapping linear range to a new linear range"
         (fact ((remap-test [] [?val ?orig-min ?orig-max ?new-min ?new-max]) 0) => (roughly ?result 1e-5))
         ?val ?orig-min ?orig-max ?new-min ?new-max ?result
         0.0  0.0       1.0       0.0      1.0      0.0
         1.0  0.0       1.0       0.0      1.0      1.0
         0.7  0.2       1.2       0.0      1.0      0.5
         0.3  0.2       0.4       0.0      1.0      0.5
         0.5  0.0       1.0       0.0      0.4      0.2
         0.5  0.0       1.0       0.2      0.4      0.3)


(def phong-probe
  (template/fn [ambient light color reflectivity]
    "#version 450 core
out vec3 fragColor;
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
void main()
{
  vec3 ambient = vec3(<%= ambient %>, <%= ambient %>, <%= ambient %>);
  vec3 light = vec3(<%= light %>, <%= light %>, <%= light %>);
  vec3 point = vec3(0, 0, 0);
  vec3 normal = vec3(0, 0, 1);
  vec3 color = vec3(<%= color %>, <%= color %>, <%= color %>);
  float reflectivity = <%= reflectivity %>;
  fragColor = phong(ambient, light, point, normal, color, reflectivity);
}"))


(def phong-test
  (shader-test
    (fn [program albedo specular origin light-direction amplification]
      (uniform-float program "albedo" albedo)
      (uniform-float program "specular" specular)
      (uniform-vector3 program "origin" origin)
      (uniform-vector3 program "light_direction" light-direction)
      (uniform-float program "amplification" amplification))
    phong-probe phong))


(tabular "Shader for phong shading (ambient, diffuse, and specular lighting)"
         (fact ((phong-test [?albedo ?specular ?origin ?light-dir ?amplification] [?ambient ?light ?color ?reflect]) 0)
               => (roughly ?result 1e-6))
         ?albedo ?specular ?origin      ?light-dir           ?amplification ?ambient ?light ?color   ?reflect ?result
         0.0      1.0      (vec3 0  0   1) (vec3 0 0   1)     0.0           0.0      0.0    0.0      0.0      0.0
         PI       1.0      (vec3 0  0   1) (vec3 0 0   1)     2.0           0.5      0.0    0.75     0.0      0.75
         PI       1.0      (vec3 0  0   1) (vec3 0 0   1)     1.0           0.0      1.0    0.75     0.0      0.75
         PI       1.0      (vec3 0  0   1) (vec3 1 0   0)     1.0           0.0      1.0    0.75     0.0      0.0
         PI       1.0      (vec3 0  0   1) (vec3 0 0  -1)     1.0           0.0      1.0    0.75     0.0      0.0
         PI       1.0      (vec3 0  0   1) (vec3 0 0   1)     1.0           0.0      1.0    0.0      0.25     0.25
         PI       1.0      (vec3 0  0   1) (vec3 0 0   1)     1.0           0.0      0.0    0.0      0.25     0.0
         PI       1.0      (vec3 0  0   1) (vec3 0 0.6 0.8)   1.0           0.0      1.0    0.0      0.25     0.2
         PI      10.0      (vec3 0  0   1) (vec3 0 0.6 0.8)   1.0           0.0      1.0    0.0      0.25     0.026844
         PI      10.0      (vec3 0 -0.6 0.8) (vec3 0 0.6 0.8) 1.0           0.0      1.0    0.0      0.25     0.25)


(def sdf-circle-probe
  (template/fn [x y cx cy radius]
"#version 450 core
out vec3 fragColor;
float sdf_circle(vec2 point, vec2 center, float radius);
void main()
{
  float result = sdf_circle(vec2(<%= x %>, <%= y %>), vec2(<%= cx %>, <%= cy %>), <%= radius %>);
  fragColor = vec3(result, 0, 0);
}"))

(def sdf-circle-test (shader-test (fn [_program]) sdf-circle-probe sdf-circle))

(tabular "Shader for testing circle signed distance function"
         (fact ((sdf-circle-test [] [?x ?y ?cx ?cy ?radius]) 0) => ?result)
          ?x   ?y  ?cx ?cy ?radius ?result
          0.0  0.0 0.0 0.0 0.0      0.0
          0.0  0.0 0.0 0.0 1.0     -1.0
          1.0  0.0 0.0 0.0 1.0      0.0
         -1.0  0.0 0.0 0.0 1.0      0.0
          0.0  1.0 0.0 0.0 1.0      0.0
          0.0 -1.0 0.0 0.0 1.0      0.0
          2.0  3.0 2.0 3.0 1.0     -1.0
          3.0  3.0 2.0 3.0 1.0      0.0
          1.0  3.0 2.0 3.0 1.0      0.0
          2.0  4.0 2.0 3.0 1.0      0.0
          2.0  2.0 2.0 3.0 1.0      0.0)


(def sdf-rectangle-probe
  (template/fn [x y rectangle-min-x rectangle-min-y rectangle-max-x rectangle-max-y]
"#version 450 core
out vec3 fragColor;
float sdf_rectangle(vec2 point, vec2 rectangle_min, vec2 rectangle_max);
void main()
{
  vec2 rectangle_min = vec2(<%= rectangle-min-x %>, <%= rectangle-min-y %>);
  vec2 rectangle_max = vec2(<%= rectangle-max-x %>, <%= rectangle-max-y %>);
  float result = sdf_rectangle(vec2(<%= x %>, <%= y %>), rectangle_min, rectangle_max);
  fragColor = vec3(result, 0, 0);
}"))

(def sdf-rectangle-test (shader-test (fn [_program]) sdf-rectangle-probe sdf-rectangle))

(tabular "Shader for testing rectangle signed distance function"
         (fact ((sdf-rectangle-test [] [?x ?y ?rect-min-x ?rect-min-y ?rect-max-x ?rect-max-y]) 0)
               => ?result)
          ?x   ?y  ?rect-min-x ?rect-min-y ?rect-max-x ?rect-max-y ?rect-max-z ?result
          1.0  2.0  1.0         2.0         3.0         6.0         11.0        0.0
          1.5  4.0  1.0         2.0         3.0         6.0         11.0       -0.5
          2.0  2.5  1.0         2.0         3.0         6.0         11.0       -0.5
          2.5  4.0  1.0         2.0         3.0         6.0         11.0       -0.5
          2.0  5.5  1.0         2.0         3.0         6.0         11.0       -0.5
          3.0  4.0  0.0         0.0         0.0         0.0          0.0        5.0
          1.0  1.0  0.0         0.0         2.0         0.0          0.0        1.0)


(def hermite-interpolate-probe
  (template/fn [a b t]
"#version 450 core
out vec3 fragColor;
float hermite_interpolate(float a, float b, float t);
void main()
{
  float result = hermite_interpolate(<%= a %>, <%= b %>, <%= t %>);
  fragColor = vec3(result, 0, 0);
}"))


(def hermite-interpolate-test (shader-test (fn [_program]) hermite-interpolate-probe hermite-interpolate))

(tabular "Shader for cubic hermite interpolation"
         (fact ((hermite-interpolate-test [] [?a ?b ?t]) 0) => ?result)
          ?a   ?b  ?t   ?result
          2.0  3.0  0.0  2.0
          2.0  3.0  0.5  2.5
          2.0  3.0  1.0  3.0
          2.0  3.0  0.25 2.15625
          2.0  3.0  0.75 2.84375)

(def interpolate-function-probe
  (template/fn [x y z]
"#version 450 core
out vec3 fragColor;
float f(vec3 point)
{
  return dot(floor(point), vec3(2, 3, 5));
}
float g(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = g(point);
  fragColor = vec3(result, 0, 0);
}"))

(def interpolate-function-test (shader-test (fn [_program]) interpolate-function-probe (interpolate-function "g" "mix" "f")))

(tabular "Shader to interpolate a function using only samples from whole coordinates"
         (fact ((interpolate-function-test [] [?x ?y ?z]) 0) => ?result)
          ?x   ?y  ?z   ?result
          1.0  0.0  0.0  2.0
          0.0  1.0  0.0  3.0
          0.0  0.0  1.0  5.0
          0.0  0.0  0.0  0.0
          0.5  0.0  0.0  1.0
          0.0  0.5  0.0  1.5
          0.5  0.5  0.0  2.5
          0.0  0.0  0.5  2.5
          0.5  0.0  0.5  3.5
          0.0  0.5  0.5  4.0
          0.5  0.5  0.5  5.0)

(def hash3d-probe
  (template/fn [x y z]
"#version 450 core
out vec3 fragColor;
float hash3d(vec3 p);
void main()
{
  float result = hash3d(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))

(def hash3d-test (shader-test (fn [_program]) hash3d-probe hash3d))

(facts "Shader function to create random noise"
       ((hash3d-test [] [0.0 0.0 0.0]) 0) => (roughly 0.0000000 1e-6)
       ((hash3d-test [] [1.0 0.0 0.0]) 0) => (roughly 0.7187500 1e-6)
       ((hash3d-test [] [0.0 1.0 0.0]) 0) => (roughly 0.2812500 1e-6)
       ((hash3d-test [] [0.0 0.0 1.0]) 0) => (roughly 0.3515625 1e-6))

(def noise3d-probe
  (template/fn [x y z]
"#version 450 core
out vec3 fragColor;
float noise3d(vec3 p);
void main()
{
  float result = noise3d(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))

(def noise3d-test (apply shader-test (fn [_program]) noise3d-probe noise3d))

(facts "Shader function to create 3D noise"
       ((noise3d-test [] [0.0 0.0 0.0]) 0) => (roughly 0.0000000 1e-6)
       ((noise3d-test [] [1.0 0.0 0.0]) 0) => (roughly 0.7187500 1e-6)
       ((noise3d-test [] [0.5 0.0 0.0]) 0) => (roughly 0.3593750 1e-6))

(def subtract-interval-probe
  (template/fn [ax ay bx by]
"#version 450 core
out vec3 fragColor;
vec2 subtract_interval(vec2 a, vec2 b);
void main()
{
  vec2 result = subtract_interval(vec2(<%= ax %>, <%= ay %>), vec2(<%= bx %>, <%= by %>));
  fragColor = vec3(result, 0);
}"))

(def subtract-interval-test (shader-test (fn [_program]) subtract-interval-probe subtract-interval))

(tabular "Shader function to subtract two intervals"
         (fact (take 2 (subtract-interval-test [] [?ax ?ay ?bx ?by])) => (vec2 ?rx ?ry))
          ?ax  ?ay  ?bx  ?by  ?rx  ?ry
          1.0  2.0  5.0  3.0  1.0  2.0
          1.0  4.0  3.0  6.0  1.0  2.0
          1.0  4.0  0.0  6.0  6.0 -1.0
          2.0  3.0  1.0  2.0  3.0  2.0
          4.0  1.0  1.0  2.0  4.0  1.0)

(GLFW/glfwTerminate)
