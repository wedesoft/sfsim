;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-physics
  (:require
    [clojure.math :refer (exp sqrt to-radians to-degrees)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [fastmath.matrix :refer (mulv rotation-matrix-3d-z)]
    [fastmath.vector :refer (vec3)]
    [sfsim.conftest :refer (roughly-vector roughly-quaternion)]
    [sfsim.quaternion :as q]
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
         ?y0                                                    ?dt ?y1
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 1.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 2.0} 1.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 2.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 2.0 {:sfsim.physics/position 2.0 :sfsim.physics/speed 1.0}
         {:sfsim.physics/position 0.0 :sfsim.physics/speed 0.0} 2.0 {:sfsim.physics/position 0.0 :sfsim.physics/speed 1.0}
         {:sfsim.physics/position 2.0 :sfsim.physics/speed 1.5} 2.0 {:sfsim.physics/position 5.0 :sfsim.physics/speed 1.5})


(facts "Determine gravitation from planetary object"
       ((gravitation (vec3         0 0 0) 0.0       ) (vec3     100 0 0) (vec3 0 0 0)) => (vec3 0 0 0)
       ((gravitation (vec3         0 0 0) 5.9722e+24) (vec3 6378000 0 0) (vec3 0 0 0)) => (roughly-vector (vec3 -9.799 0 0) 1e-3)
       ((gravitation (vec3 6378000.0 0 0) 5.9722e+24) (vec3       0 0 0) (vec3 0 0 0)) => (roughly-vector (vec3  9.799 0 0) 1e-3))


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


(def state (atom (make-physics-state sphere)))


(facts "Initial physics state"
       (:sfsim.physics/start-julian-date @state) => astro/T0
       (:sfsim.physics/offset-seconds @state) => 0.0
       (:sfsim.physics/position @state) => (vec3 0 0 0)
       (:sfsim.physics/speed @state) => (vec3 0 0 0)
       (:sfsim.physics/domain @state) => :sfsim.physics/surface
       (:sfsim.physics/display-speed @state) => 0.0
       (:sfsim.physics/throttle @state) => 0.0
       (:sfsim.physics/air-brake @state) => 0.0
       (:sfsim.physics/gear @state) => 1.0
       (:sfsim.physics/rcs-thrust @state) => (vec3 0 0 0)
       (:sfsim.physics/control-surfaces @state) => (vec3 0 0 0)
       (:sfsim.physics/brake @state) => 0.0
       (:sfsim.physics/display-speed @state) => 0.0
       (:sfsim.physics/vehicle @state) => nil)


(tabular "Handle control inputs"
         (let [input-state {:sfsim.input/throttle 0.0
                            :sfsim.input/air-brake false
                            :sfsim.input/gear-down true
                            :sfsim.input/rcs-roll 0
                            :sfsim.input/rcs-pitch 0
                            :sfsim.input/rcs-yaw 0
                            :sfsim.input/aileron 0.0
                            :sfsim.input/elevator 0.0
                            :sfsim.input/rudder 0.0}]
         (swap! state set-control-inputs (assoc input-state ?input ?value) ?dt)
         (fact (?output @state) => ?expected))
         ?input                     ?value ?dt  ?output                         ?expected
         :sfsim.input/throttle      0.1    0.25 :sfsim.physics/throttle         0.1
         :sfsim.input/air-brake     true   0.25 :sfsim.physics/air-brake        0.5
         :sfsim.input/air-brake     false  0.25 :sfsim.physics/air-brake        0.0
         :sfsim.input/gear-down     false  0.25 :sfsim.physics/gear             0.875
         :sfsim.input/rcs-roll      -1     1.0  :sfsim.physics/rcs-thrust       (vec3  1000000 0 0)
         :sfsim.input/rcs-roll       1     1.0  :sfsim.physics/rcs-thrust       (vec3 -1000000 0 0)
         :sfsim.input/rcs-roll       0     1.0  :sfsim.physics/rcs-thrust       (vec3 0 0 0)
         :sfsim.input/rcs-pitch     -1     1.0  :sfsim.physics/rcs-thrust       (vec3 0  1000000 0)
         :sfsim.input/rcs-pitch      1     1.0  :sfsim.physics/rcs-thrust       (vec3 0 -1000000 0)
         :sfsim.input/rcs-pitch      0     1.0  :sfsim.physics/rcs-thrust       (vec3 0 0 0)
         :sfsim.input/rcs-yaw       -1     1.0  :sfsim.physics/rcs-thrust       (vec3 0 0  1000000)
         :sfsim.input/rcs-yaw        1     1.0  :sfsim.physics/rcs-thrust       (vec3 0 0 -1000000)
         :sfsim.input/rcs-yaw        0     1.0  :sfsim.physics/rcs-thrust       (vec3 0 0 0)
         :sfsim.input/aileron        1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 (to-radians  20.0) 0 0) 1e-6)
         :sfsim.input/aileron       -1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 (to-radians -20.0) 0 0) 1e-6)
         :sfsim.input/aileron        0.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 0 0) 1e-6)
         :sfsim.input/elevator       1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 (to-radians  20.0) 0) 1e-6)
         :sfsim.input/elevator      -1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 (to-radians -20.0) 0) 1e-6)
         :sfsim.input/elevator       0.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 0 0) 1e-6)
         :sfsim.input/rudder         1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 0 (to-radians  20.0)) 1e-6)
         :sfsim.input/rudder        -1.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 0 (to-radians -20.0)) 1e-6)
         :sfsim.input/rudder         0.0   1.0  :sfsim.physics/control-surfaces (roughly-vector (vec3 0 0 0) 1e-6)
         :sfsim.input/brake          true  1.0  :sfsim.physics/brake            1.0
         :sfsim.input/brake          false 1.0  :sfsim.physics/brake            0.0
         :sfsim.input/parking-brake  true  1.0  :sfsim.physics/brake            0.1
         :sfsim.input/parking-brake  false 1.0  :sfsim.physics/brake            0.0)


(facts "Set position of space craft near surface (rotating coordinate system centered on Earth)"
       (swap! state set-pose :sfsim.physics/surface (vec3 6378000 0 0) (q/->Quaternion 0 1 0 0))
       (@state :sfsim.physics/domain) => :sfsim.physics/surface
       (@state :sfsim.physics/position) => (vec3 0 0 0)
       (jolt/get-translation sphere) => (vec3 6378000 0 0)
       (jolt/get-orientation sphere) => (q/->Quaternion 0 1 0 0)
       (get-position :sfsim.physics/surface @state) => (vec3 6378000 0 0)
       (get-orientation :sfsim.physics/surface @state) => (q/->Quaternion 0 1 0 0))


(facts "Set position of space craft not near surface (ICRS aligned coordinate system centered on Earth)"
       (swap! state set-pose :sfsim.physics/orbit (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (@state :sfsim.physics/domain) => :sfsim.physics/orbit
       (@state :sfsim.physics/position) => (vec3 6678000 0 0)
       (jolt/get-translation sphere) => (vec3 0 0 0)
       (jolt/get-orientation sphere) => (q/->Quaternion 0 0 1 0)
       (get-position :sfsim.physics/orbit @state) => (vec3 6678000 0 0)
       (get-orientation :sfsim.physics/orbit @state) => (q/->Quaternion 0 0 1 0))


(facts "Set linear and angular speed of space craft near surface (rotating coordinate system centered on Earth)"
       (swap! state set-pose :sfsim.physics/surface (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (swap! state set-speed :sfsim.physics/surface (vec3 100 0 0) (vec3 0 0 1))
       (@state :sfsim.physics/speed) => (vec3 0 0 0)
       (jolt/get-linear-velocity sphere) => (vec3 100 0 0)
       (jolt/get-angular-velocity sphere) => (vec3 0 0 1)
       (get-linear-speed :sfsim.physics/surface @state) => (vec3 100 0 0)
       (get-angular-speed :sfsim.physics/surface @state) => (vec3 0 0 1))


(facts "Set linear and angular speed of space craft not near surface (ICRS aligned coordinate system centered on Earth)"
       (swap! state set-pose :sfsim.physics/orbit (vec3 6678000 0 0) (q/->Quaternion 0 0 1 0))
       (swap! state set-speed :sfsim.physics/orbit (vec3 100 0 0) (vec3 0 0 2))
       (@state :sfsim.physics/speed) => (vec3 100 0 0)
       (jolt/get-linear-velocity sphere) => (vec3 0 0 0)
       (jolt/get-angular-velocity sphere) => (vec3 0 0 2)
       (get-linear-speed :sfsim.physics/orbit @state) => (vec3 100 0 0)
       (get-angular-speed :sfsim.physics/orbit @state) => (vec3 0 0 2))


(facts "Set longitude, latitude, and height of space craft"
       (let [radius 6378000.0
             planet #:sfsim.planet{:radius radius :max-height 8000.0}]
         (swap! state set-geographic (fn [_v] radius) planet 0.0 0.0 0.0 0.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 radius 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 0.0 (to-radians 90.0) 0.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 0 0 radius) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 (to-radians 90.0) 0.0 0.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 0 radius 0) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 (to-radians 90.0) (to-radians 90.0) 0.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 0 0 radius) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 0.0 0.0 1000.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 (+ radius 1000.0) 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] (+ radius 2000.0)) planet 0.0 0.0 0.0 1000.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 (+ radius 2000.0) 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] (+ radius 2000.0)) planet 5.0 0.0 0.0 1000.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 (+ radius 2005.0) 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] (+ radius 20000.0)) planet 0.0 0.0 0.0 10000.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 (+ radius 10000.0) 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] (+ radius 8000.0)) planet 5.0 0.0 0.0 8000.0)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 (+ radius 8005.0) 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 0.0 (to-radians -90.0) 0.0)
         (get-orientation :sfsim.physics/surface @state) => (roughly-quaternion (q/->Quaternion 1 0 0 0) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 0.0 0.0 0.0)
         (get-orientation :sfsim.physics/surface @state) => (roughly-quaternion (q/->Quaternion (sqrt 0.5) 0 (- (sqrt 0.5)) 0) 1e-6)
         (swap! state set-geographic (fn [_v] radius) planet 0.0 (to-radians 90.0) 0.0 0.0)
         (get-orientation :sfsim.physics/surface @state) => (roughly-quaternion (q/->Quaternion 0.5 0.5 -0.5 0.5) 1e-6)))


(tabular "Get longitude, latitude, and height of space craft"
       (let [radius 6378000.0
             planet #:sfsim.planet{:radius radius :max-height 8000.0}]
         (swap! state set-geographic (fn [_v] radius) planet 0.0 (to-radians ?longitude) (to-radians ?latitude) ?height)
         (let [geo-position (get-geographic @state planet)]
           (facts
             (to-degrees (:longitude geo-position)) => (roughly ?longitude 1e-3)
             (to-degrees (:latitude geo-position)) => (roughly ?latitude 1e-3)
             (:height geo-position) => (roughly ?height 1e-3))))
       ?longitude ?latitude ?height
        0.0        0.0        0.0
        0.0        0.0      100.0
       90.0        0.0        0.0
        0.0       90.0        0.0
       90.0       45.0        0.0)


(fact "Do nothing when switching from surface to surface"
      (swap! state set-pose :sfsim.physics/surface (vec3 6678000 0 0) (q/rotation (to-radians 45.0) (vec3 0 0 1)))
      (swap! state set-speed :sfsim.physics/surface (vec3 100 0 0) (vec3 1 0 0))
      (swap! state set-domain :sfsim.physics/surface)
      (@state :sfsim.physics/domain) => :sfsim.physics/surface
      (@state :sfsim.physics/position) => (roughly-vector (vec3 0 0 0) 1e-6)
      (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 6678000 0 0)
      (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
      (@state :sfsim.physics/speed) => (vec3 0 0 0)
      (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 100 0 0)
      (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (vec3 1 0 0))


(facts "Switch from Earth system to ICRS system and back"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (rotation-matrix-3d-z (to-radians 90.0)))]
         (swap! state set-pose :sfsim.physics/surface (vec3 6678000 0 0) (q/rotation (to-radians 45.0) (vec3 0 0 1)))
         (swap! state set-speed :sfsim.physics/surface (vec3 100 0 0) (vec3 1 0 0))
         (get-position :sfsim.physics/orbit @state) => (roughly-vector (vec3 0 6678000 0) 1e-6)
         (get-orientation :sfsim.physics/orbit @state) => (roughly-quaternion (q/rotation (to-radians 135.0) (vec3 0 0 1)) 1e-6)
         (get-linear-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 (- (* 6678000 astro/earth-rotation-speed)) 100 0) 1e-6)
         (get-angular-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 0 1 astro/earth-rotation-speed) 1e-6)

         (swap! state set-domain :sfsim.physics/orbit)
         (@state :sfsim.physics/domain) => :sfsim.physics/orbit
         (@state :sfsim.physics/position) => (roughly-vector (vec3 0 6678000 0) 1e-6)
         (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 0 0 0)
         (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 135.0) (vec3 0 0 1)) 1e-6)
         (@state :sfsim.physics/speed) => (roughly-vector (vec3 (- (* 6678000 astro/earth-rotation-speed)) 100 0) 1e-6)
         (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 0 0 0)
         (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 1 astro/earth-rotation-speed) 1e-6)
         (get-position :sfsim.physics/surface @state) => (roughly-vector (vec3 6678000 0 0) 1e-6)
         (get-orientation :sfsim.physics/surface @state) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
         (get-linear-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 100 0 0) 1e-6)
         (get-angular-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 1 0 0) 1e-6)

         (swap! state set-domain :sfsim.physics/surface)
         (@state :sfsim.physics/domain) => :sfsim.physics/surface
         (@state :sfsim.physics/position) => (vec3 0 0 0)
         (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 6678000 0 0) 1e-6)
         (jolt/get-orientation (@state :sfsim.physics/body)) => (roughly-quaternion (q/rotation (to-radians 45.0) (vec3 0 0 1)) 1e-6)
         (@state :sfsim.physics/speed) => (vec3 0 0 0)
         (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 100 0 0) 1e-6)
         (jolt/get-angular-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 1 0 0) 1e-6)))


(facts "Update domain depending on height of vehicle"
       (swap! state set-domain :sfsim.physics/surface)
       (swap! state set-pose :sfsim.physics/surface (vec3 0 0 6378000) (q/->Quaternion 1 0 0 0))
       (swap! state update-domain {:sfsim.planet/radius 6378000.0 :sfsim.planet/space-boundary 80000.0})
       (@state :sfsim.physics/domain) => :sfsim.physics/surface
       (swap! state set-pose :sfsim.physics/surface (vec3 0 0 6678000) (q/->Quaternion 1 0 0 0))
       (swap! state update-domain {:sfsim.planet/radius 6378000.0 :sfsim.planet/space-boundary 80000.0})
       (@state :sfsim.physics/domain) => :sfsim.physics/orbit)


(facts "Perform physics update in Earth centered rotating coordinate system"
       (swap! state set-pose :sfsim.physics/surface (vec3 0 0 6678000) (q/->Quaternion 1 0 0 0))
       (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
       (swap! state update-state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 0 (- 6678000 (* 0.5 8.938))) 1e-3)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 0 0 -8.938) 1e-3)
       (@state :sfsim.physics/display-speed) => (roughly 4.469 1e-3)

       (swap! state set-pose :sfsim.physics/surface (vec3 6678000 0 0) (q/->Quaternion 1 0 0 0))
       (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
       (swap! state update-state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-translation (@state :sfsim.physics/body)) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.903)) 0 0) 1e-3)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 -8.903 0 0) 1e-3)

       (swap! state set-pose :sfsim.physics/surface (vec3 0 0 6678000) (q/->Quaternion 1 0 0 0))
       (swap! state set-speed :sfsim.physics/surface (vec3 100 0 0) (vec3 0 0 0))
       (swap! state update-state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (roughly-vector (vec3 100 -0.015 -8.938) 1e-3))


(facts "Perform high precision physics update in Earth centered ICRS aligned coordinate system"
       (swap! state set-pose :sfsim.physics/orbit (vec3 6678000 0 0) (q/->Quaternion 1 0 0 0))
       (swap! state set-speed :sfsim.physics/orbit (vec3 0 0 0) (vec3 0 0 0))
       (swap! state update-state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (@state :sfsim.physics/position) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.938)) 0 0) 1e-3)
       (@state :sfsim.physics/speed) => (roughly-vector (vec3 -8.938 0 0) 1e-3)
       (@state :sfsim.physics/display-speed) => (roughly 8.938 1e-3)
       (jolt/get-translation (@state :sfsim.physics/body)) => (vec3 0 0 0)
       (jolt/get-linear-velocity (@state :sfsim.physics/body)) => (vec3 0 0 0)

       (swap! state update-state 1.0 (gravitation (vec3 0 0 0) 5.9722e+24))
       (@state :sfsim.physics/position) => (roughly-vector (vec3 (- 6678000 (* 0.5 8.938 4)) 0 0) 1e-3)
       (@state :sfsim.physics/speed) => (roughly-vector (vec3 (* 2 -8.938) 0 0) 1e-3)
       (@state :sfsim.physics/display-speed) => (roughly (* 2 8.938) 1e-3))


(facts "Apply forces in Earth centered rotating coordinate system"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (rotation-matrix-3d-z (to-radians 90.0)))]
         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-pose :sfsim.physics/surface (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/surface @state (vec3 (jolt/get-mass sphere) 0 0))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
         (add-force :sfsim.physics/orbit @state (vec3 (jolt/get-mass sphere) 0 0))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-linear-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 0 -1 0) 1e-3)))


(facts "Apply forces in Earth centered ICRS aligned coordinate system"
      (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (rotation-matrix-3d-z (to-radians 90.0)))]
        (swap! state set-julian-date-ut astro/T0)
        (swap! state set-pose :sfsim.physics/orbit (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
        (swap! state set-speed :sfsim.physics/orbit (vec3 0 0 0) (vec3 0 0 0))
        (add-force :sfsim.physics/orbit @state (vec3 (jolt/get-mass sphere) 0 0))
        (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
        (get-linear-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (swap! state set-julian-date-ut astro/T0)
        (swap! state set-speed :sfsim.physics/orbit (vec3 0 0 0) (vec3 0 0 0))
        (add-force :sfsim.physics/surface @state (vec3 (jolt/get-mass sphere) 0 0))
        (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
        (get-linear-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 0 1 0) 1e-3)))


(facts "Apply torques in Earth centered rotating coordinate system"
       (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (rotation-matrix-3d-z (to-radians 90.0)))]
         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-pose :sfsim.physics/surface (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/surface @state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-speed :sfsim.physics/surface (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/orbit @state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/surface @state) => (roughly-vector (vec3 0 -1 0) 1e-3)))


(facts "Apply torques in Earth centered ICRS aligned coordinate system"
      (with-redefs [astro/earth-to-icrs (fn [jd-ut] (fact jd-ut => astro/T0) (rotation-matrix-3d-z (to-radians 90.0)))]
         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-pose :sfsim.physics/orbit (vec3 0 0 0) (q/->Quaternion 1 0 0 0))
         (swap! state set-speed :sfsim.physics/orbit (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/orbit @state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 1 0 0) 1e-3)

         (swap! state set-julian-date-ut astro/T0)
         (swap! state set-speed :sfsim.physics/orbit (vec3 0 0 0) (vec3 0 0 0))
         (add-torque :sfsim.physics/surface @state (mulv (jolt/get-inertia sphere) (vec3 1 0 0)))
         (swap! state update-state 1.0 (constantly (vec3 0 0 0)))
         (get-angular-speed :sfsim.physics/orbit @state) => (roughly-vector (vec3 0 1 0) 1e-3)))


(facts "Get set with names of active RCS triplets"
       (active-rcs @state) => #{}
       (swap! state assoc :sfsim.physics/throttle 0.2)
       (active-rcs @state) => #{"Plume"}
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 -1000000 0 0))
       (active-rcs @state) => #{"Plume" "RCS RD1" "RCS RD2" "RCS RD3" "RCS LU1" "RCS LU2" "RCS LU3"}
       (swap! state assoc :sfsim.physics/throttle 0.0)
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 1000000 0 0))
       (active-rcs @state) => #{"RCS LD1" "RCS LD2" "RCS LD3" "RCS RU1" "RCS RU2" "RCS RU3"}
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 0 -1000000 0))
       (active-rcs @state) => #{"RCS LD1" "RCS LD2" "RCS LD3" "RCS RD1" "RCS RD2" "RCS RD3" "RCS FU1" "RCS FU2" "RCS FU3"}
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 0 1000000 0))
       (active-rcs @state) => #{"RCS LU1" "RCS LU2" "RCS LU3" "RCS RU1" "RCS RU2" "RCS RU3"
                                "RCS LFD1" "RCS LFD2" "RCS LFD3" "RCS RFD1" "RCS RFD2" "RCS RFD3"}
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 0 0 -1000000))
       (active-rcs @state) => #{"RCS L1" "RCS L2" "RCS L3" "RCS RF1" "RCS RF2" "RCS RF3"}
       (swap! state assoc :sfsim.physics/rcs-thrust (vec3 0 0 1000000))
       (active-rcs @state) => #{"RCS R1" "RCS R2" "RCS R3" "RCS LF1" "RCS LF2" "RCS LF3"})


(facts "Get and set Julian date of universal time"
       (swap! state set-julian-date-ut astro/T0)
       (swap! state set-domain :sfsim.physics/surface)
       (get-julian-date-ut @state) => (roughly astro/T0 1e-6)
       (swap! state update-state 60.0 (fn [_position _speed] (vec3 0 0 0)))
       (get-julian-date-ut @state) => (roughly (+ astro/T0 (/ 60.0 86400.0)) 1e-6)
       (swap! state set-domain :sfsim.physics/orbit)
       (swap! state update-state 60.0 (fn [_position _speed] (vec3 0 0 0)))
       (get-julian-date-ut @state) => (roughly (+ astro/T0 (/ 120.0 86400.0)) 1e-6)
       (swap! state set-julian-date-ut (+ astro/T0 1.0))
       (get-julian-date-ut @state) => (roughly (+ astro/T0 1.0) 1e-6))


(jolt/remove-and-destroy-body sphere)


(jolt/jolt-destroy)
