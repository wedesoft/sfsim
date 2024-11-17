(ns sfsim.t-ray
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [sfsim.conftest :refer (roughly-vector)]
            [fastmath.vector :refer (vec2 vec3)]
            [sfsim.ray :refer :all]))

(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(facts "Integrate over a ray"
  (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 0.0 (fn [x] (vec2 2 2)))
  => (roughly-vector (vec2 0 0) 1e-6)
  (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec2 2 2)))
  => (roughly-vector (vec2 6 6) 1e-6)
  (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec2 (x 0) (x 0))))
  => (roughly-vector (vec2 10.5 10.5) 1e-6)
  (integral-ray #:sfsim.ray{:origin (vec3 2 3 5) :direction (vec3 2 0 0)} 10 1.5 (fn [x] (vec2 (x 0) (x 0))))
  => (roughly-vector (vec2 10.5 10.5) 1e-6))
