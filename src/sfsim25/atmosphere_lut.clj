(ns sfsim25.atmosphere-lut
    "Compute lookup tables for atmospheric scattering"
    (:require [sfsim25.atmosphere :refer :all]))

(defn -main
  "Program to generate lookup tables for atmospheric scattering"
  [& args]
  (when-not (= (count args) 1)
            (.println *err* "Syntax: lein run-atmosphere-lut [size]")
            (System/exit 1))
  (let [size   (nth args 0)
        radius 6378000.0
        height 100000.0]
    0))
