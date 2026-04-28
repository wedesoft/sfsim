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


(facts "Orbital speed"
       (orbital-speed 1.0 1.0 1.0) => 1.0
       (orbital-speed 4.0 1.0 1.0) => 0.5
       (orbital-speed 1.0 4.0 1.0) => 2.0
       (orbital-speed 1.0 1.0 4.0) => 2.0
       (orbital-speed (+ 6378000.0 160000.0) 5.9722e+24) => (roughly 7808.140 1e-3))


(def test-config
  {:radius 6378000.0
   :orbit 160000.0
   :mass 5.9722e+24
   :dt 1.0
   :max-thrust 20.0
   :timeout 1200.0})


(facts "Launch rocket"
       (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 6378000 0 0) 1e-6)
       (:position (setup test-config :latitude 0.0 :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 6378000 0) 1e-6)
       (:position (setup test-config :latitude (/ PI 2) :longitude 0 :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
       (:position (setup test-config :latitude (/ PI 2) :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
       (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 500.0)) => (roughly-vector (vec3 6378500 0 0) 1e-6)
       (:speed (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 0 0 0) 1e-6)
       (:t (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => 0.0)


(facts "Test gravitation"
       (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                             (assoc test-config :dt 0.0)))
       => (vec3 0.0 0.0 0.0)
       (:position (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                                (assoc test-config :dt 0.0)))
       => (vec3 6378000.0 0.0 0.0)
       (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)} test-config))
       => (roughly-vector (vec3 -9.799 0.0 0.0) 1e-3)
       (:speed (update-state (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                                           test-config) {:control (vec3 0 0 0)} test-config))
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
       => (vec3 10.0 5.0 2.5)
       (:t (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)} test-config))
       => 1.0)


(facts "Convert array to action with length of direction vector as latent variable"
       (action [0 0 0 0]) => {:control (vec3 0 0 0)}
       (action [1 0 0 1]) => {:control (vec3 1 0 0)}
       (action [1 0 0 3]) => {:control (vec3 3 0 0)}
       (action [2 0 0 3]) => {:control (vec3 3 0 0)})


(defn position
  [observation]
  (vec3 (observation 0) (observation 1) (observation 2)))

(defn speed
  [observation]
  (vec3 (observation 3) (observation 4) (observation 5)))

(facts "Observe space craft"
       (position (observation {:position (vec3 6378000 0 0) :speed (vec3 0 0 0)} test-config))
       => (vec3 0.5 0.0 0.0)
       (position (observation {:position (vec3 (+ 6378000 160000) 0 0) :speed (vec3 0 0 0)} test-config))
       => (vec3 1.0 0.0 0.0)
       (position (observation {:position (vec3 0 6378000 0) :speed (vec3 0 0 0)} test-config))
       => (vec3 0.0 0.5 0.0)
       (speed (observation {:position (vec3 0 0 0) :speed (vec3 0 0 0)} test-config))
       => (vec3 0.0 0.0 0.0)
       (speed (observation {:position (vec3 0 0 0) :speed (vec3 7808.140 0 0)} test-config))
       => (roughly-vector (vec3 1.0 0.0 0.0) 1e-6)
       (speed (observation {:position (vec3 0 0 0) :speed (vec3 0 7808.140 0)} test-config))
       => (roughly-vector (vec3 0.0 1.0 0.0) 1e-6))


(fact "An orbit is never finished"
      (done? {} test-config) => false)


(facts "Decide whether a run should be aborted"
       (truncate? {:t 50.0} test-config) => false
       (truncate? {:t 1200.0} test-config) => true)
