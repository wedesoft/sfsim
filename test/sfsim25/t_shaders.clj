(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [sfsim25.conftest :refer (roughly-matrix shader-test vertex-passthrough)]
            [comb.template :as template]
            [clojure.core.matrix :refer (matrix mget mmul dot div transpose identity-matrix cross)]
            [clojure.core.matrix.linear :refer (norm)]
            [clojure.math :refer (cos sin PI sqrt)]
            [sfsim25.render :refer :all]
            [sfsim25.shaders :refer :all]
            [sfsim25.matrix :refer (orthogonal)]
            [sfsim25.util :refer (get-vector3 convert-4d-to-2d)])
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Pbuffer PixelFormat]))

(def ray-sphere-probe
  (template/fn [cx cy cz ox oy oz dx dy dz] "#version 410 core
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

(def ray-sphere-test (shader-test (fn [program]) ray-sphere-probe ray-sphere))

(tabular "Shader for intersection of ray with sphere"
         (fact (ray-sphere-test [] [?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz]) => (matrix [?ix ?iy 0]))
         ?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz ?ix ?iy
         0   0   0   2   2  -1   0   0   1   0.0 0.0
         0   0   0   0   0  -2   0   0   1   1.0 2.0
         2   2   0   2   2  -2   0   0   1   1.0 2.0
         0   0   0   0   0  -2   0   0   2   0.5 1.0
         0   0   0   0   0   0   0   0   1   0.0 1.0
         0   0   0   0   0   2   0   0   1   0.0 0.0)

(def convert-2d-index-probe
  (template/fn [x y] "#version 410 core
out vec3 fragColor;
vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
void main()
{
  fragColor.rg = convert_2d_index(vec2(<%= x %>, <%= y %>), 17, 15);
  fragColor.b = 0;
}"))

(def convert-2d-index-test (shader-test (fn [program]) convert-2d-index-probe convert-2d-index))

(tabular "Convert 2D index to 2D texture lookup index"
         (fact (convert-2d-index-test [] [?x ?y]) => (roughly-matrix (div (matrix [?r ?g 0]) (matrix [15 17 1])) 1e-6))
         ?x  ?y  ?r   ?g
          0   0   0.5  0.5
          1   0  14.5  0.5
          0   1   0.5 16.5)

(def convert-3d-index-probe
  (template/fn [x y z] "#version 410 core
out vec3 fragColor;
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);
void main()
{
  fragColor = convert_3d_index(vec3(<%= x %>, <%= y %>, <%= z %>), 19, 17, 15);
}"))

(def convert-3d-index-test (shader-test (fn [program]) convert-3d-index-probe convert-3d-index))

(tabular "Convert 2D index to 2D texture lookup index"
         (fact (convert-3d-index-test [] [?x ?y ?z]) => (roughly-matrix (div (matrix [?r ?g ?b]) (matrix [15 17 19])) 1e-6))
         ?x  ?y  ?z  ?r   ?g   ?b
          0   0   0  0.5  0.5  0.5
          1   0   0 14.5  0.5  0.5
          0   1   0  0.5 16.5  0.5
          0   0   1  0.5  0.5 18.5)

(def convert-cubemap-index-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
vec3 convert_cubemap_index(vec3 idx, int size);
void main()
{
  fragColor = convert_cubemap_index(vec3(<%= x %>, <%= y %>, <%= z %>), 3);
}"))

(def convert-cubemap-index-test (shader-test (fn [program]) convert-cubemap-index-probe convert-cubemap-index))

(tabular "Convert cubemap index to avoid clamping regions"
         (fact (convert-cubemap-index-test [] [?x ?y ?z]) => (roughly-matrix (matrix [?r ?g ?b]) 1e-3))
         ?x     ?y     ?z     ?r   ?g   ?b
         1.5    0.0    0.0    1.5  0.0  0.0
         1.5    1.499  0.0    1.5  1.0  0.0
        -1.5    1.499  0.0   -1.5  1.0  0.0
         1.5    0.0    1.499  1.5  0.0  1.0
         1.499  1.5    0.0    1.0  1.5  0.0
         1.499 -1.5    0.0    1.0 -1.5  0.0
         0.0    1.5    1.499  0.0  1.5  1.0
         1.499  1.499  1.5    1.0  1.0  1.5
         1.499  1.499 -1.5    1.0  1.0 -1.5
         0.000  1.499  1.5    0.0  1.0  1.5
         0.000  1.499 -1.5    0.0  1.0 -1.5)

(def convert-shadow-index-probe
  (template/fn [x y z] "#version 410 core
out vec3 fragColor;
vec4 convert_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = convert_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 4, 6).rgb;
}"))

(def convert-shadow-index-test (shader-test (fn [program]) convert-shadow-index-probe convert-shadow-index))

(tabular "Move shadow index out of clamping region"
         (fact (convert-shadow-index-test [] [?x ?y ?z]) => (roughly-matrix (div (matrix [?r ?g ?b]) (matrix [6 4 1])) 1e-6))
         ?x  ?y  ?z  ?r   ?g   ?b
          0   0   0  0.5  0.5  0.0
          1   0   0  5.5  0.5  0.0
          0   1   0  0.5  3.5  0.0
          0   0   1  0.5  0.5  1.0)

(def shrink-shadow-index-probe
  (template/fn [x y z] "#version 410 core
out vec3 fragColor;
vec4 shrink_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = shrink_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 2, 4).rgb;
}"))

(def shrink-shadow-index-test (shader-test (fn [program]) shrink-shadow-index-probe shrink-shadow-index))

(tabular "Shrink sampling index to cover full NDC space"
         (fact (shrink-shadow-index-test [] [?x ?y ?z]) => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
          ?x ?y ?z ?r    ?g   ?b
          -1 -1  0 -0.75 -0.5  0
           1 -1  0  0.75 -0.5  0
          -1  1  0 -0.75  0.5  0
          -1 -1  1 -0.75 -0.5  1)

(def grow-shadow-index-probe
  (template/fn [x y z] "#version 410 core
out vec3 fragColor;
vec4 grow_shadow_index(vec4 idx, int size_y, int size_x);
void main()
{
  fragColor = grow_shadow_index(vec4(<%= x %>, <%= y %>, <%= z %>, 1), 2, 4).rgb;
}"))

(def grow-shadow-index-test (shader-test (fn [program]) grow-shadow-index-probe grow-shadow-index))

(tabular "grow sampling index to cover full NDC space"
         (fact (grow-shadow-index-test [] [?x ?y ?z]) => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
          ?x    ?y  ?z ?r ?g ?b
         -0.75 -0.5  0 -1 -1  0
          0.75 -0.5  0  1 -1  0
         -0.75  0.5  0 -1  1  0
         -0.75 -0.5  1 -1 -1  1)

(def make-2d-index-from-4d-probe
  (template/fn [x y z w selector] "#version 410 core
out vec3 fragColor;
vec4 make_2d_index_from_4d(vec4 idx, int size_w, int size_z, int size_y, int size_x);
void main()
{
  vec4 result = make_2d_index_from_4d(vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>), 3, 5, 7, 11);
  fragColor.rg = result.<%= selector %>;
  fragColor.b = 0;
}"))

(def make-2d-index-from-4d-test (shader-test (fn [program]) make-2d-index-from-4d-probe make-2d-index-from-4d))

(tabular "Convert 4D index to 2D indices for part-manual interpolation"
         (fact (make-2d-index-from-4d-test [] [?x ?y ?z ?w ?selector]) => (roughly-matrix (div (matrix [?r ?g 0]) ?s2 ?s1) 1e-6))
         ?x ?y ?z     ?w     ?s2 ?s1 ?selector ?r               ?g
         0  0   0.123  0.123 11  5   "st"      0.5              (+ 0.5 11)
         1  0   0.123  0.123 11  5   "st"      1.5              (+ 1.5 11)
         0  0   1.123  0.123 11  5   "st"      (+ 0.5 11)       (+ 0.5 (* 2 11))
         0  0   4.123  0.123 11  5   "st"      (+ 0.5 (* 4 11)) (+ 0.5 (* 4 11))
         0  0   0.123  0.123  7  3   "pq"      0.5              (+ 0.5 7)
         0  1   0.123  0.123  7  3   "pq"      1.5              (+ 1.5 7)
         0  0   0.123  1.123  7  3   "pq"      (+ 0.5 7)        (+ 0.5 (* 2 7))
         0  0   0.123  2.123  7  3   "pq"      (+ 0.5 (* 2 7))  (+ 0.5 (* 2 7)))

(defn lookup-2d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
                          (let [indices   [0 1 3 2]
                                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                                data-2d   [[1 2] [3 4] [5 6]]
                                data-flat (flatten (map (partial repeat 3) (flatten data-2d)))
                                table     (make-vector-texture-2d :linear :clamp
                                                                  {:width 2 :height 3 :data (float-array data-flat)})
                                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                                vao       (make-vertex-array-object program indices vertices [:point 3])
                                tex       (texture-render-color
                                            1 1 true
                                            (use-program program)
                                            (uniform-sampler program :table 0)
                                            (use-textures table)
                                            (render-quads vao))
                                img       (rgb-texture->vectors3 tex)]
                            (deliver result (get-vector3 img 0 0))
                            (destroy-texture tex)
                            (destroy-texture table)
                            (destroy-vertex-array-object vao)
                            (destroy-program program)))
        @result)))

(def interpolate-2d-probe
  (template/fn [x y] "#version 410 core
out vec3 fragColor;
uniform sampler2D table;
vec3 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
void main()
{
  fragColor = interpolate_2d(table, 3, 2, vec2(<%= x %>, <%= y %>));
}"))

(def interpolate-2d-test (lookup-2d-test interpolate-2d-probe interpolate-2d convert-2d-index))

(tabular "Perform 2d interpolation"
         (fact (mget (interpolate-2d-test ?x ?y) 0) => ?result)
         ?x   ?y ?result
         0    0  1.0
         0.25 0  1.25
         0    1  5.0
         1    1  6.0)

(defn lookup-3d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
                          (let [indices   [0 1 3 2]
                                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                                data-3d   (range (* 2 3 4))
                                table     (make-float-texture-3d :linear :clamp
                                                                 {:width 4 :height 3 :depth 2 :data (float-array data-3d)})
                                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                                vao       (make-vertex-array-object program indices vertices [:point 3])
                                tex       (texture-render-color
                                            1 1 true
                                            (use-program program)
                                            (uniform-sampler program :table 0)
                                            (use-textures table)
                                            (render-quads vao))
                                img       (rgb-texture->vectors3 tex)]
                            (deliver result (get-vector3 img 0 0))
                            (destroy-texture tex)
                            (destroy-texture table)
                            (destroy-vertex-array-object vao)
                            (destroy-program program)))
        @result)))

(def interpolate-3d-probe
  (template/fn [x y z] "#version 410 core
out vec3 fragColor;
uniform sampler3D table;
float interpolate_3d(sampler3D table, int size_z, int size_y, int size_x, vec3 idx);
void main()
{
  float result = interpolate_3d(table, 2, 3, 4, vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, result, result);
}"))

(def interpolate-3d-test (lookup-3d-test interpolate-3d-probe interpolate-3d convert-3d-index))

(tabular "Perform 3d interpolation"
         (fact (mget (interpolate-3d-test ?x ?y ?z) 0) => ?result)
         ?x   ?y ?z ?result
         0    0  0   0.0
         0.25 0  0   0.75
         1    0  0   3.0
         0    1  0   8.0
         0    0  1  12.0
         3    2  1  23.0)

(defn lookup-4d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices   [0 1 3 2]
                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                data-4d   [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]]
                data-flat (flatten (map (partial repeat 3) (flatten (convert-4d-to-2d data-4d))))
                table     (make-vector-texture-2d :linear :clamp {:width 4 :height 4 :data (float-array data-flat)})
                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao       (make-vertex-array-object program indices vertices [:point 3])
                tex       (texture-render-color
                            1 1 true
                            (use-program program)
                            (uniform-sampler program :table 0)
                            (use-textures table)
                            (render-quads vao))
                img       (rgb-texture->vectors3 tex)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-texture table)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def interpolate-4d-probe
  (template/fn [x y z w] "#version 410 core
out vec3 fragColor;
uniform sampler2D table;
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
void main()
{
  fragColor = interpolate_4d(table, 2, 2, 2, 2, vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>)).rgb;
}"))

(def interpolate-4d-test (lookup-4d-test interpolate-4d-probe interpolate-4d make-2d-index-from-4d))

(tabular "Perform 4D interpolation"
         (fact (mget (interpolate-4d-test ?x ?y ?z ?w) 0) => ?result)
         ?x   ?y ?z  ?w  ?result
         0    0  0   0   1.0
         0.25 0  0   0   1.25
         0    1  0   0   3.0
         0    0  0.5 0   3.0
         0    0  0   0.5 5.0
         0    0  0.5 0.5 7.0)

(def ray-box-probe
  (template/fn [ax ay az bx by bz ox oy oz dx dy dz]
"#version 410 core
out vec3 fragColor;
vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction);
void main()
{
  vec2 result = ray_box(vec3(<%= ax %>, <%= ay %>, <%= az %>), vec3(<%= bx %>, <%= by %>, <%= bz %>),
                        vec3(<%= ox %>, <%= oy %>, <%= oz %>), vec3(<%= dx %>, <%= dy %>, <%= dz %>));
  fragColor = vec3(result, 0);
}"))

(def ray-box-test (shader-test (fn [program]) ray-box-probe ray-box))

(tabular "Shader for intersection of ray with axis-aligned box"
         (fact (ray-box-test [] [?ax ?ay ?az ?bx ?by ?bz ?ox ?oy ?oz ?dx ?dy ?dz]) => (matrix [?ix ?iy 0]))
         ?ax ?ay ?az ?bx ?by ?bz ?ox  ?oy  ?oz ?dx ?dy ?dz ?ix ?iy
         3   0   0   4   1   1  -2    0.5  0.5  1  0   0   5   1
         3   0   0   4   1   1  -2    0.5  0.5  2  0   0   2.5 0.5
         0   3   0   1   4   1   0.5 -2    0.5  0  1   0   5   1
         0   0   3   1   1   4   0.5  0.5 -2    0  0   1   5   1
         3   0   0   4   1   1   9    0.5  0.5 -1  0   0   5   1
         0   0   0   1   1   1   0.5  0.5  0.5  1  0   0   0   0.5
         0   0   0   1   1   1   1.5  0.5  0.5  1  0   0   0   0
         0   0   0   1   1   1   0.5  1.5  0.5  1  0   0   0   0)

(defn lookup-3d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
                          (let [indices   [0 1 3 2]
                                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                                data-3d   [[[1 2] [3 4]] [[5 6] [7 8]]]
                                data-flat (flatten data-3d)
                                table     (make-float-texture-3d :linear :repeat
                                                                 {:width 2 :height 2 :depth 2 :data (float-array data-flat)})
                                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                                vao       (make-vertex-array-object program indices vertices [:point 3])
                                tex       (texture-render-color
                                            1 1 true
                                            (use-program program)
                                            (uniform-sampler program :table 0)
                                            (use-textures table)
                                            (render-quads vao))
                                img       (rgb-texture->vectors3 tex)]
                            (deliver result (get-vector3 img 0 0))
                            (destroy-texture tex)
                            (destroy-texture table)
                            (destroy-vertex-array-object vao)
                            (destroy-program program)))
        @result)))

(def lookup-3d-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
uniform sampler3D table;
float lookup_3d(sampler3D tex, vec3 point);
void main()
{
  float result = lookup_3d(table, vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))

(def interpolate-3d-test (lookup-3d-test lookup-3d-probe lookup-3d))

(tabular "Perform 3d interpolation"
         (fact (mget (interpolate-3d-test ?x ?y ?z) 0) => ?result)
         ?x    ?y   ?z   ?result
         0.25  0.25 0.25 1.0
         0.75  0.25 0.25 2.0
         0.25  0.75 0.25 3.0
         0.25  0.25 0.75 5.0)

(def ray-shell-probe
  (template/fn [cx cy cz radius1 radius2 ox oy oz dx dy dz selector]
"#version 410 core
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

(def ray-shell-test (shader-test (fn [program]) ray-shell-probe ray-shell ray-sphere))

(tabular "Shader for computing intersections of ray with a shell"
         (fact (ray-shell-test [] [?cx ?cy ?cz ?radius1 ?radius2 ?ox ?oy ?oz ?dx ?dy ?dz ?selector])
               => (roughly-matrix (matrix [?ix ?iy 0]) 1e-6))
         ?cx ?cy ?cz ?radius1 ?radius2 ?ox ?oy ?oz ?dx ?dy ?dz ?selector ?ix ?iy
         0   0   0   1        2        -10 0   0   0   1   0   "st"       0  0
         0   0   0   1        2        -10 0   0   0   1   0   "pq"       0  0
         0   0   0   1        5        -10 0   3   1   0   0   "st"       6  8
         0   0   0   1        5        -10 0   3   1   0   0   "pq"       0  0
         0   0   0   2        3        -10 0   0   1   0   0   "st"       7  1
         0   0   0   2        3        -10 0   0   1   0   0   "pq"      12  1
         0   0   0   2        3          0 0   0   1   0   0   "st"       2  1
         0   0   0   2        3          0 0   0   1   0   0   "pq"       0  0)

(def clip-shell-intersections-probe
  (template/fn [a b c d limit selector]
"#version 410 core
out vec3 fragColor;
vec4 clip_shell_intersections(vec4 intersections, float limit);
void main()
{
  vec4 result = clip_shell_intersections(vec4(<%= a %>, <%= b %>, <%= c %>, <%= d %>), <%= limit %>);
  fragColor.xy = result.<%= selector %>;
  fragColor.z = 0;
}"))

(def clip-shell-intersections-test (shader-test (fn [program]) clip-shell-intersections-probe clip-shell-intersections))

(tabular "Clip the intersection information of ray and shell using given limit"
         (fact (clip-shell-intersections-test [] [?a ?b ?c ?d ?limit ?selector])
               => (roughly-matrix (matrix [?ix ?iy 0]) 1e-6))
         ?a ?b ?c ?d ?limit ?selector ?ix ?iy
         2  3  6  2  9      "xy"      2   3
         2  3  6  2  9      "zw"      6   2
         2  3  6  2  7      "zw"      6   1
         2  3  6  2  5      "zw"      0   0
         2  3  0  0  9      "zw"      0   0
         2  3  6  2  3      "xy"      2   1
         2  3  6  2  1      "xy"      0   0)

(def height-to-index-probe
  (template/fn [x y z]
"#version 410 core
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
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    height-to-index-probe height-to-index horizon-distance))

(tabular "Shader for converting height to index"
         (fact (mget (height-to-index-test [?radius ?max-height] [?x ?y ?z]) 0) => (roughly ?result 1e-6))
         ?radius ?max-height ?x    ?y ?z ?result
         4       1           4     0  0  0.0
         4       1           5     0  0  1.0
         4       1           4.5   0  0  0.687184
         4       1           3.999 0  0  0.0)

(def sun-elevation-to-index-probe
  (template/fn [x y z dx dy dz]
"#version 410 core
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
    (fn [program])
    sun-elevation-to-index-probe sun-elevation-to-index))

(tabular "Shader for converting sun elevation to index"
         (fact (mget (sun-elevation-to-index-test [] [?x ?y ?z ?dx ?dy ?dz]) 0) => (roughly ?result 1e-6))
         ?x ?y ?z ?dx ?dy      ?dz ?result
         4  0  0  1   0        0   1.0
         4  0  0  0   1        0   0.463863
         4  0  0 -0.2 0.979796 0   0.0
         4  0  0 -1   0        0   0.0)

(def sun-angle-to-index-probe
  (template/fn [dx dy dz lx ly lz]
"#version 410 core
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
    (fn [program])
    sun-angle-to-index-probe sun-angle-to-index))

(tabular "Shader for converting sun angle to index"
         (fact (mget (sun-angle-to-index-test [] [?dx ?dy ?dz ?lx ?ly ?lz]) 0) => (roughly ?result 1e-6))
         ?dx ?dy ?dz ?lx ?ly ?lz ?result
         0   1   0   0   1   0   1.0
         0   1   0   0  -1   0   0.0
         0   1   0   0   0   1   0.5)

(def elevation-to-index-probe
  (template/fn [x y z dx dy dz above-horizon]
"#version 410 core
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
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    elevation-to-index-probe elevation-to-index horizon-distance limit-quot))

(tabular "Shader for converting view direction elevation to index"
         (fact (mget (elevation-to-index-test [?radius ?max-height] [?x ?y ?z ?dx ?dy ?dz ?above-horizon]) 0)
               => (roughly ?result 1e-6))
         ?radius ?max-height ?x ?y ?z ?dx           ?dy        ?dz ?above-horizon ?result
         4       1           4  0  0 -1             0          0   false          0.5
         4       1           5  0  0 -1             0          0   false          (/ 1 3)
         4       1           5  0  0 (- (sqrt 0.5)) (sqrt 0.5) 0   false          0.222549
         4       1           5  0  0 -0.6           0.8        0   false          0.0
         4       1           4  0  0  1             0          0   true           (/ 2 3)
         4       1           5  0  0  0             1          0   true           0.5
         4       1           5  0  0 -0.6           0.8        0   true           1.0
         4       1           4  0  0  0             1          0   true           1.0
         4       1           5  0  0 -1             0          0   true           1.0
         4       1           4  0  0  1             0          0   false          0.5)

(def transmittance-forward-probe
  (template/fn [x y z dx dy dz above]
"#version 410 core
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
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    transmittance-forward-probe transmittance-forward height-to-index horizon-distance elevation-to-index limit-quot))

(tabular "Convert point and direction to 2D lookup index in transmittance table"
         (fact (transmittance-forward-test [6378000.0 100000.0] [?x ?y ?z ?dx ?dy ?dz ?above])
               => (roughly-matrix (matrix [?u ?v 0]) 1e-3))
         ?x      ?y ?z  ?dx ?dy ?dz ?above ?u  ?v
         6378000 0  0   0   1   0   true   1   0
         6478000 0  0   0   1   0   true   0.5 1
         6378000 0  0  -1   0   0   false  0.5 0)

(def surface-radiance-forward-probe
  (template/fn [x y z lx ly lz]
"#version 410 core
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
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    surface-radiance-forward-probe surface-radiance-forward height-to-index horizon-distance sun-elevation-to-index))

(tabular "Convert point and direction to 2D lookup index in surface radiance table"
         (fact (surface-radiance-forward-test [6378000.0 100000.0] [?x ?y ?z ?lx ?ly ?lz])
               => (roughly-matrix (matrix [?u ?v 0]) 1e-3))
         ?x      ?y ?z  ?lx ?ly   ?lz ?u    ?v
         6378000 0  0   1   0     0   1     0
         6478000 0  0   1   0     0   1     1
         6378000 0  0  -1   0     0   0     0
         6378000 0  0  -0.2 0.980 0   0     0
         6378000 0  0   0   1     0   0.464 0)

(def ray-scatter-forward-probe
  (template/fn [x y z dx dy dz lx ly lz above selector]
"#version 410 core
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
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    ray-scatter-forward-probe ray-scatter-forward height-to-index elevation-to-index horizon-distance limit-quot
    sun-elevation-to-index sun-angle-to-index))

(tabular "Get 4D lookup index for ray scattering"
         (fact (mget (ray-scatter-forward-test [6378000 100000] [?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?above ?selector]) 0)
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

(def fragment-cubemap-vectors
"#version 410 core
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
"#version 410 core
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
           (let [result (promise)]
             (offscreen-render 1 1
               (let [cubemap  (make-empty-vector-cubemap :linear :clamp 32)
                     indices  [0 1 3 2]
                     vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                     program  (make-program :vertex [vertex-passthrough] :fragment [fragment-cubemap-vectors cubemap-vectors])
                     vao      (make-vertex-array-object program indices vertices [:point 3])]
                 (framebuffer-render 32 32 :cullback nil [cubemap]
                                     (use-program program)
                                     (render-quads vao))
                 (destroy-vertex-array-object vao)
                 (destroy-program program)
                 (let [program (make-program :vertex [vertex-passthrough]
                                             :fragment [(face-vector-probe ?x ?y ?z) convert-cubemap-index])
                       vao     (make-vertex-array-object program indices vertices [:point 3])
                       tex     (texture-render-color 1 1 true
                                                     (use-program program)
                                                     (uniform-sampler program :cubemap 0)
                                                     (use-textures cubemap)
                                                     (render-quads vao))
                       img     (rgb-texture->vectors3 tex)]
                   (deliver result (get-vector3 img 0 0))
                   (destroy-texture tex)
                   (destroy-vertex-array-object vao)
                   (destroy-program program))
                 (destroy-texture cubemap)))
                 @result) => (roughly-matrix (matrix [?x ?y ?z]) 1e-6))
         ?x    ?y    ?z
         1.0   0.0   0.0
         1.0   0.25  0.5
        -1.0   0.0   0.0
        -1.0   0.25  0.5
         0.0   1.0   0.0
         0.25  1.0   0.5
         0.0  -1.0   0.0
         0.25 -1.0   0.5
         0.0   0.0   1.0
         0.25  0.5   1.0
         0.0   0.0  -1.0
         0.25  0.5  -1.0)

(def gradient-3d-probe
  (template/fn [x y z c dx dy dz]
"#version 410 core
out vec3 fragColor;
float f(vec3 point)
{
  return min(<%= c %> + point.x * <%= dx %> + point.y * <%= dy %> + point.z * <%= dz %>, 10);
}
vec3 gradient(vec3 point, float epsilon);
void main()
{
  fragColor = gradient(vec3(<%= x %>, <%= y %>, <%= z %>), 1e-1);
}"))

(def gradient-3d-test (shader-test (fn [program]) gradient-3d-probe (gradient-3d "gradient" "f")))

(tabular "Shader template for 3D gradients"
         (fact (gradient-3d-test [] [?x ?y ?z ?c ?dx ?dy ?dz]) => (roughly-matrix (matrix [?gx ?gy ?gz]) 1e-6))
         ?x ?y ?z ?c ?dx ?dy ?dz ?gx ?gy ?gz
         0  0  0  0  0   0   0   0   0   0
         0  0  0  0  1   2   3   1   2   3
         3  3  3  2  1   2   3   0   0   0)

(def orthogonal-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
vec3 orthogonal_vector(vec3 n);
void main()
{
  fragColor = orthogonal_vector(vec3(<%= x %>, <%= y %>, <%= z %>));
}"))

(def orthogonal-test (shader-test (fn [program]) orthogonal-probe orthogonal-vector))

(facts "Shader for generating an orthogonal vector"
       (dot (orthogonal-test [] [1 0 0]) (matrix [1 0 0])) => 0.0
       (norm (orthogonal-test [] [1 0 0])) => 1.0
       (norm (orthogonal-test [] [2 0 0])) => 1.0
       (dot (orthogonal-test [] [0 1 0]) (matrix [0 1 0])) => 0.0
       (norm (orthogonal-test [] [0 1 0])) => 1.0
       (norm (orthogonal-test [] [0 2 0])) => 1.0
       (dot (orthogonal-test [] [0 0 1]) (matrix [0 0 1])) => 0.0
       (norm (orthogonal-test [] [0 0 1])) => 1.0
       (norm (orthogonal-test [] [0 0 2])) => 1.0)

(def oriented-matrix-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
mat3 oriented_matrix(vec3 n);
void main()
{
  fragColor = oriented_matrix(vec3(0.36, 0.48, 0.8)) * vec3(<%= x %>, <%= y %>, <%= z %>);
}"))

(def oriented-matrix-test (shader-test (fn [program]) oriented-matrix-probe oriented-matrix orthogonal-vector))

(facts "Shader for creating isometry with given normal vector as first row"
       (let [n  (matrix [0.36 0.48 0.8])
             o1 (orthogonal n)
             o2 (cross n o1)]
         (oriented-matrix-test [] (vec n)) => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (oriented-matrix-test [] (vec o1)) => (roughly-matrix (matrix [0 1 0]) 1e-6)
         (oriented-matrix-test [] (vec o2)) => (roughly-matrix (matrix [0 0 1]) 1e-6)))

(def project-vector-probe
  (template/fn [nx ny nz x y z]
"#version 410 core
out vec3 fragColor;
vec3 project_vector(vec3 n, vec3 x);
void main()
{
  fragColor = project_vector(vec3(<%= nx %>, <%= ny %>, <%= nz %>), vec3(<%= x %>, <%= y %>, <%= z %>));
}"))

(def project-vector-test (shader-test (fn [program]) project-vector-probe project-vector))

(tabular "Shader to project vector x onto vector n"
         (fact (project-vector-test [] [?nx ?ny ?nz ?x ?y ?z]) => (roughly-matrix (matrix [?rx ?ry ?rz]) 1e-6))
         ?nx ?ny ?nz ?x ?y ?z ?rx ?ry ?rz
         1   0   0   1  0  0  1   0   0
         2   0   0   1  0  0  1   0   0
         1   0   0   2  0  0  2   0   0
         1   0   0   1  2  3  1   0   0)
