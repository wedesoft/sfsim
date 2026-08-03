;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-plane
  (:require
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.plane :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Put plane through three points"
       (let [p (vec3 2 3 5)
             q (vec3 3 3 5)
             r (vec3 2 4 5)]
         (:sfsim.plane/point (points->plane p q r)) => p
         (:sfsim.plane/normal (points->plane p q r)) => (vec3 0 0 1)))
