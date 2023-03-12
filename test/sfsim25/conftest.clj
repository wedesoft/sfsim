(ns sfsim25.conftest
    (:require [clojure.core.matrix :refer (sub)]
              [clojure.core.matrix.linear :refer (norm)]
              [midje.sweet :refer (roughly)]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]
              [sfsim25.util :refer :all]))

(defn roughly-matrix
  "Compare matrix with expected value."
  [expected error]
  (fn [actual] (<= (norm (sub expected actual)) error)))

(defn roughly-vector
  "Elementwise comparison of vector"
  [expected error]
  (fn [actual]
      (and (== (count expected) (count actual)))
      (every? true? (map #((roughly %1 error) %2) expected actual))))

(defn is-image
  "Compare RGB components of image and ignore alpha values."
  [filename]
  (fn [other]
      (let [img (slurp-image filename)]
        (and (== (:width img) (:width other))
             (== (:height img) (:height other))
             (= (map #(bit-and % 0x00ffffff) (:data img)) (map #(bit-and % 0x00ffffff) (:data other)))))))

(defn record-image
  "Use this test function to record the image the first time."
  [filename]
  (fn [other]
      (spit-image filename other)))

(defn shader-test [setup probe & shaders]
  (fn [uniforms args]
      (let [result (promise)]
        (offscreen-render 1 1
          (let [indices  [0 1 3 2]
                vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
                program  (make-program :vertex [shaders/vertex-passthrough] :fragment (conj shaders (apply probe args)))
                vao      (make-vertex-array-object program indices vertices [:point 3])
                tex      (texture-render-color 1 1 true (use-program program) (apply setup program uniforms) (render-quads vao))
                img      (rgb-texture->vectors3 tex)]
            (deliver result (get-vector3 img 0 0))
            (destroy-texture tex)
            (destroy-vertex-array-object vao)
            (destroy-program program)))
        @result)))
