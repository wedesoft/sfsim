(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [clojure.math :refer (PI)]
      [sfsim.aerodynamics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Basic drag function"
       ((basic-drag 0.1 2.0) 0.0) => 0.1
       ((basic-drag 0.1 2.0) (/ PI 2)) => 2.0
       ((basic-drag 0.1 2.0) PI) => 0.1)

(facts "Basic lift function"
       ((basic-lift 1.1) 0.0) => 0.0
       ((basic-lift 1.1) (/ PI 4)) => 1.1
       ((basic-lift 1.1) (/ PI 2)) => (roughly 0.0 1e-6))
