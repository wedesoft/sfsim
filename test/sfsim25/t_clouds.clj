(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix shader-test vertex-passthrough)]
              [comb.template :as template]
              [clojure.math :refer (exp log)]
              [clojure.core.matrix :refer (mget matrix identity-matrix diagonal-matrix)]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :refer :all]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer (get-vector3 get-float get-float-3d)]
              [sfsim25.clouds :refer :all]))

(def cloud-track-probe
  (template/fn [a b decay scatter density lx ly lz ir ig ib]
"#version 410 core
out vec3 fragColor;
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
out vec3 fragColor;
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
out vec3 fragColor;
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
out vec3 fragColor;
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
out vec3 fragColor;
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
out vec3 fragColor;
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
                                       :fragment (vector (cloud-density-probe x y z) cloud-density))
            vao          (make-vertex-array-object program indices vertices [:point 3])
            worley-data  (cons density0 (repeat (dec (* 2 2 2)) density1))
            worley       (make-float-texture-3d :linear :repeat {:width 2 :height 2 :depth 2 :data (float-array worley-data)})
            profile-data (cons profile0 (repeat 9 profile1))
            profile      (make-float-texture-1d :linear :clamp (float-array profile-data))
            tex          (texture-render-color
                           1 1 true
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
            img          (rgb-texture->vectors3 tex)]
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
out vec3 fragColor;
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

(def ray-shell-mock
"#version 410 core
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
"#version 410 core
uniform float radius;
uniform float density_start;
uniform float cloud_multiplier;
float cloud_density(vec3 point, float lod)
{
  if (point.z <= density_start)
    return cloud_multiplier;
  else
    return 0.0;
}")

(defn setup-opacity-fragment-static-uniforms
  [program]
  (uniform-int program :shadow_size 3)
  (uniform-float program :radius 1000)
  (uniform-float program :cloud_bottom 100)
  (uniform-float program :cloud_top 200))

(tabular "Compute deep opacity map offsets and layers"
  (fact
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
            ndc-to-shadow   (transformation-matrix (diagonal-matrix [1 1 ?depth]) (matrix [0 0 (- ?z ?depth)]))
            light-direction (matrix [0 0 1])
            program         (make-program :vertex [opacity-vertex grow-shadow-index]
                                          :fragment [(opacity-fragment 7) ray-shell-mock cloud-density-mock])
            vao             (make-vertex-array-object program indices vertices [:point 2])
            opacity-offsets (make-empty-float-texture-2d :linear :clamp 3 3)
            opacity-layers  (make-empty-float-texture-3d :linear :clamp 3 3 7)]
        (framebuffer-render 3 3 :cullback nil [opacity-offsets opacity-layers]
                            (use-program program)
                            (setup-opacity-fragment-static-uniforms program)
                            (uniform-matrix4 program :ndc_to_shadow ndc-to-shadow)
                            (uniform-vector3 program :light_direction light-direction)
                            (uniform-int program :num_shell_intersections ?shells)
                            (uniform-float program :cloud_multiplier ?multiplier)
                            (uniform-float program :scatter_amount ?scatter-amount)
                            (uniform-float program :depth ?depth)
                            (uniform-float program :cloud_max_step ?cloudstep)
                            (uniform-float program :opacity_step ?opacitystep)
                            (uniform-float program :density_start ?start)
                            (render-quads vao))
        ({:offset (get-float (float-texture-2d->floats opacity-offsets) 1 ?px)
          :layer (get-float-3d (float-texture-3d->floats opacity-layers) ?layer 1 ?px)} ?selector) => (roughly ?result 1e-6)
        (destroy-texture opacity-layers)
        (destroy-texture opacity-offsets)
        (destroy-vertex-array-object vao)
        (destroy-program program))))
  ?shells ?px ?depth ?cloudstep ?opacitystep ?scatter-amount ?start ?multiplier ?z   ?selector ?layer ?result
  2       1    1000   50         50          0                1200  0.02        1200 :offset   0      1
  2       1    1000   50         50          0                1200  0.02        1400 :offset   0      (- 1 0.2)
  2       0    1000   50         50          0                1200  0.02        1400 :offset   0      (- 1 0.201)
  2       1    1000   50         50          0                1150  0.02        1200 :offset   0      (- 1 0.05)
  2       1   10000   50         50          0               -9999  0.02        1200 :offset   0      0.0
  2       1    1000   50         50          0                1200  0.02        1200 :layer    0      1.0
  2       1    1000   50         50          0                1200  0.02        1200 :layer    1      (exp -1)
  2       1    1000   50         50          0.5              1200  0.02        1200 :layer    1      (exp -0.5)
  2       1    1000   50         50          0                1200  0.02        1400 :layer    1      (exp -1)
  2       1    1000   50         25          0                1200  0.02        1200 :layer    1      (/ (+ 1 (exp -1)) 2)
  2       1    1000   50         50          0                1200  0.02        1200 :layer    2      (exp -2)
  2       1    1000   50         50          0                1200  0.02        1200 :layer    3      (exp -2)
  2       1   10000   50         50          0                   0  0.02        1200 :offset   0      (- 1 0.23)
  2       1   10000   50         50          0                   0  0.02        1200 :layer    0      1.0
  2       1   10000   50         50          0                   0  0.02        1200 :layer    1      (exp -1)
  2       1   10000   50         50          0                   0  0.02        1200 :layer    2      (exp -2)
  2       1   10000   50         50          0                   0  0.02        1200 :layer    3      (exp -2)
  2       1   10000   50         50          0                   0  0.02        1200 :layer    6      (exp -2)
  2       1   10000   50         50          0               -9999  0.02        1200 :layer    6      1.0
  1       1   10000   50         50          0               -1000  0.02        1200 :offset   0      (- 1 0.22))

(def opacity-lookup-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
uniform sampler2D opacity_offsets;
uniform sampler3D opacity_layers;
float opacity_lookup(sampler2D offsets, sampler3D layers, vec3 opacity_map_coords);
void main()
{
  vec3 opacity_map_coords = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = opacity_lookup(opacity_offsets, opacity_layers, opacity_map_coords);
  fragColor = vec3(result, result, result);
}"))

(defn opacity-lookup-test [offset step x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program         (make-program :vertex [vertex-passthrough]
                                          :fragment [(opacity-lookup-probe x y z) opacity-lookup convert-2d-index
                                                     convert-3d-index])
            vao             (make-vertex-array-object program indices vertices [:point 3])
            zeropad         (fn [x] [0 x 0 0])
            opacity-data    (flatten (map (partial repeat 4) [1.0 0.9 0.8 0.7 0.6 0.5 0.4]))
            opacity-layers  (make-float-texture-3d :linear :clamp {:width 2 :height 2 :depth 7
                                                                   :data (float-array opacity-data)})
            offset-data     (zeropad offset)
            opacity-offsets (make-float-texture-2d :linear :clamp {:width 2 :height 2
                                                                   :data (float-array offset-data)})
            tex             (texture-render-color 1 1 true
                                                  (use-program program)
                                                  (uniform-sampler program :opacity_offsets 0)
                                                  (uniform-sampler program :opacity_layers 1)
                                                  (uniform-float program :opacity_step step)
                                                  (uniform-int program :opacity_size_z 7)
                                                  (uniform-int program :opacity_size_y 2)
                                                  (uniform-int program :opacity_size_x 2)
                                                  (use-textures opacity-offsets opacity-layers)
                                                  (render-quads vao))
            img             (rgb-texture->vectors3 tex)]
        (deliver result (get-vector3 img 0 0))
        (destroy-texture opacity-offsets)
        (destroy-texture opacity-layers)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(tabular "Lookup values from deep opacity map taking into account offsets"
  (fact (mget (opacity-lookup-test ?offset ?step ?x ?y ?z) 0) => (roughly ?result 1e-6))
  ?offset ?step      ?x    ?y ?z        ?result
  1.0     (/ 1.0 6)  1     0  1         1.0
  1.0     (/ 1.0 6)  1     0  0         0.4
  0.0     (/ 1.0 6)  1     0  0         1.0
  1.0     (/ 1.0 12) 1     0  0.5       0.4
  1.0     (/ 1.0 6)  1     0  (/ 5.0 6) 0.9
  1.0     (/ 1.0 6)  0.75  0  0.5       0.85)

(def opacity-lookup-mock
"#version 410 core
uniform int select;
float opacity_lookup(sampler2D offsets, sampler3D layers, vec3 opacity_map_coords)
{
  if (select == 0)
    return texture(layers, vec3(0.5, 0.5, 0.5)).r;
  else
    return opacity_map_coords.x;
}")

(def opacity-cascade-lookup-probe
  (template/fn [z]
"#version 410 core
out vec3 fragColor;
float opacity_cascade_lookup(vec4 point);
void main()
{
  vec4 point = vec4(0, 0, <%= z %>, 1);
  float result = opacity_cascade_lookup(point);
  fragColor = vec3(result, 0, 0);
}"))

(defn opacity-cascade-lookup-test [n z opacities offsets select]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program         (make-program :vertex [vertex-passthrough]
                                          :fragment [(opacity-cascade-lookup-probe z) (opacity-cascade-lookup n)
                                                     opacity-lookup-mock])
            vao             (make-vertex-array-object program indices vertices [:point 3])
            opacity-texs    (map #(make-float-texture-3d :linear :clamp {:width 1 :height 1 :depth 1 :data (float-array [%])})
                                 opacities)
            offset-texs     (map #(make-float-texture-2d :linear :clamp {:width 1 :height 1 :data (float-array [%])})
                                 offsets)
            tex             (texture-render-color 1 1 true
                                                  (use-program program)
                                                  (doseq [idx (range n)]
                                                         (uniform-sampler program (keyword (str "opacity" idx)) (* 2 idx))
                                                         (uniform-sampler program (keyword (str "offset" idx)) (inc (* 2 idx))))
                                                  (doseq [idx (range n)]
                                                         (uniform-matrix4 program (keyword (str "shadow_map_matrix" idx))
                                                                          (transformation-matrix (identity-matrix 3)
                                                                                                 (matrix [(inc idx) 0 0]))))
                                                  (doseq [idx (range (inc n))]
                                                         (uniform-float program (keyword (str "split" idx))
                                                                        (+ 10.0 (/ (* 30.0 idx) n))))
                                                  (uniform-int program :select ({:opacity 0 :coord 1} select))
                                                  (apply use-textures (interleave opacity-texs offset-texs))
                                                  (render-quads vao))
            img             (rgb-texture->vectors3 tex)]
        (deliver result (get-vector3 img 0 0))
        (doseq [tex offset-texs] (destroy-texture tex))
        (doseq [tex opacity-texs] (destroy-texture tex))
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(tabular "Perform opacity (transparency) lookup in cascade of deep opacity maps"
         (fact (mget (opacity-cascade-lookup-test ?n ?z ?opacities ?offsets ?select) 0) => (roughly ?result 1e-6))
         ?n ?z ?opacities ?offsets ?select  ?result
         1  10 [0.75]     [0]      :opacity 0.75
         2  40 [0.75 0.5] [0 0]    :opacity 0.5
         2  50 [0.75 0.5] [0 0]    :opacity 1.0
         1  10 [1.0]      [0]      :coord   1.0
         2  10 [1.0]      [0]      :coord   1.0
         2  40 [1.0]      [0]      :coord   2.0)
