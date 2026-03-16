;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-sphere
  (:require
    [clojure.math :refer (PI)]
    [fastmath.vector :refer (vec2 vec3)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.sphere :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Determine height above surface for given point"
       (height #:sfsim.sphere{:centre (vec3 0 0 0) :radius 10.0} (vec3 10 0 0)) => 0.0
       (height #:sfsim.sphere{:centre (vec3 0 0 0) :radius 10.0} (vec3 13 0 0)) => 3.0
       (height #:sfsim.sphere{:centre (vec3 2 0 0) :radius 10.0} (vec3 13 0 0)) => 1.0)


(facts "Check ray for intersection with sphere"
       (let [sphere #:sfsim.sphere{:centre (vec3 0 0 3) :radius 1.0}]
         (ray-intersects-sphere? sphere #:sfsim.ray{:origin (vec3 -2 0 3) :direction (vec3 0 1 0)}) => false
         (ray-intersects-sphere? sphere #:sfsim.ray{:origin (vec3 -2 0 3) :direction (vec3 1 0 0)}) => true))


(facts "Compute intersection of line with sphere or closest point with sphere"
       (let [sphere #:sfsim.sphere{:centre (vec3 0 0 3) :radius 1.0}]
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3 -2 0 3) :direction (vec3 0 1 0)})
         => {:sfsim.intersection/distance 0.0 :sfsim.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3 -2 0 3) :direction (vec3 1 0 0)})
         => {:sfsim.intersection/distance 1.0 :sfsim.intersection/length 2.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3  0 0 3) :direction (vec3 1 0 0)})
         => {:sfsim.intersection/distance 0.0 :sfsim.intersection/length 1.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3  2 0 3) :direction (vec3 1 0 0)})
         => {:sfsim.intersection/distance 0.0 :sfsim.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3 -2 0 3) :direction (vec3 2 0 0)})
         => {:sfsim.intersection/distance 0.5 :sfsim.intersection/length 1.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3 -5 0 0) :direction (vec3 1 0 0)})
         => {:sfsim.intersection/distance 5.0 :sfsim.intersection/length 0.0}
         (ray-sphere-intersection sphere #:sfsim.ray{:origin (vec3  5 0 0) :direction (vec3 1 0 0)})
         => {:sfsim.intersection/distance 0.0 :sfsim.intersection/length 0.0}))


(facts "Check ray pointing downwards"
       (let [sphere #:sfsim.sphere{:centre (vec3 3 2 1) :radius 1.0}]
         (ray-pointing-downwards sphere #:sfsim.ray {:origin (vec3 5 2 1) :direction (vec3 -1 0 0)}) => true
         (ray-pointing-downwards sphere #:sfsim.ray {:origin (vec3 5 2 1) :direction (vec3  1 0 0)}) => false
         (ray-pointing-downwards sphere #:sfsim.ray {:origin (vec3 2 2 1) :direction (vec3  1 0 0)}) => true))


(facts "Integrate over a circle"
       (integrate-circle 64 (fn [x] (vec3 0 0 0))) => (roughly-vector (vec3 0 0 0) 1e-6)
       (integrate-circle 64 (fn [x] (vec3 1 1 1))) => (roughly-vector (vec3 (* 2 PI) (* 2 PI) (* 2 PI)) 1e-6))


(facts "Integrate over half unit sphere"
       (let [left (vec3 1 0 0)
             up   (vec3 0 1 0)]
         (integral-half-sphere 64 left (fn [v] (vec2 0 0))) => (roughly-vector (vec2 0 0) 1e-6)
         (integral-half-sphere 64 left (fn [v] (vec2 1 1))) => (roughly-vector (vec2 (* 2 PI) (* 2 PI)) 1e-6)
         (integral-half-sphere 64 left (fn [v] (vec3 1 (v 1) (v 2)))) => (roughly-vector (vec3 (* 2 PI) 0 0) 1e-6)
         (integral-half-sphere 64 up (fn [v] (vec3 (v 0) 1 (v 2)))) => (roughly-vector (vec3 0 (* 2 PI) 0) 1e-6)))


(facts "Integrate over unit sphere"
       (let [left (vec3 1 0 0)]
         (integral-sphere 64 left (fn [v] (vec2 0 0))) => (roughly-vector (vec2 0 0) 1e-6)
         (integral-sphere 64 left (fn [v] (vec2 1 1))) => (roughly-vector (vec2 (* 4 PI) (* 4 PI)) 1e-6)))
