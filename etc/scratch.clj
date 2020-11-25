(require '[clojure.core.async :refer :all]
         '[sfsim25.util :refer :all]
         '[sfsim25.cubemap :refer :all])

(def radius1 6378000.0)
(def radius2 6357000.0)

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

(def tree (agent (mapv #(tile % 0 0 0) (range 6))))

(send tree #(update-in % [2] split))

(send tree #(update-in % [2 3] split))

(send tree #(assoc-in % [2 3] (tile 2 1 1 1)))

(await tree); Wait for at least 1/30 seconds?

(get-in @tree [2 3])
