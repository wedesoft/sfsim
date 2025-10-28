(require '[clojure.repl :refer (source source-fn)]
         '[sfsim.quaternion :as q]
         '[rewrite-clj.zip :as z])

(source q/+)

(binding [*ns* (the-ns 'sfsim.quaternion)]
         (read-string (source-fn '+)))
(binding [*ns* (the-ns 'sfsim.quaternion)]
         (read-string "::k"))

(z/of-string (source-fn 'q/+))
