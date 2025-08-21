;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-physics
  (:require
    [clojure.math :refer (exp to-radians)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [fastmath.matrix :refer (inverse mulv)]
    [fastmath.vector :refer (vec3)]
    [sfsim.conftest :refer (roughly-vector roughly-quaternion)]
    [sfsim.quaternion :as q]
    [sfsim.matrix :as matrix]
    [sfsim.jolt :as jolt]
    [sfsim.astro :as astro]
    [sfsim.physics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(jolt/jolt-init)


(def add (fn [x y] (+ x y)))
(def scale (fn [x s] (* x s)))


(facts "Euler integration method"
       (euler 42.0 1.0 (fn [_y _dt] 0.0) add scale) => 42.0
       (euler 42.0 1.0 (fn [_y _dt] 5.0) add scale) => 47.0
       (euler 42.0 2.0 (fn [_y _dt] 5.0) add scale) => 52.0
       (euler 42.0 2.0 (fn [_y dt] dt) add scale) => 46.0)


(facts "Runge-Kutta integration method"
       (runge-kutta 42.0 1.0 (fn [_y _dt] 0.0) add scale) => 42.0
       (runge-kutta 42.0 1.0 (fn [_y _dt] 5.0) add scale) => 47.0
       (runge-kutta 42.0 2.0 (fn [_y _dt] 5.0) add scale) => 52.0
       (runge-kutta 42.0 1.0 (fn [_y dt] (* 2.0 dt)) add scale) => 43.0
       (runge-kutta 42.0 2.0 (fn [_y dt] (* 2.0 dt)) add scale) => 46.0
       (runge-kutta 42.0 1.0 (fn [_y dt] (* 3.0 dt dt)) add scale) => 43.0
       (runge-kutta 1.0 1.0 (fn [y _dt] y) add scale) => (roughly (exp 1) 1e-2))


(defn linear-motion
  [y _dt]
  {:sfsim.physics/position (:sfsim.physics/speed y) :sfsim.physics/speed 0.0})


(def add-values (fn [x y] (merge-with + x y)))
(def scale-values (fn [x s] (into {} (for [[k v] x] [k (* v s)]))))


(defn matched-euler
  [y0 dt [dv0 dv1]]
  (-> y0
      (update :sfsim.physics/speed #(add dv0 %))
      (euler dt linear-motion add-values scale-values)
      (update :sfsim.physics/speed #(add dv1 %))))


(facts "Sanity check for euler test function"
       (matched-euler {:sfsim.physics/position 42.0 :sfsim.physics/speed 0.0} 1.0 [2 3])
       => {:sfsim.physics/position 44.0 :sfsim.physics/speed 5.0}
       (matched-euler {:sfsim.physics/position 42.0 :sfsim.physics/speed 0.0} 2.0 [2 3])
       => {:sfsim.physics/position 46.0 :sfsim.physics/speed 5.0})


(tabular "Test Runge Kutta matching scheme for semi-implicit Euler"
         (fact (matched-euler ?y0 ?dt (matching-scheme ?y0 ?dt ?y1 * #(- %1 %2))) => ?y1)
         ?y0                        ?dt ?y1
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 2.0} 1.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 2.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 1.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 2.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 1.0}
         {:sfsim.physics/position 2.0 :sfsim.physics/speed 1.5} 2.0 {:sfsim.physics/position 5.0 :sfsim.physics/speed 1.5})


(facts "Determine gravitation from planetary object"
       ((gravitation (vec3 0 0 0) 0.0) (vec3 100 0 0) (vec3 0 0 0)) => (vec3 0 0 0)
       ((gravitation (vec3 0 0 0) 5.9722e+24) (vec3 6378000.0 0 0) (vec3 0 0 0)) => (roughly-vector (vec3 -9.799 0 0) 1e-3)
       ((gravitation (vec3 6378000.0 0 0) 5.9722e+24) (vec3 0 0 0) (vec3 0 0 0)) => (roughly-vector (vec3 9.799 0 0) 1e-3))


(facts "State change from position-dependent acceleration"
       ((state-change (fn [_position _] (vec3 0 0 0))) {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 0 0 0)} 0.0)
       => {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 0 0 0)}
       ((state-change (fn [_position _] (vec3 0 0 0))) {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 2 0 0)} 0.0)
       => {:sfsim.physics/position (vec3 2 0 0) :sfsim.physics/speed (vec3 0 0 0)}
       ((state-change (fn [_position _] (vec3 3 0 0))) {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 0 0 0)} 0.0)
       => {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 3 0 0)}
       ((state-change (fn [position _] position)) {:sfsim.physics/position (vec3 5 0 0) :sfsim.physics/speed (vec3 0 0 0)} 0.0)
       => {:sfsim.physics/position (vec3 0 0 0) :sfsim.physics/speed (vec3 5 0 0)})


(facts "Add and scale states"
       (state-add {:sfsim.physics/position (vec3 2 0 0) :sfsim.physics/speed (vec3 3 0 0)}
                  {:sfsim.physics/position (vec3 5 0 0) :sfsim.physics/speed (vec3 7 0 0)})
       => {:sfsim.physics/position (vec3 7 0 0) :sfsim.physics/speed (vec3 10 0 0)}
       (state-scale {:sfsim.physics/position (vec3 2 0 0) :sfsim.physics/speed (vec3 3 0 0)} 5.0)
       => {:sfsim.physics/position (vec3 10 0 0) :sfsim.physics/speed (vec3 15 0 0)})


(facts "Centrifugal acceleration in rotating coordinate system"
       (centrifugal-acceleration (vec3 0 0 0) (vec3 1 0 0)) => (vec3 0 0 0)
       (centrifugal-acceleration (vec3 0 0 1) (vec3 1 0 0)) => (vec3 1 0 0)
       (centrifugal-acceleration (vec3 0 0 2) (vec3 1 0 0)) => (vec3 4 0 0)
       (centrifugal-acceleration (vec3 0 0 2) (vec3 0 0 1)) => (vec3 0 0 0))


(facts "Coriolis acceleration in rotating coordinate system"
       (coriolis-acceleration (vec3 0 0 0) (vec3 1 0 0)) => (vec3 0 0 0)
       (coriolis-acceleration (vec3 0 0 1) (vec3 1 0 0)) => (vec3 0 -2 0)
       (coriolis-acceleration (vec3 0 0 1) (vec3 0 0 1)) => (vec3 0 0 0))


(def sphere (jolt/create-and-add-dynamic-body (jolt/sphere-settings 1.0 1000.0) (vec3 0 0 0) (q/->Quaternion 1 0 0 0)))


(def state (atom {:sfsim.physics/position (vec3 2 3 5) :sfsim.physics/speed (vec3 1 0 0) :sfsim.physics/body sphere}))


(facts "Set position of space craft near surface (rotating coordinate system centered on Earth)"
       (set-pose :sfsim.physics/surface state (vec3 6378000 0 0) (q/->Quaternion 0 1 0 0))
       (@state :sfsim.physics/domain) => :sfsim.physics/surface
       (@state :sfsim.physics/position) => (vec3 0 0 0)
       (jolt/get-translation sphere) => (vec3 6378000 0 0)
       (jolt/get-orientation sphere) => (q/->Quaternion 0 1 0 0)
       (get-position :sfsim.physics/surface astro/T0 state) => (vec3 6378000 0 0)
       (get-orientation :sfsim.physics/surface astro/T0 state) => (q/->Quaternion 0 1 0 0))


(facts "Set position of space craft not near surface (ICRS aligned coordinate system centered on Earth)"
       (set-pose :sfsim.physics/orbit state (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (@state :sfsim.physics/domain) => :sfsim.physics/orbit
       (@state :sfsim.physics/position) => (vec3 6678000 0 0)
       (jolt/get-translation sphere) => (vec3 0 0 0)
       (jolt/get-orientation sphere) => (q/->Quaternion 0 0 1 0)
       (get-position :sfsim.physics/orbit astro/T0 state) => (vec3 6678000 0 0)
       (get-orientation :sfsim.physics/orbit astro/T0 state) => (q/->Quaternion 0 0 1 0))


(facts "Set linear and angular speed of space craft near surface (rotating coordinate system centered on Earth)"
       (set-pose :sfsim.physics/surface state (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (set-speed :sfsim.physics/surface state (vec3 100 0 0) (vec3 0 0 1))
       (@state :sfsim.physics/speed) => (vec3 0 0 0)
       (jolt/get-linear-velocity sphere) => (vec3 100 0 0)
       (jolt/get-angular-velocity sphere) => (vec3 0 0 1)
       (get-linear-speed :sfsim.physics/surface astro/T0 state) => (vec3 100 0 0)
       (get-angular-speed :sfsim.physics/surface astro/T0 state) => (vec3 0 0 1))


(facts "Set linear and angular speed of space craft not near surface (ICRS aligned coordinate system centered on Earth)"
       (set-pose :sfsim.physics/orbit state (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (set-speed :sfsim.physics/orbit state (vec3 100 0 0) (vec3 0 0 2))
       (@state :sfsim.physics/speed) => (vec3 100 0 0)
       (jolt/get-linear-velocity sphere) => (vec3 0 0 0)
       (jolt/get-angular-velocity sphere) => (vec3 0 0 2)
       (get-linear-speed :sfsim.physics/orbit astro/T0 state) => (vec3 100 0 0)
       (get-angular-speed :sfsim.physics/orbit astro/T0 state) => (vec3 0 0 2))


(fact "Do nothing when switching from surface to surface"
      (set-pose :sfsim.physics/surface state (vec3 6678000 0 0) (q/rotation (to-radians 45.0) (vec3 0 0 1)))
      (set-speed :sfsim.physics/surface state (vec3 100 0 0) (vec3 1 0 0))
      (set-domain :sfsim.physics/surface astro/T0 state)
      (@state :sfsim.physics/domain) => :sfsim.physics/surface
      (@state :sfsim.physics/position) => (roughly-vector (vec3 0 0 0) 1e-6)
      (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 6678000 0 0)
      (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
      (@state :sfsim.physics/speed) => (vec3 0 0 0)
      (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 100 0 0)
      (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (vec3 1 0 0))


(facts "Switch from Earth system to ICRS system and back"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (matrix/rotation-z (to-radians 90.0)))]
         (set-pose :sfsim.physics/surface state (vec3 6678000 0 0) (q/rotation (to-radians 45.0) (vec3 0 0 1)))
         (set-speed :sfsim.physics/surface state (vec3 100 0 0) (vec3 1 0 0))
         (get-position :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 0 6678000 0) 1e-6)
         (get-orientation :sfsim.physics/orbit astro/T0 state) => (roughly-quaternion (q/rotation (to-radians 135.0) (vec3 0 0 1)) 1e-6)
         (get-linear-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 (- (* 6678000 astro/earth-rotation-speed)) 100 0) 1e-6)
         (get-angular-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 0 1 astro/earth-rotation-speed) 1e-6)

         (set-domain :sfsim.physics/orbit astro/T0 state)
         (@state :sfsim.physics/domain) => :sfsim.physics/orbit
         (@state :sfsim.physics/position) => (roughly-vector (vec3 0 6678000 0) 1e-6)
         (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 0 0 0)
         (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 135.0) (vec3 0 0 1)) 1e-6)
         (@state :sfsim.physics/speed) => (roughly-vector (vec3 (- (* 6678000 astro/earth-rotation-speed)) 100 0) 1e-6)
         (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 0 0 0)
         (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 1 astro/earth-rotation-speed) 1e-6)
         (get-position :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 6678000 0 0) 1e-6)
         (get-orientation :sfsim.physics/surface astro/T0 state) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
         (get-linear-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 100 0 0) 1e-6)
         (get-angular-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 1 0 0) 1e-6)

         (set-domain :sfsim.physics/surface astro/T0 state)
         (@state :sfsim.physics/domain) => :sfsim.physics/surface
         (@state :sfsim.physics/position) => (vec3 0 0 0)
         (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 6678000 0 0) 1e-6)
         (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
         (@state :sfsim.physics/speed) => (vec3 0 0 0)
         (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 100 0 0) 1e-6)
         (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 1 0 0) 1e-6)))


(facts "Perform physics update in Earth centered rotating coordinate system"
       (set-pose :sfsim.physics/surface state (vec3 0 0 6678000) (q/->Quaternion 1 0 0 0))
       (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
       (update-state state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 0 (- 6678000 (* 0.5 8.938))) 1e-3)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 0 -8.938) 1e-3)

       (set-pose :sfsim.physics/surface state (vec3 6678000 0 0) (q/->Quaternion 1 0 0 0))
       (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
       (update-state state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.903)) 0 0) 1e-3)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 -8.903 0 0) 1e-3)

       (set-pose :sfsim.physics/surface state (vec3 0 0 6678000) (q/->Quaternion 1 0 0 0))
       (set-speed :sfsim.physics/surface state (vec3 100 0 0) (vec3 0 0 0))
       (update-state state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 100 -0.015 -8.938) 1e-3))


(facts "Perform high precision physics update in Earth centered ICRS aligned coordinate system"
       (set-pose :sfsim.physics/orbit state (vec3 6678000 0 0) (q/->Quaternion 1 0 0 0))
       (set-speed :sfsim.physics/orbit state (vec3 0 0 0) (vec3 0 0 0))
       (update-state state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (@state :sfsim.physics/position) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.938)) 0 0) 1e-3)
       (@state :sfsim.physics/speed) => (roughly-vector (vec3 -8.938 0 0) 1e-3)
       (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 0 0 0)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 0 0 0)

       (update-state state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (@state :sfsim.physics/position) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.938 4)) 0 0) 1e-3)
       (@state :sfsim.physics/speed) => (roughly-vector (vec3 (* 2 -8.938) 0 0) 1e-3))


(facts "Apply forces in Earth centered rotating coordinate system"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (matrix/rotation-z (to-radians 90.0)))]
         (set-pose :sfsim.physics/surface state (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/surface astro/T0 state (vec3 (jolt/get-mass sphere) 0 0))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/orbit astro/T0 state (vec3 (jolt/get-mass sphere) 0 0))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 0 -1 0) 1e-3)))


(facts "Apply forces in Earth centered ICRS aligned coordinate system"
      (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (matrix/rotation-z (to-radians 90.0)))]
         (set-pose :sfsim.physics/orbit state (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (set-speed :sfsim.physics/orbit state (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/orbit astro/T0 state (vec3 (jolt/get-mass sphere) 0 0))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (set-speed :sfsim.physics/orbit state (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/surface astro/T0 state (vec3 (jolt/get-mass sphere) 0 0))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 0 1 0) 1e-3)))


(facts "Apply torques in Earth centered rotating coordinate system"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (matrix/rotation-z (to-radians 90.0)))]
         (set-pose :sfsim.physics/surface state (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/surface astro/T0 state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (set-speed :sfsim.physics/surface state (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/orbit astro/T0 state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/surface astro/T0 state) => (roughly-vector (vec3 0 -1 0) 1e-3)))


(facts "Apply torques in Earth centered ICRS aligned coordinate system"
      (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (matrix/rotation-z (to-radians 90.0)))]
         (set-pose :sfsim.physics/orbit state (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (set-speed :sfsim.physics/orbit state (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/orbit astro/T0 state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (set-speed :sfsim.physics/orbit state (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/surface astro/T0 state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (update-state state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/orbit astro/T0 state) => (roughly-vector (vec3 0 1 0) 1e-3)))


(jolt/remove-and-destroy-body sphere)


(jolt/jolt-destroy)
