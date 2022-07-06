(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix shader-test)]
              [comb.template :as template]
              [clojure.math :refer (exp log)]
              [clojure.core.matrix :refer (ecount mget matrix)]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :refer :all]
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

(def cloud-track-probe
  (template/fn [px qx decay scatter density lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_forward(vec3 point, vec3 direction)
{
  float distance = 10 - point.x;
  float transmittance = exp(-<%= decay %> * distance);
  return vec3(transmittance, transmittance, transmittance);
}
vec3 ray_scatter_forward(vec3 point, vec3 direction, vec3 light)
{
  float distance = 10 - point.x;
  float amount = <%= scatter %> * (1 - pow(2, -distance));
  return vec3(0, 0, amount);
}
float cloud_density(vec3 point)
{
  return <%= density %>;
}
vec3 cloud_shadow(vec3 point, vec3 light)
{
  return vec3(0, 1, 0);
}
float phase(float g, float mu)
{
  return 1.0 - 0.5 * mu;
}
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);
void main()
{
  vec3 p = vec3(<%= px %>, 0, 0);
  vec3 q = vec3(<%= qx %>, 0, 0);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = cloud_track(light_direction, p, q, incoming);
}
"))

(def cloud-track-test
  (shader-test
    (fn [program anisotropic n]
        (uniform-float program :anisotropic anisotropic)
        (uniform-int program :cloud_samples n))
    cloud-track-probe
    cloud-track))

(tabular "Shader for putting volumetric clouds into the atmosphere"
         (fact (cloud-track-test [?anisotropic ?n] [?px ?qx ?decay ?scatter ?density ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-3))
         ?px ?qx ?n ?decay  ?scatter ?density ?anisotropic ?lx ?ly ?lz ?ir ?ig ?ib ?or      ?og                    ?ob
         0    1  1  0       0        0.0      1            0   0   1   0   0   0   0        0                      0
         0    0  1  0       0        0.0      1            0   0   1   1   1   1   1        1                      1
         0    1  1  0       0        0.0      1            0   0   1   1   1   1   1        1                      1
         0    1  1  1       0        0.0      1            0   0   1   1   0   0   (exp -1) 0                      0
         9   10  1  0       1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         8    9  1  0       1        0.0      1            0   0   1   0   0   0   0        0                      0.25
         8    9  1  (log 2) 1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         8    9  2  (log 2) 1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         0    1  1  0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -1))         0
         0    2  1  0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0    2  2  0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0    1  1  0       0        1.0      1            1   0   0   0   0   0   0        (* 0.5 (- 1 (exp -1))) 0
         0    1  1  0       0        1.0      0            1   0   0   0   0   0   0        (- 1 (exp -1))         0)

(def cloud-track-base-probe
  (template/fn [px qx decay scatter density ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_forward(vec3 point, vec3 direction)
{
  float distance = 10 - point.x;
  float transmittance = exp(-<%= decay %> * distance);
  return vec3(transmittance, transmittance, transmittance);
}
vec3 ray_scatter_forward(vec3 point, vec3 direction, vec3 light)
{
  float distance = 10 - point.x;
  float amount = <%= scatter %> * (1 - pow(2, -distance));
  return vec3(0, 0, amount);
}
float cloud_density(vec3 point)
{
  return <%= density %>;
}
float phase(float g, float mu)
{
  return 1.0 + 0.5 * mu;
}
vec3 cloud_track_base(vec3 p, vec3 q, vec3 incoming);
void main()
{
  vec3 p = vec3(<%= px %>, 0, 0);
  vec3 q = vec3(<%= qx %>, 0, 0);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = cloud_track_base(p, q, incoming);
}
"))

(def cloud-track-base-test
  (shader-test
    (fn [program anisotropic n]
        (uniform-float program :anisotropic anisotropic)
        (uniform-int program :cloud_base_samples n))
    cloud-track-base-probe
    cloud-track-base))

(tabular "Shader for determining shadowing (or lack of shadowing) by clouds"
         (fact (cloud-track-base-test [?anisotropic ?n] [?px ?qx ?decay ?scatter ?density ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-3))
         ?px  ?qx ?n ?decay  ?scatter ?density ?anisotropic ?ir ?ig ?ib ?or        ?og ?ob
         0    0   1  0       0        0.0      1            0   0   0   0          0   0
         0    0   1  0       0        0.0      1            1   0   0   1          0   0
         0    1   1  0       0        0.0      1            1   0   0   1          0   0
         0    1   1  1       0        0.0      1            1   0   0   (exp -1)   0   0
         0    1   2  1       0        0.0      1            1   0   0   (exp -1)   0   0
         9   10   1  0       1        0.0      1            0   0   0   0          0   0.5
         8    9   1  0       1        0.0      1            0   0   0   0          0   0.25
         8    9   1  (log 2) 1        0.0      1            0   0   0   0          0   0.5
         0    1   1  0       0        1.0      1            1   0   0   (exp -0.5) 0   0
         0    1   2  0       0        1.0      1            1   0   0   (exp -0.5) 0   0
         0    1   2  0       0        1.0      0            1   0   0   1          0   0)

(def sky-outer-probe
  (template/fn [x y z dx dy dz lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 sky_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming);
vec3 attenuation_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming)
{
  return vec3(point.x * 0.01, incoming.g, incoming.b);
}
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  return vec3(incoming.r, incoming.g + (q.x - p.x) * 0.01, incoming.b);
}
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  return vec3(incoming.r, incoming.g, incoming.b + (q.x - p.x) * 0.01);
}
void main()
{
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = sky_outer(light_direction, point, direction, incoming);
}"))

(def sky-outer-test
  (shader-test
    (fn [program radius max-height cloud-bottom cloud-top]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height)
        (uniform-float program :cloud_bottom cloud-bottom)
        (uniform-float program :cloud_top cloud-top))
    sky-outer-probe
    sky-outer
    ray-sphere
    ray-shell))

(tabular "Shader for determining lighting of atmosphere including clouds coming from space"
         (fact (sky-outer-test [60 40 ?h1 ?h2] [?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-5))
         ?x  ?y ?z ?dx ?dy ?dz ?h1 ?h2 ?lx ?ly ?lz ?ir ?ig ?ib  ?or ?og ?ob
         110 0  0  1   0   0   10  20  1   0   0   0   0   0    0   0   0
         110 0  0  1   0   0   10  20  1   0   0   0.1 0.0 0.0  0.1 0.0 0.0
          90 0  0  1   0   0   10  20  1   0   0   0.1 0.0 0.0  0.9 0.0 0.0
        -110 0  0  1   0   0    0   0  1   0   0   0.1 0.0 0.0 -1.0 0.0 0.0
          80 0  0  1   0   0   10  30  1   0   0   0.1 0.0 0.0  0.9 0.1 0.0
          70 0  0  1   0   0   20  30  1   0   0   0.1 0.0 0.0  0.9 0.1 0.1
        -110 0  0  1   0   0   20  30  1   0   0   0.1 0.0 0.0  0.9 0.2 1.7)

(def sky-track-probe
  (template/fn [px py pz qx qy qz lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 sky_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  return vec3(incoming.r, incoming.g + (p.x - q.x) * 0.01, incoming.b);
}
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  return vec3(incoming.r, incoming.g, incoming.b + (p.x - q.x) * 0.01);
}
void main()
{
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 p = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 q = vec3(<%= qx %>, <%= qy %>, <%= qz %>);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = sky_track(light_direction, p, q, incoming);
}"))

(def sky-track-test
  (shader-test
    (fn [program radius max-height cloud-bottom cloud-top]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height)
        (uniform-float program :cloud_bottom cloud-bottom)
        (uniform-float program :cloud_top cloud-top))
    sky-track-probe
    sky-track
    ray-sphere
    ray-shell
    clip-shell-intersections))

(tabular "Shader for determining lighting of atmosphere including clouds between two points"
         (fact (sky-track-test [60 40 ?h1 ?h2] [?px ?py ?pz ?qx ?qy ?qz ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-5))
         ?px ?py ?pz  ?qx ?qy ?qz ?h1 ?h2 ?lx ?ly ?lz ?ir ?ig ?ib  ?or ?og ?ob
        -120 0  -110 -110 0   0   20  30  1   0   0   0   0   0    0   0   0
        -120 0  -110 -110 0   0   20  30  1   0   0   0.1 0   0    0.1 0   0
          70 0   0     60 0   0   20  30  1   0   0   0.1 0   0    0.1 0   0.1
         110 0   0     60 0   0    0   0  1   0   0   0.1 0   0    0.1 0   0.4
          90 0   0     60 0   0   20  30  1   0   0   0.1 0   0    0.1 0.1 0.2
         100 0   0     60 0   0   20  30  1   0   0   0.1 0   0    0.1 0.1 0.3
         100 0   0   -100 0   0   20  30  1   0   0   0.1 0   0    0.1 0.2 1.8)

(def cloud-shadow-probe
  (template/fn [x y z lx ly lz]
"#version 410 core
out lowp vec3 fragColor;
vec3 cloud_shadow(vec3 point, vec3 light_direction);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = cloud_shadow(point, light_direction);
}"))

(def cloud-shadow-test
  (shader-test
    (fn [program radius max-height cloud-bottom cloud-top]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height)
        (uniform-float program :cloud_bottom cloud-bottom)
        (uniform-float program :cloud_top cloud-top))
    cloud-shadow-probe
    cloud-shadow
    ray-sphere
    ray-shell))

(tabular "Shader for determining illumination of clouds"
         (fact (cloud-shadow-test [60 40 ?h1 ?h2] [?x ?y ?z ?lx ?ly ?lz])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-5))
         ?x  ?y ?z ?lx ?ly ?lz ?h1 ?h2 ?or ?og ?ob
         100 0  0  1   0   0   0   0   1   1   1)
