(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [comb.template :as template]
              [clojure.core.matrix :refer :all]
              [clojure.core.matrix.linear :refer (norm)]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
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
                             (let [indices   [0 1 3 2]
                                   vertices  [-0.5 -0.5 0.5 0 0 0 0
                                               0.5 -0.5 0.5 0 0 0 0
                                              -0.5  0.5 0.5 0 0 0 0
                                               0.5  0.5 0.5 0 0 0 0]
                                   program   (make-program :vertex [vertex-planet]
                                                           :tess-control [tess-control-planet]
                                                           :tess-evaluation [tess-evaluation-planet]
                                                           :geometry [geometry-planet]
                                                           :fragment [fragment-white])
                                   variables [:point 3 :heightcoord 2 :colorcoord 2]
                                   vao       (make-vertex-array-object program indices vertices variables)]
                               (clear (matrix [0 0 0]))
                               (use-program program)
                               (uniform-int program :high_detail 4)
                               (uniform-int program :low_detail 2)
                               (uniform-int program :neighbours ?neighbours)
                               (raster-lines (render-patches vao))
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
  mediump vec2 heightcoord;
  mediump vec2 colorcoord;
} frag_in;
out lowp vec3 fragColor;
void main()
{
  fragColor.rg = <%= selector %>;
  fragColor.b = 0;
}"))

(tabular "Test color texture coordinates"
         (fact (offscreen-render 256 256
                                 (let [indices   [0 1 3 2]
                                       vertices  [-0.5 -0.5 0.5 0.125 0.125 0.25 0.25
                                                   0.5 -0.5 0.5 0.875 0.125 0.75 0.25
                                                  -0.5  0.5 0.5 0.125 0.875 0.25 0.75
                                                   0.5  0.5 0.5 0.875 0.875 0.75 0.75]
                                       program   (make-program :vertex [vertex-planet]
                                                               :tess-control [tess-control-planet]
                                                               :tess-evaluation [tess-evaluation-planet]
                                                               :geometry [geometry-planet]
                                                               :fragment [(texture-coordinates-probe ?selector)])
                                       variables [:point 3 :heightcoord 2 :colorcoord 2]
                                       vao       (make-vertex-array-object program indices vertices variables)]
                                   (clear (matrix [0 0 0]))
                                   (use-program program)
                                   (uniform-sampler program :colors 0)
                                   (uniform-int program :high_detail 4)
                                   (uniform-int program :low_detail 2)
                                   (uniform-int program :neighbours 15)
                                   (render-patches vao)
                                   (destroy-vertex-array-object vao)
                                   (destroy-program program))) => (is-image ?result))
         ?selector         ?result
         "frag_in.colorcoord"  "test/sfsim25/fixtures/planet-color-coords.png"
         "frag_in.heightcoord" "test/sfsim25/fixtures/planet-height-coords.png")

(defn roughly-matrix [y error] (fn [x] (<= (norm (sub y x)) error)))

(def pi Math/PI)

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
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))

(def ground-radiance-probe
  (template/fn [albedo x y z nx ny nz lx ly lz r g b] "#version 410 core
uniform sampler2D red;
uniform sampler2D blue;
out lowp vec3 fragColor;
vec3 ground_radiance(float albedo, sampler2D transmittance, sampler2D surface_radiance, float radius, float max_height, int size,
                     float power, vec3 point, vec3 normal, vec3 light, vec3 color);
void main()
{
  vec3 point = vec3(<%= x %>, <%= y %>, <%= z %>);
  vec3 normal = vec3(<%= nx %>, <%= ny %>, <%= nz %>);
  vec3 light = vec3(<%= lx %>, <%= ly %>, <%= lz %>);
  vec3 color = vec3(<%= r %>, <%= g %>, <%= b %>);
  fragColor = ground_radiance(<%= albedo %>, red, blue, 6378000, 100000, 17, 2.0, point, normal, light, color);
}"))

(def ground-radiance-test (shader-test ground-radiance-probe ground-radiance shaders/transmittance-forward shaders/horizon-angle
                                       shaders/elevation-to-index shaders/interpolate-2d shaders/convert-2d-index))

(tabular "Shader function to compute light emitted from ground"
         (fact (ground-radiance-test ?albedo ?x ?y ?z ?nx ?ny ?nz ?lx ?ly ?lz ?cr ?cg ?cb)
               => (roughly-matrix (matrix [?r ?g ?b]) 1e-6))
         ?albedo ?x ?y ?z      ?nx ?ny ?nz ?lx ?ly ?lz ?cr ?cg ?cb ?r         ?g ?b
         1       0  0  6378000 0   0   1   0   0   1   0   0   0   0          0  0
         1       0  0  6378000 0   0   1   0   0   1   0.2 0.5 0.8 (/ 0.2 pi) 0  (/ 0.8 pi)
         0.3     0  0  6378000 0   0   1   0   0   1   1   1   1   (/ 0.3 pi) 0  (/ 0.3 pi)
         1       0  0  6378000 0   0   1   1   0   0   1   1   1   0          0  (/ 1.0 pi)
         1       0  0  6378000 0   0   1   0   0  -1   1   1   1   0          0  (/ 1.0 pi))
