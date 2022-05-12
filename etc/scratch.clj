(require '[clojure.core.matrix :refer (matrix div sub mul add mget) :as m]
         '[clojure.core.matrix.linear :refer (norm)]
         '[clojure.math :refer (PI sqrt pow cos sin)]
         '[com.climate.claypoole :as cp]
         '[gnuplot.core :as g]
         '[sfsim25.atmosphere :refer :all]
         '[sfsim25.ray :refer :all]
         '[sfsim25.sphere :refer (ray-sphere-intersection)]
         '[sfsim25.interpolate :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])

(def d 3)
(def size 64)

(defn points [n] (vec (repeatedly n #(mul size (matrix (repeatedly d rand))))))

(def points1 (points 30))
(def points2 (points 120))

(defn values [points] (vec (cp/pfor (+ 2 (cp/ncpus)) [i (range size) j (range size) k (range size)]
                           (apply min (map (fn [point] (norm (sub point (matrix [i j k])))) points)))))

(defn normed [values] (let [largest (apply max values)] (vec (pmap #(/ (- largest %) largest) values))))

(def values1 (normed (values points1)))
(def values2 (normed (values points2)))

(def mixed (normed (pmap #(* %1 (+ 0.25 (* 0.75 %2))) values1 values2)))

(show-floats {:width size :height size :data (float-array mixed)})
