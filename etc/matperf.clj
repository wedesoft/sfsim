(require '[clojure.reflect :as r])
(import '[org.ejml.simple SimpleMatrix])

(def m (SimpleMatrix. 3 3 true (float-array [1 0 0 0 2 0 0 0 4])))
(def v (SimpleMatrix. 3 1 true (float-array [1 2 3])))
(.invert m)
(.mult m v)
