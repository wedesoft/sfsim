(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (shader-test roughly-vector is-image record-image)]
              [comb.template :as template]
              [clojure.math :refer (PI exp pow)]
              [fastmath.vector :refer (vec3 mult dot mag)]
              [fastmath.matrix :refer (eye inverse)]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.atmosphere :as atmosphere]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.matrix :refer :all]
              [sfsim25.interpolate :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.planet :refer :all]
              [sfsim25.bluenoise :as bluenoise]
              [sfsim25.clouds :as clouds])
    (:import [org.lwjgl.glfw GLFW]
             [fastmath.matrix Mat4x4]))

(GLFW/glfwInit)

(def radius 6378000.0)
(def max-height 100000.0)
(def ray-steps 10)
(def size 12)
(def earth #:sfsim25.sphere{:centre (vec3 0 0 0)
                            :radius radius
                            :sfsim25.atmosphere/height max-height
                            :sfsim25.atmosphere/brightness (vec3 0.3 0.3 0.3)})

(facts "Create vertex array object for drawing cube map tiles"
       (let [a (vec3 -0.75 -0.5  -1.0)
             b (vec3 -0.5  -0.5  -1.0)
             c (vec3 -0.75 -0.25 -1.0)
             d (vec3 -0.5  -0.25 -1.0)]
         (with-redefs [cubemap/cube-map-corners (fn [^long face ^long level ^long y ^long x]
                                                    (fact [face level y x] => [5 3 2 1])
                                                    [a b c d])]
           (let [arr (make-cube-map-tile-vertices 5 3 2 1 10 100)]
             (subvec arr  0  3) => (vec a)
             (subvec arr  7 10) => (vec b)
             (subvec arr 14 17) => (vec c)
             (subvec arr 21 24) => (vec d)
             (subvec arr  3  5) => [0.05 0.05]
             (subvec arr 10 12) => [0.95 0.05]
             (subvec arr 17 19) => [0.05 0.95]
             (subvec arr 24 26) => [0.95 0.95]
             (subvec arr  5  7) => [0.005 0.005]
             (subvec arr 12 14) => [0.995 0.005]
             (subvec arr 19 21) => [0.005 0.995]
             (subvec arr 26 28) => [0.995 0.995]))))

(def fragment-white "#version 410 core
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")

(fact "Use vertex data to draw a quad"
      (offscreen-render 256 256
                        (let [indices   [0 1 3 2]
                              vertices  [-0.5 -0.5 0.5 0 0 0 0
                                          0.5 -0.5 0.5 0 0 0 0
                                         -0.5  0.5 0.5 0 0 0 0
                                          0.5  0.5 0.5 0 0 0 0]
                              program   (make-program :vertex [vertex-planet]
                                                      :fragment [fragment-white])
                              variables ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao       (make-vertex-array-object program indices vertices variables)]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (raster-lines (render-quads vao))
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet/quad.png" 2.0))

(tabular "Tessellation control shader to control outer tessellation of quad using a uniform integer"
         (fact
           (offscreen-render 256 256
                             (let [indices     [0 1 3 2]
                                   vertices    [-0.5 -0.5 0.5 0.25 0.25 0 0,
                                                 0.5 -0.5 0.5 0.75 0.25 0 0,
                                                -0.5  0.5 0.5 0.25 0.75 0 0,
                                                 0.5  0.5 0.5 0.75 0.75 0 0]
                                   program     (make-program :vertex [vertex-planet]
                                                             :tess-control [tess-control-planet]
                                                             :tess-evaluation [tess-evaluation-planet]
                                                             :geometry [geometry-planet]
                                                             :fragment [fragment-white])
                                   variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   data        [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5  0.5 0.5, 0.5  0.5 0.5]
                                   surface     (make-vector-texture-2d :linear :clamp
                                                                       {:width 2 :height 2 :data (float-array data)})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" ?neighbours)
                               (uniform-matrix4 program "transform" (eye 4))
                               (uniform-matrix4 program "projection" (eye 4))
                               (uniform-float program "z_near" 0.0)
                               (use-textures {0 surface})
                               (raster-lines (render-patches vao))
                               (destroy-texture surface)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result 5.0))
         ?neighbours ?result
         15          "test/sfsim25/fixtures/planet/tessellation.png"
          1          "test/sfsim25/fixtures/planet/tessellation-0.png"
          2          "test/sfsim25/fixtures/planet/tessellation-1.png"
          4          "test/sfsim25/fixtures/planet/tessellation-2.png"
          8          "test/sfsim25/fixtures/planet/tessellation-3.png")

(def texture-coordinates-probe
  (template/fn [selector] "#version 410 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} frag_in;
out vec3 fragColor;
void main()
{
  fragColor.rg = <%= selector %>;
  fragColor.b = 0;
}"))

(tabular "Test color texture coordinates"
         (fact
           (offscreen-render 256 256
                             (let [indices     [0 1 3 2]
                                   vertices    [-0.5 -0.5 0.5 0.25 0.25 0.25 0.25,
                                                 0.5 -0.5 0.5 0.75 0.25 0.75 0.25,
                                                -0.5  0.5 0.5 0.25 0.75 0.25 0.75,
                                                 0.5  0.5 0.5 0.75 0.75 0.75 0.75]
                                   program     (make-program :vertex [vertex-planet]
                                                             :tess-control [tess-control-planet]
                                                             :tess-evaluation [tess-evaluation-planet]
                                                             :geometry [geometry-planet]
                                                             :fragment [(texture-coordinates-probe ?selector)])
                                   variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   data        (map #(* % ?scale) [-0.5 -0.5 0.5, 0.5 -0.5 0.5, -0.5  0.5 0.5, 0.5  0.5 0.5])
                                   surface     (make-vector-texture-2d :linear :clamp
                                                                       {:width 2 :height 2 :data (float-array data)})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" 15)
                               (uniform-matrix4 program "recenter_and_transform" (eye 4))
                               (uniform-vector3 program "tile_center" (vec3 0 0 0))
                               (uniform-matrix4 program "projection" (eye 4))
                               (uniform-float program "z_near" 0.5)
                               (use-textures {0 surface})
                               (render-patches vao)
                               (destroy-texture surface)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result 0.02))
         ?selector                            ?scale ?result
         "frag_in.colorcoord"                 1.0    "test/sfsim25/fixtures/planet/color-coords.png"
         "frag_in.point.xy + vec2(0.5, 0.5)"  1.0    "test/sfsim25/fixtures/planet/point.png"
         "frag_in.point.xy + vec2(0.5, 0.5)"  1.1    "test/sfsim25/fixtures/planet/scaled-point.png")

(fact "Apply transformation to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.6 -0.5 0.5 0.25 0.25 0 0
                                            0.4 -0.5 0.5 0.75 0.25 0 0
                                           -0.6  0.5 0.5 0.25 0.75 0 0
                                            0.4  0.5 0.5 0.75 0.75 0 0]
                              program     (make-program :vertex [vertex-planet]
                                                        :tess-control [tess-control-planet]
                                                        :tess-evaluation [tess-evaluation-planet]
                                                        :geometry [geometry-planet]
                                                        :fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              data        [-0.6 -0.5 0.5, 0.4 -0.5 0.5, -0.6  0.5 0.5, 0.4  0.5 0.5]
                              vao         (make-vertex-array-object program indices vertices variables)
                              surface     (make-vector-texture-2d :linear :clamp
                                                                  {:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "transform" (transformation-matrix (eye 3) (vec3 0.1 0 0)))
                          (uniform-matrix4 program "projection" (eye 4))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/sfsim25/fixtures/planet/tessellation.png" 5.0))

(fact "Apply projection matrix to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.5 -0.5 0 0.25 0.25 0 0
                                            0.5 -0.5 0 0.75 0.25 0 0
                                           -0.5  0.5 0 0.25 0.75 0 0
                                            0.5  0.5 0 0.75 0.75 0 0]
                              program     (make-program :vertex [vertex-planet]
                                                        :tess-control [tess-control-planet]
                                                        :tess-evaluation [tess-evaluation-planet]
                                                        :geometry [geometry-planet]
                                                        :fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              data        [-0.5 -0.5 0, 0.5 -0.5 0, -0.5  0.5 0, 0.5  0.5 0]
                              surface     (make-vector-texture-2d :linear :clamp
                                                                  {:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-float program "z_near" 0.0)
                          (uniform-matrix4 program "transform" (transformation-matrix (eye 3) (vec3 0 0 -2)))
                          (uniform-matrix4 program "projection" (projection-matrix 256 256 1 3 (/ PI 3)))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/sfsim25/fixtures/planet/projection.png" 0.9))

(fact "Scale vertex coordinates using given height field"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.5 -0.5 0.5 0.25 0.25 0 0
                                            0.5 -0.5 0.5 0.75 0.25 0 0
                                           -0.5  0.5 0.5 0.25 0.75 0 0
                                            0.5  0.5 0.5 0.75 0.75 0 0]
                              program     (make-program :vertex [vertex-planet]
                                                        :tess-control [tess-control-planet]
                                                        :tess-evaluation [tess-evaluation-planet]
                                                        :geometry [geometry-planet]
                                                        :fragment [fragment-white])
                              variables   ["point" 3 "surfacecoord" 2 "colorcoord" 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              data        [-0.25 -0.25 0.25, 0.5 -0.5 0.5, -0.75 0.75 0.75, 1.0 1.0 1.0]
                              surface     (make-vector-texture-2d :linear :clamp
                                                                  {:width 2 :height 2 :data (float-array data)})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "surface" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "transform" (eye 4))
                          (uniform-matrix4 program "projection" (eye 4))
                          (uniform-float program "z_near" 0.0)
                          (use-textures {0 surface})
                          (raster-lines (render-patches vao))
                          (destroy-texture surface)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/sfsim25/fixtures/planet/heightfield.png" 1.6))

(defn ray-scatter [x view-direction light-direction above-horizon]
  (let [value (* (pow (max (dot view-direction light-direction) 0.0) 10) (exp (/ (- radius (mag x)) 5500.0)))]
    (vec3 value value value)))

(def surface-radiance-earth (partial atmosphere/surface-radiance earth ray-scatter ray-steps))
(def surface-radiance-space-earth (atmosphere/surface-radiance-space earth [size size]))
(def S (pack-matrices (make-lookup-table surface-radiance-earth surface-radiance-space-earth)))

(defn surface-radiance-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (with-invisible-window
        (let [indices          [0 1 3 2]
              vertices         [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
              surface-radiance (make-vector-texture-2d :linear :clamp {:width size :height size :data S})
              program          (make-program :vertex [shaders/vertex-passthrough] :fragment (conj shaders (apply probe args)))
              vao              (make-vertex-array-object program indices vertices ["point" 3])
              tex              (texture-render-color
                                 1 1 true
                                 (use-program program)
                                 (uniform-sampler program "surface_radiance" 0)
                                 (apply setup program uniforms)
                                 (use-textures {0 surface-radiance})
                                 (render-quads vao))
              img           (rgb-texture->vectors3 tex)]
          (destroy-texture tex)
          (destroy-texture surface-radiance)
          (destroy-vertex-array-object vao)
          (destroy-program program)
          (get-vector3 img 0 0)))))

(def surface-radiance-probe
  (template/fn [px py pz lx ly lz] "#version 410 core
out vec3 fragColor;
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
void main()
{
  vec3 point = vec3(<%= px %>, <%= py %>, <%= pz %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  fragColor = surface_radiance_function(point, light_direction);
}"))

(def surface-radiance-test
  (surface-radiance-shader-test
    (fn [program radius max-height size]
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height)
        (uniform-int program "surface_sun_elevation_size" size)
        (uniform-int program "surface_height_size" size))
    surface-radiance-probe surface-radiance-function shaders/surface-radiance-forward shaders/interpolate-2d
    shaders/height-to-index shaders/horizon-distance shaders/sun-elevation-to-index shaders/convert-2d-index))

(tabular "Shader function to determine ambient light scattered by the atmosphere"
         (fact ((surface-radiance-test [radius max-height size] [?x ?y ?z ?lx ?ly ?lz]) 0) => (roughly ?value 1e-6))
         ?x ?y ?z              ?lx ?ly ?lz ?value
         0  0  radius          0   0   1   0.770411
         0  0  radius          1   0   0   0.095782
         0  0  (+ radius 1000) 0   0   1   0.639491)

(def ground-radiance-probe
  (template/fn [x y z incidence-frac cos-normal highlight lx ly lz water cr cg cb]
"#version 410 core
out vec3 fragColor;
vec3 surface_radiance_function(vec3 point, vec3 light_direction)
{
  return vec3(0, 0, 1);
}
vec3 transmittance_outer(vec3 point, vec3 direction)
{
  return vec3(1, 0, 0);
}
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float incidence_fraction, float cos_normal,
                     float highlight, vec3 land_color, vec3 night_color, vec3 water_color);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 land_color = vec3(<%= cr %>, <%= cg %>, <%= cb %>);
  vec3 water_color = vec3(0.1, 0.2, 0.4);
  vec3 night_color = vec3(0.0, 1.0, 0.0);
  fragColor = ground_radiance(point, light_direction, <%= water %>, <%= incidence-frac %>, <%= cos-normal %>, <%= highlight %>,
                              land_color, night_color, water_color);
}"))

(def ground-radiance-test
  (shader-test
    (fn [program radius max-height elevation-size height-size albedo reflectivity amplification dawn-start dawn-end]
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height)
        (uniform-int program "elevation_size" elevation-size)
        (uniform-int program "height_size" height-size)
        (uniform-float program "albedo" albedo)
        (uniform-float program "reflectivity" reflectivity)
        (uniform-float program "amplification" amplification)
        (uniform-float program "dawn_start" dawn-start)
        (uniform-float program "dawn_end" dawn-end))
    ground-radiance-probe (last ground-radiance) shaders/elevation-to-index shaders/interpolate-2d
    shaders/convert-2d-index shaders/is-above-horizon shaders/height-to-index shaders/sun-elevation-to-index shaders/remap))

(tabular "Shader function to compute light emitted from ground"
         (fact (mult (ground-radiance-test [6378000.0 100000.0 17 17 ?albedo 0.5 ?ampl -0.05 0.05]
                                           [?x ?y ?z ?incidence ?cnormal ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb]) PI)
               => (roughly-vector (vec3 ?r ?g ?b) 1e-6))
         ?albedo ?ampl ?x ?y ?z       ?incidence ?cnormal ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb ?r          ?g         ?b
         1       1     0  0  6378000  1          1.0      0          0   0   1   0      0   0   0   0           0          0
         1       1     0  0  6378000  1          1.0      0          0   0   1   0      0.2 0.5 0.8 0.2         0          0.8
         1       2     0  0  6378000  1          1.0      0          0   0   1   0      0.2 0.5 0.8 0.4         0          1.6
         0.9     1     0  0  6378000  1          1.0      0          0   0   1   0      1   1   1   0.9         0          0.9
         1       1     0  0  6378000  0          1.0      0          1   0   0   0      1   1   1   0           0          1.0
         1       1     0  0  6378000  1          1.0      0          0   0   1   1      0.2 0.5 0.8 0.1         0          0.4
         1       1     0  0  6378000  0          1.0      0.5        0   0   1   1      0.2 0.5 0.8 (* 0.25 PI) 0          0.4
         1       1     0  0  6378000  1          1.0      0.5        0   0   1   0      0.2 0.5 0.8 0.2         0          0.8
         1       1     0  0  6378000  1          1.0      0          0   0  -1   0      1   1   1   0           0          1.0
         1       1     0  0  6378000  0         -0.05     0          0   0   1   0      0   0   0   0           PI         0.0
         1       1     0  0  6378000  0          0.0      0          0   0   1   0      0   0   0   0           (* 0.5 PI) 0.0
         1       1     0  0  6378000  0         -1.0      0          0   0   1   0      0   0   0   0           PI         0.0)

(def vertex-planet-probe "#version 410 core
in vec3 point;
in vec2 colorcoord;
uniform float radius;
out GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.colorcoord = colorcoord;
  vs_out.point = vec3(0, 0, radius);
}")

(def fake-transmittance "#version 410 core
vec3 transmittance_track(vec3 p, vec3 q)
{
  float dist = distance(p, q);
  if (dist < 150) return vec3(1, 1, 1);
  if (dist < 150000) return vec3(0.5, 0.5, 0.5);
  return vec3(0, 0, 0);
}")

(def fake-ray-scatter "#version 410 core
uniform vec3 scatter;
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q)
{
  return scatter;
}")

(def opacity-lookup-mock
"#version 410 core
float opacity_cascade_lookup(vec4 point)
{
  return 1.0;
}")

(def sampling-offset-mock
"#version 410 core
float sampling_offset()
{
  return 0.5;
}")

(def cloud-overlay-mock
"#version 410 core
uniform float clouds;
vec4 cloud_overlay()
{
  return vec4(clouds, clouds, clouds, clouds);
}")

(def overall-shadow-mock
"#version 410 core
uniform float shadow;
float overall_shadow(vec4 point)
{
  return shadow;
}")

(defn make-planet-program []
  (make-program :vertex [vertex-planet-probe]
                :fragment [(last (fragment-planet 3)) opacity-lookup-mock sampling-offset-mock cloud-overlay-mock
                           overall-shadow-mock fake-transmittance fake-ray-scatter ground-radiance shaders/ray-shell
                           (last atmosphere/attenuation-track)]))

(defn setup-static-uniforms [program]
  ; Moved this code out of the test below, otherwise method is too large
  (uniform-sampler program "day" 0)
  (uniform-sampler program "night" 1)
  (uniform-sampler program "normals" 2)
  (uniform-sampler program "transmittance" 3)
  (uniform-sampler program "ray_scatter" 4)
  (uniform-sampler program "mie_strength" 5)
  (uniform-sampler program "surface_radiance" 6)
  (uniform-sampler program "water" 7)
  (uniform-sampler program "worley" 8)
  (uniform-float program "specular" 100)
  (uniform-float program "max_height" 100000)
  (uniform-vector3 program "water_color" (vec3 0.09 0.11 0.34)))

(defn setup-uniforms [program size ?albedo ?refl ?clouds ?shd ?radius ?dist ?lx ?ly ?lz ?a]
  ; Moved this code out of the test below, otherwise method is too large
  (uniform-int program "height_size" size)
  (uniform-int program "elevation_size" size)
  (uniform-int program "light_elevation_size" size)
  (uniform-int program "heading_size" size)
  (uniform-int program "transmittance_height_size" size)
  (uniform-int program "transmittance_elevation_size" size)
  (uniform-int program "surface_height_size" size)
  (uniform-int program "surface_sun_elevation_size" size)
  (uniform-float program "albedo" ?albedo)
  (uniform-float program "reflectivity" ?refl)
  (uniform-float program "clouds" ?clouds)
  (uniform-float program "shadow" ?shd)
  (uniform-float program "radius" radius)
  (uniform-float program "z_near" 0.0)
  (uniform-vector3 program "origin" (vec3 0 0 (+ ?radius ?dist)))
  (uniform-matrix4 program "transform" (transformation-matrix (eye 3) (vec3 0 0 (- 0 ?radius ?dist))))
  (uniform-vector3 program "light_direction" (vec3 ?lx ?ly ?lz))
  (uniform-float program "dawn_start" -0.05)
  (uniform-float program "dawn_end" 0.05)
  (uniform-float program "amplification" ?a))

(def planet-indices [0 1 3 2])
(def planet-vertices [-0.5 -0.5 0.5 0.25 0.25 0.5 0.5
                       0.5 -0.5 0.5 0.75 0.25 0.5 0.5
                      -0.5  0.5 0.5 0.25 0.75 0.5 0.5
                       0.5  0.5 0.5 0.75 0.75 0.5 0.5])

(tabular "Fragment shader to render planetary surface"
         (fact
           (offscreen-render 256 256
                             (let [program       (make-planet-program)
                                   variables     ["point" 3 "colorcoord" 2 "surfacecoord" 2]
                                   vao           (make-vertex-array-object program planet-indices planet-vertices variables)
                                   radius        6378000
                                   size          7
                                   day           (make-rgb-texture :linear :clamp
                                                   (slurp-image (str "test/sfsim25/fixtures/planet/" ?colors ".png")))
                                   night         (make-rgb-texture :linear :clamp
                                                   (slurp-image (str "test/sfsim25/fixtures/planet/night.png")))
                                   normals       (make-vector-texture-2d :linear :clamp
                                                   {:width 2 :height 2 :data (float-array (flatten (repeat 4 [?nx ?ny ?nz])))})
                                   transmittance (make-vector-texture-2d :linear :clamp
                                                   {:width size :height size
                                                    :data (float-array (flatten (repeat (* size size) [?tr ?tg ?tb])))})
                                   ray-scatter   (make-vector-texture-2d :linear :clamp
                                                   {:width (* size size) :height (* size size)
                                                    :data (float-array (repeat (* size size size size 3) ?s))})
                                   mie-strength  (make-vector-texture-2d :linear :clamp
                                                   {:width (* size size) :height (* size size)
                                                    :data (float-array (repeat (* size size size size 3) 0))})
                                   radiance      (make-vector-texture-2d :linear :clamp
                                                   {:width size :height size
                                                    :data (float-array (flatten (repeat (* size size) [?ar ?ag ?ab])))})
                                   water         (make-ubyte-texture-2d :linear :clamp
                                                   {:width 2 :height 2 :data (byte-array (repeat 8 ?water))})
                                   worley-data   (float-array (repeat (* 2 2 2) 1.0))
                                   worley        (make-float-texture-3d :linear :repeat
                                                                        {:width 2 :height 2 :depth 2 :data worley-data})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (setup-static-uniforms program)
                               (setup-uniforms program size ?albedo ?refl ?clouds ?shd radius ?dist ?lx ?ly ?lz ?a)
                               (use-textures {0 day 1 night 2 normals 3 transmittance 4 ray-scatter 5 mie-strength 6 radiance
                                              7 water 8 worley})
                               (render-quads vao)
                               (doseq [tex [worley water radiance ray-scatter mie-strength transmittance normals night day]]
                                      (destroy-texture tex))
                               (destroy-vertex-array-object vao)
                               (destroy-program program)))
           => (is-image (str "test/sfsim25/fixtures/planet/" ?result ".png") 0.0))
         ?colors   ?albedo ?a ?tr ?tg ?tb ?ar ?ag ?ab ?water ?dist  ?s  ?refl ?clouds ?shd ?lx ?ly ?lz ?nx ?ny ?nz ?result
         "white"   PI      1  1   1   1   0   0   0     0       100 0   0     0       1.0  0   0   1   0   0   1   "fragment"
         "pattern" PI      1  1   1   1   0   0   0     0       100 0   0     0       1.0  0   0   1   0   0   1   "colors"
         "white"   PI      1  1   1   1   0   0   0     0       100 0   0     0       1.0  0   0   1   0.8 0   0.6 "normal"
         "white"   0.9     1  1   1   1   0   0   0     0       100 0   0     0       1.0  0   0   1   0   0   1   "albedo"
         "white"   0.9     2  1   1   1   0   0   0     0       100 0   0     0       1.0  0   0   1   0   0   1   "amplify"
         "white"   PI      1  1   0   0   0   0   0     0       100 0   0     0       1.0  0   0   1   0   0   1   "transmit"
         "pattern" PI      1  1   1   1   0.2 0.3 0.5   0       100 0   0     0       1.0  0   0   1   0   0   1   "ambient"
         "white"   PI      1  1   1   1   0   0   0   255       100 0   0     0       1.0  0   0   1   0   0   0   "water"
         "white"   PI      1  1   1   1   0   0   0   255       100 0   0.5   0       1.0  0   0   1   0   0   1   "reflection1"
         "white"   PI      1  1   1   1   0   0   0   255       100 0   0.5   0       1.0  0   0.6 0.8 0   0   1   "reflection2"
         "pattern" PI      1  1   1   1   0   0   0   255       100 0   0.5   0       1.0  0   0  -1   0   0   1   "reflection3"
         "white"   PI      1  1   1   1   0   0   0     0     10000 0   0     0       1.0  0   0   1   0   0   1   "absorption"
         "white"   PI      1  1   1   1   0   0   0     0    200000 0   0     0       1.0  0   0   1   0   0   1   "absorption"
         "white"   PI      1  1   1   1   0   0   0     0       100 0.5 0     0       1.0  0   0   1   0   0   1   "scatter"
         "pattern" PI      1  1   1   1   0   0   0     0       100 0   0     0.5     1.0  0   0   1   0   0   1   "clouds"
         "pattern" PI      1  1   1   1   0   0   0     0       100 0   0     0       0.5  0   0   1   0   0   1   "shadow")

(def fragment-white-tree
"#version 410 core
in GEO_OUT
{
  vec2 colorcoord;
  vec3 point;
} fs_in;
out vec3 fragColor;
void main()
{
  fragColor = vec3(1, 1, 1);
}")

(tabular "Render a planetary tile using the specified texture keys and neighbour tessellation"
         (fact
           (offscreen-render 256 256
                             (let [program    (make-program :vertex [vertex-planet]
                                                            :tess-control [tess-control-planet]
                                                            :tess-evaluation [tess-evaluation-planet]
                                                            :geometry [geometry-planet]
                                                            :fragment [fragment-white-tree])
                                   indices    [0 2 3 1]
                                   face       0
                                   vertices   (make-cube-map-tile-vertices face 0 0 0 9 9)
                                   data       (flatten
                                                (for [y (range 1.0 -1.25 -0.25) x (range -1.0 1.25 0.25)]
                                                     [(* x 0.5) (* y 0.5) 0.5]))
                                   surf-tex   (make-vector-texture-2d :linear :clamp {:width 9 :height 9 :data (float-array data)})
                                   vao        (make-vertex-array-object program indices vertices
                                                                        ["point" 3 "surfacecoord" 2 "colorcoord" 2])
                                   transform  (transformation-matrix (eye 3) (vec3 0 0 2.5))
                                   projection (projection-matrix 256 256 0.5 5.0 (/ PI 3))
                                   neighbours {:sfsim25.quadtree/up    ?up
                                               :sfsim25.quadtree/left  ?left
                                               :sfsim25.quadtree/down  ?down
                                               :sfsim25.quadtree/right ?right}
                                   tile       (merge {:vao vao :surf-tex surf-tex :center (vec3 0 0 0.5)} neighbours)]
                               (use-program program)
                               (clear (vec3 0 0 0))
                               (uniform-sampler program "surface" 0)
                               (uniform-int program "high_detail" 8)
                               (uniform-int program "low_detail" 4)
                               (uniform-matrix4 program "projection" projection)
                               (raster-lines (render-tile program tile (inverse transform) [:surf-tex]))
                               (destroy-texture surf-tex)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/planet/" ?result) 0.01))
         ?up   ?left ?down ?right ?result
         true  true  true  true   "tile.png"
         false true  true  true   "tile-up.png"
         true  false true  true   "tile-left.png"
         true  true  false true   "tile-down.png"
         true  true  true  false  "tile-right.png")

(defn render-tile-calls [program node transform texture-keys]
  (let [calls (atom [])]
    (with-redefs [render-tile (fn [^long program ^clojure.lang.IPersistentMap tile ^Mat4x4 transform
                                   ^clojure.lang.PersistentVector texture-keys]
                                  (swap! calls conj [program tile transform texture-keys]))]
      (render-tree program node transform texture-keys)
      @calls)))

(tabular "Call each tile in tree to be rendered"
         (fact (render-tile-calls ?program ?node ?transform ?texture-keys) => ?result)
         ?program ?transform ?node               ?texture-keys ?result
         1234     :transform {}                  [:surf-tex]   []
         1234     :transform {:vao 42}           [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:0 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:1 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:2 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:3 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:4 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:5 {:vao 42}}      [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]]
         1234     :transform {:3 {:2 {:vao 42}}} [:surf-tex]   [[1234 {:vao 42} :transform [:surf-tex]]])

(GLFW/glfwTerminate)
