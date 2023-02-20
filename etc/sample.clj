(require '[clojure.math :refer (to-radians cos sin PI round floor pow)]
         '[clojure.core.matrix :refer (matrix add mul inverse mmul mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[sfsim25.worley :refer :all]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.util :refer :all])

(defn mix
  "Linear mixing of values"
  [a b ^double scalar]
  (add (mul (- 1 scalar) a) (mul scalar b)))

(defn interpolate-value
  "Linear interpolation for point in table"
  [lookup-table ^clojure.lang.PersistentVector point]
  (if (seq point)
    (let [size       (count lookup-table)
          [c & args] point
          i          (mod c size)
          u          (floor i)
          v          (mod (inc u) size)
          s          (- i u)]
      (mix (interpolate-value (nth lookup-table u) args) (interpolate-value (nth lookup-table v) args) s))
    lookup-table))

(def size 64)
(def divisions 8)
(def data (worley-noise divisions size true))
(defn parcel [size x] (mapv vec (partition size x)))
(def worley (mapv #(parcel size %) (parcel (* size size) data)))
(defn worley-smooth [point] (interpolate-value worley point))
(def octaves [0.5 0.25 0.125 0.0625])
(defn worley-octaves [point]
  (apply + (map-indexed #(* %2 (worley-smooth (mul (pow 2 %1) point))) octaves)))

(def w 640)
(show-floats {:width w :height w :data (float-array (for [j (range w) i (range w)] (worley-octaves (mul 0.1 (matrix [i j 0])))))})
