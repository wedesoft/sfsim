(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [clojure.math :refer (PI to-radians sqrt)]
      [fastmath.vector :refer (vec3)]
      [sfsim.conftest :refer (roughly-vector)]
      [sfsim.quaternion :as q]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.aerodynamics :refer :all :as aerodynamics]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Linear segment"
       ((linear 0.0 0.0 1.0 1.0) 0.0) => (roughly 0.0 1e-6)
       ((linear 0.0 0.0 1.0 1.0) 1.0) => (roughly 1.0 1e-6)
       ((linear 0.0 0.0 1.0 2.0) 1.0) => (roughly 2.0 1e-6)
       ((linear 0.0 1.0 1.0 2.0) 0.0) => (roughly 1.0 1e-6)
       ((linear 1.0 1.0 2.0 2.0) 1.0) => (roughly 1.0 1e-6)
       ((linear 1.0 1.0 3.0 2.0) 3.0) => (roughly 2.0 1e-6))


(facts "Cubic Hermite spline"
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 1.0 0.0) 0.0) => (roughly 0.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 1.0 0.0) 1.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 2.0 0.0) 1.0) => (roughly 2.0 1e-6)
       ((cubic-hermite-spline 0.0 1.0 0.0 1.0 2.0 0.0) 0.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 1.0 1.0 0.0 2.0 2.0 0.0) 1.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 1.0 1.0 0.0 3.0 2.0 0.0) 3.0) => (roughly 2.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 1.0 2.0 0.0 -1.0) 1.0) => (roughly 0.5 1e-6)
       ((cubic-hermite-spline 0.0 0.0 1.0 2.0 0.0 1.0) 1.0) => (roughly 0.0 1e-6))


(facts "Piecewise function"
       ((piecewise [0.0 1.0] (fn [x] x)) 0.5) => 0.5
       ((piecewise [0.0 1.0] (constantly 1.0) [1.0 2.0] (constantly 2.0)) 1.5) => 2.0
       ((piecewise [0.0 1.0] (constantly 1.0) [1.0 2.0] (constantly 2.0)) 0.5) => 1.0)


(facts "Piecewise linear function"
       ((piecewise-linear 2.0 3.0, 5.0 7.0) 3.5) => 5.0
       ((piecewise-linear 2.0 3.0, 5.0 7.0, 11.0 13.0) 8.0) => 10.0)


(facts "Cubic spline function"
       ((cubic-spline 2.0 3.0, 5.0 7.0, 6.0 8.0, 7.0 8.0, 8.0 7.0) 3.5) => (roughly 5.0582 1e-4)
       ((cubic-spline 2.0 3.0, 5.0 7.0, 11.0 13.0, 17.0 19.0, 23.0 29.0) 8.0) => (roughly 10.2744 1e-4))


(facts "Akima spline function"
       ((akima-spline 2.0 3.0, 5.0 7.0, 6.0 8.0, 7.0 8.0, 8.0 7.0) 3.5) => (roughly 5.1875 1e-4)
       ((akima-spline 2.0 3.0, 5.0 7.0, 11.0 13.0, 17.0 19.0, 23.0 29.0) 8.0) => (roughly 10.1667 1e-4))


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
       ((compose (fn [_x] 0.0)) 0.0) => 0.0
       ((compose (fn [_x] 1.0)) 0.0) => 1.0
       ((compose (fn [x] x)) 2.0) => 2.0
       ((compose (fn [_x] 1.0) (fn [_x] 2.0)) 0.0) => 3.0)


(facts "Coefficient of lift"
       (coefficient-of-lift 0.6 (to-radians 0.0)) => 0.0
       (coefficient-of-lift 0.6 (to-radians 3.0)) => (roughly (* 2.7825 (to-radians 3.0)) 1e-6)
       (coefficient-of-lift 0.6 (to-radians 33.0)) => (roughly 1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 90.0)) => 0.0
       (coefficient-of-lift 0.6 (to-radians -3.0)) => (roughly (* 2.7825 (to-radians -3.0)) 1e-6)
       (coefficient-of-lift 0.6 (to-radians -33.0)) => (roughly -1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians -90.0)) => 0.0
       (coefficient-of-lift 0.6 (to-radians 147.0)) => (roughly -1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 180.0)) => 0.0
       (coefficient-of-lift 0.6 (to-radians -147.0)) => (roughly 1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians -180.0)) => 0.0
       (coefficient-of-lift 0.6 (to-radians 213.0)) => (roughly 1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians -213.0)) => (roughly -1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 33.0) (to-radians 0.0)) => (roughly 1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 213.0) (to-radians 180.0)) => (roughly -1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 147.0) (to-radians 180.0)) => (roughly 1.3 1e-6)
       (coefficient-of-lift 0.6 (to-radians 33.0) (to-radians 90.0)) => (roughly 0.0 1e-6)
       (coefficient-of-lift 0.6 (to-radians 33.0) (to-radians -90.0)) => (roughly 0.0 1e-6))


(facts "Coefficient of drag"
       (coefficient-of-drag 0.6 (to-radians 0.0))
       => 0.04741
       (coefficient-of-drag 0.6 (to-radians 33.0))  ; TODO: drag for alpha 90 degrees should be large
       => (roughly (+ 0.04741 (/ (* 1.3 1.3) PI 0.9859 aspect-ratio)) 1e-6)
       (coefficient-of-drag 0.6 (to-radians 33.0) (to-radians 0.0))
       => (roughly (+ 0.04741 (/ (* 1.3 1.3) PI 0.9859 aspect-ratio)) 1e-6)
       (coefficient-of-drag 0.6 (to-radians 33.0) (to-radians 90.0))  ; TODO: increase zero lift drag when flying sideways
       => 0.04741)

; (facts "Sanity check for the aerodynamic coefficient functions"
;        (coefficient-of-drag (to-radians 0)) => #(>= % 0.1)
;        (coefficient-of-drag (to-radians 180)) => #(>= % 0.1)
;        (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 0))))
;        (coefficient-of-drag (to-radians 90)) => #(>= % (* 2 (coefficient-of-drag (to-radians 180))))
;        (coefficient-of-side-force (to-radians 0)) => zero?
;        (coefficient-of-side-force (to-radians 45)) => #(<= % -0.1)
;        (coefficient-of-side-force (to-radians -45)) => #(>= % 0.1)
;        (coefficient-of-side-force (to-radians 135)) => #(>= % 0.1)
;        (coefficient-of-side-force (to-radians -135)) => #(<= % -0.1))


(facts "Mirror values at 90 degrees"
       (mirror (to-radians 90)) => (roughly (to-radians 90) 1e-6)
       (mirror (to-radians 0)) => (roughly (to-radians 180) 1e-6)
       (mirror (to-radians 180)) => (roughly (to-radians 0) 1e-6)
       (mirror (to-radians -90)) => (roughly (to-radians -90) 1e-6)
       (mirror (to-radians -180)) => (roughly (to-radians 0) 1e-6))


; (facts "Sanity check for the 3D aerodynamic coefficient functions"
;        (coefficient-of-drag (to-radians 0) (to-radians 0))
;        => #(>= % 0.05)
;        (coefficient-of-drag (to-radians 0) (to-radians 0))
;        => (roughly (coefficient-of-drag (to-radians 0) (to-radians 180)) 1e-6)
;        (coefficient-of-drag (to-radians 0) (to-radians 90))
;        => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 0)) 0.1))
;        (coefficient-of-drag (to-radians 0) (to-radians -90))
;        => (roughly (coefficient-of-drag (to-radians 0) (to-radians 90)) 1e-6)
;        (coefficient-of-drag (to-radians 90) (to-radians 0))
;        => #(>= % (+ (coefficient-of-drag (to-radians 0) (to-radians 90)) 0.1))
;        (coefficient-of-drag (to-radians 20) (to-radians 90))
;        => (roughly (coefficient-of-drag (to-radians 45) (to-radians 90)))
;        (coefficient-of-side-force (to-radians 0) (to-radians 0))
;        => (roughly 0.0 1e-6)
;        (coefficient-of-side-force (to-radians 0) (to-radians 45))
;        => #(<= % -0.1)
;        (coefficient-of-side-force (to-radians 0) (to-radians 90))
;        => (roughly 0.0 1e-6)
;        (coefficient-of-side-force (to-radians 90) (to-radians 45))
;        => #(>= % 1.0)
;        (coefficient-of-side-force (to-radians 90) (to-radians 45))
;        => (roughly (- (coefficient-of-side-force (to-radians 90) (to-radians -45))) 1e-6))


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
       (angle-of-attack (vec3 -1 0 0)) => (roughly (to-radians 180.0) 1e-6)
       (angle-of-attack (vec3 0 0 1)) => (roughly (to-radians 90) 1e-6)
       (angle-of-attack (speed-vector (to-radians 20) (to-radians 30))) => (roughly (to-radians 20) 1e-6))


(facts "Get angle of side-slip from speed vector in aircraft body system"
       (angle-of-side-slip (vec3 1 0 0)) => 0.0
       (angle-of-side-slip (vec3 0 1 0)) => (roughly (to-radians 90) 1e-6)
       (angle-of-side-slip (speed-vector (to-radians 20) (to-radians 30))) => (roughly (to-radians 30) 1e-6))


; (facts "Sanity checks for the aerodynamic moment coefficients"
;        (coefficient-of-pitch-moment (to-radians 0.0)) => 0.0
;        (coefficient-of-pitch-moment (to-radians 90)) => #(<= % -0.5)
;        (coefficient-of-pitch-moment (to-radians 180)) => (roughly 0.0 1e-6)
;        (coefficient-of-pitch-moment (to-radians -90)) => #(>= % 0.5)
;        (coefficient-of-pitch-moment (to-radians 170)) => #(<= % (- (coefficient-of-pitch-moment (to-radians 10)) 0.1))
;        (coefficient-of-pitch-moment (to-radians -170)) => #(>= % (+ (coefficient-of-pitch-moment (to-radians -10)) 0.1))
;        (coefficient-of-yaw-moment (to-radians 0.0)) => 0.0
;        (coefficient-of-yaw-moment (to-radians 90)) => #(>= % 1.5)
;        (coefficient-of-yaw-moment (to-radians 180)) => (roughly 0.0 1e-6)
;        (coefficient-of-roll-moment (to-radians 0.0)) => 0.0
;        (coefficient-of-roll-moment (to-radians 90.0)) => #(<= % -0.5)
;        (coefficient-of-roll-moment (to-radians 180.0)) => (roughly 0.0 1e-6))


; (facts "Tests for 3D version of pitch moment"
;        (coefficient-of-pitch-moment (to-radians 0) (to-radians 0)) => 0.0
;        (coefficient-of-pitch-moment (to-radians 90) (to-radians 0)) => (coefficient-of-pitch-moment (to-radians 90))
;        (coefficient-of-pitch-moment (to-radians 90) (to-radians 90)) => (roughly 0.0 1e-6)
;        (coefficient-of-pitch-moment (to-radians 10) (to-radians 180))
;        => (coefficient-of-pitch-moment (to-radians 190) (to-radians 0)))


(facts "Convert glTF model coordinates to aerodynamic body coordinates"
       (gltf->aerodynamic (vec3 1 0 0)) => (vec3 1 0 0)
       (gltf->aerodynamic (vec3 0 0 1)) => (vec3 0 1 0)
       (gltf->aerodynamic (vec3 0 1 0)) => (vec3 0 0 -1))


(facts "Convert aerodynamic body coordinates to glTF model coordinates"
       (aerodynamic->gltf (vec3 1 0 0)) => (vec3 1 0 0)
       (aerodynamic->gltf (vec3 0 1 0)) => (vec3 0 0 1)
       (aerodynamic->gltf (vec3 0 0 -1)) => (vec3 0 1 0))


(facts "Get angle of attack, side slip angle, and speed magnitude"
       (:sfsim.aerodynamics/speed (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 5.0
       (:sfsim.aerodynamics/alpha (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 0.0
       (:sfsim.aerodynamics/beta  (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 5 0 0))) => 0.0
       (:sfsim.aerodynamics/alpha (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 1))) => (roughly (/ PI 4) 1e-6)
       (:sfsim.aerodynamics/beta  (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 1 0))) => (roughly (/ PI 4) 1e-6)
       (:sfsim.aerodynamics/alpha (linear-speed-in-body-system (q/->Quaternion 0 1 0 0) (vec3 1 0 -1))) => (roughly (/ PI 4) 1e-6)
       (:sfsim.aerodynamics/beta  (linear-speed-in-body-system (q/rotation (/ PI 2) (vec3 0 0 -1)) (vec3 5 0 0)))
       => (roughly (/ PI 2) 1e-6))


; (facts "Compute lift for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-lift
;                      (fn [alpha beta] (facts alpha => 0.0 beta => 0.0) 1.0)]
;          (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0) => 0.5
;          (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0) => 0.25
;          (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0) => 2.5
;          (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0) => 2.0))
;
;
; (facts "Compute drag for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-drag
;                      (fn [alpha beta] (facts alpha => 0.0 beta => 0.0) 1.0)]
;          (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0) => 0.5
;          (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0) => 0.25
;          (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0) => 2.5
;          (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0) => 2.0))
;
;
; (facts "Compute side force for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-side-force
;                      (fn [alpha beta] (facts alpha => 0.0 beta => 0.0) 1.0)]
;          (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0) => 0.5
;          (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0) => 0.25
;          (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0) => 2.5
;          (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0) => 2.0))
;
;
; (facts "Compute pitch moment for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-pitch-moment
;                      (fn [alpha beta] (facts alpha => 0.0 beta => 0.0) 1.0)]
;          (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 1.0) => 0.5
;          (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0 1.0) => 0.25
;          (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0 1.0) => 2.5
;          (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0 1.0) => 2.0
;          (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 0.5) => 0.25))
;
;
; (facts "Compute yaw moment for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-yaw-moment
;                      (fn [beta] (facts beta => 0.0) 1.0)]
;          (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 1.0) => 0.5
;          (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0 1.0) => 0.25
;          (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0 1.0) => 2.5
;          (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0 1.0) => 2.0
;          (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 0.5) => 0.25))
;
;
; (facts "Compute roll moment for given speed in body system"
;        (with-redefs [aerodynamics/coefficient-of-roll-moment
;                      (fn [beta] (facts beta => 0.0) 1.0)]
;          (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 1.0) => 0.5
;          (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 0.5 1.0 1.0) => 0.25
;          (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 5.0 1.0) => 2.5
;          (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 2 0 0)) 1.0 1.0 1.0) => 2.0
;          (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) 1.0 1.0 0.5) => 0.25))


(facts "Compute pitch damping moment for given pitch rate in body system"
       (let [speed-data {:sfsim.aerodynamics/beta 0.0 :sfsim.aerodynamics/alpha 0.0}]
         (with-redefs [aerodynamics/coefficient-of-pitch-damping -1.0]
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 1.0) => -0.25
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 2.0) 1.0 1.0 1.0 1.0) => -0.5
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 2.0 1.0 1.0 1.0) => -0.5
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 2.0 1.0 1.0) => -0.5
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 2.0 1.0) => -0.5
           (pitch-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 2.0) => -1.0)))


(facts "Compute yaw damping moment for given yaw rate in body system"
       (let [speed-data {:sfsim.aerodynamics/beta 0.0 :sfsim.aerodynamics/alpha 0.0}]
         (with-redefs [aerodynamics/coefficient-of-yaw-damping -1.0]
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 1.0) => -0.25
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 2.0) 1.0 1.0 1.0 1.0) => -0.5
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 2.0 1.0 1.0 1.0) => -0.5
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 2.0 1.0 1.0) => -0.5
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 2.0 1.0) => -0.5
           (yaw-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 2.0) => -1.0)))


(facts "Compute roll damping moment for given roll rate in body system"
       (let [speed-data {:sfsim.aerodynamics/beta 0.0 :sfsim.aerodynamics/alpha 0.0}]
         (with-redefs [aerodynamics/coefficient-of-roll-damping -1.0]
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 1.0) => -0.25
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 2.0) 1.0 1.0 1.0 1.0) => -0.5
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 2.0 1.0 1.0 1.0) => -0.5
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 2.0 1.0 1.0) => -0.5
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 2.0 1.0) => -0.5
           (roll-damping (assoc speed-data :sfsim.aerodynamics/speed 1.0) 1.0 1.0 1.0 2.0) => -1.0)))


(facts "Convert vector from wind system to body system"
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) (vec3 1 0 0))
       => (roughly-vector (vec3 1 0 0) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 0 1 0)) (vec3 1 0 0))
       => (roughly-vector (vec3 0 1 0) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 0 0 1)) (vec3 1 0 0))
       => (roughly-vector (vec3 0 0 1) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 1 1)) (vec3 (sqrt 3) 0 0))
       => (roughly-vector (vec3 1 1 1) 1e-6))


; (facts "Determine aerodynamic forces and moments"
;        (let [height       1000.0
;              orientation  (q/->Quaternion 1.0 0.0 0.0 0.0)
;              linear-speed (vec3 5.0 0.0 0.0)
;              angular-speed (vec3 3.0 1.0 2.0)]
;          (with-redefs [atmosphere/density-at-height
;                        (fn [height] (facts height => 1000.0) :density)
;                        aerodynamics/linear-speed-in-body-system
;                        (fn [orientation speed] (facts orientation => (q/->Quaternion 1.0 0.0 0.0 0.0) speed => (vec3 5 0 0))
;                            :speed-body)
;                        wind-to-body-system
;                        (fn [speed-body force-vector]
;                            (facts speed-body => :speed-body)
;                            force-vector)
;                        aerodynamics/lift
;                        (fn [speed-body density surface]
;                            (facts speed-body => :speed-body density => :density surface => 100.0)
;                            2.0)
;                        aerodynamics/drag
;                        (fn [speed-body density surface]
;                            (facts speed-body => :speed-body density => :density surface => 100.0)
;                            3.0)
;                        aerodynamics/side-force
;                        (fn [speed-body density surface]
;                            (facts speed-body => :speed-body density => :density surface => 100.0)
;                            5.0)
;                        aerodynamics/pitch-moment
;                        (fn [speed-body density surface chord]
;                            (facts speed-body => :speed-body density => :density surface => 100.0 chord => 25.0)
;                            0.125)
;                        aerodynamics/yaw-moment
;                        (fn [speed-body density surface wingspan]
;                            (facts speed-body => :speed-body density => :density surface => 100.0 wingspan => 30.0)
;                            0.25)
;                        aerodynamics/roll-moment
;                        (fn [speed-body density surface wingspan]
;                            (facts speed-body => :speed-body density => :density surface => 100.0 wingspan => 30.0)
;                            0.5)
;                        aerodynamics/pitch-damping
;                        (fn [speed-body rate density surface chord]
;                            (facts speed-body => :speed-body (abs rate) => 1.0 density => :density surface => 100.0 chord => 25.0)
;                            -0.25)
;                        aerodynamics/yaw-damping
;                        (fn [speed-body rate density surface wingspan]
;                            (facts speed-body => :speed-body (abs rate) => 2.0 density => :density surface => 100.0
;                                   wingspan => 30.0)
;                            -0.5)
;                        aerodynamics/roll-damping
;                        (fn [speed-body rate density surface wingspan]
;                            (facts speed-body => :speed-body rate => 3.0 density => :density surface => 100.0 wingspan => 30.0)
;                            -1.0)]
;            (:sfsim.aerodynamics/forces (aerodynamic-loads height orientation linear-speed angular-speed 100.0 30.0 25.0))
;            => (vec3 -3.0 5.0 -2.0)
;            (:sfsim.aerodynamics/moments (aerodynamic-loads height orientation linear-speed angular-speed 100.0 30.0 25.0))
;            => (vec3 -0.5 -0.125 -0.25)
;            (with-redefs [aerodynamics/linear-speed-in-body-system
;                          (fn [orientation speed] (facts orientation => (q/->Quaternion 0.0 1.0 0.0 0.0) speed => (vec3 5 0 0))
;                              :speed-body)]
;              (:sfsim.aerodynamics/forces (aerodynamic-loads height (q/->Quaternion 0.0 1.0 0.0 0.0) linear-speed angular-speed
;                                                             100.0 30.0 25.0))
;              => (q/rotate-vector (q/->Quaternion 0.0 1.0 0.0 0.0) (vec3 -3.0 5.0 -2.0))
;              (:sfsim.aerodynamics/moments (aerodynamic-loads height (q/->Quaternion 0.0 1.0 0.0 0.0) linear-speed angular-speed
;                                                              100.0 30.0 25.0))
;              => (q/rotate-vector (q/->Quaternion 0.0 1.0 0.0 0.0) (vec3 -0.5 -0.125 -0.25))))))
