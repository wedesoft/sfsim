;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-map-tiles
  (:require
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (is-image)]
    [sfsim.image :refer (slurp-image)]
    [sfsim.map-tiles :refer :all])
  (:import
    (java.nio.file
      Files)
    (java.nio.file.attribute
      FileAttribute)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Convert image into set of tiles"
       (let [path (str (Files/createTempDirectory "tiles" (into-array FileAttribute [])))]
         (make-map-tiles "test/clj/sfsim/fixtures/map-tiles/tiles.jpg" 64 5 path 0 0)
         (slurp-image (str path "/5/0/0.png")) => (is-image "test/clj/sfsim/fixtures/map-tiles/top-left.png" 1.0 false)
         (slurp-image (str path "/5/1/0.png")) => (is-image "test/clj/sfsim/fixtures/map-tiles/top-right.png" 1.0 false)
         (slurp-image (str path "/5/0/1.png")) => (is-image "test/clj/sfsim/fixtures/map-tiles/bottom-left.png" 1.0 false)
         (slurp-image (str path "/5/1/1.png")) => (is-image "test/clj/sfsim/fixtures/map-tiles/bottom-right.png" 1.0 false)))
