(ns sfsim.t-plane
  (:require [midje.sweet :refer :all]
            [malli.instrument :as mi]
            [malli.dev.pretty :as pretty]
            [fastmath.vector :refer (vec3)]
            [sfsim.plane :refer :all]))

(mi/collect! {:ns ['sfsim.plane]})
(mi/instrument! {:report (pretty/thrower)})

(facts "Put plane through three points"
      (let [p (vec3 2 3 5)
            q (vec3 3 3 5)
            r (vec3 2 4 5)]
        (:sfsim.plane/point (points->plane p q r)) => p
        (:sfsim.plane/normal (points->plane p q r)) => (vec3 0 0 1)))

(facts "Compute intersection of ray with plane"
       (ray-plane-intersection #:sfsim.plane{:point (vec3 0 0 0) :normal (vec3 0 0 1)}
                               #:sfsim.ray{:origin (vec3 0 0 0) :direction (vec3 0 0 1)}) => (vec3 0 0 0)
       (ray-plane-intersection #:sfsim.plane{:point (vec3 0 0 -1) :normal (vec3 0 0 1)}
                               #:sfsim.ray{:origin (vec3 0 0 0) :direction (vec3 0 0 1)}) => (vec3 0 0 -1)
       (ray-plane-intersection #:sfsim.plane{:point (vec3 0 0 -1) :normal (vec3 0 0 1)}
                               #:sfsim.ray{:origin (vec3 1 0 0) :direction (vec3 0 0 1)}) => (vec3 1 0 -1)
       (ray-plane-intersection #:sfsim.plane{:point (vec3 0 0 -1) :normal (vec3 0 0 1)}
                               #:sfsim.ray{:origin (vec3 1 0 0) :direction (vec3 0 0 2)}) => (vec3 1 0 -1)
       (ray-plane-intersection #:sfsim.plane{:point (vec3 0 0 -1) :normal (vec3 0 0 2)}
                               #:sfsim.ray{:origin (vec3 1 0 0) :direction (vec3 0 0 1)}) => (vec3 1 0 -1))
