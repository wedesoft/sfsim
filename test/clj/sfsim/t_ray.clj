;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-ray
  (:require
    [fastmath.vector :refer (vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.ray :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Integrate over a ray"
       (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 0.0 (fn [x] (vec3 2 2 0)))
       => (roughly-vector (vec3 0 0 0) 1e-6)
       (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec3 2 2 0)))
       => (roughly-vector (vec3 6 6 0) 1e-6)
       (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec3 (x 0) (x 0) 0)))
       => (roughly-vector (vec3 10.5 10.5 0) 1e-6)
       (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 2 0 0)} 10 1.5 (fn [x] (vec3 (x 0) (x 0) 0)))
       => (roughly-vector (vec3 10.5 10.5 0) 1e-6))
