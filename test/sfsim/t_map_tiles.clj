(ns sfsim.t-map-tiles
    (:require [midje.sweet :refer :all]
              [malli.instrument :as mi]
              [malli.dev.pretty :as pretty]
              [sfsim.conftest :refer (is-image)]
              [sfsim.image :refer (slurp-image)]
              [sfsim.map-tiles :refer :all])
    (:import [java.nio.file Files]
             [java.nio.file.attribute FileAttribute]))

(mi/collect! {:ns ['sfsim.map-tiles]})
(mi/instrument! {:report (pretty/thrower)})

(facts "Convert image into set of tiles"
       (let [path (str (Files/createTempDirectory "tiles" (into-array FileAttribute [])))]
         (make-map-tiles "test/sfsim/fixtures/map-tiles/tiles.jpg" 64 5 path 0 0)
         (slurp-image (str path "/5/0/0.png")) => (is-image "test/sfsim/fixtures/map-tiles/top-left.png" 1.0)
         (slurp-image (str path "/5/1/0.png")) => (is-image "test/sfsim/fixtures/map-tiles/top-right.png" 1.0)
         (slurp-image (str path "/5/0/1.png")) => (is-image "test/sfsim/fixtures/map-tiles/bottom-left.png" 1.0)
         (slurp-image (str path "/5/1/1.png")) => (is-image "test/sfsim/fixtures/map-tiles/bottom-right.png" 1.0)))
