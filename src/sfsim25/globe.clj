(ns sfsim25.globe
  (:gen-class))

(defn -main
  "Program to generate tiles for cube map"
  [& args]
  (when-not (= (count args) 1)
    (.println *err* "Syntax: lein run-globe [output level]")
    (System/exit 1))
  (let [out-level (Integer/parseInt (nth args 0))
        in-level   out-level
        n          (bit-shift-left 1 out-level)
        tilesize   256]
    (doseq [k (range 6) b (range n) a (range n)]
      (println k b a))))
