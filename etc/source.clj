(require '[clojure.repl :refer (source source-fn)]
         '[sfsim.quaternion :as q]
         '[clojure.edn :as edn])

(source q/+)
(read-string (source-fn 'q/+))
