(ns sfsim25.conftest
    (:require [clojure.math :refer (sqrt)]
              [fastmath.matrix :as fm]
              [sfsim25.render :refer (make-program make-vertex-array-object render-quads rgb-texture->vectors3
                                      texture-render-color use-program with-invisible-window destroy-program destroy-texture
                                      destroy-vertex-array-object)]
              [sfsim25.shaders :as shaders]
              [sfsim25.util :refer (get-vector3 slurp-image spit-png)]))

(defn roughly-matrix
  "compare matrix with expected value."
  [expected error]
  (fn [actual]
      (let [difference (fm/sub expected actual)]
        (<= (sqrt (apply + (fm/mat->array (fm/emulm difference difference)))) error))))

(defn roughly-vector
  "Compare vector with expected value."
  [expected error]
  (fn [actual]
      (and (== (count expected) (count actual))
           (<= (sqrt (apply + (map (comp #(* % %) -) actual expected))) error))))

(defn roughly-quaternion
  "Compare quaternion with expected value."
  [expected error]
  (fn [actual]
      (and (<= (sqrt (apply + (map (comp #(* % %) #(- (% actual) (% expected))) [:a :b :c :d]))) error))))

(defn rgba-dist [c1 c2]
  (apply max (map #(abs (- %1 %2)) c1 c2)))

(defn average-rgba-dist [data1 data2]
  (let [distances (map rgba-dist (partition 4 data1) (partition 4 data2))]
    (float (/ (reduce + distances) (count distances)))))

(defn is-image
  "Compare RGB components of image and ignore alpha values."
  [filename tolerance]
  (fn [other]
      (let [{:keys [width height data]} (slurp-image filename)]
        (and (== (:width other) width)
             (== (:height other) height)
             (let [avg-dist (average-rgba-dist (:data other) data)]
               (or (<= avg-dist tolerance)
                   (do
                     (println (format "Average deviation from %s averages %5.2f > %5.2f" filename avg-dist tolerance))
                     false)))))))

(defn record-image
  "Use this test function to record the image the first time."
  [filename _]
  (fn [other]
      (spit-png filename other)))

(defn shader-test [setup probe & shaders]
  (fn [uniforms args]
      (with-invisible-window
        (let [indices  [0 1 3 2]
              vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
              program  (make-program :vertex [shaders/vertex-passthrough] :fragment (conj shaders (apply probe args)))
              vao      (make-vertex-array-object program indices vertices ["point" 3])
              tex      (texture-render-color 1 1 true
                                             (use-program program)
                                             (apply setup program uniforms)
                                             (render-quads vao))
              img      (rgb-texture->vectors3 tex)]
          (destroy-texture tex)
          (destroy-vertex-array-object vao)
          (destroy-program program)
          (get-vector3 img 0 0)))))
