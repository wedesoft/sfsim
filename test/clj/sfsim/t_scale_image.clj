(ns sfsim.t-scale-image
  (:require
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (is-image)]
    [sfsim.image :refer (slurp-image)]
    [sfsim.scale-image :refer :all])
  (:import
    (java.io
      File)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(fact "Scale image to 50% size"
      (let [file-name (.getPath (File/createTempFile "scaled" ".png"))]
        (scale-image-file "test/clj/sfsim/fixtures/scale-image/earth.png" file-name)
        (slurp-image file-name) => (is-image "test/clj/sfsim/fixtures/scale-image/earth2.png" 4.0 false)))
