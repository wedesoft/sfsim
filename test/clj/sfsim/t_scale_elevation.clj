(ns sfsim.t-scale-elevation
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [sfsim.scale-elevation :refer :all]
              [sfsim.util :refer :all])
    (:import [java.io File]))

(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(def img (short-array [2 3 0 0 5 7 0 0 0 0 0 0 0 0 0 0]))

(fact "Scale short integer data"
      (let [input-file  (.getPath (File/createTempFile "elevation" ".raw"))
            output-file (.getPath (File/createTempFile "elevation" ".raw"))]
        (spit-shorts input-file img)
        (scale-elevation input-file output-file)
        (vec (slurp-shorts output-file)) => [4 0 0 0]))
