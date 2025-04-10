(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [clojure.math :refer (PI to-radians sqrt)]
      [fastmath.vector :refer (vec3)]
      [sfsim.conftest :refer (roughly-vector)]
      [sfsim.quaternion :as q]
      [sfsim.aerodynamics :refer :all]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Mix two values depending on angle"
       (mix 0.1 0.4 (to-radians 0)) => 0.1
       (mix 0.1 0.4 (to-radians 180)) => 0.4
       (mix 0.1 0.4 (to-radians 360)) => 0.1)


(facts "Basic drag function"
       ((basic-drag 0.1 2.0) 0.0) => 0.1
       ((basic-drag 0.1 2.0) (to-radians 90)) => 2.0
       ((basic-drag 0.1 2.0) (to-radians 180)) => 0.1)

(facts "Basic lift function"
       ((basic-lift 1.1) 0.0) => 0.0
       ((basic-lift 1.1) (to-radians 45)) => 1.1
       ((basic-lift 1.1) (to-radians 90)) => (roughly 0.0 1e-6))


(facts "Ellipse-like fall-off function"
       ((fall-off 0.8 0.5) 0.0) => 0.8
       ((fall-off 0.8 0.5) 0.5) => 0.0
       ((fall-off 0.8 0.5) 0.2) => (roughly 0.16 1e-6)
       ((fall-off 0.8 0.6) 1.0) => 0.0)

(facts "Increase of lift for small angles of attack before stall"
       ((glide 0.8 0.6 0.4 0.5) 0.0) => 0.0
       ((glide 0.8 0.6 0.4 0.5) 0.6) => 0.8
       ((glide 0.8 0.6 0.4 0.5) 1.1) => 0.0
       ((glide 0.8 0.6 0.4 0.5) 0.8) => (roughly 0.08 1e-6)
       ((glide 0.8 0.6 0.4 0.5) -0.6) => -0.8
       ((glide 0.8 0.6 0.4 0.5) -1.1) => 0.0
       ((glide 0.8 0.6 0.4 0.5) -0.8) => (roughly -0.08 1e-6))

(facts "Bumps to add to drag before 180 and -180 degrees"
       ((bumps 0.1 0.4) 0.0) => 0.0
       ((bumps 0.1 0.4) (- PI 0.2)) => 0.1
       ((bumps 0.1 0.4) (- 0.2 PI)) => 0.1
       ((bumps 0.1 0.4) (- PI)) => 0.0
       ((bumps 0.1 0.4) (+ PI)) => 0.0
       ((bumps 0.1 0.4) (- PI 0.4)) => 0.0
       ((bumps 0.1 0.4) (- 0.4 PI)) => 0.0)


(facts "Lift increase to add near 180 and -180 degrees"
       ((tail 0.4 0.1 0.2) PI) => 0.0
       ((tail 0.4 0.1 0.2) 0.1) => 0.0
       ((tail 0.4 0.1 0.2) -0.1) => 0.0
       ((tail 0.4 0.1 0.2) (- PI 0.1)) => (roughly -0.4 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.1 PI)) => (roughly 0.4 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.05)) => (roughly -0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.05 PI)) => (roughly 0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.2)) => (roughly -0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.2 PI)) => (roughly 0.2 1e-6)
       ((tail 0.4 0.1 0.2) (- PI 0.25)) => (roughly -0.1 1e-6)
       ((tail 0.4 0.1 0.2) (- 0.25 PI)) => (roughly 0.1 1e-6))


(facts "Compose an aerodynamic curve"
       ((compose (fn [x] 0.0)) 0.0) => 0.0
       ((compose (fn [x] 1.0)) 0.0) => 1.0
       ((compose (fn [x] x)) 2.0) => 2.0
       ((compose (fn [x] 1.0) (fn [x] 2.0)) 0.0) => 3.0)


(facts "Sanity check for the aerodynamic coefficient functions"
       (coefficient-of-lift (to-radians 10)) => #(>= % 0.1)
       (coefficient-of-lift (to-radians -10)) => #(<= % -0.1)
       (coefficient-of-drag (to-radians 0)) => #(>= % 0.1)
       (coefficient-of-drag (to-radians 180)) => #(>= % 0.1)
       (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 0))))
       (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 180))))
       (coefficient-of-side-force (to-radians 0)) => zero?
       (coefficient-of-side-force (to-radians 45)) => #(<= % -0.1)
       (coefficient-of-side-force (to-radians -45)) => #(>= % 0.1)
       (coefficient-of-side-force (to-radians 135)) => #(>= % 0.1)
       (coefficient-of-side-force (to-radians -135)) => #(<= % -0.1))


(facts "Mirror values at 90 degrees"
       (mirror (to-radians 90)) => (roughly (to-radians 90) 1e-6)
       (mirror (to-radians 0)) => (roughly (to-radians 180) 1e-6)
       (mirror (to-radians 180)) => (roughly (to-radians 0) 1e-6)
       (mirror (to-radians -90)) => (roughly (to-radians -90) 1e-6)
       (mirror (to-radians -180)) => (roughly (to-radians 0) 1e-6))


(facts "Sanity check for the 3D aerodynamic coefficient functions"
       (coefficient-of-lift (to-radians 30) (to-radians 0))
       => #(>= % 0.3)
       (coefficient-of-lift (to-radians 30) (to-radians 0))
       => (roughly (- (coefficient-of-lift (to-radians 30) (to-radians 180))) 1e-6)
       (coefficient-of-lift (to-radians 5) (to-radians 0))
       => (roughly (- (coefficient-of-lift (to-radians -175) (to-radians 180))) 1e-6)
       (coefficient-of-lift (to-radians 10) (to-radians 90))
       => (roughly 0.0 1e-6)
       (coefficient-of-drag (to-radians 0) (to-radians 0))
       => #(>= % 0.05)
       (coefficient-of-drag (to-radians 0) (to-radians 0))
       => (roughly (coefficient-of-drag (to-radians 0) (to-radians 180)) 1e-6)
       (coefficient-of-drag (to-radians 0) (to-radians 90))
       => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 0)) 0.1))
       (coefficient-of-drag (to-radians 0) (to-radians -90))
       => (roughly (coefficient-of-drag (to-radians 0) (to-radians 90)) 1e-6)
       (coefficient-of-drag (to-radians 90) (to-radians 0))
       => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 90)) 0.1))
       (coefficient-of-drag (to-radians 20) (to-radians 90))
       => (roughly (coefficient-of-drag (to-radians 45) (to-radians 90)))
       (coefficient-of-side-force (to-radians 0) (to-radians 0))
       => (roughly 0.0 1e-6)
       (coefficient-of-side-force (to-radians 0) (to-radians 45))
       => #(<= % -0.1)
       (coefficient-of-side-force (to-radians 0) (to-radians 90))
       => (roughly 0.0 1e-6)
       (coefficient-of-side-force (to-radians 90) (to-radians 45))
       => #(>= % 1.0)
       (coefficient-of-side-force (to-radians 90) (to-radians 45))
       => (roughly (- (coefficient-of-side-force (to-radians 90) (to-radians -45))) 1e-6))


(facts "Spike function with linear and sinusoidal ramp"
       ((spike 2.0 0.5 0.4) 0.0) => 0.0
       ((spike 2.0 0.5 0.4) 0.25) => 1.0
       ((spike 2.0 0.5 0.4) 0.5) => 2.0
       ((spike 2.0 0.5 0.4) 0.9) => 0.0
       ((spike 2.0 0.5 0.4) 1.0) => 0.0
       ((spike 2.0 0.5 0.4) 0.7) => (roughly (- 2 (sqrt 2)) 1e-6)
       ((spike 2.0 0.5 0.4) -0.25) => -1.0
       ((spike 2.0 0.5 0.4) -0.7) => (roughly (- (sqrt 2) 2) 1e-6))


(facts "Airplane coordinates of speed vector in body system for angle of attack and side slip angle"
       (speed-x (to-radians 0) (to-radians 0)) => 1.0
       (speed-y (to-radians 0) (to-radians 0)) => 0.0
       (speed-z (to-radians 0) (to-radians 0)) => 0.0
       (speed-x (to-radians 90) (to-radians 0)) => (roughly 0.0 1e-6)
       (speed-z (to-radians 90) (to-radians 0)) => 1.0
       (speed-y (to-radians 0) (to-radians 90)) => 1.0
       (speed-x (to-radians 0) (to-radians 90)) => (roughly 0.0 1e-6)
       (speed-z (to-radians 90) (to-radians 90)) => (roughly 0.0 1e-6))


(fact "Get speed vector in aircraft body system"
      (let [angle-of-attack (to-radians 20)
            angle-of-side-slip (to-radians 30)]
        (speed-vector angle-of-attack angle-of-side-slip)
        => (roughly-vector (vec3 (speed-x angle-of-attack angle-of-side-slip)
                                 (speed-y angle-of-attack angle-of-side-slip)
                                 (speed-z angle-of-attack angle-of-side-slip))
                           1e-6)))


(facts "Get angle of attack and side slip angles from speed vector in aircraft body system"
       (angle-of-attack (vec3 1 0 0)) => 0.0
       (angle-of-attack (vec3 0 0 1)) => (roughly (to-radians 90) 1e-6)
       (angle-of-attack (speed-vector (to-radians 20) (to-radians 30))) => (roughly (to-radians 20) 1e-6))


(facts "Get angle of side-slip from speed vector in aircraft body system"
       (angle-of-side-slip (vec3 1 0 0)) => 0.0
       (angle-of-side-slip (vec3 0 1 0)) => (roughly (to-radians 90) 1e-6)
       (angle-of-side-slip (speed-vector (to-radians 20) (to-radians 30))) => (roughly (to-radians 30) 1e-6))


(facts "Sanity checks for the aerodynamic moment coefficients"
       (coefficient-of-pitch-moment (to-radians 0.0)) => 0.0
       (coefficient-of-pitch-moment (to-radians 90)) => #(<= % -0.5)
       (coefficient-of-pitch-moment (to-radians 180)) => (roughly 0.0 1e-6)
       (coefficient-of-pitch-moment (to-radians -90)) => #(>= % 0.5)
       (coefficient-of-pitch-moment (to-radians 170)) => #(<= % (- (coefficient-of-pitch-moment (to-radians 10)) 0.1))
       (coefficient-of-pitch-moment (to-radians -170)) => #(>= % (+ (coefficient-of-pitch-moment (to-radians -10)) 0.1))
       (coefficient-of-yaw-moment (to-radians 0.0)) => 0.0
       (coefficient-of-yaw-moment (to-radians 90)) => #(>= % 1.5)
       (coefficient-of-yaw-moment (to-radians 180)) => (roughly 0.0 1e-6)
       (coefficient-of-roll-moment (to-radians 0.0)) => 0.0
       (coefficient-of-roll-moment (to-radians 90.0)) => #(<= % -0.5)
       (coefficient-of-roll-moment (to-radians 180.0)) => (roughly 0.0 1e-6))


(facts "Tests for 3D version of pitch moment"
       (coefficient-of-pitch-moment (to-radians 0) (to-radians 0)) => 0.0
       (coefficient-of-pitch-moment (to-radians 90) (to-radians 0)) => (coefficient-of-pitch-moment (to-radians 90))
       (coefficient-of-pitch-moment (to-radians 90) (to-radians 90)) => (roughly 0.0 1e-6)
       (coefficient-of-pitch-moment (to-radians 10) (to-radians 180))
       => (coefficient-of-pitch-moment (to-radians 190) (to-radians 0)))


(facts "Convert glTF model coordinates to aerodynamic body coordinates"
       (gltf->aerodynamic (vec3 1 0 0)) => (vec3 1 0 0)
       (gltf->aerodynamic (vec3 0 0 1)) => (vec3 0 1 0)
       (gltf->aerodynamic (vec3 0 1 0)) => (vec3 0 0 -1))


(facts "Convert aerodynamic body coordinates to glTF model coordinates"
       (aerodynamic->gltf (vec3 1 0 0)) => (vec3 1 0 0)
       (aerodynamic->gltf (vec3 0 1 0)) => (vec3 0 0 1)
       (aerodynamic->gltf (vec3 0 0 -1)) => (vec3 0 1 0))


(facts "Get angle of attack, side slip angle, and speed magnitude"
       (:sfsim.aerodynamics/speed (speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 5.0
       (:sfsim.aerodynamics/alpha (speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 0.0
       (:sfsim.aerodynamics/beta  (speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 0.0
       (:sfsim.aerodynamics/alpha (speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 1))) => (roughly (/ PI 4) 1e-6)
       (:sfsim.aerodynamics/beta  (speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 1 0))) => (roughly (/ PI 4) 1e-6)
       (:sfsim.aerodynamics/alpha (speed-in-body-system (q/->Quaternion 0 1 0 0) (vec3 1 0 -1))) => (roughly (/ PI 4) 1e-6))
