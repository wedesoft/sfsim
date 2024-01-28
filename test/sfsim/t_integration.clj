(ns sfsim.t-integration
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]))

(mi/collect! {:ns ['sfsim.render]})
(mi/instrument! {:report (pretty/thrower)})
