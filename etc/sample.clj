(require '[clojure.math :refer (to-radians cos sin PI round floor pow)]
         '[clojure.core.matrix :refer (matrix array dimension-count eseq slice-view add sub mul inverse mmul mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[com.climate.claypoole :refer (pfor ncpus)])

(import '[ij ImagePlus]
        '[ij.process FloatProcessor])

(defn show-floats
  "Open a window displaying the image"
  [{:keys [width height data]}]
  (let [processor (FloatProcessor. width height data)
        img       (ImagePlus.)]
    (.setProcessor img processor)
    (.show img)))

(defn random-point-grid
  "Create a 3D grid with a random point in each cell"
  ([divisions size]
   (random-point-grid divisions size rand))
  ([divisions size random]
   (let [cellsize (/ size divisions)]
     (array
       (map (fn [j]
                (map (fn [i]
                         (add (matrix [(* i cellsize) (* j cellsize)])
                              (matrix (repeatedly 2 #(random cellsize)))))
                     (range divisions)))
            (range divisions))))))

(defn- clipped-index-and-offset
  "Return index modulo dimension of grid and offset for corresponding coordinate"
  [grid size dimension index]
  (let [divisions     (dimension-count grid dimension)
        clipped-index (if (< index divisions) (if (>= index 0) index (+ index divisions)) (- index divisions))
        offset        (if (< index divisions) (if (>= index 0) 0 (- size)) size)]
    [clipped-index offset]))

(defn extract-point-from-grid
  "Extract the random point from the given grid cell"
  [grid size j i]
  (let [[i-clip x-offset] (clipped-index-and-offset grid size 1 i)
        [j-clip y-offset] (clipped-index-and-offset grid size 0 j)]
    (add (slice-view (slice-view grid j-clip) i-clip) (matrix [x-offset y-offset]))))

(defn closest-distance-to-point-in-grid
  "Return distance to closest point in 3D grid"
  [grid divisions size point]
  (let [cellsize (/ size divisions)
        [x y]  (eseq point)
        [i j]  [(quot x cellsize) (quot y cellsize)]
        points   (for [dj [-1 0 1] di [-1 0 1]] (extract-point-from-grid grid size (+ j dj) (+ i di)))]
    (apply min (map #(norm (sub point %)) points))))

(defn normalise-vector
  "Normalise the values of a vector"
  [values]
  (let [maximum (apply max values)]
    (vec (pmap #(/ % maximum) values))))

(defn invert-vector
  "Invert values of a vector"
  [values]
  (mapv #(- 1 %) values))

(defn worley-noise
  "Create 3D Worley noise"
  [divisions size]
  (let [grid (random-point-grid divisions size)]
    (invert-vector
      (normalise-vector
        (for [j (range size) i (range size)]
              (do
                (closest-distance-to-point-in-grid grid divisions size (matrix [(+ j 0.5) (+ i 0.5)]))))))))

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
(defn parcel [size x] (mapv vec (partition size x)))
(def worley (parcel size (worley-noise divisions size)))
(defn worley-smooth [point] (interpolate-value worley point))
(def potential (parcel size (worley-noise divisions size)))
(defn potential-smooth [point] (interpolate-value potential point))
(def octaves1 [0.5 0.25 0.125 0.0625])
(defn worley-octaves [point]
  (apply + (map-indexed #(* %2 (worley-smooth (mul (pow 2 %1) point))) octaves1)))
(def octaves2 [0.8 0.2])
(defn potential-octaves [point]
  (+ (apply + (map-indexed #(* %2 (potential-smooth (mul (pow 2 %1) point))) octaves2))
     (* 2 (cos (* (* 1.5 PI) (/ (- (mget point 1) 32) 32))))))

(def epsilon (pow 0.5 3))
(defn gradient-x [point]
  (* 0.5 (- (potential-octaves (add point (matrix [1 0]))) (potential-octaves (sub point (matrix [1 0]))))))
(defn gradient-y [point]
  (* 0.5 (- (potential-octaves (add point (matrix [0 1]))) (potential-octaves (sub point (matrix [0 1]))))))

(defn warp [point n scale]
  (if (zero? n)
    (max 0.0 (- (* 2.25 (worley-octaves (mul 0.5 point))) 1.25))
    (let [dx (gradient-x (mul 1.0 point))
          dy (gradient-y (mul 1.0 point))]
      (recur (add point (mul scale (matrix [dy (- dx)]))) (dec n) scale))))

(def w 640)

;(show-floats {:width w :height w :data (float-array (pfor (ncpus) [j (range w) i (range w)] (potential-octaves (mul 0.1 (matrix [i j])))))})
;(show-floats {:width w :height w :data (float-array (pfor (ncpus) [j (range w) i (range w)] (worley-octaves (mul 0.1 (matrix [i j])))))})

(show-floats {:width w :height w :data (float-array (pfor (ncpus) [j (range w) i (range w)] (warp (mul 0.1 (matrix [i j])) 15 10)))})
