(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [comb.template :as template]
            [clojure.core.matrix :refer (matrix mget mmul dot sub div transpose identity-matrix)]
            [clojure.math :refer (cos sin PI)]
            [clojure.core.matrix.linear :refer (norm)]
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

(defn shader-test [probe & shaders]
  (fn [& args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program  (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao      (make-vertex-array-object program indices vertices [:point 3])
                tex      (texture-render 1 1 true (use-program program) (render-quads vao))
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

(def ray-sphere-test (shader-test ray-sphere-probe ray-sphere))

(tabular "Shader for intersection of ray with sphere"
         (fact (ray-sphere-test ?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz) => (matrix [?ix ?iy 0]))
         ?cx ?cy ?cz ?ox ?oy ?oz ?dx ?dy ?dz ?ix ?iy
         0   0   0   2   2  -1   0   0   1   0.0 0.0
         0   0   0   0   0  -2   0   0   1   1.0 2.0
         2   2   0   2   2  -2   0   0   1   1.0 2.0
         0   0   0   0   0  -2   0   0   2   0.5 1.0
         0   0   0   0   0   0   0   0   1   0.0 1.0
         0   0   0   0   0   2   0   0   1   0.0 0.0)

(def elevation-to-index-probe
  (template/fn [above-horizon elevation horizon-angle power] "#version 410 core
out lowp vec3 fragColor;
float elevation_to_index(int size, float elevation, float horizon_angle, float power, bool above_horizon);
void main()
{
  float result = elevation_to_index(17, <%= elevation %>, <%= horizon-angle %>, <%= power %>, <%= above-horizon %>);
  fragColor = vec3(result, 0, 0);
}"))

(def elevation-to-index-test (shader-test elevation-to-index-probe elevation-to-index))

(tabular "Shader for converting elevation to index"
         (fact (mget (elevation-to-index-test ?above-horizon ?elevation ?horizon-angle ?power) 0) => (roughly (/ ?result 16)))
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
float horizon_angle(vec3 point, float radius);
void main()
{
  float result = horizon_angle(vec3(<%= x %>, <%= y %>, <%= z %>), 6378000);
  fragColor = vec3(result, 0, 0);
}"))

(def horizon-angle-test (shader-test horizon-angle-probe horizon-angle))

(facts "Angle of sphere's horizon angle below horizontal plane depending on height"
       (mget (horizon-angle-test 6378000 0 0) 0)       => (roughly 0.0)
       (mget (horizon-angle-test (* 2 6378000) 0 0) 0) => (roughly (/ PI 3))
       (mget (horizon-angle-test 6377999 0 0) 0)       => (roughly 0.0))

(def orthogonal-vector-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
vec3 orthogonal_vector(vec3 n);
void main()
{
  fragColor = orthogonal_vector(vec3(<%= x %>, <%= y %>, <%= z %>));
}"))

(def orthogonal-vector-test (shader-test orthogonal-vector-probe orthogonal-vector))

(facts "Create normal vector orthogonal to the specified one"
  (dot  (orthogonal-vector-test 1 0 0) (matrix [1 0 0])) => 0.0
  (norm (orthogonal-vector-test 1 0 0)) => 1.0
  (dot  (orthogonal-vector-test 0 1 0) (matrix [0 1 0])) => 0.0
  (norm (orthogonal-vector-test 0 1 0)) => 1.0
  (dot  (orthogonal-vector-test 0 0 1) (matrix [0 0 1])) => 0.0
  (norm (orthogonal-vector-test 0 0 1)) => 1.0)

(defn roughly-matrix [m] (fn [x] (< (norm (sub m x)) 1e-3)))

(def oriented-matrix-probe
  (template/fn [x y z] "#version 410 core
out lowp vec3 fragColor;
mat3 oriented_matrix(vec3 n);
void main()
{
  fragColor = oriented_matrix(vec3(0.36, 0.48, 0.8)) * vec3(<%= x %>, <%= y %>, <%= z %>);
}"))

(def oriented-matrix-test (shader-test oriented-matrix-probe orthogonal-vector oriented-matrix))

(facts "Create oriented matrix given a normal vector"
       (let [m (transpose (matrix [(oriented-matrix-test 1 0 0) (oriented-matrix-test 0 1 0) (oriented-matrix-test 0 0 1)])) ]
         (mmul m (matrix [0.36 0.48 0.8])) => (roughly-matrix (matrix [1 0 0]))
         (mmul m (transpose m)) => (roughly-matrix (identity-matrix 3))))

(def clip-angle-probe
  (template/fn [angle] "#version 410 core
out lowp vec3 fragColor;
float clip_angle(float angle);
void main()
{
  fragColor = vec3(clip_angle(<%= angle %>), 0, 0);
}"))

(def clip-angle-test (shader-test clip-angle-probe clip-angle))

(facts "Convert angle to be between -pi and +pi"
       (mget (clip-angle-test 0) 0) => (roughly 0 1e-6)
       (mget (clip-angle-test 1) 0) => (roughly 1 1e-6)
       (mget (clip-angle-test (- 0 PI 0.01)) 0) => (roughly (- PI 0.01) 1e-6)
       (mget (clip-angle-test (+ PI 0.01)) 0) => (roughly (- 0.01 PI) 1e-6))

(def convert-2d-index-probe
  (template/fn [x y] "#version 410 core
out lowp vec3 fragColor;
vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
void main()
{
  fragColor.rg = convert_2d_index(vec2(<%= x %>, <%= y %>), 17, 15);
  fragColor.b = 0;
}"))

(def convert-2d-index-test (shader-test convert-2d-index-probe convert-2d-index))

(tabular "Convert 2D index to 2D texture lookup index"
         (fact (convert-2d-index-test ?x ?y) => (roughly-matrix (div (matrix [?r ?g 0]) (matrix [15 17 1]))))
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

(def convert-4d-index-test (shader-test convert-4d-index-probe convert-4d-index))

(tabular "Convert 4D index to 2D indices for part-manual interpolation"
         (fact (convert-4d-index-test ?x ?y ?z ?w ?selector) => (roughly-matrix (div (matrix [?r ?g 0]) ?s2 ?s1)))
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
  (template/fn [x y z dx dy dz power above] "#version 410 core
out lowp vec3 fragColor;
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int height_size, int elevation_size,
                           float power, bool above_horizon);
void main()
{
  fragColor.rg = transmittance_forward(vec3(<%= x %>, <%= y %>, <%= z %>), vec3(<%= dx %>, <%= dy %>, <%= dz %>),
                                       6378000.0, 100000, 9, 17, <%= power %>, <%= above %>);
  fragColor.b = 0;
}"))

(def transmittance-forward-test (shader-test transmittance-forward-probe transmittance-forward elevation-to-index horizon-angle))

(let [angle (* 0.375 PI)
      ca    (cos angle)
      sa    (sin angle)]
  (tabular "Convert point and direction to 2D lookup index in transmittance table"
           (fact (transmittance-forward-test ?x ?y ?z ?dx ?dy ?dz ?power ?above)
                 => (roughly-matrix (div (matrix [?u ?v 0]) (matrix [16 8 1]))))
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
                data-2d   [[1 2] [3 4]]
                data-flat (flatten (map (partial repeat 3) (flatten data-2d)))
                table     (make-vector-texture-2d {:width 2 :height 2 :data (float-array data-flat)})
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
  fragColor = interpolate_2d(table, 2, 2, vec2(<%= x %>, <%= y %>)).rgb;
}"))

(def interpolate-2d-test (lookup-2d-test interpolate-2d-probe interpolate-2d convert-2d-index))

(tabular "Perform 2d interpolation"
         (fact (mget (interpolate-2d-test ?x ?y) 0) => ?result)
         ?x   ?y ?result
         0    0  1.0
         0.25 0  1.25
         0    1  3.0
         1    1  4.0)

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
  (template/fn [x y z dx dy dz lx ly lz power above selector] "#version 410 core
out lowp vec3 fragColor;
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int height_size,
                         int elevation_size, int light_elevation_size, int heading_size, float power, bool above_horizon);
void main()
{
  vec4 result = ray_scatter_forward(vec3(<%= x %>, <%= y %>, <%= z %>), vec3(<%= dx %>, <%= dy %>, <%= dz %>),
                                    vec3(<%= lx %>, <%= ly %>, <%= lz %>), 6378000.0, 100000.0, 17, 17, 17, 17, <%= power %>,
                                    <%= above %>);
  fragColor.r = result.<%= selector %>;
  fragColor.g = 0;
  fragColor.b = 0;
}"))

(def ray-scatter-forward-test (shader-test ray-scatter-forward-probe ray-scatter-forward elevation-to-index horizon-angle
                                           clip-angle oriented-matrix orthogonal-vector sky-or-ground))

(let [angle (* 0.375 PI)
      ca    (cos angle)
      sa    (sin angle)
      r     6378000
      h     100000]
  (tabular "Get 4D lookup index for ray scattering"
           (fact (mget (ray-scatter-forward-test ?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?power ?above ?sel) 0)
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
