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

(defn center [face & path]
  (let [b     (y-tile path)
        a     (x-tile path)
        level (count path)
        j     (cube-coordinate level 3 b 1)
        i     (cube-coordinate level 3 a 1)
        p     (cube-map face j i)]
    (scale-point p radius1 radius2)))

(defn tile [face level b a]
  (let [j (cube-coordinate level 3 b 1)
        i (cube-coordinate level 3 a 1)
        p (cube-map face j i)
        s (scale-point p radius1 radius2)]
    {:face face :level level :b b :a a :center [(.x s) (.y s) (.z s)]}))

(defn split [{:keys [face level b a]}]
  (vec
    (for [bb [(* 2 b) (inc (* 2 b))] aa [(* 2 a) (inc (* 2 a))]]
      (tile face (inc level) bb aa))))

(def running (atom true))

(def tiles (chan 8))
(def requests (chan))

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

(def tree (agent (mapv #(tile % 0 0 0) (range 6))))

(send tree #(update-in % [2] split))

(send tree #(update-in % [2 3] split))

(send tree #(assoc-in % [2 3] (tile 2 1 1 1)))

(await tree); Wait for at least 1/30 seconds?

(get-in @tree [2 3])
