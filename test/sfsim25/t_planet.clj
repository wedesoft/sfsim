(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (roughly-matrix is-image vertex-passthrough)]
              [comb.template :as template]
              [clojure.math :refer (PI)]
              [clojure.core.matrix :refer (matrix mul identity-matrix)]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.planet :refer :all]))

(facts "Create vertex array object for drawing cube map tiles"
       (let [a (matrix [-0.75 -0.5  -1.0])
             b (matrix [-0.5  -0.5  -1.0])
             c (matrix [-0.75 -0.25 -1.0])
             d (matrix [-0.5  -0.25 -1.0])]
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
out lowp vec3 fragColor;
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
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (raster-lines (render-quads vao))
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet/quad.png"))

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
                                   heightfield (make-float-texture-2d {:width 1 :height 1 :data (float-array [1.0])})]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :heightfield 0)
                               (uniform-int program :high_detail 4)
                               (uniform-int program :low_detail 2)
                               (uniform-int program :neighbours ?neighbours)
                               (uniform-matrix4 program :inverse_transform (identity-matrix 4))
                               (uniform-matrix4 program :projection (identity-matrix 4))
                               (use-textures heightfield)
                               (raster-lines (render-patches vao))
                               (destroy-texture heightfield)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
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
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
  highp vec3 point;
} frag_in;
out lowp vec3 fragColor;
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
                                   heightfield (make-float-texture-2d {:width 1 :height 1 :data (float-array [?scale])})]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :heightfield 0)
                               (uniform-int program :high_detail 4)
                               (uniform-int program :low_detail 2)
                               (uniform-int program :neighbours 15)
                               (uniform-matrix4 program :inverse_transform (identity-matrix 4))
                               (uniform-matrix4 program :projection (identity-matrix 4))
                               (use-textures heightfield)
                               (render-patches vao)
                               (destroy-texture heightfield)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
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
                              heightfield (make-float-texture-2d {:width 1 :height 1 :data (float-array [1.0])})]
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (uniform-sampler program :heightfield 0)
                          (uniform-int program :high_detail 4)
                          (uniform-int program :low_detail 2)
                          (uniform-int program :neighbours 15)
                          (uniform-matrix4 program :inverse_transform (transformation-matrix (identity-matrix 3)
                                                                                             (matrix [0.1 0 0])))
                          (uniform-matrix4 program :projection (identity-matrix 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet/tessellation.png"))

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
                              heightfield (make-float-texture-2d {:width 1 :height 1 :data (float-array [1.0])})]
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (uniform-sampler program :heightfield 0)
                          (uniform-int program :high_detail 4)
                          (uniform-int program :low_detail 2)
                          (uniform-int program :neighbours 15)
                          (uniform-matrix4 program :inverse_transform (transformation-matrix (identity-matrix 3)
                                                                                             (matrix [0 0 -2])))
                          (uniform-matrix4 program :projection (projection-matrix 256 256 1 3 (/ PI 3)))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet/projection.png"))

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
                              heightfield (make-float-texture-2d {:width 2 :height 2 :data (float-array [0.5 1.0 1.5 2.0])})]
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (uniform-sampler program :heightfield 0)
                          (uniform-int program :high_detail 4)
                          (uniform-int program :low_detail 2)
                          (uniform-int program :neighbours 15)
                          (uniform-matrix4 program :inverse_transform (identity-matrix 4))
                          (uniform-matrix4 program :projection (identity-matrix 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet/heightfield.png"))

(defn radiance-shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices   [0 1 3 2]
                vertices  [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                red-data  (flatten (repeat (* 17 17) [0 0 1]))
                red       (make-vector-texture-2d {:width 17 :height 17 :data (float-array red-data)})
                blue-data (flatten (repeat (* 17 17) [1 0 0]))
                blue      (make-vector-texture-2d {:width 17 :height 17 :data (float-array blue-data)})
                program   (make-program :vertex [vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao       (make-vertex-array-object program indices vertices [:point 3])
                tex       (texture-render 1 1 true
                                          (use-program program)
                                          (uniform-sampler program :transmittance 0)
                                          (uniform-sampler program :surface_radiance 1)
                                          (apply setup program uniforms)
                                          (use-textures red blue)
                                          (render-quads vao))
                img       (texture->vectors tex 1 1)]
            (deliver result (get-vector img 0 0))
            (destroy-texture tex)
            (destroy-texture blue)
            (destroy-texture red)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ground-radiance-probe
  (template/fn [x y z cos-incidence highlight lx ly lz water cr cg cb] "#version 410 core
out lowp vec3 fragColor;
vec3 ground_radiance(vec3 point, vec3 light_direction, float water, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 land_color = vec3(<%= cr %>, <%= cg %>, <%= cb %>);
  vec3 water_color = vec3(0.1, 0.2, 0.4);
  fragColor = ground_radiance(point, light, <%= water %>, <%= cos-incidence %>, <%= highlight %>, land_color, water_color);
}"))

(def ground-radiance-test
  (radiance-shader-test
    (fn [program radius max-height elevation-size height-size elevation-power albedo reflectivity]
        (uniform-float program :radius radius)
        (uniform-float program :max_height max-height)
        (uniform-int program :elevation_size elevation-size)
        (uniform-int program :height_size height-size)
        (uniform-float program :elevation_power elevation-power)
        (uniform-float program :albedo albedo)
        (uniform-float program :reflectivity reflectivity))
    ground-radiance-probe
    ground-radiance
    shaders/transmittance-forward
    shaders/horizon-angle
    shaders/elevation-to-index
    shaders/interpolate-2d
    shaders/convert-2d-index
    shaders/is-above-horizon))

(tabular "Shader function to compute light emitted from ground"
         (fact (mul (ground-radiance-test [6378000.0 100000.0 17 17 2.0 ?albedo 0.5]
                                          [?x ?y ?z ?cos-incidence ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb]) PI)
               => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
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
in highp vec3 point;
in mediump vec2 colorcoord;
in mediump vec2 heightcoord;
uniform float radius;
uniform float polar_radius;
out GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
  highp vec3 point;
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

(defn setup-static-uniforms [program]
  ; Moved this code out of the test below, otherwise method is too large
  (uniform-sampler program :colors 0)
  (uniform-sampler program :normals 1)
  (uniform-sampler program :transmittance 2)
  (uniform-sampler program :ray_scatter 3)
  (uniform-sampler program :surface_radiance 4)
  (uniform-sampler program :water 5)
  (uniform-float program :elevation_power 2.0)
  (uniform-float program :specular 100)
  (uniform-float program :max_height 100000)
  (uniform-vector3 program :water_color (matrix [0.09 0.11 0.34])))

(defn setup-uniforms [program size ?albedo ?refl radius ?polar ?dist ?lx ?ly ?lz ?a]
  ; Moved this code out of the test below, otherwise method is too large
  (uniform-int program :height_size size)
  (uniform-int program :elevation_size size)
  (uniform-int program :light_elevation_size size)
  (uniform-int program :heading_size size)
  (uniform-float program :albedo ?albedo)
  (uniform-float program :reflectivity ?refl)
  (uniform-float program :radius radius)
  (uniform-float program :polar_radius ?polar)
  (uniform-vector3 program :position (matrix [0 0 (+ ?polar ?dist)]))
  (uniform-vector3 program :light_direction (matrix [?lx ?ly ?lz]))
  (uniform-float program :amplification ?a))

(tabular "Fragment shader to render planetary surface"
         (fact
           (offscreen-render 256 256
                             (let [indices       [0 1 3 2]
                                   vertices      [-0.5 -0.5 0.5 0.25 0.25 0.5 0.5
                                                   0.5 -0.5 0.5 0.75 0.25 0.5 0.5
                                                  -0.5  0.5 0.5 0.25 0.75 0.5 0.5
                                                   0.5  0.5 0.5 0.75 0.75 0.5 0.5]
                                   program       (make-program :vertex [vertex-planet-probe]
                                                               :fragment [fragment-planet fake-transmittance
                                                                          shaders/interpolate-2d shaders/convert-2d-index
                                                                          shaders/horizon-angle shaders/transmittance-forward
                                                                          shaders/elevation-to-index shaders/ray-sphere
                                                                          shaders/is-above-horizon fake-ray-scatter
                                                                          ground-radiance])
                                   variables     [:point 3 :colorcoord 2 :heightcoord 2]
                                   vao           (make-vertex-array-object program indices vertices variables)
                                   radius        6378000
                                   size          7
                                   colors        (make-rgb-texture
                                                   (slurp-image (str "test/sfsim25/fixtures/planet/" ?colors ".png")))
                                   normals       (make-vector-texture-2d
                                                   {:width 2 :height 2 :data (float-array (flatten (repeat 4 [?nz ?ny ?nx])))})
                                   transmittance (make-vector-texture-2d
                                                   {:width size :height size
                                                    :data (float-array (flatten (repeat (* size size) [?tb ?tg ?tr])))})
                                   ray-scatter   (make-vector-texture-2d
                                                   {:width (* size size) :height (* size size)
                                                    :data (float-array (repeat (* size size size size 3) ?s))})
                                   radiance      (make-vector-texture-2d
                                                   {:width size :height size
                                                    :data (float-array (flatten (repeat (* size size) [?ab ?ag ?ar])))})
                                   water         (make-ubyte-texture-2d
                                                   {:width 2 :height 2 :data (byte-array (repeat 8 ?water))})]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (setup-static-uniforms program)
                               (setup-uniforms program size ?albedo ?refl radius ?polar ?dist ?lx ?ly ?lz ?a)
                               (use-textures colors normals transmittance ray-scatter radiance water)
                               (render-quads vao)
                               (destroy-texture water)
                               (destroy-texture radiance)
                               (destroy-texture ray-scatter)
                               (destroy-texture transmittance)
                               (destroy-texture normals)
                               (destroy-texture colors)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/planet/" ?result ".png")))
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
