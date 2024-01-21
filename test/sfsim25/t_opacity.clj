(ns sfsim25.t-opacity
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim25.opacity :refer :all]))

(mi/collect! {:ns ['sfsim25.util]})
(mi/instrument! {:report (pretty/thrower)})
