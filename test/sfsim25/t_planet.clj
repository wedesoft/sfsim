(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-vector is-image record-image)]
              [comb.template :as template]
              [clojure.math :refer (PI)]
              [fastmath.vector :refer (vec3 mult)]
              [fastmath.matrix :refer (eye)]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.atmosphere :as atmosphere]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.planet :refer :all])
    (:import [org.lwjgl.glfw GLFW]))

(GLFW/glfwInit)

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
                              variables [:point 3 :heightcoord 2 :colorcoord 2]
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
                                   vertices    [-0.5 -0.5 0.5 0 0 0 0
                                                 0.5 -0.5 0.5 0 0 0 0
                                                -0.5  0.5 0.5 0 0 0 0
                                                 0.5  0.5 0.5 0 0 0 0]
                                   program     (make-program :vertex [vertex-planet]
                                                             :tess-control [tess-control-planet]
                                                             :tess-evaluation [tess-evaluation-planet]
                                                             :geometry [geometry-planet]
                                                             :fragment [fragment-white])
                                   variables   [:point 3 :heightcoord 2 :colorcoord 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   heightfield (make-float-texture-2d :linear :clamp
                                                                      {:width 1 :height 1 :data (float-array [1.0])})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "heightfield" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" ?neighbours)
                               (uniform-matrix4 program "inverse_transform" (eye 4))
                               (uniform-matrix4 program "projection" (eye 4))
                               (use-textures heightfield)
                               (raster-lines (render-patches vao))
                               (destroy-texture heightfield)
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
  vec2 heightcoord;
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
                                   vertices    [-0.5 -0.5 0.5 0.125 0.125 0.25 0.25
                                                 0.5 -0.5 0.5 0.875 0.125 0.75 0.25
                                                -0.5  0.5 0.5 0.125 0.875 0.25 0.75
                                                 0.5  0.5 0.5 0.875 0.875 0.75 0.75]
                                   program     (make-program :vertex [vertex-planet]
                                                             :tess-control [tess-control-planet]
                                                             :tess-evaluation [tess-evaluation-planet]
                                                             :geometry [geometry-planet]
                                                             :fragment [(texture-coordinates-probe ?selector)])
                                   variables   [:point 3 :heightcoord 2 :colorcoord 2]
                                   vao         (make-vertex-array-object program indices vertices variables)
                                   heightfield (make-float-texture-2d :linear :clamp
                                                                      {:width 1 :height 1 :data (float-array [?scale])})]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (uniform-sampler program "heightfield" 0)
                               (uniform-int program "high_detail" 4)
                               (uniform-int program "low_detail" 2)
                               (uniform-int program "neighbours" 15)
                               (uniform-matrix4 program "inverse_transform" (eye 4))
                               (uniform-matrix4 program "projection" (eye 4))
                               (use-textures heightfield)
                               (render-patches vao)
                               (destroy-texture heightfield)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result 0.02))
         ?selector                           ?scale ?result
         "frag_in.colorcoord"                1.0    "test/sfsim25/fixtures/planet/color-coords.png"
         "frag_in.heightcoord"               1.0    "test/sfsim25/fixtures/planet/height-coords.png"
         "frag_in.point.xy + vec2(0.5, 0.5)" 1.0    "test/sfsim25/fixtures/planet/point.png"
         "frag_in.point.xy + vec2(0.5, 0.5)" 1.1    "test/sfsim25/fixtures/planet/scaled-point.png")

(fact "Apply transformation to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.6 -0.5 0.5 0 0 0 0
                                            0.4 -0.5 0.5 0 0 0 0
                                           -0.6  0.5 0.5 0 0 0 0
                                            0.4  0.5 0.5 0 0 0 0]
                              program     (make-program :vertex [vertex-planet]
                                                        :tess-control [tess-control-planet]
                                                        :tess-evaluation [tess-evaluation-planet]
                                                        :geometry [geometry-planet]
                                                        :fragment [fragment-white])
                              variables   [:point 3 :heightcoord 2 :colorcoord 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              heightfield (make-float-texture-2d :linear :clamp
                                                                 {:width 1 :height 1 :data (float-array [1.0])})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "heightfield" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "inverse_transform" (transformation-matrix (eye 3)
                                                                                             (vec3 0.1 0 0)))
                          (uniform-matrix4 program "projection" (eye 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/sfsim25/fixtures/planet/tessellation.png" 5.0))

(fact "Apply projection matrix to points in tessellation evaluation shader"
      (offscreen-render 256 256
                        (let [indices     [0 1 3 2]
                              vertices    [-0.5 -0.5 0 0 0 0 0
                                            0.5 -0.5 0 0 0 0 0
                                           -0.5  0.5 0 0 0 0 0
                                            0.5  0.5 0 0 0 0 0]
                              program     (make-program :vertex [vertex-planet]
                                                        :tess-control [tess-control-planet]
                                                        :tess-evaluation [tess-evaluation-planet]
                                                        :geometry [geometry-planet]
                                                        :fragment [fragment-white])
                              variables   [:point 3 :heightcoord 2 :colorcoord 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              heightfield (make-float-texture-2d :linear :clamp
                                                                 {:width 1 :height 1 :data (float-array [1.0])})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "heightfield" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "inverse_transform" (transformation-matrix (eye 3)
                                                                                             (vec3 0 0 -2)))
                          (uniform-matrix4 program "projection" (projection-matrix 256 256 1 3 (/ PI 3)))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
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
                              variables   [:point 3 :heightcoord 2 :colorcoord 2]
                              vao         (make-vertex-array-object program indices vertices variables)
                              heightfield (make-float-texture-2d :linear :clamp
                                                                 {:width 2 :height 2 :data (float-array [0.5 1.0 1.5 2.0])})]
                          (clear (vec3 0 0 0))
                          (use-program program)
                          (uniform-sampler program "heightfield" 0)
                          (uniform-int program "high_detail" 4)
                          (uniform-int program "low_detail" 2)
                          (uniform-int program "neighbours" 15)
                          (uniform-matrix4 program "inverse_transform" (eye 4))
                          (uniform-matrix4 program "projection" (eye 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program)))
      => (is-image "test/sfsim25/fixtures/planet/heightfield.png" 1.6))

(defn radiance-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (with-invisible-window
          (let [indices   [0 1 3 2]
                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                red-data  (flatten (repeat (* 17 17) [1 0 0]))
                red       (make-vector-texture-2d :linear :clamp {:width 17 :height 17 :data (float-array red-data)})
                blue-data (flatten (repeat (* 17 17) [0 0 1]))
                blue      (make-vector-texture-2d :linear :clamp {:width 17 :height 17 :data (float-array blue-data)})
                program   (make-program :vertex [shaders/vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao       (make-vertex-array-object program indices vertices [:point 3])
                tex       (texture-render-color
                            1 1 true
                            (use-program program)
                            (uniform-sampler program "transmittance" 0)
                            (uniform-sampler program "surface_radiance" 1)
                            (apply setup program uniforms)
                            (use-textures red blue)
                            (render-quads vao))
                img       (rgb-texture->vectors3 tex)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-texture blue)
            (destroy-texture red)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ground-radiance-probe
  (template/fn [x y z cos-incidence highlight lx ly lz water cr cg cb] "#version 410 core
out vec3 fragColor;
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light_direction = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 land_color = vec3(<%= cr %>, <%= cg %>, <%= cb %>);
  vec3 water_color = vec3(0.1, 0.2, 0.4);
  fragColor = ground_radiance(point, light_direction, <%= water %>, <%= cos-incidence %>, <%= highlight %>, land_color, water_color);
}"))

(def ground-radiance-test
  (radiance-shader-test
    (fn [program radius max-height elevation-size height-size albedo reflectivity]
        (uniform-float program "radius" radius)
        (uniform-float program "max_height" max-height)
        (uniform-int program "elevation_size" elevation-size)
        (uniform-int program "height_size" height-size)
        (uniform-float program "albedo" albedo)
        (uniform-float program "reflectivity" reflectivity))
    ground-radiance-probe ground-radiance shaders/transmittance-forward shaders/elevation-to-index shaders/interpolate-2d
    shaders/convert-2d-index shaders/is-above-horizon shaders/height-to-index shaders/horizon-distance shaders/limit-quot
    shaders/surface-radiance-forward shaders/sun-elevation-to-index surface-radiance-function atmosphere/transmittance-outer))

(tabular "Shader function to compute light emitted from ground"
         (fact (mult (ground-radiance-test [6378000.0 100000.0 17 17 ?albedo 0.5]
                                           [?x ?y ?z ?cos-incidence ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb]) PI)
               => (roughly-vector (vec3 ?r ?g ?b) 1e-6))
         ?albedo ?x ?y ?z       ?cos-incidence ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb ?r          ?g ?b
         1       0  0  6378000  1              0          0   0   1   0      0   0   0   0           0  0
         1       0  0  6378000  1              0          0   0   1   0      0.2 0.5 0.8 0.2         0  0.8
         0.9     0  0  6378000  1              0          0   0   1   0      1   1   1   0.9         0  0.9
         1       0  0  6378000  0              0          1   0   0   0      1   1   1   0           0  1.0
         1       0  0  6378000  1              0          0   0   1   1      0.2 0.5 0.8 0.1         0  0.4
         1       0  0  6378000  0              0.5        0   0   1   1      0.2 0.5 0.8 (* 0.25 PI) 0  0.4
         1       0  0  6378000  1              0.5        0   0   1   0      0.2 0.5 0.8 0.2         0  0.8
         1       0  0  6378000  1              0          0   0  -1   0      1   1   1   0           0  1)

(def vertex-planet-probe "#version 410 core
in vec3 point;
in vec2 colorcoord;
in vec2 heightcoord;
uniform float radius;
uniform float polar_radius;
out GEO_OUT
{
  vec2 colorcoord;
  vec2 heightcoord;
  vec3 point;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.colorcoord = colorcoord;
  vs_out.heightcoord = heightcoord;
  vs_out.point = vec3(0, 0, polar_radius);
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

(defn make-planet-program []
  (make-program :vertex [vertex-planet-probe]
                :fragment [fragment-planet fake-transmittance
                           shaders/interpolate-2d shaders/convert-2d-index
                           shaders/transmittance-forward shaders/elevation-to-index
                           shaders/ray-sphere shaders/is-above-horizon
                           fake-ray-scatter atmosphere/attenuation-track
                           atmosphere/transmittance-outer
                           ground-radiance shaders/ray-shell
                           atmosphere/phase-function
                           shaders/clip-shell-intersections
                           shaders/ray-scatter-forward shaders/height-to-index
                           shaders/horizon-distance shaders/limit-quot
                           shaders/surface-radiance-forward
                           shaders/sun-elevation-to-index opacity-lookup-mock
                           sampling-offset-mock
                           surface-radiance-function]))

(defn setup-static-uniforms [program]
  ; Moved this code out of the test below, otherwise method is too large
  (uniform-sampler program "colors" 0)
  (uniform-sampler program "normals" 1)
  (uniform-sampler program "transmittance" 2)
  (uniform-sampler program "ray_scatter" 3)
  (uniform-sampler program "mie_strength" 4)
  (uniform-sampler program "surface_radiance" 5)
  (uniform-sampler program "water" 6)
  (uniform-sampler program "worley" 7)
  (uniform-sampler program "profile" 8)
  (uniform-float program "specular" 100)
  (uniform-float program "max_height" 100000)
  (uniform-vector3 program "water_color" (vec3 0.09 0.11 0.34)))

(defn setup-uniforms [program size ?albedo ?refl radius ?polar ?dist ?lx ?ly ?lz ?a]
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
  (uniform-float program "radius" radius)
  (uniform-float program "polar_radius" ?polar)
  (uniform-vector3 program "position" (vec3 0 0 (+ ?polar ?dist)))
  (uniform-vector3 program "light_direction" (vec3 ?lx ?ly ?lz))
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
                                   variables     [:point 3 :colorcoord 2 :heightcoord 2]
                                   vao           (make-vertex-array-object program planet-indices planet-vertices variables)
                                   radius        6378000
                                   size          7
                                   colors        (make-rgb-texture :linear :clamp
                                                   (slurp-image (str "test/sfsim25/fixtures/planet/" ?colors ".png")))
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
                                                                        {:width 2 :height 2 :depth 2 :data worley-data})
                                   profile-data  (float-array [0 1 1 1 1 1 1 0])
                                   profile       (make-float-texture-1d :linear :clamp profile-data)]
                               (clear (vec3 0 0 0))
                               (use-program program)
                               (setup-static-uniforms program)
                               (setup-uniforms program size ?albedo ?refl radius ?polar ?dist ?lx ?ly ?lz ?a)
                               (use-textures colors normals transmittance ray-scatter mie-strength radiance water worley profile)
                               (render-quads vao)
                               (doseq [tex [profile worley water radiance ray-scatter mie-strength transmittance normals colors]]
                                      (destroy-texture tex))
                               (destroy-vertex-array-object vao)
                               (destroy-program program)))
           => (is-image (str "test/sfsim25/fixtures/planet/" ?result ".png") 0.0))
         ?colors   ?albedo ?a ?polar       ?tr ?tg ?tb ?ar ?ag ?ab ?water ?dist  ?s  ?refl ?lx ?ly ?lz ?nx ?ny ?nz ?result
         "white"   PI      1  radius       1   1   1   0   0   0     0       100 0   0     0   0   1   0   0   1   "fragment"
         "pattern" PI      1  radius       1   1   1   0   0   0     0       100 0   0     0   0   1   0   0   1   "colors"
         "white"   PI      1  radius       1   1   1   0   0   0     0       100 0   0     0   0   1   0.8 0   0.6 "normal"
         "white"   0.9     1  radius       1   1   1   0   0   0     0       100 0   0     0   0   1   0   0   1   "albedo"
         "white"   0.9     2  radius       1   1   1   0   0   0     0       100 0   0     0   0   1   0   0   1   "amplify"
         "white"   PI      1  radius       1   0   0   0   0   0     0       100 0   0     0   0   1   0   0   1   "transmit"
         "white"   PI      1  radius       1   1   1   0.4 0.6 0.8   0       100 0   0     0   1   0   0   0   1   "ambient"
         "white"   PI      1  radius       1   1   1   0   0   0   255       100 0   0     0   0   1   0   0   1   "water"
         "white"   PI      1  radius       1   1   1   0   0   0   255       100 0   0.5   0   0   1   0   0   1   "reflection1"
         "white"   PI      1  radius       1   1   1   0   0   0   255       100 0   0.5   0   0.6 0.8 0   0   1   "reflection2"
         "white"   PI      1  radius       1   1   1   0   0   0   255       100 0   0.5   0   0   1   0   0  -1   "reflection3"
         "white"   PI      1  radius       1   1   1   0   0   0     0     10000 0   0     0   0   1   0   0   1   "absorption"
         "white"   PI      1  radius       1   1   1   0   0   0     0    200000 0   0     0   0   1   0   0   1   "absorption"
         "white"   PI      1  radius       1   1   1   0   0   0     0       100 0.5 0     0   0   1   0   0   1   "scatter"
         "white"   PI      1  (/ radius 2) 1   1   1   0   0   0     0       100 0   0     0   0   1   0   0   1   "scaled")

(GLFW/glfwTerminate)
