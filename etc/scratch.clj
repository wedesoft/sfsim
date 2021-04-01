(require '[clojure.core.async :refer (go chan <! >! <!! >!! poll! close!) :as a]
         '[sfsim25.util :refer :all]
         '[sfsim25.vector3 :refer (->Vector3)]
         '[sfsim25.cubemap :refer :all]
         '[sfsim25.quadtree :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode GL11 GL12 GL13 GL15 GL20 GL30 GL32 GL40])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. 640 480))
(Display/create)

(GL11/glClearColor 0.0 0.0 0.0 0.0)
(GL11/glClear (bit-or GL11/GL_COLOR_BUFFER_BIT GL11/GL_DEPTH_BUFFER_BIT))

(Display/update)

(def radius1 6378000.0)
(def radius2 6357000.0)

(def tree-state (chan 1))
(def changes (chan 1))

(def position (atom (->Vector3 (* 3 radius1) 0 0)))
(def tree {})

(go
  (loop [tree (<! tree-state)]
    (when tree
      (let [increase? (partial increase-level? 33 radius1 radius2 1280 60 10 @position)
            drop-list (tiles-to-drop tree increase?)
            load-list (tiles-to-load tree increase?)
            tiles     (load-tiles-data (tiles-meta-data load-list))]
        (>! changes {:drop drop-list :load load-list :tiles tiles})
        (recur (<! tree-state))))))

(def vao (GL30/glGenVertexArrays))
(GL30/glBindVertexArray vao)

(>!! tree-state tree)

(def data (poll! changes))

(def tree (quadtree-add (quadtree-drop tree (:drop data)) (:load data) (:tiles data)))

(Display/destroy)
