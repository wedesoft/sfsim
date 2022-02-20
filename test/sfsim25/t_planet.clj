(ns sfsim25.t-planet
    (:require [midje.sweet :refer :all]
              [clojure.core.matrix :refer :all]
              [sfsim25.cubemap :as cubemap]
              [sfsim25.render :refer :all]
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
                        (let [indices  [0 1 3 2]
                              vertices [-0.5 -0.5 0.5 0 0 0 0
                                         0.5 -0.5 0.5 0 0 0 0
                                        -0.5  0.5 0.5 0 0 0 0
                                         0.5  0.5 0.5 0 0 0 0]
                              program  (make-program :vertex [vertex-planet]
                                                     :fragment [fragment-white])
                              vao      (make-vertex-array-object program indices vertices [:point 3 :heightcoord 2 :colorcoord 2])]
                          (clear (matrix [0 0 0]))
                          (use-program program)
                          (raster-lines (render-quads vao))
                          (destroy-vertex-array-object vao)
                          (destroy-program program))) => (is-image "test/sfsim25/fixtures/planet-quad.png"))
