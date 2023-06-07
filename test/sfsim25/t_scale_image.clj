(ns sfsim25.t-scale-image
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (is-image)]
              [sfsim25.util :refer (slurp-image)]
              [sfsim25.scale-image :refer :all])
    (:import [java.io File]))

(fact "Scale image to 50% size"
      (let [file-name (.getPath (File/createTempFile "scaled" ".png"))]
        (scale-image-file "test/sfsim25/fixtures/scale-image/earth.png" file-name)
        (slurp-image file-name) => (is-image "test/sfsim25/fixtures/scale-image/earth2.png" 4.0)))
