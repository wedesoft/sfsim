(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix shader-test vertex-passthrough)]
              [comb.template :as template]
              [clojure.math :refer (exp log)]
              [clojure.core.matrix :refer (ecount mget matrix array dimensionality dimension-count)]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.clouds :refer :all :as clouds]))

(facts "Create a 3D grid with a random point in each cell"
       (dimensionality (random-point-grid 1 1)) => 4
       (map #(dimension-count (random-point-grid 1 1) %) (range 4)) => [1 1 1 3]
       (map #(mget (random-point-grid 1 1 identity) 0 0 0 %) (range 3)) => [1.0 1.0 1.0]
       (map #(dimension-count (random-point-grid 2 1) %) (range 4)) => [2 2 2 3]
       (mget (random-point-grid 2 8 identity) 0 0 0 0) => 4.0
       (mget (random-point-grid 2 8 identity) 0 0 1 0) => 8.0
       (mget (random-point-grid 2 8 identity) 0 1 0 1) => 8.0
       (mget (random-point-grid 2 8 identity) 1 0 0 2) => 8.0)

(facts "Extract point from specified cell"
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 0 0 0) => (matrix [1 2 3])
       (extract-point-from-grid (array [[[[1 2 3] [4 5 6]]]]) 10 0 0 1) => (matrix [4 5 6])
       (extract-point-from-grid (array [[[[1 2 3]] [[4 5 6]]]]) 10 0 1 0) => (matrix [4 5 6])
       (extract-point-from-grid (array [[[[1 2 3]]] [[[4 5 6]]]]) 10 1 0 0) => (matrix [4 5 6])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 0 0 1) => (matrix [11 2 3])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 0 1 0) => (matrix [1 12 3])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 1 0 0) => (matrix [1 2 13])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 0 0 -1) => (matrix [-9 2 3])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 0 -1 0) => (matrix [1 -8 3])
       (extract-point-from-grid (array [[[[1 2 3]]]]) 10 -1 0 0) => (matrix [1 2 -7]))

(facts "Closest distance of point in grid"
       (closest-distance-to-point-in-grid (array [[[[1 1 1]]]]) 1 2 (matrix [1 1 1])) => 0.0
       (closest-distance-to-point-in-grid (array [[[[1 1 1]]]]) 1 2 (matrix [0.5 1 1])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1 1 1] [3 1 1]]]]) 2 4 (matrix [3 1 1])) => 0.0
       (closest-distance-to-point-in-grid (array [[[[1 1 1]] [[1 3 1]]]]) 2 4 (matrix [1 3 1])) => 0.0
       (closest-distance-to-point-in-grid (array [[[[1 1 1]]] [[[1 1 3]]]]) 2 4 (matrix [1 1 3])) => 0.0
       (closest-distance-to-point-in-grid (array [[[[0.25 1 1]]]]) 1 2 (matrix [1.75 1 1])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1.75 1 1]]]]) 1 2 (matrix [0.25 1 1])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1 0.25 1]]]]) 1 2 (matrix [1 1.75 1])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1 1.75 1]]]]) 1 2 (matrix [1 0.25 1])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1 1 0.25]]]]) 1 2 (matrix [1 1 1.75])) => 0.5
       (closest-distance-to-point-in-grid (array [[[[1 1 1.75]]]]) 1 2 (matrix [1 1 0.25])) => 0.5)

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
       (with-redefs [clouds/random-point-grid (fn [n size] (facts n => 1 size => 2) (array [[[[0.5 0.5 0.5]]]]))]
         (nth (worley-noise 1 2) 0) => 1.0
         (count (worley-noise 1 2)) => 8
         (apply min (worley-noise 1 2)) => 0.0))

(def cloud-track-probe
  (template/fn [a b decay scatter density lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_track(vec3 p, vec3 q)
{
  float dp = 10 - p.x;
  float dq = 10 - q.x;
  float transmittance = exp(-<%= decay %> * (dp - dq));
  return vec3(transmittance, transmittance, transmittance);
}
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  float dp = 10 - p.x;
  float amountp = <%= scatter %> * (1 - pow(2, -dp));
  float dq = 10 - q.x;
  float amountq = <%= scatter %> * (1 - pow(2, -dq));
  float amount = amountp - transmittance_track(p, q).r * amountq;
  return vec3(0, 0, amount);
}
float cloud_density(vec3 point, float lod)
{
  return <%= density %>;
}
vec3 cloud_shadow(vec3 point, vec3 light, float lod)
{
  return vec3(0, 1, 0);
}
float phase(float g, float mu)
{
  return 1.0 - 0.5 * mu;
}
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
void main()
{
  vec3 origin = vec3(0, 0, 0);
  vec3 direction = vec3(1, 0, 0);
  float a = <%= a %>;
  float b = <%= b %>;
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = cloud_track(light_direction, origin, direction, a, b, incoming);
}
"))

(def cloud-track-test
  (shader-test
    (fn [program anisotropic n amount]
        (uniform-float program :anisotropic anisotropic)
        (uniform-int program :cloud_min_samples 1)
        (uniform-int program :cloud_max_samples n)
        (uniform-float program :cloud_scatter_amount amount)
        (uniform-float program :cloud_max_step 0.1)
        (uniform-float program :transparency_cutoff 0.0))
    cloud-track-probe
    cloud-track
    linear-sampling))

(tabular "Shader for putting volumetric clouds into the atmosphere"
         (fact (cloud-track-test [?anisotropic ?n ?amnt] [?a ?b ?decay ?scatter ?density ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-3))
         ?a  ?b  ?n ?amnt ?decay  ?scatter ?density ?anisotropic ?lx ?ly ?lz ?ir ?ig ?ib ?or      ?og                    ?ob
         0    1  1  1     0       0        0.0      1            0   0   1   0   0   0   0        0                      0
         0    0  1  1     0       0        0.0      1            0   0   1   1   1   1   1        1                      1
         0    1  1  1     0       0        0.0      1            0   0   1   1   1   1   1        1                      1
         0    1  1  1     1       0        0.0      1            0   0   1   1   0   0   (exp -1) 0                      0
         9   10  1  1     0       1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         8    9  1  1     0       1        0.0      1            0   0   1   0   0   0   0        0                      0.25
         8    9  1  1     (log 2) 1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         8    9  2  1     (log 2) 1        0.0      1            0   0   1   0   0   0   0        0                      0.5
         0    1  1  1     0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -1))         0
         0    1  1  0.5   0       0        1.0      1            0   0   1   0   0   0   0        (* 0.5 (- 1 (exp -1))) 0
         0    2  1  1     0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0    2  2  1     0       0        1.0      1            0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0    1  1  1     0       0        1.0      1            1   0   0   0   0   0   0        (* 0.5 (- 1 (exp -1))) 0
         0    1  1  1     0       0        1.0      0            1   0   0   0   0   0   0        (- 1 (exp -1))         0)

(def cloud-track-base-probe
  (template/fn [a b decay scatter density ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 transmittance_track(vec3 p, vec3 q)
{
  float dp = 10 - p.x;
  float dq = 10 - q.x;
  float transmittance = exp(-<%= decay %> * (dp - dq));
  return vec3(transmittance, transmittance, transmittance);
}
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  float dp = 10 - p.x;
  float amountp = <%= scatter %> * (1 - pow(2, -dp));
  float dq = 10 - q.x;
  float amountq = <%= scatter %> * (1 - pow(2, -dq));
  float amount = amountp - transmittance_track(p, q).r * amountq;
  return vec3(0, 0, amount);
}
float cloud_density(vec3 point, float lod)
{
  return <%= density %>;
}
float phase(float g, float mu)
{
  return 1.0 + 0.5 * mu;
}
vec3 cloud_track_base(vec3 origin, vec3 light_direction, float a, float b, vec3 incoming, float lod);
void main()
{
  vec3 origin = vec3(0, 0, 0);
  vec3 light_direction = vec3(1, 0, 0);
  float a = <%= a %>;
  float b = <%= b %>;
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = cloud_track_base(origin, light_direction, a, b, incoming, 0);
}
"))

(def cloud-track-base-test
  (shader-test
    (fn [program anisotropic n amount]
        (uniform-float program :anisotropic anisotropic)
        (uniform-int program :cloud_base_samples n)
        (uniform-float program :cloud_scatter_amount amount))
    cloud-track-base-probe
    cloud-track-base))

(tabular "Shader for determining shadowing (or lack of shadowing) by clouds"
         (fact (cloud-track-base-test [?anisotropic ?n ?amount] [?a ?b ?decay ?scatter ?density ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-3))
         ?a   ?b  ?n ?amount ?decay  ?scatter ?density ?anisotropic ?ir ?ig ?ib ?or         ?og ?ob
         0    0   1  1       0       0        0.0      1            0   0   0   0           0   0
         0    0   1  1       0       0        0.0      1            1   0   0   1           0   0
         0    1   1  1       0       0        0.0      1            1   0   0   1           0   0
         0    1   1  1       1       0        0.0      1            1   0   0   (exp -1)    0   0
         0    1   2  1       1       0        0.0      1            1   0   0   (exp -1)    0   0
         9   10   1  1       0       1        0.0      1            0   0   0   0           0   0.5
         8    9   1  1       0       1        0.0      1            0   0   0   0           0   0.25
         8    9   1  1       (log 2) 1        0.0      1            0   0   0   0           0   0.5
         0    1   1  1       0       0        1.0      1            1   0   0   (exp -0.5)  0   0
         0    1   2  1       0       0        1.0      1            1   0   0   (exp -0.5)  0   0
         0    1   2  1       0       0        1.0      0            1   0   0   1           0   0
         0    1   1  0.5     0       0        1.0      1            1   0   0   (exp -0.75) 0   0)

(def sky-outer-probe
  (template/fn [x y z dx dy dz lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 sky_outer(vec3 light_direction, vec3 origin, vec3 direction, vec3 incoming);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming)
{
  return vec3((origin.x + a) * 0.01, incoming.g, incoming.b);
}
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return vec3(incoming.r, incoming.g + (b - a) * 0.01, incoming.b);
}
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return vec3(incoming.r, incoming.g, incoming.b + (b - a) * 0.01);
}
void main()
{
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 origin = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  fragColor = sky_outer(light_direction, origin, direction, incoming);
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
  (template/fn [px py pz dx dy dz a b lx ly lz ir ig ib]
"#version 410 core
out lowp vec3 fragColor;
vec3 sky_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return vec3(incoming.r, incoming.g + (b - a) * 0.01, incoming.b);
}
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return vec3(incoming.r, incoming.g, incoming.b + (b - a) * 0.01);
}
void main()
{
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 origin = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 direction = vec3(<%= dx %>, <%= dy %>, <%= dz %>);
  vec3 incoming = vec3(<%= ir %>, <%= ig %>, <%= ib %>);
  float a = <%= a %>;
  float b = <%= b %>;
  fragColor = sky_track(light_direction, origin, direction, a, b, incoming);
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
         (fact (sky-track-test [60 40 ?h1 ?h2] [?px ?py ?pz ?dx ?dy ?dz ?a ?b ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-5))
         ?px ?py ?pz  ?a ?b  ?dx ?dy  ?dz ?h1 ?h2 ?lx ?ly ?lz ?ir ?ig ?ib  ?or ?og ?ob
        -120 0  -110   0  0  1   0    0   20  30  1   0   0   0   0   0    0   0   0
        -120 0  -110   0  0  1   0    0   20  30  1   0   0   0.1 0   0    0.1 0   0
          70 0     0   0 10 -1   0    0   20  30  1   0   0   0.1 0   0    0.1 0   0.1
         110 0     0  10 50 -1   0    0    0   0  1   0   0   0.1 0   0    0.1 0   0.4
          90 0     0  0  30 -1   0    0   20  30  1   0   0   0.1 0   0    0.1 0.1 0.2
         100 0     0  0  40 -1   0    0   20  30  1   0   0   0.1 0   0    0.1 0.1 0.3
         100 0     0  0 200 -1   0    0   20  30  1   0   0   0.1 0   0    0.1 0.2 1.8)

(def cloud-shadow-probe
  (template/fn [x y z lx ly lz]
"#version 410 core
out lowp vec3 fragColor;
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return vec3(incoming.r - abs(b - a) * 0.01, incoming.g, abs(b - a) * 0.01);
}
vec3 cloud_track_base(vec3 origin, vec3 direction, float a, float b, vec3 incoming, float lod)
{
  return vec3(incoming.r, incoming.g - abs(b - a) * 0.01, incoming.b);
}
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = cloud_shadow(point, light_direction, 0);
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
         (fact (cloud-shadow-test [?radius ?h ?h1 ?h2] [?x ?y ?z ?lx ?ly ?lz])
               => (roughly-matrix (matrix [?or ?og ?ob]) 1e-5))
         ?x  ?y ?z ?lx ?ly ?lz ?radius ?h  ?h1 ?h2  ?or  ?og ?ob
         100 0  0  1   0   0   60       40   0   0  1    1   1
          80 0  0  1   0   0   60       40   0   0  0.8  1   0.2
          80 0  0 -1   0   0   60       40   0   0 -0.2  0   0.2
         120 0  0 -1   0   0   60       40   0   0 -0.4  0   0.4
          80 0  0  1   0   0   60       40  20  30  0.9  0.9 0.0
          70 0  0  1   0   0   60       40  20  30  0.8  0.9 0.1
        -100 0  0  1   0   0    0      100  80  90 -0.8  0.8 0.1
        -200 0  0  1   0   0    0      100  80  90 -0.8  0.8 0.1
        -100 0  0  1   0   0   60       40  20  30 -0.3 -0.1 0.1)

(def cloud-density-probe
  (template/fn [x y z]
"#version 410 core
out lowp vec3 fragColor;
float cloud_density(vec3 point, float lod);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_density(point, 0);
  fragColor = vec3(result, 0, 0);
}"))

(defn cloud-density-test [radius cloud-bottom cloud-top density0 density1 cloud-size profile0 profile1 cloud-multiplier x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices      [0 1 3 2]
            vertices     [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program      (make-program :vertex [vertex-passthrough]
                                       :fragment (list (cloud-density-probe x y z) cloud-density))
            vao          (make-vertex-array-object program indices vertices [:point 3])
            worley-data  (cons density0 (repeat (dec (* 2 2 2)) density1))
            worley       (make-float-texture-3d {:width 2 :height 2 :depth 2 :data (float-array worley-data)})
            profile-data (cons profile0 (repeat 9 profile1))
            profile      (make-float-texture-1d (float-array profile-data))
            tex          (texture-render 1 1 true
                                         (use-program program)
                                         (uniform-sampler program :worley 0)
                                         (uniform-sampler program :cloud_profile 1)
                                         (uniform-float program :radius radius)
                                         (uniform-float program :cloud_bottom cloud-bottom)
                                         (uniform-float program :cloud_top cloud-top)
                                         (uniform-float program :cloud_scale cloud-size)
                                         (uniform-float program :cloud_multiplier cloud-multiplier)
                                         (use-textures worley profile)
                                         (render-quads vao))
            img          (texture->vectors3 tex 1 1)]
        (deliver result (get-vector3 img 0 0))
        (destroy-texture tex)
        (destroy-texture profile)
        (destroy-texture worley)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(tabular "Shader for determining cloud density at specified point"
         (fact (mget (cloud-density-test 60 ?h1 ?h2 ?d0 ?d1 ?size ?prof0 ?prof1 ?mult ?x ?y ?z) 0) => (roughly ?result 1e-5))
         ?h1 ?h2 ?d0 ?d1 ?size ?prof0 ?prof1 ?mult ?x    ?y   ?z   ?result
         20  30  0   0   1     1      1      1      0    0    0    0
         20  30  1   1   1     1      1      1     85    0    0    1
         20  30  1   2   1     1      1      1     80.25 0.25 0.25 1
         20  30  1   2   4     1      1      1     81    1    1    1
         20  30  1   1   1     1      1      3     85    0    0    3
         20  30  1   1   1     0      0      1     85    0    0    0
         20  30  1   1   1     0      0      1     85    0    0    0
         20  30  1   1   1     1      0      1     80.5  0    0    1
         20  30  1   1   1     1      0      1     85    0    0    0)

(def sampling-probe
  (template/fn [term]
"#version 410 core
out lowp vec3 fragColor;
int number_of_steps(float a, float b, int min_samples, int max_samples, float max_step);
float step_size(float a, float b, float scaling_offset, int num_steps);
float next_point(float p, float scaling_offset, float step_size);
float scaling_offset(float a, float b, int samples, float max_step);
float initial_lod(float a, float scaling_offset, float step_size);
float lod_increment(float step_size);
void main()
{
  fragColor = vec3(<%= term %>, 0, 0);
}"))

(def linear-sampling-test
  (shader-test
    (fn [program]
        (uniform-float program :cloud_scale 100)
        (uniform-int program :cloud_size 20))
    sampling-probe
    linear-sampling))

(tabular "Shader functions for defining linear sampling"
         (fact (mget (linear-sampling-test [] [?term]) 0) => (roughly ?result 1e-5))
         ?term                              ?result
         "number_of_steps(10, 20, 1, 10, 0.5)" 10
         "number_of_steps(10, 20, 1, 10, 2.0)"  5
         "number_of_steps(10, 20, 1, 10, 2.1)"  5
         "number_of_steps(10, 20, 6, 10, 2.1)"  6
         "step_size(10, 20, 0, 5)"              2
         "next_point(26, 0, 2)"                28
         "scaling_offset(10, 20, 10, 2.0)"      0
         "initial_lod(10, 0, 5)"                0
         "initial_lod(10, 0, 10)"               1
         "lod_increment(10)"                    0)

(def exponential-sampling-test
  (shader-test
    (fn [program]
        (uniform-float program :cloud_scale 100)
        (uniform-int program :cloud_size 20))
    sampling-probe
    exponential-sampling))

(tabular "Shader functions for defining exponential sampling"
         (fact (mget (exponential-sampling-test [] [?term]) 0) => (roughly ?result 1e-5))
         ?term                               ?result
         "number_of_steps(10, 20, 1, 10, 1.05)" 10
         "number_of_steps(10, 20, 1, 10, 2.0)"   1
         "number_of_steps(10, 20, 1, 10, 2.1)"   1
         "number_of_steps(10, 20, 2, 10, 2.1)"   2
         "scaling_offset(10, 20, 1, 2.0)"        0
         "scaling_offset(10, 30, 1, 2.0)"       10
         "scaling_offset(10, 30, 10, 2.0)"       0
         "step_size(10, 20, 0, 1)"               2
         "step_size(10, 40, 0, 2)"               2
         "step_size(10, 30, 10, 1)"              2
         "next_point(10, 0, 2)"                 20
         "next_point(10, 10, 2)"                30
         "initial_lod(10, 0, 1.5)"               0
         "initial_lod(10, 0, 2.0)"               1
         "initial_lod(3, 7, 2.0)"                1
         "lod_increment(1.0)"                    0
         "lod_increment(2.0)"                    1)
