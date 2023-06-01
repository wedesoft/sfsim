(ns sfsim25.t-scale-image
    (:require [midje.sweet :refer :all]
              [sfsim25.conftest :refer (is-image)]
              [sfsim25.util :refer (slurp-image)]
              [sfsim25.scale-image :refer :all]))

(fact "Scale image to 50% size"
      (scale-image (slurp-image "test/sfsim25/fixtures/scale-image/earth.png"))
                   => (is-image "test/sfsim25/fixtures/scale-image/earth2.png" 4.0))
