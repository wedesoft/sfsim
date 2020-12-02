(require '[clojure.core.async :refer (go chan <! >! <!! >!! poll!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.vector3 :refer (vector3 norm) :as v]
         '[sfsim25.cubemap :refer :all])

(import '[sfsim25.vector3 Vector3])

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
