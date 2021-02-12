(require '[clojure.core.async :refer (go chan <! >! <!! >!! poll!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.cubemap :refer :all])

(def radius1 6378000.0)
(def radius2 6357000.0)

(defn dy [index]
  (case index
    0 0
    1 0
    2 1
    3 1))

(defn dx [index]
  (case index
    0 0
    1 1
    2 0
    3 1))

(defn y-tile [path] (reduce #(+ (* 2 %1) %2) 0 (map dy path)))
(defn x-tile [path] (reduce #(+ (* 2 %1) %2) 0 (map dx path)))

(defn center [[face & path]]
  (let [b     (y-tile path)
        a     (x-tile path)
        level (count path)
        j     (cube-coordinate level 3 b 1)
        i     (cube-coordinate level 3 a 1)
        p     (cube-map face j i)]
    (scale-point p radius1 radius2)))

(center [5 0])
(center [5 1])
(center [5 2])
(center [5 3])

(defn tree-file [[face & path] suffix]
  (let [b     (y-tile path)
        a     (x-tile path)
        level (count path)]
    (cube-path "globe" face level b a suffix)))

(def tiles (chan 8))
(def requests (chan 8))

(defn sub-paths [path]
  (if (empty? path)
    (map vector (range 6))
    (map (partial conj path) (range 4))))

(go
  (loop [request (<! requests)]
    (when request
      (if (:increase request)
        (>! tiles {:path (:path request) :images (mapv #(slurp-image (tree-file % ".png")) (sub-paths (:path request)))})
        (>! tiles {:path (:path request) :images (slurp-image (tree-file (:path request) ".png"))}))
      (recur (<! requests)))))

(def center-tree (atom (mapv center (sub-paths []))))

(>!! requests {:increase true :path []})

(def map-tree (atom (:images (<!! tiles))))

(defn increase-resolution [path]
  (swap! center-tree assoc-in path (mapv center (sub-paths path)))
  (>!! requests {:path path :increase true}))

(defn decrease-resolution [path]
  (swap! center-tree assoc-in path (center path))
  (>!! requests {:path path :increase false}))

(defn update-map []
  (loop [data (poll! tiles)]
    (when data
      (swap! map-tree assoc-in (:path data) (:images data))
      (recur (poll! tiles)))))

@map-tree
(increase-resolution [1])
(update-map)
(get-in @map-tree [1])
(decrease-resolution [1])
(update-map)
(get-in @map-tree [1])

(def running (atom true))

(defn load-tile [x] (* x x))

(go
  (loop [request (<! requests)]
    (when-not (nil? request)
      (>! tiles (load-tile request))
      (recur (<! requests)))))

(go
  (loop [detail-tree {}]
    (when @running
      (let [tiles-needed (first-tiles-needed @position detail-tree)]
        (doseq [request tiles-needed] (>! requests request))
        (recur (update-tree detail-tree tiles-needed))))))

(while @running
  (loop [tile (poll! tiles)]
    (when-not (nil? tile)
      (swap! tree update-in (path tile) tile)
      (recur (poll! tiles)))))


; --------------------------------------------------------------------------
(require '[sfsim25.util :refer :all])
(def in-level 2)
(def out-level (dec in-level))
(def n (bit-shift-left 1 out-level))
(def tilesize 33)
(def subtilesize (/ (inc tilesize) 2))

(doseq [k (range 6) b (range n) a (range n)]
  (let [tile [tilesize tilesize (byte-array (* 3 tilesize tilesize))]]
    (doseq [j (range 2) i (range 2)]
      (let [subtile (slurp-image (cube-path "globe" k in-level (+ (* 2 b) j) (+ (* 2 a) i) ".png"))]
        (doseq [y (range subtilesize) x (range subtilesize)]
          (set-pixel! tile (+ (* (dec subtilesize) j) y) (+ (* (dec subtilesize) i) x) (get-pixel subtile (* 2 y) (* 2 x))))))
    (println (cube-path "globe" k out-level b a ".png"))))


(defn t [s a b]
  (concat (map (vec a) (range 0 tilesize 2)) (map (vec b) (range 2 tilesize 2))))

(t 3 [1 2 3] [3 4 5])

(def a [ 1  2  3,  6  7  8, 11 12 13])
(def b [ 3  4  5,  8  9 10, 13 14 15])
(def c [11 12 13, 16 17 18, 21 22 23])
(def d [13 14 15, 18 19 20, 23 24 25])

(mapcat (partial t 3) (t 3 (partition 3 a) (partition 3 c)) (t 3 (partition 3 b) (partition 3 d)))
