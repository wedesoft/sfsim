(ns sfsim.t-launch
  (:require
    [clojure.math :refer (PI atan2 hypot to-radians cos sin sqrt)]
    [midje.sweet :refer :all]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [fastmath.vector :refer (vec3 mag dot)]
    [sfsim.conftest :refer (roughly-vector)]
    [sfsim.aerodynamics :as aerodynamics]
    [sfsim.atmosphere :as atmosphere]
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
   :planet-mass 5.9722e+24
   :mass 100000.0
   :dt 1.0
   :max-thrust 2000000.0
   :timeout 1200.0
   :initial-delta-v 20000.0
   :free-delta-v 5000.0
   :weight-height-reward 1.0
   :weight-speed-reward 1.0
   :weight-fuel-reward 1.0})


(facts "Launch rocket"
       (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 6378000 0 0) 1e-6)
       (:position (setup test-config :latitude 0.0 :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 6378000 0) 1e-6)
       (:position (setup test-config :latitude (/ PI 2) :longitude 0 :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
       (:position (setup test-config :latitude (/ PI 2) :longitude (/ PI 2) :height 0.0)) => (roughly-vector (vec3 0 0 6378000) 1e-6)
       (:position (setup test-config :latitude 0.0 :longitude 0.0 :height 500.0)) => (roughly-vector (vec3 6378500 0 0) 1e-6)
       (:speed (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => (roughly-vector (vec3 0 0 0) 1e-6)
       (:t (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => 0.0
       (:delta-v (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0)) => 20000.0)


(defn zero-lift
  ^double [_linear-speed _rotation ^double _speed-of-sound ^double _density]
  0.0)


(facts "Test gravitation. thrust, and aerodynamics"
       (with-redefs [aerodynamics/drag (fn [_linear-speed _speed-of-sound _density _gear _air-brake] 0.0)
                     aerodynamics/lift zero-lift]
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
                               (assoc test-config :planet-mass 0.0)))
         => (vec3 0.0 0.0 0.0)
         (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0.5 0.25 0.125)}
                               (assoc test-config :planet-mass 0.0 :max-thrust 100000.0)))
         => (vec3 0.5 0.25 0.125)
         (:speed (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0.5 0.25 0.125)}
                               (assoc test-config :planet-mass 0.0)))
         => (vec3 10.0 5.0 2.5)
         (:delta-v (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)}
                                 test-config))
         => 20000.0
         (:delta-v (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 1 0 0)}
                                 test-config))
         => 19980.0
         (:t (update-state (setup test-config :latitude 0.0 :longitude 0.0 :height 0.0) {:control (vec3 0 0 0)} test-config))
         => 1.0)
       (with-redefs [aerodynamics/drag (fn [_linear-speed _speed-of-sound _density _gear _air-brake] 200000.0)
                     aerodynamics/lift zero-lift]
         (:speed (update-state {:position (vec3 0 0 6378000) :speed (vec3 100 0 0) :t 0.0 :delta-v 20000.0} {:control (vec3 0 0 0)}
                               (assoc test-config :planet-mass 0.0)))
         => (vec3 98 0 0)))


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


(tabular "Nominal orbit speed for position"
         (let [[v1 v2] (orbital-vector {:position ?position} (to-radians ?inclination))]
           (mag v1) => (roughly 1.0 1e-6)
           (mag v2) => (roughly 1.0 1e-6)
           (dot ?position v1) => (roughly 0.0 1e-6)
           (dot ?position v2) => (roughly 0.0 1e-6)
           (atan2 (hypot (v1 0) (v1 1)) (v1 2)) => (roughly (to-radians ?actual-inclination) 1e-6)
           (atan2 (hypot (v2 0) (v2 1)) (v2 2)) => (roughly (to-radians ?actual-inclination) 1e-6))
         ?position                       ?inclination  ?actual-inclination
         (vec3 6378000       0        0)   0.0           0.0
         (vec3       0 6378000        0)   0.0           0.0
         (vec3 6378000       0        0)  90.0          90.0
         (vec3       0 6378000        0)  90.0          90.0
         (vec3 6378000       0        0) 180.0         180.0
         (vec3 6378000       0        0)  45.0          45.0
         (vec3 5523510       0  3189000)  45.0          45.0
         (vec3 5523510       0  3189000) 135.0         135.0
         (vec3 5523510       0 -3189000)  45.0          45.0
         (vec3 5523510       0 -3189000) 135.0         135.0
         (vec3 5523510       0  3189000)  20.0          30.0
         (vec3 5523510       0  3189000) 160.0         150.0
         (vec3 5523510       0 -3189000)  20.0          30.0
         (vec3 5523510       0 -3189000) 160.0         150.0)


(facts "Orientation of space craft depending on speed and thrust vector"
       (forward {:speed (vec3 1 0 0)} {:control (vec3 0 1 0)}) => (roughly-vector (vec3 0 1 0) 1e-6)
       (forward {:speed (vec3 1 0 0)} {:control (vec3 0 2 0)}) => (roughly-vector (vec3 0 1 0) 1e-6)
       (forward {:speed (vec3 1 0 0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 1 0 0) 1e-6)
       (forward {:speed (vec3 2 0 0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 1 0 0) 1e-6)
       (forward {:speed (vec3 0 0 0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 0 0 1) 1e-6)
       (up {:speed (vec3  0 -1  0)} {:control (vec3 1 0 0)}) => (roughly-vector (vec3 0  1 0) 1e-6)
       (up {:speed (vec3  0  1  0)} {:control (vec3 1 0 0)}) => (roughly-vector (vec3 0 -1 0) 1e-6)
       (up {:speed (vec3  0 -2  0)} {:control (vec3 1 0 0)}) => (roughly-vector (vec3 0  1 0) 1e-6)
       (up {:speed (vec3  3  0 -1)} {:control (vec3 1 0 0)}) => (roughly-vector (vec3 0  0 1) 1e-6)
       (up {:speed (vec3  3  0 -1)} {:control (vec3 2 0 0)}) => (roughly-vector (vec3 0  0 1) 1e-6)
       (up {:speed (vec3 -1  0  3)} {:control (vec3 0 0 1)}) => (roughly-vector (vec3 1  0 0) 1e-6)
       (up {:speed (vec3  1  0  0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 0  0 1) 1e-6)
       (up {:speed (vec3  2  0  0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 0  0 1) 1e-6)
       (up {:speed (vec3  0  0  1)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 0  1 0) 1e-6)
       (up {:speed (vec3  0  0  0)} {:control (vec3 0 0 0)}) => (roughly-vector (vec3 1  0 0) 1e-6))


(defn lift-mock
  ^double [{:sfsim.aerodynamics/keys [alpha speed]} rotation ^double speed-of-sound ^double density]
  (* density (/ speed speed-of-sound) (* 1000000.0 (sin (* 2 alpha)))))

(defn temperature-mock
  ^double [^double _height]
  300.0)

(defn speed-of-sound-mock
  ^double [^double _temperature]
  400.0)

(defn density-mock
  ^double [^double _height]
  1.0)

(facts "Determine drag and lift"
       (with-redefs [aerodynamics/drag (fn [{:sfsim.aerodynamics/keys [alpha speed]} speed-of-sound density _gear _air-brake]
                                           (* density (/ speed speed-of-sound) (+ (* 200000.0 (cos alpha)) (* 400000.0 (sin alpha)))))
                     aerodynamics/lift lift-mock
                     atmosphere/temperature-at-height temperature-mock
                     atmosphere/speed-of-sound speed-of-sound-mock
                     atmosphere/density-at-height density-mock]
         ((drag-and-lift {:control (vec3 1 0 0)} test-config) (vec3 0 0 6378000) (vec3 0 0 0))
         => (roughly-vector(vec3 0 0 0) 1e-6)
         ((drag-and-lift  {:control (vec3 1 0 0)} test-config) (vec3 0 0 6378000) (vec3 100 0 0))
         => (roughly-vector (vec3 -0.5 0 0) 1e-6)
         ((drag-and-lift {:control (vec3 0 0 1)} test-config) (vec3 0 0 6378000) (vec3 -100 0 0))
         => (roughly-vector (vec3 1.0 0 0) 1e-6)
         ((drag-and-lift {:control (vec3 -1 0 0)} test-config) (vec3 0 0 6378000) (vec3 -100 0 0))
         => (roughly-vector (vec3 0.5 0 0) 1e-6)
         ((drag-and-lift {:control (vec3 1 0 1)} test-config) (vec3 0 0 6378000) (vec3 100 0 0))
         => (roughly-vector(vec3 (- (sqrt 1.125)) 0 2.5) 1e-6)
         ((drag-and-lift {:control (vec3 1 1 0)} test-config) (vec3 0 0 6378000) (vec3 100 0 0))
         => (roughly-vector(vec3 (- (sqrt 1.125)) 2.5 0) 1e-6)))


(facts "Penalise height deviations"
       (reward-height {:position (vec3 6378000 0 0)} test-config) => -1.0
       (reward-height {:position (vec3 6538000 0 0)} test-config) => 0.0
       (reward-height {:position (vec3 6698000 0 0)} test-config) => -1.0)


(facts "Penalise deviation from desired speed vector"
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 0 0)} test-config 0.0) => (roughly -1.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 7809.447 0)} test-config 0.0) => (roughly 0.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 -7809.447 0)} test-config 0.0) => (roughly -2.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 -7809.447 0)} test-config PI) => (roughly 0.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 7809.447 0)} test-config PI) => (roughly -2.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 0 7809.447)} test-config (/ PI 2)) => (roughly 0.0 1e-3)
       (reward-speed {:position (vec3 6538000 0 0) :speed (vec3 0 0 -7809.447)} test-config (/ PI 2)) => (roughly 0.0 1e-3))


(facts "Penalise fuel use"
       (reward-fuel {:delta-v 20000.0} test-config) => 0.0
       (reward-fuel {:delta-v 15000.0} test-config) => 0.0
       (reward-fuel {:delta-v     0.0} test-config) => -1.0
       (reward-fuel {:delta-v  7500.0} test-config) => -0.5)


(facts "Overall reward function"
       (reward {:position (vec3 6378000 0 0) :speed (vec3 0 7809.447 0) :delta-v 20000.0} test-config)
       => (roughly -1.0 1e-3)
       (reward {:position (vec3 6378000 0 0) :speed (vec3 0 7809.447 0) :delta-v 20000.0}
               (assoc test-config :weight-height-reward 0.5))
       => (roughly -0.5 1e-3)
       (reward {:position (vec3 6538000 0 0) :speed (vec3 0 0 0) :delta-v 20000.0} test-config)
       => (roughly -1.0 1e-3)
       (reward {:position (vec3 6538000 0 0) :speed (vec3 0 0 0) :delta-v 20000.0}
               (assoc test-config :weight-speed-reward 0.5))
       => (roughly -0.5 1e-3)
       (reward {:position (vec3 6538000 0 0) :speed (vec3 0 7809.447 0) :delta-v 0.0} test-config)
       => (roughly -1.0 1e-3)
       (reward {:position (vec3 6538000 0 0) :speed (vec3 0 7809.447 0) :delta-v 0.0} (assoc test-config :weight-fuel-reward 0.5))
       => (roughly -0.5 1e-3))
