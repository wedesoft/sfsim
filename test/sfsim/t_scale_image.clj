(ns sfsim.t-scale-image
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [sfsim.conftest :refer (is-image)]
              [sfsim.image :refer (slurp-image)]
              [sfsim.scale-image :refer :all])
    (:import [java.io File]))

(mi/collect! {:ns ['sfsim.scale-image]})
(mi/instrument! {:report (pretty/thrower)})

(fact "Scale image to 50% size"
      (let [file-name (.getPath (File/createTempFile "scaled" ".png"))]
        (scale-image-file "test/sfsim/fixtures/scale-image/earth.png" file-name)
        (slurp-image file-name) => (is-image "test/sfsim/fixtures/scale-image/earth2.png" 4.0)))
