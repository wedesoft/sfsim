(ns sfsim25.t-shaders
  (:require [midje.sweet :refer :all]
            [comb.template :as template]
            [clojure.core.matrix :refer :all]
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

(def ray-sphere-test (shader-test ray-sphere-probe ray-sphere))

(facts "Shader for intersection of ray with sphere"
       (ray-sphere-test 0 0 0 2 2 -1 0 0 1) => (matrix [0.0 0.0 0.0])
       (ray-sphere-test 0 0 0 0 0 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (ray-sphere-test 2 2 0 2 2 -2 0 0 1) => (matrix [1.0 2.0 0.0])
       (ray-sphere-test 0 0 0 0 0 -2 0 0 2) => (matrix [0.5 1.0 0.0])
       (ray-sphere-test 0 0 0 0 0  0 0 0 1) => (matrix [0.0 1.0 0.0])
       (ray-sphere-test 0 0 0 0 0  2 0 0 1) => (matrix [0.0 0.0 0.0]))

(def elevation-to-index-probe
  (template/fn [size elevation horizon-angle power] "#version 410 core
out lowp vec3 fragColor;
float elevation_to_index(int size, float elevation, float horizon_angle, float power);
void main()
{
  float result = elevation_to_index(<%= size %>, <%= elevation %>, <%= horizon-angle %>, <%= power %>);
  fragColor = vec3(result, 0, 0);
}"))

(def elevation-to-index-test (shader-test elevation-to-index-probe elevation-to-index))

(facts "Shader for converting elevation to index"
       (mget (elevation-to-index-test 17 0.0 0.0 1.0) 0)               => (roughly (/ 0.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.14159) 0.0 1.0) 0)   => (roughly (/ 8.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.14160) 0.0 1.0) 0)   => (roughly (/ 9.5 17))
       (mget (elevation-to-index-test 17 3.14159 0.0 1.0) 0)           => (roughly (/ 16.5 17))
       (mget (elevation-to-index-test 17 (* 0.375 3.14159) 0.0 1.0) 0) => (roughly (/ 6.5 17))
       (mget (elevation-to-index-test 17 (* 0.375 3.14159) 0.0 2.0) 0) => (roughly (/ 4.5 17))
       (mget (elevation-to-index-test 17 (* 0.625 3.14159) 0.0 1.0) 0) => (roughly (/ 11.25 17))
       (mget (elevation-to-index-test 17 (* 0.625 3.14159) 0.0 2.0) 0) => (roughly (/ 13.0 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.34159) 0.1 1.0) 0)   => (roughly (/ 8.5 17))
       (mget (elevation-to-index-test 17 (* 0.5 3.34160) 0.1 1.0) 0)   => (roughly (/ 9.5 17)))

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
       (mget (horizon-angle-test (* 2 6378000) 0 0) 0) => (roughly (/ Math/PI 3))
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
  (dot (orthogonal-vector-test 1 0 0) (matrix [1 0 0])) => 0.0
  (norm (orthogonal-vector-test 1 0 0)) => 1.0
  (dot (orthogonal-vector-test 0 1 0) (matrix [0 1 0])) => 0.0
  (norm (orthogonal-vector-test 0 1 0)) => 1.0
  (dot (orthogonal-vector-test 0 0 1) (matrix [0 0 1])) => 0.0
  (norm (orthogonal-vector-test 0 0 1)) => 1.0)

(defn roughly-matrix [m] (fn [x] (< (norm (sub m x)) 1e-6)))

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
         (slice m 1 0) => (roughly-matrix (matrix [0.36 0.48 0.8]))
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
       (mget (clip-angle-test (- 0 Math/PI 0.01)) 0) => (roughly (- Math/PI 0.01) 1e-6)
       (mget (clip-angle-test (+ Math/PI 0.01)) 0) => (roughly (- 0.01 Math/PI) 1e-6))

(def convert-4d-index-probe
  (template/fn [x y z w a b] "#version 410 core
out lowp vec3 fragColor;
vec4 convert_4d_index(vec4 idx, int size);
void main()
{
  vec4 result = convert_4d_index(vec4(<%= x %>, <%= y %>, <%= z %>, <%= w %>), 17);
  fragColor.rg = <%= a %> * result.st + <%= b %> * result.pq;
  fragColor.b = 0;
}"))

(def convert-4d-index-test (shader-test convert-4d-index-probe convert-4d-index))

(facts "Convert 4D index to 2D indices for part-manual interpolation"
       (convert-4d-index-test 0 0  0.123  0.123 1 0) => (roughly-matrix (div (matrix [0.5 (+ 0.5 17) 0]) 17 17))
       (convert-4d-index-test 1 0  0.123  0.123 1 0) => (roughly-matrix (div (matrix [1.5 (+ 1.5 17) 0]) 17 17))
       (convert-4d-index-test 0 0  1.123  0.123 1 0) => (roughly-matrix (div (matrix [(+ 0.5 17) (+ 0.5 (* 2 17)) 0]) 17 17))
       (convert-4d-index-test 0 0 16.123  0.123 1 0) => (roughly-matrix (div (matrix [(+ 0.5 (* 16 17)) (+ 0.5 (* 16 17)) 0]) 17 17))
       (convert-4d-index-test 0 0  0.123  0.123 0 1) => (roughly-matrix (div (matrix [0.5 (+ 0.5 17) 0]) 17 17))
       (convert-4d-index-test 0 1  0.123  0.123 0 1) => (roughly-matrix (div (matrix [1.5 (+ 1.5 17) 0]) 17 17))
       (convert-4d-index-test 0 0  0.123  1.123 0 1) => (roughly-matrix (div (matrix [(+ 0.5 17) (+ 0.5 (* 2 17)) 0]) 17 17))
       (convert-4d-index-test 0 0  0.123 16.123 0 1) => (roughly-matrix (div (matrix [(+ 0.5 (* 16 17)) (+ 0.5 (* 16 17)) 0]) 17 17)))
