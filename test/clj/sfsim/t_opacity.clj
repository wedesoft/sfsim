(ns sfsim.t-opacity
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim.opacity :refer :all]))

(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})
