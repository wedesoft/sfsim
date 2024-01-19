(ns sfsim25.t-opacity
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim25.opacity :refer :all]))

(mi/collect! {:ns ['sfsim25.util]})
(mi/instrument! {:report (pretty/thrower)})

(facts "Maximum shadow depth for cloud shadows"
       (shadow-depth 4.0 1.0 0.0) => 3.0
       (shadow-depth 3.0 2.0 0.0) => 4.0
       (shadow-depth 4.0 0.0 1.0) => 3.0
       (shadow-depth 4.0 1.0 1.0) => 6.0)
