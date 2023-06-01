(ns sfsim25.t-clouds
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-vector shader-test is-image record-image)]
              [comb.template :as template]
              [clojure.math :refer (exp log sin cos asin)]
              [fastmath.vector :refer (vec3)]
              [fastmath.matrix :refer (mat3x3 eye)]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer (get-vector3 get-float get-float-3d slurp-floats)]
              [sfsim25.clouds :refer :all])
    (:import [org.lwjgl.glfw GLFW]))

(GLFW/glfwInit)

(def cloud-track-probe
  (template/fn [a b decay scatter offset density gradient lx ly lz ir ig ib]
"#version 410 core
out vec3 fragColor;
float sampling_offset()
{
  return <%= offset %>;
}
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
  return <%= density %> + <%= gradient %> * point.x;
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
    (fn [program anisotropic step]
        (uniform-float program "anisotropic" anisotropic)
        (uniform-float program "cloud_max_step" step)
        (uniform-float program "lod_offset" 0.0)
        (uniform-float program "transparency_cutoff" 0.0))
    cloud-track-probe
    cloud-track
    linear-sampling))

(tabular "Shader for putting volumetric clouds into the atmosphere"
         (fact (cloud-track-test [?aniso ?s] [?a ?b ?decay ?scatter ?offs ?dens ?grad ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-vector (vec3 ?or ?og ?ob) 1e-3))
         ?a ?b  ?s ?decay  ?scatter ?offs ?dens ?grad ?aniso ?lx ?ly ?lz ?ir ?ig ?ib ?or      ?og                    ?ob
         0   1  1   0       0        0.5   0.0   0     1      0   0   1   0   0   0   0        0                      0
         0   0  1   0       0        0.5   0.0   0     1      0   0   1   1   1   1   1        1                      1
         0   1  1   0       0        0.5   0.0   0     1      0   0   1   1   1   1   1        1                      1
         0   1  1   1       0        0.5   0.0   0     1      0   0   1   1   0   0   (exp -1) 0                      0
         9  10  1   0       1        0.5   0.0   0     1      0   0   1   0   0   0   0        0                      0.5
         8   9  1   0       1        0.5   0.0   0     1      0   0   1   0   0   0   0        0                      0.25
         8   9  1   (log 2) 1        0.5   0.0   0     1      0   0   1   0   0   0   0        0                      0.5
         8   9  0.5 (log 2) 1        0.5   0.0   0     1      0   0   1   0   0   0   0        0                      0.5
         0   1  1   0       0        0.5   1.0   0     1      0   0   1   0   0   0   0        (- 1 (exp -1))         0
         0   2  2   0       0        0.5   1.0   0     1      0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0   2  1   0       0        0.5   1.0   0     1      0   0   1   0   0   0   0        (- 1 (exp -2))         0
         0   1  1   0       0        0.5   1.0   0     1      1   0   0   0   0   0   0        (* 0.5 (- 1 (exp -1))) 0
         0   1  1   0       0        0.5   1.0   0     0      1   0   0   0   0   0   0        (- 1 (exp -1))         0
         0   1  1   0       0        0.5   0.0   2.0   0      0   0   1   0   0   0   0        (- 1 (exp -1))         0)

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
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height)
        (uniform-float program "cloud_bottom" cloud-bottom)
        (uniform-float program "cloud_top" cloud-top))
    sky-outer-probe
    sky-outer
    shaders/ray-sphere
    shaders/ray-shell))

(tabular "Shader for determining lighting of atmosphere including clouds coming from space"
         (fact (sky-outer-test [60 40 ?h1 ?h2] [?x ?y ?z ?dx ?dy ?dz ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-vector (vec3 ?or ?og ?ob) 1e-5))
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
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height)
        (uniform-float program "cloud_bottom" cloud-bottom)
        (uniform-float program "cloud_top" cloud-top))
    sky-track-probe
    sky-track
    shaders/ray-sphere
    shaders/ray-shell
    shaders/clip-shell-intersections))

(tabular "Shader for determining lighting of atmosphere including clouds between two points"
         (fact (sky-track-test [60 40 ?h1 ?h2] [?px ?py ?pz ?dx ?dy ?dz ?a ?b ?lx ?ly ?lz ?ir ?ig ?ib])
               => (roughly-vector (vec3 ?or ?og ?ob) 1e-5))
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
uniform float radius;
uniform float max_height;
vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod);
vec3 transmittance_outer(vec3 point, vec3 direction)
{
  if (point.y == 0)
    return vec3(1, 1, 1) * (point.x - radius) / max_height;
  else
    return vec3(1, 1, 1) * abs(point.x) / (radius + max_height);
}
float opacity_cascade_lookup(vec4 point)
{
  return length(point) < 110 ? 0.5 : 1.0;
}
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = cloud_shadow(point, light_direction, 0);
}"))

(def cloud-shadow-test
  (shader-test
    (fn [program radius max-height]
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height))
    cloud-shadow-probe
    cloud-shadow
    shaders/ray-sphere
    shaders/ray-shell))

(tabular "Shader for determining illumination of clouds"
         (fact (cloud-shadow-test [?radius ?h] [?x ?y ?z ?lx ?ly ?lz])
               => (roughly-vector (vec3 ?or ?og ?ob) 1e-3))
         ?radius ?h  ?x   ?y ?z ?lx ?ly ?lz ?or   ?og   ?ob
         100     20  120   0  0  1   0   0   1     1     1
         100     20 -120   0  0  1   0   0   0     0     0
         100     20 -120 110  0  1   0   0   0.4   0.4   0.4
         100     20  110   0  0  1   0   0   0.5   0.5   0.5
         100     20  105   0  0  1   0   0   0.125 0.125 0.125)

(def cloud-noise-probe
  (template/fn [x y z]
"#version 410 core
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

(defn cloud-noise-test [scale octaves x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            data     (cons 1.0 (repeat (dec (* 2 2 2)) 0.0))
            worley   (make-float-texture-3d :linear :repeat {:width 2 :height 2 :depth 2 :data (float-array data)})
            program  (make-program :vertex [shaders/vertex-passthrough]
                                   :fragment [(cloud-noise-probe x y z)
                                              cloud-noise])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            tex      (texture-render-color 1 1 true
                                           (use-program program)
                                           (uniform-sampler program "worley" 0)
                                           (uniform-float program "detail_scale" scale)
                                           (use-textures worley)
                                           (render-quads vao))
            img      (rgb-texture->vectors3 tex)]
        (deliver result (get-vector3 img 0 0))
        (destroy-texture tex)
        (destroy-texture worley)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(tabular "Shader to sample 3D cloud noise texture"
         (fact ((cloud-noise-test ?scale ?octaves ?x ?y ?z) 0) => (roughly ?result 1e-5))
         ?scale ?octaves ?x   ?y   ?z   ?result
         1.0    [1.0]    0.25 0.25 0.25 1.0
         1.0    [1.0]    0.25 0.75 0.25 0.0
         2.0    [1.0]    0.5  0.5  0.5  1.0)

(def sampling-probe
  (template/fn [term]
"#version 410 core
out vec3 fragColor;
int number_of_samples(float a, float b, float max_step);
float step_size(float a, float b, int num_samples);
float sample_point(float a, float idx, float step_size);
float lod_at_distance(float dist, float lod_offset);
void main()
{
  fragColor = vec3(<%= term %>, 0, 0);
}"))

(def linear-sampling-test
  (shader-test
    (fn [program]
        (uniform-float program "cloud_scale" 100)
        (uniform-int program "cloud_size" 20))
    sampling-probe
    linear-sampling))

(tabular "Shader functions for defining linear sampling"
         (fact ((linear-sampling-test [] [?term]) 0) => (roughly ?result 1e-5))
         ?term                          ?result
         "number_of_samples(10, 20, 5)"  2
         "number_of_samples(10, 20, 3)"  4
         "number_of_samples(10, 10, 5)"  1
         "step_size(10, 20, 2)"          5
         "step_size(10, 20, 4)"          2.5
         "sample_point(20, 0, 2)"       20
         "sample_point(20, 3, 2)"       26
         "sample_point(20, 0.5, 2)"     21
         "lod_at_distance(1, 0)"         0
         "lod_at_distance(1, 3)"         3
         "lod_at_distance(2, 3)"         4)

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
    return cloud_multiplier * pow(0.5, lod);
  else
    return 0.0;
}")

(def sampling-offset-mock
"#version 410 core
uniform float offset;
float sampling_offset()
{
  return offset;
}")

(defn setup-opacity-fragment-static-uniforms
  [program]
  (uniform-int program "shadow_size" 3)
  (uniform-float program "radius" 1000)
  (uniform-float program "cloud_bottom" 100)
  (uniform-float program "cloud_top" 200))

(defn setup-opacity-fragment-dynamic-uniforms [program ndc-to-shadow light-direction shells multiplier scatter
                                               depth offset cloudstep opacitystep start lod]
  (uniform-matrix4 program "ndc_to_shadow" ndc-to-shadow)
  (uniform-vector3 program "light_direction" light-direction)
  (uniform-int program "num_shell_intersections" shells)
  (uniform-float program "cloud_multiplier" multiplier)
  (uniform-float program "scatter_amount" scatter)
  (uniform-float program "depth" depth)
  (uniform-float program "offset" offset)
  (uniform-float program "cloud_max_step" cloudstep)
  (uniform-float program "opacity_step" opacitystep)
  (uniform-float program "density_start" start)
  (uniform-float program "level_of_detail" lod))

(tabular "Compute deep opacity map offsets and layers"
  (fact
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
            ndc-to-shadow   (transformation-matrix (mat3x3 1 1 ?depth) (vec3 0 0 (- ?z ?depth)))
            light-direction (vec3 0 0 1)
            program         (make-program :vertex [opacity-vertex
                                                   shaders/grow-shadow-index]
                                          :fragment [(opacity-fragment 7)
                                                     ray-shell-mock
                                                     cloud-density-mock
                                                     sampling-offset-mock
                                                     linear-sampling])
            vao             (make-vertex-array-object program indices vertices [:point 2])
            opacity-offsets (make-empty-float-texture-2d :linear :clamp 3 3)
            opacity-layers  (make-empty-float-texture-3d :linear :clamp 3 3 7)]
        (framebuffer-render 3 3 :cullback nil [opacity-offsets opacity-layers]
                            (use-program program)
                            (setup-opacity-fragment-static-uniforms program)
                            (setup-opacity-fragment-dynamic-uniforms program ndc-to-shadow light-direction ?shells ?multiplier
                                                                     ?scatter ?depth ?offset ?cloudstep ?opacitystep ?start
                                                                     ?lod)
                            (render-quads vao))
        ({:offset (get-float (float-texture-2d->floats opacity-offsets) 1 ?px)
          :layer (get-float-3d (float-texture-3d->floats opacity-layers) ?layer 1 ?px)} ?selector) => (roughly ?result 1e-6)
        (destroy-texture opacity-layers)
        (destroy-texture opacity-offsets)
        (destroy-vertex-array-object vao)
        (destroy-program program))))
  ?shells ?px ?depth ?cloudstep ?opacitystep ?scatter ?offset ?start ?multiplier ?lod ?z   ?selector ?layer ?result
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1200 :offset   0      1
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1400 :offset   0      (- 1 0.2)
  2       0    1000   50         50          0        0.5      1200  0.02        0.0  1400 :offset   0      (- 1 0.201)
  2       1    1000   50         50          0        0.5      1150  0.02        0.0  1200 :offset   0      (- 1 0.05)
  2       1   10000   50         50          0        0.5     -9999  0.02        0.0  1200 :offset   0      0.0
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1200 :layer    0      1.0
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1200 :layer    1      (exp -1)
  2       1    1000   50         50          0        0.5      1200  0.02        1.0  1200 :layer    1      (exp -0.5)
  2       1    1000   50         50          0.5      0.5      1200  0.02        0.0  1200 :layer    1      (exp -0.5)
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1400 :layer    1      (exp -1)
  2       1    1000   50         25          0        0.5      1200  0.02        0.0  1200 :layer    1      (/ (+ 1 (exp -1)) 2)
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1200 :layer    2      (exp -2)
  2       1    1000   50         50          0        0.5      1200  0.02        0.0  1200 :layer    3      (exp -2)
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :offset   0      (- 1 0.23)
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :layer    0      1.0
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :layer    1      (exp -1)
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :layer    2      (exp -2)
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :layer    3      (exp -2)
  2       1   10000   50         50          0        0.5         0  0.02        0.0  1200 :layer    6      (exp -2)
  2       1   10000   50         50          0        0.5     -9999  0.02        0.0  1200 :layer    6      1.0
  1       1   10000   50         50          0        0.5     -1000  0.02        0.0  1200 :offset   0      (- 1 0.22)
  1       1   10000   50         50          0        0.0     -1000  0.02        0.0  1200 :offset   0      (- 1 0.225))

(def opacity-lookup-probe
  (template/fn [x y z depth]
"#version 410 core
out vec3 fragColor;
uniform sampler2D opacity_offsets;
uniform sampler3D opacity_layers;
float opacity_lookup(sampler2D offsets, sampler3D layers, float depth, vec3 opacity_map_coords);
void main()
{
  vec3 opacity_map_coords = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = opacity_lookup(opacity_offsets, opacity_layers, <%= depth %>, opacity_map_coords);
  fragColor = vec3(result, result, result);
}"))

(defn opacity-lookup-test [offset step depth x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program         (make-program :vertex [shaders/vertex-passthrough]
                                          :fragment [(opacity-lookup-probe x y z depth) opacity-lookup shaders/convert-2d-index
                                                     shaders/convert-3d-index])
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
                                                  (uniform-sampler program "opacity_offsets" 0)
                                                  (uniform-sampler program "opacity_layers" 1)
                                                  (uniform-float program "opacity_step" step)
                                                  (uniform-int program "num_opacity_layers" 7)
                                                  (uniform-int program "shadow_size" 2)
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
"#version 410 core
uniform int select;
float opacity_lookup(sampler2D offsets, sampler3D layers, float depth, vec3 opacity_map_coords)
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

(defn opacity-cascade-lookup-test [n z shift-z opacities offsets select]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices         [0 1 3 2]
            vertices        [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            inv-transform   (transformation-matrix (eye 3) (vec3 0 0 shift-z))
            program         (make-program :vertex [shaders/vertex-passthrough]
                                          :fragment [(opacity-cascade-lookup-probe z) (opacity-cascade-lookup n)
                                                     opacity-lookup-mock])
            vao             (make-vertex-array-object program indices vertices [:point 3])
            opacity-texs    (map #(make-float-texture-3d :linear :clamp {:width 1 :height 1 :depth 1 :data (float-array [%])})
                                 opacities)
            offset-texs     (map #(make-float-texture-2d :linear :clamp {:width 1 :height 1 :data (float-array [%])})
                                 offsets)
            tex             (texture-render-color 1 1 true
                                                  (use-program program)
                                                  (uniform-matrix4 program "inverse_transform" inv-transform)
                                                  (doseq [idx (range n)]
                                                         (uniform-sampler program (str "opacity" idx) (* 2 idx))
                                                         (uniform-sampler program (str "offset" idx) (inc (* 2 idx)))
                                                         (uniform-float program (str "depth" idx) 200.0))
                                                  (doseq [idx (range n)]
                                                         (uniform-matrix4 program (str "shadow_map_matrix" idx)
                                                                          (transformation-matrix (eye 3)
                                                                                                 (vec3 (inc idx) 0 0))))
                                                  (doseq [idx (range (inc n))]
                                                         (uniform-float program (str "split" idx)
                                                                        (+ 10.0 (/ (* 30.0 idx) n))))
                                                  (uniform-int program "select" ({:opacity 0 :coord 1} select))
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
         (fact ((opacity-cascade-lookup-test ?n ?z ?shift-z ?opacities ?offsets ?select) 0) => (roughly ?result 1e-6))
         ?n ?z  ?shift-z ?opacities ?offsets ?select  ?result
         1  -10  0       [0.75]     [0]      :opacity 0.75
         2  -40  0       [0.75 0.5] [0 0]    :opacity 0.5
         2  -50 10       [0.75 0.5] [0 0]    :opacity 0.5
         2  -50  0       [0.75 0.5] [0 0]    :opacity 1.0
         1  -10  0       [1.0]      [0]      :coord   1.0
         2  -10  0       [1.0]      [0]      :coord   1.0
         2  -40  0       [1.0]      [0]      :coord   2.0)

(def cubemap-probe
  (template/fn [x y z]
"#version 410 core
uniform samplerCube cubemap;
out vec3 fragColor;
vec3 convert_cubemap_index(vec3 idx, int size);
void main()
{
  vec3 idx = convert_cubemap_index(vec3(<%= x %>, <%= y %>, <%= z %>), 15);
  fragColor = texture(cubemap, idx).rgb;
}"))

(defn identity-cubemap-test [x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :vertex [shaders/vertex-passthrough]
                                   :fragment [(cubemap-probe x y z) shaders/convert-cubemap-index])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            cubemap  (identity-cubemap 15)
            tex      (texture-render-color 1 1 true
                                           (use-program program)
                                           (uniform-sampler program "cubemap" 0)
                                           (use-textures cubemap)
                                           (render-quads vao))
            img      (rgb-texture->vectors3 tex)]
        (deliver result (get-vector3 img 0 0))
        (destroy-texture tex)
        (destroy-texture cubemap)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

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
"#version 410 core
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

(defn update-cubemap [program current scale x y z]
  (iterate-cubemap 15 scale program
    (uniform-sampler program "current" 0)
    (uniform-float program "x" x)
    (uniform-float program "y" y)
    (uniform-float program "z" z)
    (use-textures current)))

(defn iterate-cubemap-warp-test [n scale px py pz x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :vertex [shaders/vertex-passthrough]
                                   :fragment [(cubemap-probe px py pz) shaders/convert-cubemap-index])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            cubemap  (atom (identity-cubemap 15))
            update   (make-iterate-cubemap-warp-program "current" "curl_field_mock" [curl-field-mock])]
        (dotimes [i n]
          (let [updated (update-cubemap update @cubemap scale x y z)]
            (destroy-texture @cubemap)
            (reset! cubemap updated)))
        (let [tex (texture-render-color 1 1 true
                                        (use-program program)
                                        (uniform-sampler program "cubemap" 0)
                                        (use-textures @cubemap)
                                        (render-quads vao))
              img (rgb-texture->vectors3 tex)]
          (deliver result (get-vector3 img 0 0))
          (destroy-texture tex)
          (destroy-texture @cubemap)
          (destroy-program update)
          (destroy-vertex-array-object vao)
          (destroy-program program))))
    @result))

(tabular "Update normalised cubemap warp vectors using specified vectors"
         (fact (iterate-cubemap-warp-test ?n ?scale ?px ?py ?pz ?x ?y ?z) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-3))
         ?n ?scale ?px ?py ?pz ?x ?y ?z ?rx   ?ry   ?rz
         0  1      1   0   0   0  0  0  1     0     0
         0  1     -1   0   0   0  0  0 -1     0     0
         0  1      0   1   0   0  0  0  0     1     0
         0  1      0  -1   0   0  0  0  0    -1     0
         0  1      0   0   1   0  0  0  0     0     1
         0  1      0   0  -1   0  0  0  0     0    -1
         1  1      1   0   0   2  0  0  3     0     0
         1  1      1   0   0   0  1  0  1     1     0
         1  1     -1   0   0   0  1  0 -1     0     0
         1  0.5    1   0   0   2  0  0  2     0     0
         2  1      1   0   0   0  1  0  0.707 1.707 0)

(def lookup-mock
"#version 410 core
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

(defn cubemap-warp-test [px py pz selector]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :vertex [shaders/vertex-passthrough]
                                   :fragment [(cubemap-probe px py pz) shaders/convert-cubemap-index])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            vectors  [[1 0 0] [0 2 0] [0 0 4] [-1 0 0] [0 -1 0] [0 0 -1]]
            to-data  (fn [v] (float-array (flatten (repeat 9 v))))
            current  (make-vector-cubemap :linear :clamp (mapv (fn [v] {:width 3 :height 3 :data (to-data v)}) vectors))
            warp     (make-cubemap-warp-program "current" "lookup_mock" [lookup-mock])
            warped   (cubemap-warp 3 warp
                                   (uniform-sampler warp "current" 0)
                                   (uniform-int warp "selector" selector)
                                   (use-textures current))]
        (let [tex (texture-render-color 1 1 true
                                        (use-program program)
                                        (uniform-sampler program "cubemap" 0)
                                        (use-textures warped)
                                        (render-quads vao))
              img (rgb-texture->vectors3 tex)]
          (deliver result ((get-vector3 img 0 0) 0))
          (destroy-texture tex)
          (destroy-texture warped)
          (destroy-texture current)
          (destroy-program warp)
          (destroy-vertex-array-object vao)
          (destroy-program program))))
    @result))

(tabular "Lookup floating-point values using cubemap warp vector field"
         (fact (cubemap-warp-test ?px ?py ?pz ?selector) => (roughly ?result 1e-3))
         ?px ?py ?pz ?selector ?result
         1   0   0   0         1
         1   0   0   1         0
         1   0   0   2         0
        -1   0   0   1         1
         0   1   0   2         1
         0  -1   0   0        -1
         0   0   1   1        -1
         0   0  -1   2        -1)

(def noise-mock
"#version 410 core
uniform float dx;
uniform float dy;
uniform float dz;
float noise_mock(vec3 point)
{
  return dot(point, vec3(dx, dy, dz));
}")

(def curl-probe
  (template/fn [x y z]
"#version 410 core
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
    shaders/rotate-vector
    shaders/oriented-matrix
    shaders/orthogonal-vector
    (shaders/gradient-3d "gradient" "noise_mock" "epsilon")
    shaders/project-vector
    noise-mock))

(tabular "Shader for computing curl vectors from noise function"
         (fact (curl-test [0.125 ?dx ?dy ?dz] [?x ?y ?z]) => (roughly-vector (vec3 ?rx ?ry ?rz) 1e-3))
         ?dx ?dy ?dz ?x ?y ?z ?rx ?ry ?rz
         0   0   0   1  0  0  0   0   0
         0   0.1 0   1  0  0  0   0   0.1
         0   0   0.1 1  0  0  0  -0.1 0
         0.2 0.1 0   1  0  0  0   0   0.1)

(def flow-field-probe
  (template/fn [north south x y z]
"#version 410 core
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
    flow-field))

(tabular "Shader to create potential field for generating curl noise for global cloud cover"
         (fact ((flow-field-test [?curl-scale ?prevailing ?whirl] [?north ?south ?x ?y ?z]) 0) => (roughly ?result 1e-5))
         ?curl-scale ?prevailing ?whirl ?north ?south ?x                     ?y                     ?z ?result
         1           0           0      0.0    0.0    1                      0                      0  0
         1           1           0      0.0    0.0    0                     -1                      0  1
         1           1           0      0.0    0.0    1                      0                      0  0
         1           1           0      0.0    0.0    (cos (/ (asin 0.5) 3)) (sin (/ (asin 0.5) 3)) 0  0.5
         1           0           1      1.0    0.3    0                     -1                      0  1
         1           0           1      0.3    1.0    0                      1                      0 -1
         1           0           0.5    1.0    0.0    0                     -1                      0  0.5
         1           0           0.5    0.0    1.0    0                      1                      0 -0.5
         1           0           1      0.0    0.0    0                      0                      1  0.25
         2           0           1      0.0    0.0    0                      0                      1  0.125)

(def cover-vertex
"#version 410 core
in vec3 point;
out VS_OUT
{
  vec3 point;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")

(def cover-fragment
"#version 410 core
uniform samplerCube cubemap;
uniform float threshold;
uniform float multiplier;
in VS_OUT
{
  vec3 point;
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
            worley-north (make-float-texture-3d :linear :repeat
                                                {:width worley-size :height worley-size :depth worley-size
                                                 :data (slurp-floats "test/sfsim25/fixtures/clouds/worley-north.raw")})
            worley-south (make-float-texture-3d :linear :repeat
                                                {:width worley-size :height worley-size :depth worley-size
                                                 :data (slurp-floats "test/sfsim25/fixtures/clouds/worley-south.raw")})
            worley-cover (make-float-texture-3d :linear :repeat
                                                {:width worley-size :height worley-size :depth worley-size
                                                 :data (slurp-floats "test/sfsim25/fixtures/clouds/worley-cover.raw")})
            program      (make-program :vertex [cover-vertex] :fragment [cover-fragment shaders/ray-sphere])
            indices      [0 1 3 2]
            vertices     [-1 -1 0, 1 -1 0, -1 1 0, 1 1 0]
            vao          (make-vertex-array-object program indices vertices [:point 3])
            cubemap      (cloud-cover-cubemap :size 256
                                              :worley-size worley-size
                                              :worley-south worley-south
                                              :worley-north worley-north
                                              :worley-cover worley-cover
                                              :flow-octaves [0.5 0.25 0.125]
                                              :cloud-octaves [0.25 0.25 0.125 0.125 0.0625 0.0625]
                                              :whirl 2.0
                                              :prevailing 0.1
                                              :curl-scale 1.0
                                              :cover-scale 2.0
                                              :num-iterations 50
                                              :flow-scale 1.5e-3)]
        (let [tex (texture-render-color 128 128 false
                                        (clear (vec3 1 0 0))
                                        (use-program program)
                                        (uniform-sampler program "cubemap" 0)
                                        (uniform-float program "threshold" 0.3)
                                        (uniform-float program "multiplier" 4.0)
                                        (use-textures cubemap)
                                        (render-quads vao)
                                        )
              img (texture->image tex)]
          (destroy-texture cubemap)
          (destroy-vertex-array-object vao)
          (destroy-program program)
          (destroy-texture worley-cover)
          (destroy-texture worley-south)
          (destroy-texture worley-north)
          (destroy-texture tex)
          img)))
    => (is-image "test/sfsim25/fixtures/clouds/cover.png" 0.0))

(def cloud-profile-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
float cloud_profile(vec3 point);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  float result = cloud_profile(point);
  fragColor = vec3(result, 0, 0);
}"))

(defn cloud-profile-test [radius cloud-bottom cloud-top x y z]
  (let [result (promise)]
    (offscreen-render 1 1
      (let [indices  [0 1 3 2]
            vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
            program  (make-program :vertex [shaders/vertex-passthrough]
                                   :fragment [(cloud-profile-probe x y z) cloud-profile shaders/convert-1d-index])
            vao      (make-vertex-array-object program indices vertices [:point 3])
            data     [0.0 2.0 1.0]
            profile  (make-float-texture-1d :linear :clamp (float-array data))
            tex      (texture-render-color 1 1 true
                                           (use-program program)
                                           (uniform-sampler program "profile" 0)
                                           (uniform-int program "profile_size" 3)
                                           (uniform-float program "radius" radius)
                                           (uniform-float program "cloud_bottom" cloud-bottom)
                                           (uniform-float program "cloud_top" cloud-top)
                                           (use-textures profile)
                                           (render-quads vao))
            img     (rgb-texture->vectors3 tex)]
        (deliver result (get-vector3 img 0 0))
        (destroy-texture tex)
        (destroy-texture profile)
        (destroy-vertex-array-object vao)
        (destroy-program program)))
    @result))

(tabular "Shader for creating vertical cloud profile"
         (fact ((cloud-profile-test ?radius ?bottom ?top ?x ?y ?z) 0) => (roughly ?result 1e-5))
         ?radius ?bottom ?top ?x  ?y  ?z ?result
         100     10      14   110 0   0  0.0
         100     10      14   112 0   0  2.0
         100     10      14   0   112 0  2.0
         100     10      14   111 0   0  1.0)

(def sphere-noise-probe
  (template/fn [x y z]
"#version 410 core
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
"#version 410 core
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
    cloud-base))

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
          1.0     1.0     1.5        950  1  0 -0.1)

(def cloud-cover-probe
  (template/fn [x y z]
"#version 410 core
out vec3 fragColor;
float cloud_cover(vec3 idx);
void main()
{
  float result = cloud_cover(vec3(<%= x %>, <%= y %>, <%= z %>));
  fragColor = vec3(result, 0, 0);
}"))

(defn cloud-cover-test [x y z]
  (let [result (promise)]
    (offscreen-render 1 1
                      (let [indices     [0 1 3 2]
                            vertices    [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                            datas       [[0 1 0 1] [2 2 2 2] [3 3 3 3] [4 4 4 4] [5 5 5 5] [6 6 6 6]]
                            data->image (fn [data] {:width 2 :height 2 :data (float-array data)})
                            cube        (make-float-cubemap :linear :clamp (mapv data->image datas))
                            program     (make-program :vertex [shaders/vertex-passthrough]
                                                      :fragment [(cloud-cover-probe x y z) cloud-cover
                                                                 shaders/interpolate-float-cubemap
                                                                 shaders/convert-cubemap-index])
                            vao         (make-vertex-array-object program indices vertices [:point 3])
                            tex         (texture-render-color
                                          1 1 true
                                          (use-program program)
                                          (uniform-sampler program "cover" 0)
                                          (uniform-int program "cover_size" 2)
                                          (use-textures cube)
                                          (render-quads vao))
                            img         (rgb-texture->vectors3 tex)]
                        (deliver result (get-vector3 img 0 0))
                        (destroy-texture tex)
                        (destroy-texture cube)
                        (destroy-vertex-array-object vao)
                        (destroy-program program)))
    @result))

(tabular "Perform cloud cover lookup in cube map"
         (fact ((cloud-cover-test ?x ?y ?z) 0) => ?result)
         ?x   ?y ?z  ?result
         1    0   0   0.5
         1    0  -1   1.0
         1    0   0.5 0.25)

(def cloud-density-probe
  (template/fn [lod x y z]
"#version 410 core
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
    cloud-density
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

(GLFW/glfwTerminate)
