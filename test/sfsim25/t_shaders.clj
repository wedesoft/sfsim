(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [sfsim25.conftest :refer (roughly-matrix)]
            [comb.template :as template]
            [clojure.core.matrix :refer (matrix mget mmul dot div transpose identity-matrix)]
            [clojure.core.matrix.linear :refer (norm)]
            [clojure.math :refer (cos sin PI)]
            [sfsim25.shaders :refer :all]
            [sfsim25.render :refer :all]
            [sfsim25.util :refer :all])
  (:import [org.lwjgl BufferUtils]
           [org.lwjgl.opengl Pbuffer PixelFormat]))

(def vertex-passthrough "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(defn shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program  (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao      (make-vertex-array-object program indices vertices [:point 3])
                tex      (texture-render 1 1 true (use-program program) (apply setup program uniforms) (render-quads vao))
                img      (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ray-sphere-probe
  (template/fn [cx cy cz ox oy oz dx dy dz] "#version 410 core
out lowp vec3 fragColor;
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

(def elevation-to-index-probe
  (template/fn [elevation-size, above-horizon elevation horizon-angle] "#version 410 core
out lowp vec3 fragColor;
float elevation_to_index(int elevation_size, float elevation, float horizon_angle, bool above_horizon);
void main()
{
  float result = elevation_to_index(<%= elevation-size %>, <%= elevation %>, <%= horizon-angle %>, <%= above-horizon %>);
  fragColor = vec3(result, 0, 0);
}"))

(def elevation-to-index-test
  (shader-test
    (fn [program elevation-power] (uniform-float program :elevation_power elevation-power))
    elevation-to-index-probe elevation-to-index))

(tabular "Shader for converting elevation to index"
         (fact (mget (elevation-to-index-test [?power] [17 ?above-horizon ?elevation ?horizon-angle]) 0)
               => (roughly (/ ?result 16)))
         ?above-horizon ?elevation        ?horizon-angle ?power ?result
         true           0.0               0.0            1.0     0
         true           (* 0.5 3.14159)   0.0            1.0     8
         false          (* 0.5 3.14160)   0.0            1.0     9
         false          (* 0.5 3.14159)   0.0            1.0     9
         true           (* 0.5 3.14160)   0.0            1.0     8
         false          3.14159           0.0            1.0    16
         true           (* 0.375 3.14159) 0.0            1.0     6
         true           (* 0.375 3.14159) 0.0            2.0     4
         false          (* 0.625 3.14159) 0.0            1.0    10.75
         false          (* 0.625 3.14159) 0.0            2.0    12.5
         true           (* 0.5 3.34159)   0.1            1.0     8
         false          (* 0.5 3.34160)   0.1            1.0     9)

(def horizon-angle-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
float horizon_angle(vec3 point);
void main()
{
  float result = horizon_angle(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))

(def horizon-angle-test
  (shader-test (fn [program radius] (uniform-float program :radius radius)) horizon-angle-probe horizon-angle))

(facts "Angle of sphere's horizon angle below horizontal plane depending on height"
       (mget (horizon-angle-test [6378000] [6378000 0 0]) 0)       => (roughly 0.0 1e-3)
       (mget (horizon-angle-test [6378000] [(* 2 6378000) 0 0]) 0) => (roughly (/ PI 3) 1e-3)
       (mget (horizon-angle-test [6378000] [6377999 0 0]) 0)       => (roughly 0.0 1e-3))

(def orthogonal-vector-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
vec3 orthogonal_vector(vec3 n);
void main()
{
  fragColor = orthogonal_vector(vec3(<%= x %>, <%= y %>, <%= z %>));
}"))

(def orthogonal-vector-test (shader-test (fn [program]) orthogonal-vector-probe orthogonal-vector))

(facts "Create normal vector orthogonal to the specified one"
       (dot  (orthogonal-vector-test [] [1 0 0]) (matrix [1 0 0])) => 0.0
       (norm (orthogonal-vector-test [] [1 0 0])) => 1.0
       (dot  (orthogonal-vector-test [] [0 1 0]) (matrix [0 1 0])) => 0.0
       (norm (orthogonal-vector-test [] [0 1 0])) => 1.0
       (dot  (orthogonal-vector-test [] [0 0 1]) (matrix [0 0 1])) => 0.0
       (norm (orthogonal-vector-test [] [0 0 1])) => 1.0)

(def oriented-matrix-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
mat3 oriented_matrix(vec3 n);
void main()
{
  fragColor = oriented_matrix(vec3(0.36, 0.48, 0.8)) * vec3(<%= x %>, <%= y %>, <%= z %>);
}"))

(def oriented-matrix-test (shader-test (fn [program]) oriented-matrix-probe orthogonal-vector oriented-matrix))

(facts "Create oriented matrix given a normal vector"
       (let [m (transpose (matrix [(oriented-matrix-test [] [1 0 0])
                                   (oriented-matrix-test [] [0 1 0])
                                   (oriented-matrix-test [] [0 0 1])])) ]
         (mmul m (matrix [0.36 0.48 0.8])) => (roughly-matrix (matrix [1 0 0]) 1e-6)
         (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3) 1e-6)))

(def clip-angle-probe
  (template/fn [angle] "#version 410 core
out lowp vec3 fragColor;
float clip_angle(float angle);
void main()
{
  fragColor = vec3(clip_angle(<%= angle %>), 0, 0);
}"))

(def clip-angle-test (shader-test (fn [program]) clip-angle-probe clip-angle))

(facts "Convert angle to be between -pi and +pi"
       (mget (clip-angle-test [] [0            ]) 0) => (roughly 0           1e-6)
       (mget (clip-angle-test [] [1            ]) 0) => (roughly 1           1e-6)
       (mget (clip-angle-test [] [(- 0 PI 0.01)]) 0) => (roughly (- PI 0.01) 1e-6)
       (mget (clip-angle-test [] [(+ PI 0.01)  ]) 0) => (roughly (- 0.01 PI) 1e-6))

(def convert-2d-index-probe
  (template/fn [x y] "#version 410 core
out lowp vec3 fragColor;
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
         14   0  14.5  0.5
          0  16   0.5 16.5)

(def convert-4d-index-probe
  (template/fn [x y z w selector] "#version 410 core
out lowp vec3 fragColor;
vec4 convert_4d_index(vec4 idx, int size_w, int size_z, int size_y, int size_x);
void main()
{
  vec4 result = convert_4d_index(vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>), 3, 5, 7, 11);
  fragColor.rg = result.<%= selector %>;
  fragColor.b = 0;
}"))

(def convert-4d-index-test (shader-test (fn [program]) convert-4d-index-probe convert-4d-index))

(tabular "Convert 4D index to 2D indices for part-manual interpolation"
         (fact (convert-4d-index-test [] [?x ?y ?z ?w ?selector]) => (roughly-matrix (div (matrix [?r ?g 0]) ?s2 ?s1) 1e-6))
         ?x ?y ?z     ?w     ?s2 ?s1 ?selector ?r               ?g
         0  0   0.123  0.123 11  5   "st"      0.5              (+ 0.5 11)
         1  0   0.123  0.123 11  5   "st"      1.5              (+ 1.5 11)
         0  0   1.123  0.123 11  5   "st"      (+ 0.5 11)       (+ 0.5 (* 2 11))
         0  0   4.123  0.123 11  5   "st"      (+ 0.5 (* 4 11)) (+ 0.5 (* 4 11))
         0  0   0.123  0.123  7  3   "pq"      0.5              (+ 0.5 7)
         0  1   0.123  0.123  7  3   "pq"      1.5              (+ 1.5 7)
         0  0   0.123  1.123  7  3   "pq"      (+ 0.5 7)        (+ 0.5 (* 2 7))
         0  0   0.123  2.123  7  3   "pq"      (+ 0.5 (* 2 7))  (+ 0.5 (* 2 7)))

(def transmittance-forward-probe
  (template/fn [x y z dx dy dz above] "#version 410 core
out lowp vec3 fragColor;
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
    (fn [program height-size elevation-size elevation-power radius max-height]
        (uniform-int program :height_size height-size)
        (uniform-int program :elevation_size elevation-size)
        (uniform-float program :elevation_power elevation-power)
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    transmittance-forward-probe transmittance-forward elevation-to-index horizon-angle))

(let [angle (* 0.375 PI)
      ca    (cos angle)
      sa    (sin angle)]
  (tabular "Convert point and direction to 2D lookup index in transmittance table"
           (fact (transmittance-forward-test [9 17 ?power 6378000.0 100000.0] [?x ?y ?z ?dx ?dy ?dz ?above])
                 => (roughly-matrix (div (matrix [?u ?v 0]) (matrix [16 8 1])) 1e-3))
           ?x      ?y ?z ?dx  ?dy ?dz ?power ?above ?u  ?v
           6378000 0  0  1    0   0   1      true   0.0  0.0
           6478000 0  0  1    0   0   1      true   0.0  8.0
           6378000 0  0  1e-6 1   0   1      true   8.0  0.0
           6378000 0  0 -1e-6 1   0   1      false  9.0  0.0
           6378025 0  0 -1e-6 1   0   1      true   8.0  0.0
           6378000 0  0  ca   sa  0   1      true   6.0  0.0
           6378000 0  0  ca   sa  0   2      true   4.0  0.0))

(defn lookup-2d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
                          (let [indices   [0 1 3 2]
                                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                                data-2d   [[1 2] [3 4] [5 6]]
                                data-flat (flatten (map (partial repeat 3) (flatten data-2d)))
                                table     (make-vector-texture-2d {:width 2 :height 3 :data (float-array data-flat)})
                                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                                vao       (make-vertex-array-object program indices vertices [:point 3])
                                tex       (texture-render 1 1 true
                                                          (use-program program)
                                                          (uniform-sampler program :table 0)
                                                          (use-textures table)
                                                          (render-quads vao))
                                img       (texture->vectors tex 1 1)]
                            (deliver result (get-vector img 0 0))
                            (destroy-texture tex)
                            (destroy-texture table)
                            (destroy-vertex-array-object vao)
                            (destroy-program program)))
        @result)))

(def interpolate-2d-probe
  (template/fn [x y] "#version 410 core
out lowp vec3 fragColor;
uniform sampler2D table;
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
void main()
{
  fragColor = interpolate_2d(table, 3, 2, vec2(<%= x %>, <%= y %>)).rgb;
}"))

(def interpolate-2d-test (lookup-2d-test interpolate-2d-probe interpolate-2d convert-2d-index))

(tabular "Perform 2d interpolation"
         (fact (mget (interpolate-2d-test ?x ?y) 0) => ?result)
         ?x   ?y ?result
         0    0  1.0
         0.25 0  1.25
         0    1  5.0
         1    1  6.0)

(defn lookup-4d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices   [0 1 3 2]
                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                data-4d   [[[[1 2] [3 4]] [[5 6] [7 8]]] [[[9 10] [11 12]] [[13 14] [15 16]]]]
                data-flat (flatten (map (partial repeat 3) (flatten (convert-4d-to-2d data-4d))))
                table     (make-vector-texture-2d {:width 4 :height 4 :data (float-array data-flat)})
                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao       (make-vertex-array-object program indices vertices [:point 3])
                tex       (texture-render 1 1 true
                                          (use-program program)
                                          (uniform-sampler program :table 0)
                                          (use-textures table)
                                          (render-quads vao))
                img       (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-texture table)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def interpolate-4d-probe
  (template/fn [x y z w] "#version 410 core
out lowp vec3 fragColor;
uniform sampler2D table;
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);
void main()
{
  fragColor = interpolate_4d(table, 2, 2, 2, 2, vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>)).rgb;
}"))

(def interpolate-4d-test (lookup-4d-test interpolate-4d-probe interpolate-4d convert-4d-index))

(tabular "Perform 4D interpolation"
         (fact (mget (interpolate-4d-test ?x ?y ?z ?w) 0) => ?result)
         ?x   ?y ?z  ?w  ?result
         0    0  0   0   1.0
         0.25 0  0   0   1.25
         0    1  0   0   3.0
         0    0  0.5 0   3.0
         0    0  0   0.5 5.0
         0    0  0.5 0.5 7.0)

(def ray-scatter-forward-probe
  (template/fn [x y z dx dy dz lx ly lz above selector] "#version 410 core
out lowp vec3 fragColor;
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
    (fn [program elevation-size light-elevation-size elevation-power radius max-height]
        (uniform-int program :elevation_size elevation-size)
        (uniform-int program :light_elevation_size light-elevation-size)
        (uniform-float program :elevation_power elevation-power)
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height))
    ray-scatter-forward-probe ray-scatter-forward elevation-to-index horizon-angle clip-angle oriented-matrix
    orthogonal-vector is-above-horizon))

(let [angle (* 0.375 PI)
      ca    (cos angle)
      sa    (sin angle)
      r     6378000
      h     100000]
  (tabular "Get 4D lookup index for ray scattering"
           (fact (mget (ray-scatter-forward-test [17 17 ?power 6378000.0 100000.0]
                                                 [?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?above ?sel]) 0)
                 => (roughly (/ ?result 16) 1e-3))
           ?x       ?y ?z ?dx  ?dy ?dz  ?lx    ?ly ?lz  ?power ?above ?sel ?result
           r        0  0  1    0   0    1      0   0    1.0    true   "w"   0.0
           (+ r h)  0  0  1    0   0    1      0   0    1.0    true   "w"  16.0
           r        0  0  1    0   0    1      0   0    1.0    true   "z"   0.0
           r        0  0  0    1   0    1      0   0    1.0    true   "z"   8.0
           r        0  0  0    1   0    1      0   0    1.0    false  "z"   9.0
           r        0  0  ca   sa  0    1      0   0    1.0    true   "z"   6.0
           r        0  0  ca   sa  0    1      0   0    2.0    true   "z"   4.0
           r        0  0  1    0   0    1      0   0    1.0    true   "y"   0.0
           r        0  0  1    0   0    1e-3   1   0    1.0    true   "y"   8.0
           r        0  0  1    0   0   -1e-3   1   0    1.0    true   "y"   9.0
           (+ r 25) 0  0  1    0   0   -1e-3   1   0    1.0    true   "y"   8.0
           r        0  0  1    0   0    ca     sa  0    1.0    true   "y"   6.0
           r        0  0  1    0   0    ca     sa  0    2.0    true   "y"   4.0
           r        0  0  0    1   0    0      1   0    1.0    true   "x"   0.0
           r        0  0  0    1   0    0      0   1    1.0    true   "x"   8.0
           r        0  0  0    1   0    0      0  -1    1.0    true   "x"   8.0
           r        0  0  0    0   1    0      0   1    1.0    true   "x"   0.0
           r        0  0  0   -1   1e-6 0     -1  -1e-6 1.0    true   "x"   0.0
           0        r  0  ca   0   sa   (- sa) 0   ca   1.0    true   "x"   8.0))

(def ray-box-probe
  (template/fn [ax ay az bx by bz ox oy oz dx dy dz]
"#version 410 core
out lowp vec3 fragColor;
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

(def convert-3d-index-probe
  (template/fn [x y z]
"#version 410 core
out lowp vec3 fragColor;
vec3 convert_3d_index(vec3 point, vec3 box_min, vec3 box_max);
void main()
{
  fragColor = convert_3d_index(vec3(<%= x %>, <%= y %>, <%= z %>), vec3(-30, -20, -10), vec3(10, 0, 5));
}"))

(def convert-3d-index-test (shader-test (fn [program]) convert-3d-index-probe convert-3d-index))

(tabular "Convert 3D point to 3D texture lookup index"
         (fact (convert-3d-index-test [] [?x ?y ?z]) => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
         ?x  ?y  ?z ?r ?g ?b
        -30 -20 -10 0  0  0
         10 -20 -10 1  0  0
        -30   0 -10 0  1  0
        -30 -20   5 0  0  1)

(defn lookup-3d-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
                          (let [indices   [0 1 3 2]
                                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                                data-3d   [[[1 2] [3 4]] [[5 6] [7 8]]]
                                data-flat (flatten data-3d)
                                table     (make-float-texture-3d {:width 2 :height 2 :depth 2 :data (float-array data-flat)})
                                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                                vao       (make-vertex-array-object program indices vertices [:point 3])
                                tex       (texture-render 1 1 true
                                                          (use-program program)
                                                          (uniform-sampler program :table 0)
                                                          (use-textures table)
                                                          (render-quads vao))
                                img       (texture->vectors tex 1 1)]
                            (deliver result (get-vector img 0 0))
                            (destroy-texture tex)
                            (destroy-texture table)
                            (destroy-vertex-array-object vao)
                            (destroy-program program)))
        @result)))

(def interpolate-3d-probe
  (template/fn [x y z]
"#version 410 core
out lowp vec3 fragColor;
uniform sampler3D table;
float interpolate_3d(sampler3D tex, vec3 point, vec3 box_min, vec3 box_max);
void main()
{
  float result = interpolate_3d(table, vec3(<%= x %>, <%= y %>, <%= z %>), vec3 (0, 0, 0), vec3(1, 1, 1));
  fragColor = vec3(result, 0, 0);
}"))

(def interpolate-3d-test (lookup-3d-test interpolate-3d-probe interpolate-3d convert-3d-index))

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
out lowp vec3 fragColor;
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
void main()
{
  vec4 result = ray_shell(vec3(<%= cx %>, <%= cy %>, <%= cz %>),
                          <%= radius1 %>,
                          <%= radius2 %>,
                          vec3(<%= ox %>, <%= oy %>, <%= oz %>),
                          vec3(<%= dx %>, <%= dy %>, <%= dz %>));
  fragColor.rg = result.<%= selector %>;
  fragColor.b = 0;
}"))

(def ray-shell-test (shader-test ray-shell-probe ray-shell ray-sphere))

(tabular "Shader for computing intersections of ray with a shell"
         (fact (ray-shell-test ?cx ?cy ?cz ?radius1 ?radius2 ?ox ?oy ?oz ?dx ?dy ?dz ?selector)
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
