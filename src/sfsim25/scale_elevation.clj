(ns sfsim25.scale-elevation
  (:require [sfsim25.util :refer (slurp-shorts spit-shorts)])
  (:gen-class))

(defn -main
  "Program to scale elevation images"
  [& args]
  (when-not (= (count args) 2)
    (.println *err* "Syntax: lein run-scale-elevation [input data] [output data]")
    (System/exit 1))
  (let [[input-data
         output-data] args
        data          (slurp-shorts input-data)
        n             (alength ^shorts data)
        w             (int (Math/sqrt n))
        size          (/ w 2)
        result        (short-array (* size size))]
    (doseq [^int j (range size) ^int i (range size)]
      (let [offset (+ (* j 2 w) (* i 2))
            x1 (aget ^shorts data (+ offset))
            x2 (aget ^shorts data (+ offset 1))
            x3 (aget ^shorts data (+ offset w))
            x4 (aget ^shorts data (+ offset w 1))]
        (aset ^shorts result (+ (* j size) i) (short (/ (+ x1 x2 x3 x4) 4)))))
    (spit-shorts output-data result)))
