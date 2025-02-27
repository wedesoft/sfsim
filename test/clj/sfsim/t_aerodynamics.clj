(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.instrument :as mi]
      [sfsim.aerodynamics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
