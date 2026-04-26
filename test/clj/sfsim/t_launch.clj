(ns sfsim.t-launch
  (:require
    [clojure.math :refer (PI)]
    [midje.sweet :refer :all]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [fastmath.vector :refer (vec3)]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.launch :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(def test-config
  {:radius 6378000.0})


(fact "Launch rocket"
      (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 6378000 0 0) 1e-6)
      (:position (setup test-config :latitude 0.0 :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 6378000 0) 1e-6)
      (:position (setup test-config :latitude (/ PI 2) :longitude 0 :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
      (:position (setup test-config :latitude (/ PI 2) :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
      (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 500.0)) => (roughly-vector (vec3 6378500 0 0) 1e-6)
      (:speed (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 0 0 0) 1e-6))
