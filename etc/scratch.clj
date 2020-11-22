(require '[sfsim25.util :refer :all]
         '[sfsim25.cubemap :refer :all])

(def radius1 6378000.0)
(def radius2 6357000.0)

(defn tile [face level b a]
  (let [j (cube-coordinate level 3 b 1)
        i (cube-coordinate level 3 a 1)
        p (cube-map face j i)
        s (scale-point p radius1 radius2)]
    {:face face :level level :b b :a a :center [(.x s) (.y s) (.z s)]}))

(def tree (mapv #(tile % 0 0 0) (range 6)))

(defn split [{:keys [face level b a]}]
  (vec
    (for [bb [(* 2 b) (inc (* 2 b))] aa [(* 2 a) (inc (* 2 a))]]
      (tile face (inc level) bb aa))))

(def tree2 (update-in tree [2] split))

(def tree3 (update-in tree2 [2 3] split))

(get-in tree3 [2 3 2])
