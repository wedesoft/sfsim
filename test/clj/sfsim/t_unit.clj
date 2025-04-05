(ns sfsim.t-unit
    (:require
      [midje.sweet :refer :all]
      [sfsim.units :refer :all]))


(fact "Convert lb/ft^2 to N/m^2"
      (/ pound-force (* foot foot)) => (roughly 47.880258888889 1e-6))
