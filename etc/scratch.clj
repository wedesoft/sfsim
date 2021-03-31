(require '[clojure.core.async :refer (go chan <! >! <!! >!! poll! close!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.vector3 :refer (->Vector3)]
         '[sfsim25.cubemap :refer :all]
         '[sfsim25.quadtree :refer :all])

(def radius1 6378000.0)
(def radius2 6357000.0)

(def tree-state (chan 1))
(def changes (chan 1))

(def position (atom (->Vector3 (* 3 radius1) 0 0)))
(def tree {})

(>!! tree-state tree)

(go
  (loop [tree (<! tree-state)]
    (when tree
      (let [increase? (partial increase-level? 33 radius1 radius2 1280 60 10 @position)
            drop-list (tiles-to-drop tree increase?)
            load-list (tiles-to-load tree increase?)
            tiles     (load-tiles-data (map tile-meta-data load-list))]
        (>! changes {:drop drop-list :load load-list :tiles tiles})
        (recur (<! tree-state))))))
