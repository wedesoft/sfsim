(require '[clojure.math :refer (to-radians cos sin PI round floor pow)]
         '[clojure.core.matrix :refer (matrix array dimension-count eseq slice-view add sub mul inverse mmul mget)]
         '[clojure.core.matrix.linear :refer (norm)]
         '[com.climate.claypoole :refer (pfor ncpus)]
         '[comb.template :as template]
         '[sfsim25.render :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 640))
(Display/create)

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

(def size 64)
(def divisions 8)
(def worley (worley-noise divisions size))
(def potential (worley-noise divisions size))
(def octaves1 [0.5 0.25 0.125 0.0625])
(def octaves2 [0.8 0.2])
;(defn worley-octaves [point]
;  (apply + (map-indexed #(* %2 (worley-smooth (mul (pow 2 %1) point))) octaves1)))
;(defn potential-octaves [point]
;  (+ (apply + (map-indexed #(* %2 (potential-smooth (mul (pow 2 %1) point))) octaves2))
;     (* 2 (sin (* (* 1.0 PI) (/ (- (mget point 1) 32) 32))))))

(def texture-octaves
  (template/fn [octaves]
"#version 410 core
float texture_octaves(sampler2D tex, vec2 point)
{
  float result = 0.0;
<% (doseq [multiplier octaves] %>
  result += <%= multiplier %> * texture(tex, point).r;
  point *= 2;
<% ) %>
  return result;
}"))

(def gradient
"#version 410 core
uniform float epsilon;
uniform int size;
vec2 gradient(sampler2D tex, vec2 point)
{
  vec2 delta_x = vec2(epsilon / size, 0);
  vec2 delta_y = vec2(0, epsilon / size);
  float dx = (texture_octaves(tex, point + delta_x) - texture_octaves(tex, point - delta_x)) / (2 * epsilon);
  float dy = (texture_octaves(tex, point + delta_y) - texture_octaves(tex, point - delta_y)) / (2 * epsilon);
  return vec2(dx, dy);
}")

(def epsilon (pow 0.5 3))

;(defn warp [point n scale]
;  (if (zero? n)
;    (max 0.0 (- (* 2.0 (worley-octaves (mul 0.5 point))) 1.0))
;    (let [dx (gradient-x (mul 1.0 point))
;          dy (gradient-y (mul 1.0 point))]
;      (recur (add point (mul scale (matrix [dy (- dx)]))) (dec n) scale))))

(Display/destroy)
