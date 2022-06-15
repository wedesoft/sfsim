(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [comb.template :as template]
              [clojure.math :refer (exp log)]
              [clojure.core.matrix :refer (ecount mget matrix sub)]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.render :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.clouds :refer :all :as clouds]))

(facts "Create a vector of random points"
       (random-points 0 64)                  => []
       (count (random-points 1 64))          => 1
       (random-points 1 64)                  => vector?
       (ecount (first (random-points 1 64))) => 3
       (mget (first (random-points 1 64)) 0) => #(>= % 0)
       (mget (first (random-points 1 64)) 0) => #(<= % 64))

(facts "Repeat point cloud in each direction"
       (repeat-points 10 [])                         => []
       (count (repeat-points 10 [(matrix [2 3 5])])) => 27
       (repeat-points 10 [(matrix [2 3 5])])         => vector?
       (nth (repeat-points 10 [(matrix [2 3 5])]) 0) => (matrix [2 3 5])
       (nth (repeat-points 1 [(matrix [0 0 0])])  0) => (matrix [ 0  0  0])
       (nth (repeat-points 1 [(matrix [0 0 0])])  1) => (matrix [-1  0  0])
       (nth (repeat-points 1 [(matrix [0 0 0])])  2) => (matrix [ 1  0  0])
       (nth (repeat-points 1 [(matrix [0 0 0])])  3) => (matrix [ 0 -1  0])
       (nth (repeat-points 1 [(matrix [0 0 0])])  6) => (matrix [ 0  1  0])
       (nth (repeat-points 1 [(matrix [0 0 0])])  9) => (matrix [ 0  0 -1])
       (nth (repeat-points 1 [(matrix [0 0 0])]) 18) => (matrix [ 0  0  1]))

(facts "Normalise values of a vector"
       (normalise-vector [1.0])         => [1.0]
       (normalise-vector [0.0 1.0 2.0]) => [0.0 0.5 1.0]
       (normalise-vector [1.0])         => vector?)

(facts "Invert values of a vector"
       (invert-vector []) => []
       (invert-vector [0.0]) => [1.0]
       (invert-vector [1.0]) => [0.0]
       (invert-vector [1.0]) => vector?)

(facts "Create 3D Worley noise"
       (with-redefs [clouds/random-points (fn [n size] (facts n => 1 size => 2) [(matrix [0.5 0.5 0.5])])]
         (nth (worley-noise 1 2) 0)     => 1.0
         (count (worley-noise 1 2))     => 8
         (apply min (worley-noise 1 2)) => 0.0)
       (with-redefs [clouds/random-points (fn [n size] (facts n => 2 size => 2) [(matrix [0.5 0.5 0.5]) (matrix [1.5 1.5 1.5])])]
         (nth (worley-noise 2 2) 7)     => 1.0)
      (with-redefs [clouds/random-points (fn [n size] (facts n => 1 size => 2) [(matrix [0.0 0.0 0.0])])]
         (nth (worley-noise 1 2) 7)     => (nth (worley-noise 1 2) 0)))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

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
          (let [indices       [0 1 3 2]
                vertices      [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program       (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao           (make-vertex-array-object program indices vertices [:point 3])
                tex           (texture-render 1 1 true
                                              (use-program program)
                                              (render-quads vao))
                img           (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def cloud-track-probe
  (template/fn [px qx n decay scatter density ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_forward(vec3 point, vec3 direction)
{
  float distance = 10 - point.x;
  float transmittance = exp(-<%= decay %> * distance);
  return vec3(transmittance, transmittance, transmittance);
}
vec3 ray_scatter_forward(vec3 point, vec3 direction)
{
  float distance = 10 - point.x;
  float amount = <%= scatter %> * (1 - pow(2, -distance));
  return vec3(0, 0, amount);
}
float cloud_density(vec3 point)
{
  return <%= density %>;
}
vec3 cloud_track(vec3 p, vec3 q, int n, vec3 background);
void main()
{
  vec3 p = vec3(<%= px %>, 0, 0);
  vec3 q = vec3(<%= qx %>, 0, 0);
  vec3 background = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = cloud_track(p, q, <%= n %>, background);
}
"))

(def cloud-track-test (shader-test cloud-track-probe cloud-track))

(tabular "Shader for putting volumetric clouds into the atmosphere"
         (fact (cloud-track-test ?px ?qx ?n ?decay ?scatter ?density ?ir ?ig ?ib)
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-3))
         ?px ?qx ?n ?decay  ?scatter ?density ?ir ?ig ?ib ?or      ?og ?ob
         0    1  1  0       0        0.0      0   0   0   0        0   0
         0    0  1  0       0        0.0      1   1   1   1        1   1
         0    1  1  0       0        0.0      1   1   1   1        1   1
         0    1  1  1       0        0.0      1   0   0   (exp -1) 0   0
         9   10  1  0       1        0.0      0   0   0   0        0   0.5
         8    9  1  0       1        0.0      0   0   0   0        0   0.25
         8    9  1  (log 2) 1        0.0      0   0   0   0        0   0.5
         8    9  2  (log 2) 1        0.0      0   0   0   0        0   0.5
         0    1  1  0       0        1.0      0   0   0   0        0   0)
