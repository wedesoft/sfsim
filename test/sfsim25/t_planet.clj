(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [comb.template :as template]
              [clojure.core.matrix :refer :all]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.matrix :refer :all]
              [sfsim25.util :refer :all]
              [sfsim25.planet :refer :all]))

; Compare RGB components of image and ignore alpha values.
(defn is-image [filename]
  (fn [other]
      (let [img (slurp-image filename)]
        (and (= (:width img) (:width other))
             (= (:height img) (:height other))
             (= (map #(bit-and % 0x00ffffff) (:data img)) (map #(bit-and % 0x00ffffff) (:data other)))))))

; Use this test function to record the image the first time.
(defn record-image [filename]
  (fn [other]
      (spit-image filename other)))

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
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet-quad.png"))

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
                               (uniform-matrix4 program :transform (identity-matrix 4))
                               (uniform-matrix4 program :projection (identity-matrix 4))
                               (use-textures heightfield)
                               (raster-lines (render-patches vao))
                               (destroy-texture heightfield)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
         ?neighbours ?result
         15          "test/sfsim25/fixtures/planet-tessellation.png"
          1          "test/sfsim25/fixtures/planet-tessellation-0.png"
          2          "test/sfsim25/fixtures/planet-tessellation-1.png"
          4          "test/sfsim25/fixtures/planet-tessellation-2.png"
          8          "test/sfsim25/fixtures/planet-tessellation-3.png")

(def texture-coordinates-probe
  (template/fn [selector] "#version 410 core
in GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
} frag_in;
out lowp vec3 fragColor;
void main()
{
  fragColor.rg = frag_in.<%= selector %>;
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
                                   heightfield (make-float-texture-2d {:width 1 :height 1 :data (float-array [1.0])})]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :heightfield 0)
                               (uniform-int program :high_detail 4)
                               (uniform-int program :low_detail 2)
                               (uniform-int program :neighbours 15)
                               (uniform-matrix4 program :transform (identity-matrix 4))
                               (uniform-matrix4 program :projection (identity-matrix 4))
                               (use-textures heightfield)
                               (render-patches vao)
                               (destroy-texture heightfield)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image ?result))
         ?selector     ?result
         "colorcoord"  "test/sfsim25/fixtures/planet-color-coords.png"
         "heightcoord" "test/sfsim25/fixtures/planet-height-coords.png")

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
                          (uniform-matrix4 program :transform (transformation-matrix (identity-matrix 3) (matrix [0.1 0 0])))
                          (uniform-matrix4 program :projection (identity-matrix 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet-tessellation.png"))

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
                          (uniform-matrix4 program :transform (transformation-matrix (identity-matrix 3) (matrix [0 0 -2])))
                          (uniform-matrix4 program :projection (projection-matrix 256 256 1 3 (/ Math/PI 3)))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet-projection.png"))

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
                          (uniform-matrix4 program :transform (identity-matrix 4))
                          (uniform-matrix4 program :projection (identity-matrix 4))
                          (use-textures heightfield)
                          (raster-lines (render-patches vao))
                          (destroy-texture heightfield)
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet-heightfield.png"))

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(def vertex-passthrough "#version 410 core
in highp vec3 point;
void main()
{
  gl_Position = vec4(point, 1);
}")

(defn radiance-shader-test [probe & shaders]
  (fn [& args]
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
                                          (uniform-sampler program :red 0)
                                          (uniform-sampler program :blue 1)
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
  (template/fn [albedo x y z cos-incidence highlight lx ly lz water cr cg cb] "#version 410 core
uniform sampler2D red;
uniform sampler2D blue;
out lowp vec3 fragColor;
vec3 ground_radiance(float albedo, sampler2D transmittance, sampler2D surface_radiance, float radius, float max_height, int size,
                     float power, vec3 point, vec3 light, float water, float reflectivity, float cos_incidence, float highlight,
                     vec3 land_color, vec3 water_color);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 light = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 land_color = vec3(<%= cr %>, <%= cg %>, <%= cb %>);
  vec3 water_color = vec3(0.1, 0.2, 0.4);
  fragColor = ground_radiance(<%= albedo %>, red, blue, 6378000, 100000, 17, 2.0, point, light, <%= water %>, 0.5,
                              <%= cos-incidence %>, <%= highlight %>, land_color, water_color);
}"))

(def ground-radiance-test (radiance-shader-test ground-radiance-probe ground-radiance shaders/transmittance-forward
                                                shaders/horizon-angle shaders/elevation-to-index shaders/interpolate-2d
                                                shaders/convert-2d-index))

(tabular "Shader function to compute light emitted from ground"
         (fact (mul (ground-radiance-test ?albedo ?x ?y ?z ?cos-incidence ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb) Math/PI)
               => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
         ?albedo ?x ?y ?z       ?cos-incidence ?highlight ?lx ?ly ?lz ?water ?cr ?cg ?cb ?r               ?g ?b
         1       0  0  6378000  1              0          0   0   1   0      0   0   0   0                0  0
         1       0  0  6378000  1              0          0   0   1   0      0.2 0.5 0.8 0.2              0  0.8
         0.9     0  0  6378000  1              0          0   0   1   0      1   1   1   0.9              0  0.9
         1       0  0  6378000  0              0          1   0   0   0      1   1   1   0                0  1.0
         1       0  0  6378000  1              0          0   0   1   1      0.2 0.5 0.8 0.1              0  0.4
         1       0  0  6378000  0              0.5        0   0   1   1      0.2 0.5 0.8 (* 0.25 Math/PI) 0  0.4
         1       0  0  6378000  1              0.5        0   0   1   0      0.2 0.5 0.8 0.2              0  0.8)

(def vertex-planet-probe "#version 410 core
in highp vec3 point;
in mediump vec2 colorcoord;
in mediump vec2 heightcoord;
out GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
} vs_out;
void main()
{
  gl_Position = vec4(point, 1);
  vs_out.colorcoord = colorcoord;
  vs_out.heightcoord = heightcoord;
}")

(tabular "Fragment shader to render planetary surface"
         (fact
           (offscreen-render 256 256
                             (let [indices   [0 1 3 2]
                                   vertices  [-0.5 -0.5 0.5 0.25 0.25 0.5 0.5
                                               0.5 -0.5 0.5 0.75 0.25 0.5 0.5
                                              -0.5  0.5 0.5 0.25 0.75 0.5 0.5
                                               0.5  0.5 0.5 0.75 0.75 0.5 0.5]
                                   program   (make-program :vertex [vertex-planet-probe]
                                                           :fragment [fragment-planet])
                                   variables [:point 3 :colorcoord 2 :heightcoord 2]
                                   vao       (make-vertex-array-object program indices vertices variables)
                                   colors    (make-rgb-texture (slurp-image (str "test/sfsim25/fixtures/" ?colors ".png")))
                                   normals   (make-vector-texture-2d {:width 2 :height 2
                                                                      :data (float-array (flatten (repeat 4 [?nz ?ny ?nx])))})]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-sampler program :colors 0)
                               (uniform-sampler program :normals 1)
                               (uniform-vector3 program :light (matrix [?lx ?ly ?lz]))
                               (use-textures colors normals)
                               (render-quads vao)
                               (destroy-texture normals)
                               (destroy-texture colors)
                               (destroy-vertex-array-object vao)
                               (destroy-program program))) => (is-image (str "test/sfsim25/fixtures/" ?result ".png")))
         ?colors   ?lx ?ly ?lz ?nx ?ny ?nz ?result
         "white"   0   0   1   0   0   1   "planet-fragment"
         "pattern" 0   0   1   0   0   1   "planet-colors"
         "white"   0   0   1   0.8 0   0.6 "planet-normal")
