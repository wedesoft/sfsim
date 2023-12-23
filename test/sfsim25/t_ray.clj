(ns sfsim25.t-ray
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [sfsim25.conftest :refer (roughly-vector)]
            [fastmath.vector :refer (vec2 vec3)]
            [sfsim25.ray :refer :all]))

(mi/collect! {:ns ['sfsim25.ray]})
(mi/instrument!)

(facts "Integrate over a ray"
  (integral-ray #:sfsim25.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 0.0 (fn [x] (vec2 2 2)))
  => (roughly-vector (vec2 0 0) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec2 2 2)))
  => (roughly-vector (vec2 6 6) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (vec3 2 3 5) :direction (vec3 1 0 0)} 10 3.0 (fn [x] (vec2 (x 0) (x 0))))
  => (roughly-vector (vec2 10.5 10.5) 1e-6)
  (integral-ray #:sfsim25.ray{:origin (vec3 2 3 5) :direction (vec3 2 0 0)} 10 1.5 (fn [x] (vec2 (x 0) (x 0))))
  => (roughly-vector (vec2 10.5 10.5) 1e-6))
