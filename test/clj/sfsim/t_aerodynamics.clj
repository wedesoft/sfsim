;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-aerodynamics
    (:require
      [midje.sweet :refer :all]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [clojure.math :refer (PI to-radians sqrt)]
      [fastmath.matrix :refer (eye col)]
      [fastmath.vector :refer (vec3)]
      [sfsim.conftest :refer (roughly-vector)]
      [sfsim.quaternion :as q]
      [sfsim.util :refer (sqr)]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.aerodynamics :refer :all :as aerodynamics]))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})


(facts "Cubic Hermite spline"
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 1.0 0.0) 0.0) => (roughly 0.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 1.0 0.0) 1.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 0.0 1.0 2.0 0.0) 1.0) => (roughly 2.0 1e-6)
       ((cubic-hermite-spline 0.0 1.0 0.0 1.0 2.0 0.0) 0.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 1.0 1.0 0.0 2.0 2.0 0.0) 1.0) => (roughly 1.0 1e-6)
       ((cubic-hermite-spline 1.0 1.0 0.0 3.0 2.0 0.0) 3.0) => (roughly 2.0 1e-6)
       ((cubic-hermite-spline 0.0 0.0 1.0 2.0 0.0 -1.0) 1.0) => (roughly 0.5 1e-6)
       ((cubic-hermite-spline 0.0 0.0 1.0 2.0 0.0 1.0) 1.0) => (roughly 0.0 1e-6))


(facts "Logistic function"
       (logistic-function    0.0) => 0.5
       (logistic-function   1e-3) => (roughly 0.50025 1e-5)
       (logistic-function  -10.0) => (roughly 0.0 1e-4)
       (logistic-function   10.0) => (roughly 1.0 1e-4))


(facts "Limiting function"
       ((limiting-function 1.0) 0.0) => (roughly 0.0 1e-4)
       ((limiting-function 1.0) 10.0) => (roughly 1.0 1e-4)
       ((limiting-function 0.5) 10.0) => (roughly 0.5 1e-4)
       ((limiting-function 1.0) 0.001) => (roughly 0.001 1e-4)
       ((limiting-function 0.5) 0.001) => (roughly 0.001 1e-4))


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
       (coefficient-of-drag 0.6 (to-radians 3.0))
       => (roughly (+ 0.04741 (/ (sqr (* 2.7825 (to-radians 3.0))) PI 0.9859 aspect-ratio)) 1e-2)
       (coefficient-of-drag 0.6 (to-radians 90.0)) => (roughly 1.1 1e-6)
       (coefficient-of-drag 0.6 (to-radians 3.0) (to-radians 0.0))
       => (roughly (+ 0.04741 (/ (sqr (* 2.7825 (to-radians 3.0))) PI 0.9859 aspect-ratio)) 1e-2)
       (coefficient-of-drag 0.6 (to-radians 0.0) (to-radians 90.0))
       (coefficient-of-drag 0.6 (to-radians 0.0) (to-radians 90.0)) => (roughly 0.0721 1e-4)
       (coefficient-of-drag 0.6 (to-radians 33.0) (to-radians 90.0)) => (roughly 0.0721 1e-4))


(facts "Coefficient of side force"
       (coefficient-of-side-force (to-radians 0.0)) => 0.0
       (coefficient-of-side-force (to-radians 3.0)) => (roughly (* -0.05 (to-radians 3.0)) 1e-5)
       (coefficient-of-side-force (to-radians -3.0)) => (roughly (* -0.05 (to-radians -3.0)) 1e-5)
       (coefficient-of-side-force (to-radians 90.0)) => (roughly 0.0 1e-6)
       (coefficient-of-side-force (to-radians 0.0) (to-radians 3.0)) => (roughly (* -0.05 (to-radians 3.0)) 1e-5)
       (coefficient-of-side-force (to-radians 90.0) (to-radians 45.0)) => (roughly 0.3 1e-5)
       (coefficient-of-side-force (to-radians -90.0) (to-radians 45.0)) => (roughly 0.3 1e-5)
       (coefficient-of-side-force (to-radians 90.0) (to-radians -45.0)) => (roughly -0.3 1e-5)
       (coefficient-of-side-force (to-radians -90.0) (to-radians -45.0)) => (roughly -0.3 1e-5)
       (coefficient-of-side-force (to-radians 90.0) (to-radians 90.0)) => (roughly 0.0 1e-5))


(facts "Mirror values at 90 degrees"
       (mirror (to-radians 90)) => (roughly (to-radians 90) 1e-6)
       (mirror (to-radians 0)) => (roughly (to-radians 180) 1e-6)
       (mirror (to-radians 180)) => (roughly (to-radians 0) 1e-6)
       (mirror (to-radians -90)) => (roughly (to-radians -90) 1e-6)
       (mirror (to-radians -180)) => (roughly (to-radians 0) 1e-6))


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


(facts "Coefficient of pitch moment"
       (coefficient-of-pitch-moment 0.6 (to-radians 0.0)) => 0.0
       (coefficient-of-pitch-moment 0.6 (to-radians 3.0)) => (roughly (* 2.7825 (to-radians 3.0) 0.01 (- 25.0 25.8613)) 1e-6)
       (coefficient-of-pitch-moment 0.6 (to-radians 3.0) (to-radians 0.0))
       => (roughly (* 2.7825 (to-radians 3.0) 0.01 (- 25.0 25.8613)) 1e-6)
       (coefficient-of-pitch-moment 0.6 (to-radians 3.0) (to-radians 90.0)) => (roughly 0.0 1e-6))


(facts "Coefficient of yaw moment"
       (coefficient-of-yaw-moment 0.6 (to-radians 0.0)) => 0.0
       (coefficient-of-yaw-moment 0.6 (to-radians 3.0)) => (roughly (* 0.0578 (to-radians 3.0)) 1e-5))
       (coefficient-of-yaw-moment 0.6 (to-radians 180.0)) => (roughly 0.0 1e-5)


(facts "Coefficient of roll moment"
       (coefficient-of-roll-moment 0.6 (to-radians 0.0) (to-radians 0.0)) => 0.0
       (coefficient-of-roll-moment 0.6 (to-radians 3.0) (to-radians 5.0))
       => (roughly (* -3.1333 (to-radians 3.0) (to-radians 5.0)) 5e-3)
       (coefficient-of-roll-moment 0.6 (to-radians 3.0) (to-radians 90.0)) => (roughly 0.0 1e-6)
       (coefficient-of-roll-moment 0.6 (to-radians 90.0) (to-radians 3.0)) => (roughly 0.0 1e-6))


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


(facts "Compute lift for given speed in body system"
       (with-redefs [aerodynamics/coefficient-of-lift
                     (fn [speed-mach alpha beta] (facts alpha => 0.0 beta => 0.0 speed-mach => 0.5) 0.14)]
         (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* 0.14 0.5 1.225 (* 160 160) reference-area) 1e-6)
         (lift (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.0)
         => (roughly (* 0.14 0.5 1.0 (* 160 160) reference-area) 1e-6)))


(facts "Compute drag for given speed in body system"
       (with-redefs [aerodynamics/coefficient-of-drag
                     (fn [speed-mach alpha beta] (facts alpha => 0.0 beta => 0.0 speed-mach => 0.5) 0.047475)]
         (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* 0.047475 0.5 1.225 (* 160 160) reference-area) 1e-6)
         (drag (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.0)
         => (roughly (* 0.047475 0.5 1.0 (* 160 160) reference-area) 1e-6)))


(facts "Compute side force for given speed in body system"
       (with-redefs [aerodynamics/coefficient-of-side-force
                     (fn [alpha beta] (facts alpha => 0.0 beta => 0.0) -0.0026)]
         (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* -0.0026 0.5 1.225 (* 160 160) reference-area) 1e-6)
         (side-force (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.0)
         => (roughly (* -0.0026 0.5 1.0 (* 160 160) reference-area) 1e-6)))


(facts "Compute roll moment"
       (with-redefs [aerodynamics/coefficient-of-roll-moment
                     (fn [speed-mach alpha beta] (facts alpha => 0.0 beta => 0.0 speed-mach => 0.5) -0.008)]
         (roll-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* -0.008 0.5 1.225 (* 160 160) reference-area 0.5 wing-span) 1e-6)))


(facts "Compute pitch moment"
       (with-redefs [aerodynamics/coefficient-of-pitch-moment
                     (fn [speed-mach alpha beta] (facts alpha => 0.0 beta => 0.0 speed-mach => 0.5) 0.001)]
         (pitch-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* 0.001 0.5 1.225 (* 160 160) reference-area chord) 1e-6)))


(facts "Compute yaw moment"
       (with-redefs [aerodynamics/coefficient-of-yaw-moment
                     (fn [speed-mach beta] (facts beta => 0.0 speed-mach => 0.5) 0.002)]
         (yaw-moment (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) 320.0 1.225)
         => (roughly (* 0.002 0.5 1.225 (* 160 160) reference-area 0.5 wing-span) 1e-6)))


(defn basis [i] (col (eye 3) i))


(facts "Compute roll damping for given roll, pitch, and yaw rates"
       (let [speed (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 100 0 0))]
         (roll-damping speed (basis 0) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-l-p wing-span) 1e-6)
         (roll-damping speed (basis 1) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-l-q chord    ) 1e-6)
         (roll-damping speed (basis 2) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-l-r wing-span) 1e-6)))


(facts "Compute pitch damping for given roll, pitch, and yaw rates"
       (let [speed (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 100 0 0))]
         (pitch-damping speed (basis 0) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area chord C-m-p wing-span) 1e-6)
         (pitch-damping speed (basis 1) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area chord C-m-q chord    ) 1e-6)
         (pitch-damping speed (basis 2) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area chord C-m-r wing-span) 1e-6)))


(facts "Compute pitch damping for given roll, pitch, and yaw rates"
       (let [speed (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 100 0 0))]
         (yaw-damping speed (basis 0) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-n-p wing-span) 1e-6)
         (yaw-damping speed (basis 1) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-n-q chord    ) 1e-6)
         (yaw-damping speed (basis 2) 1.225) => (roughly (* 0.25 1.225 100.0 reference-area wing-span C-n-r wing-span) 1e-6)))


(facts "Determine coefficient of roll moment due to aileron"
       (coefficient-of-roll-moment-aileron 0.6 (to-radians 0.0)) => 0.0
       (coefficient-of-roll-moment-aileron 0.6 (to-radians 3.0)) => (roughly (* -0.3935 (to-radians 3.0)) 1e-4))


(facts "Determine coefficient of pitch moment due to flaps"
       (coefficient-of-pitch-moment-flaps 0.6 (to-radians 0.0)) => 0.0
       (coefficient-of-pitch-moment-flaps 0.6 (to-radians 3.0)) => (roughly (* -0.4277 (to-radians 3.0)) 1e-4))


(facts "Determine coefficient of yaw moment due to rudder and ailerons"
       (coefficient-of-yaw-moment-rudder 0.6 (to-radians 0.0) (to-radians 0.0)) => 0.0
       (coefficient-of-yaw-moment-rudder 0.6 (to-radians 3.0) (to-radians 0.0)) => (roughly (* -0.0693 (to-radians 3.0)) 1e-4)
       (coefficient-of-yaw-moment-rudder 0.6 (to-radians 0.0) (to-radians 3.0)) => (roughly (*  0.0653 (to-radians 3.0)) 1e-4))


(facts "Compute roll control moment"
       (with-redefs [aerodynamics/coefficient-of-roll-moment-aileron
                     (fn [speed-mach ailerons] (facts ailerons => 0.01 speed-mach => 0.5) -0.004)]
         (roll-moment-control (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) (vec3 0.01 0 0) 320.0 1.225)
         => (roughly (* -0.004 0.5 1.225 (* 160 160) reference-area 0.5 wing-span) 1e-6)))


(facts "Compute pitch control moment"
       (with-redefs [aerodynamics/coefficient-of-pitch-moment-flaps
                     (fn [speed-mach flaps] (facts flaps => 0.01 speed-mach => 0.5) -0.004)]
         (pitch-moment-control (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) (vec3 0 0.01 0) 320.0 1.225)
         => (roughly (* -0.004 0.5 1.225 (* 160 160) reference-area chord) 1e-6)))


(facts "Compute yaw control moment"
       (with-redefs [aerodynamics/coefficient-of-yaw-moment-rudder
                     (fn [speed-mach rudder ailerons] (facts rudder => 0.01 ailerons => 0.02 speed-mach => 0.5) 0.001)]
         (yaw-moment-control (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 160 0 0)) (vec3 0.02 0 0.01) 320.0 1.225)
         => (roughly (* 0.001 0.5 1.225 (* 160 160) reference-area 0.5 wing-span) 1e-6)))


(facts "Convert vector from wind system to body system"
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 0 0)) (vec3 1 0 0))
       => (roughly-vector (vec3 1 0 0) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 0 1 0)) (vec3 1 0 0))
       => (roughly-vector (vec3 0 1 0) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 0 0 1)) (vec3 1 0 0))
       => (roughly-vector (vec3 0 0 1) 1e-6)
       (wind-to-body-system (linear-speed-in-body-system (q/->Quaternion 1 0 0 0) (vec3 1 1 1)) (vec3 (sqrt 3) 0 0))
       => (roughly-vector (vec3 1 1 1) 1e-6))


(facts "Determine aerodynamic forces and moments"
       (let [height       1000.0
             orientation  (q/->Quaternion 1.0 0.0 0.0 0.0)
             linear-speed (vec3 5.0 0.0 0.0)
             angular-speed (vec3 3.0 1.0 2.0)]
         (with-redefs [aerodynamics/reference-area 100.0
                       aerodynamics/wing-span 30.0
                       aerodynamics/chord 25.0
                       atmosphere/density-at-height
                       (fn [height] (facts height => 1000.0) :density)
                       atmosphere/temperature-at-height
                       (fn [height] (facts height => 1000.0) :temperature)
                       atmosphere/speed-of-sound
                       (fn [temperature] (facts temperature => temperature) :c-air)
                       aerodynamics/linear-speed-in-body-system
                       (fn [orientation speed] (facts orientation => (q/->Quaternion 1.0 0.0 0.0 0.0) speed => (vec3 5 0 0))
                           :speed-body)
                       aerodynamics/angular-speed-in-body-system
                       (fn [orientation speed] (facts orientation => (q/->Quaternion 1.0 0.0 0.0 0.0) speed => (vec3 3 1 2))
                           :angular-body)
                       wind-to-body-system
                       (fn [speed-body force-vector]
                           (facts speed-body => :speed-body)
                           force-vector)
                       aerodynamics/lift
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           2.0)
                       aerodynamics/drag
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           3.0)
                       aerodynamics/side-force
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           5.0)
                       aerodynamics/roll-moment
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           0.5)
                       aerodynamics/pitch-moment
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           0.125)
                       aerodynamics/yaw-moment
                       (fn [speed-body speed-of-sound density]
                           (facts speed-body => :speed-body speed-of-sound => :c-air density => :density)
                           0.25)
                       aerodynamics/roll-damping
                       (fn [speed-body rate density]
                           (facts speed-body => :speed-body rate => :angular-body density => :density)
                           -1.0)
                       aerodynamics/pitch-damping
                       (fn [speed-body rate density]
                           (facts speed-body => :speed-body rate => :angular-body density => :density)
                           -0.25)
                       aerodynamics/yaw-damping
                       (fn [speed-body rate density]
                           (facts speed-body => :speed-body rate => :angular-body density => :density)
                           -0.5)
                       aerodynamics/roll-moment-control
                       (fn [speed-body control speed-of-sound density]
                           (facts speed-body => :speed-body control => (vec3 0 1 2) speed-of-sound => :c-air density => :density)
                           0.0)
                       aerodynamics/pitch-moment-control
                       (fn [speed-body control speed-of-sound density]
                           (facts speed-body => :speed-body control => (vec3 0 1 2) speed-of-sound => :c-air density => :density)
                           0.0)
                       aerodynamics/yaw-moment-control
                       (fn [speed-body control speed-of-sound density]
                           (facts speed-body => :speed-body control => (vec3 0 1 2) speed-of-sound => :c-air density => :density)
                           0.0)]
           (:sfsim.aerodynamics/forces (aerodynamic-loads height orientation linear-speed angular-speed (vec3 0 1 2)))
           => (vec3 -3.0 5.0 -2.0)
           (:sfsim.aerodynamics/moments (aerodynamic-loads height orientation linear-speed angular-speed (vec3 0 1 2)))
           => (vec3 -0.5 -0.125 -0.25)
           (with-redefs [aerodynamics/linear-speed-in-body-system
                         (fn [orientation speed] (facts orientation => (q/->Quaternion 0.0 1.0 0.0 0.0) speed => (vec3 5 0 0))
                             :speed-body)
                       aerodynamics/angular-speed-in-body-system
                       (fn [orientation speed] (facts orientation => (q/->Quaternion 0.0 1.0 0.0 0.0) speed => (vec3 3 1 2))
                           :angular-body)]
             (:sfsim.aerodynamics/forces (aerodynamic-loads height (q/->Quaternion 0.0 1.0 0.0 0.0) linear-speed angular-speed
                                                            (vec3 0 1 2)))
             => (q/rotate-vector (q/->Quaternion 0.0 1.0 0.0 0.0) (vec3 -3.0 5.0 -2.0))
             (:sfsim.aerodynamics/moments (aerodynamic-loads height (q/->Quaternion 0.0 1.0 0.0 0.0) linear-speed angular-speed
                                                             (vec3 0 1 2)))
             => (q/rotate-vector (q/->Quaternion 0.0 1.0 0.0 0.0) (vec3 -0.5 -0.125 -0.25))))))
