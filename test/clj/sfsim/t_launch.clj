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
  {:radius 6378000.0
   :mass 5.9722e+24
   :dt 1.0
   :max-thrust 20.0})


(fact "Launch rocket"
      (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 6378000 0 0) 1e-6)
      (:position (setup test-config :latitude 0.0 :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 6378000 0) 1e-6)
      (:position (setup test-config :latitude (/ PI 2) :longitude 0 :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
      (:position (setup test-config :latitude (/ PI 2) :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
      (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 500.0)) => (roughly-vector (vec3 6378500 0 0) 1e-6)
      (:speed (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 0 0 0) 1e-6))


(fact "Test gravitation"
      (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                            (assoc test-config :dt 0.0)))
      => (vec3 0.0 0.0 0.0)
      (:position (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                               (assoc test-config :dt 0.0)))
      => (vec3 6378000.0 0.0 0.0)
      (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                            test-config))
      => (roughly-vector (vec3 -9.799 0.0 0.0) 1e-3)
      (:speed (update-state (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)
                                          {:control (vec3 0 0 0)} test-config) {:control (vec3 0 0 0)} test-config))
      => (roughly-vector (vec3 -19.598 0.0 0.0) 1e-3)
      (:position (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                               test-config))
      => (roughly-vector (vec3 (- 6378000.0 4.899) 0.0 0.0) 1e-3)
      (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                            (assoc test-config :mass 0.0)))
      => (vec3 0.0 0.0 0.0)
      (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0.5 0.25 0.125)}
                            (assoc test-config :mass 0.0 :max-thrust 1.0)))
      => (vec3 0.5 0.25 0.125)
      (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0.5 0.25 0.125)}
                            (assoc test-config :mass 0.0)))
      => (vec3 10.0 5.0 2.5))
