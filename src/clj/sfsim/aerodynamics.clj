;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (PI cos sin to-radians atan2 hypot exp)]
      [fastmath.matrix :refer (mat3x3 mulv)]
      [fastmath.vector :refer (vec3 mag add)]
      [fastmath.interpolation :as interpolation]
      [malli.core :as m]
      [sfsim.matrix :refer [fvec3]]
      [sfsim.quaternion :as q]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.util :refer (sqr cube)])
    (:import
      [fastmath.vector
       Vec3]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn cubic-hermite-spline
  "Create a cubic Hermite spline from two points with derivative"
  {:malli/schema [:=> [:cat :double :double :double :double :double :double] [:=> [:cat :double] :double]]}
  [x0 y0 dy0 x1 y1 dy1]
  (let [h (- ^double x1 ^double x0)]
    (fn cubic-spline-function [x]
        (let [t (/ (- ^double x ^double x0) h)
              t2 (sqr t)
              t3 (cube t)]
          (+ (* (+ (*  2.0 ^double t3) (* -3.0 ^double t2) 1.0) ^double y0)
             (* (+         ^double t3  (* -2.0 ^double t2)   t) ^double h ^double dy0)
             (* (+ (* -2.0 ^double t3) (*  3.0 ^double t2)    ) ^double y1)
             (* (+         ^double t3  (* -1.0 ^double t2)    ) ^double h ^double dy1))))))


(defn logistic-function
  "Logistic function, 1 / (1 + exp(-x))"
  ^double [^double x]
  (/ 1 (+ 1 (exp (- x)))))


(defn limiting-function
  "Function with derivative equal to 1 at x = 0 and y=h as assymptote"
  [^double h]
  (fn ^double [^double x] (* 2.0 h (- (logistic-function (/ (* 2.0 x) h)) 0.5))))


(defn piecewise
  "Create piecewise function from intervals interleaved with functions"
  {:malli/schema [:=> [:cat [:* [:cat [:tuple :double :double] [:=> [:cat :double] :double]]]] [:=> [:cat :double] :double]]}
  [[start end] function & remaining]
  (fn piecewise-function [x]
      (if (<= start x end)
        (function x)
        ((apply piecewise remaining) x))))


(defn piecewise-linear
  "Create piecewise linear function from series of interleaved x and y coordinates"
  {:malli/schema [:=> [:cat [:* [:cat :double :double]]] [:=> [:cat :double] :double]]}
  [& args]
  (let [points (partition 2 args)
        x      (map first points)
        y      (map second points)]
    (interpolation/linear-smile x y)))


(defn cubic-spline
  "Create cubic spline function from series of interleaved x and y coordinates"
  {:malli/schema [:=> [:cat [:* [:cat :double :double]]] [:=> [:cat :double] :double]]}
  [& args]
  (let [points (partition 2 args)
        x      (map first points)
        y      (map second points)]
    (interpolation/cubic-spline x y)))


(defn akima-spline
  "Create Akima spline function from series of interleaved x and y coordinates"
  {:malli/schema [:=> [:cat [:* [:cat :double :double]]] [:=> [:cat :double] :double]]}
  [& args]
  (let [points (partition 2 args)
        x      (map first points)
        y      (map second points)]
    (interpolation/akima-spline x y)))


(defn mix
  "Mix two values depending on angle"
  ^double [^double a ^double b ^double angle]
  (let [cos-angle (cos angle)]
    (* 0.5 (+ (* a (+ 1.0 cos-angle)) (* b (- 1.0 cos-angle))))))


(defn mirror
  "Mirror values at 90 degrees"
  ^double [^double angle]
  (if (>= angle 0.0)
    (- PI angle)
    (- (- PI) angle)))


(defn speed-x
  "Airplane x-coordinate (forward) of speed vector in body system for angle of attack and side slip angle"
  {:malli/schema [:=> [:cat :double :double] :double]}
  [angle-of-attack angle-of-side-slip]
  (* (cos angle-of-attack) (cos angle-of-side-slip)))


(defn speed-y
  "Airplane y-coordinate (right) of speed vector in body system for angle of attack and side slip angle"
  {:malli/schema [:=> [:cat :double :double] :double]}
  [_angle-of-attack angle-of-side-slip]
  (sin angle-of-side-slip))


(defn speed-z
  "Airplane z-coordinate (down) of speed vector in body system for angle of attack and side slip angle"
  {:malli/schema [:=> [:cat :double :double] :double]}
  [angle-of-attack angle-of-side-slip]
  (* (sin angle-of-attack) (cos angle-of-side-slip)))


(defn speed-vector
  "Speed vector in aircraft body system for given angle of attack and side slip angle"
  {:malli/schema [:=> [:cat :double :double] fvec3]}
  [angle-of-attack angle-of-side-slip]
  (vec3 (speed-x angle-of-attack angle-of-side-slip)
        (speed-y angle-of-attack angle-of-side-slip)
        (speed-z angle-of-attack angle-of-side-slip)))


(defn angle-of-attack
  "Get angle of attack from speed vector in aircraft body system"
  {:malli/schema [:=> [:cat fvec3] :double]}
  [speed-vector]
  (atan2 (speed-vector 2) (speed-vector 0)))


(defn angle-of-side-slip
  "Get angle of side-slip from speed vector in aircraft body system"
  {:malli/schema [:=> [:cat fvec3] :double]}
  [speed-vector]
  (atan2 (speed-vector 1) (hypot (speed-vector 2) (speed-vector 0))))


(def gltf-to-aerodynamic (mat3x3 1 0 0, 0 0 1, 0 -1 0))


(defn gltf->aerodynamic
  "Convert glTF model coordinates (x: forward, y: up, z:right) to aerodynamic body ones (x: forward, y: right, z: down)"
  {:malli/schema [:=> [:cat fvec3] fvec3]}
  [gltf-vector]
  (mulv gltf-to-aerodynamic gltf-vector))


(def aerodynamic-to-gltf (mat3x3 1 0 0, 0 0 -1, 0 1 0))


(defn aerodynamic->gltf
  "Convert aerodynamic body coordinates (x: forward, y: right, z: down) to glTF model ones (x: forward, y: up, z:right)"
  {:malli/schema [:=> [:cat fvec3] fvec3]}
  [aerodynamic-vector]
  (mulv aerodynamic-to-gltf aerodynamic-vector))


(def wing-area 682.1415)
(def reference-area 668.7206)
(def wing-span (* 2.0 21.1480))
(def chord 22.5714)
(def aspect-ratio (/ (sqr wing-span) ^double wing-area))

(def body-width 23.0)
(def body-length 35.0)

(def c-l-alpha (akima-spline
                 0.0 2.5596
                 0.6 2.7825
                 0.8 3.0453
                 1.2 2.8237
                 1.4 2.7156
                 1.6 2.3735
                 1.8 2.1063
                 2.0 1.8934
                 3.0 1.3273
                 4.0 0.9907
                 5.0 0.7816
                 10.0 2.0000
                 20.0 2.0000
                 30.0 2.0000))


(def c-l-lin (comp #(* 0.75 ^double %) (akima-spline 0.0 20, 0.6 22, 0.8 24, 5.0 24, 10.0 30, 20.0 30, 30.0 30)))


(def c-l-alpha-max (akima-spline 0.0 35 0.6 33 0.8 30 5.0 30 10.0 45, 20.0 45, 30.0 45))


(def c-l-max (akima-spline
               0.0  1.20
               0.6  1.30
               0.8  1.40
               1.2  1.20
               1.4  1.10
               1.6  1.00
               1.8  0.90
               2.0  0.80
               3.0  0.50
               4.0  0.40
               5.0  0.35
               10.0 1.00
               20.0 1.00
               30.0 1.00))


(defn coefficient-of-lift
  "Determine coefficient of lift (negative z in wind system) depending on mach speed, angle of attack, and optional angle of side-slip"
  (^double [^double speed-mach ^double alpha]
   (cond
     (neg? alpha)
     (- (coefficient-of-lift speed-mach (- alpha)))
     (< (to-radians 90.0) alpha)
     (- (coefficient-of-lift speed-mach (- (to-radians 180.0) alpha)))
     :else
     (let [[^double gradient ^double linear ^double alpha-peak ^double peak]
           ((juxt c-l-alpha c-l-lin c-l-alpha-max c-l-max) speed-mach)]
       (cond
         (< alpha (to-radians linear))
         (* gradient alpha)
         (< alpha (to-radians alpha-peak))
         ((cubic-hermite-spline (to-radians linear)     (* gradient (to-radians linear)) gradient
                                (to-radians alpha-peak) peak                             0.0)
          alpha)
         (<= alpha (to-radians 90.0))
         ((cubic-hermite-spline (to-radians alpha-peak) peak 0.0
                                (to-radians 90)         0.0  (/ (- peak) 0.75 (to-radians (- 90 alpha-peak))))
          alpha)))))
  (^double [^double speed-mach ^double alpha ^double beta]
   (* (mix (coefficient-of-lift speed-mach alpha) (- (coefficient-of-lift speed-mach (mirror alpha))) beta) (cos beta))))


(def c-d-0 (akima-spline
             0.0  0.04780
             0.6  0.04741
             0.8  0.04728
             1.2  0.26410
             1.4  0.26382
             1.6  0.26355
             1.8  0.26327
             2.0  0.26299
             3.0  0.20472
             4.0  0.15512
             5.0  0.13197
             10.0 0.21126
             20.0 0.21126
             30.0 0.21126))


(def c-d-90 (akima-spline
              0.0   1.1000
              0.6   1.1000
              0.8   1.1000
              1.2   2.3884
              1.4   1.4936
              1.6   1.5653
              1.8   1.6180
              2.0   1.6573
              3.0   1.7557
              4.0   1.7918
              5.0   1.8088
              10.0  1.8317
              20.0  1.8317
              30.0  1.8317))


(def oswald-factor (akima-spline
                     0.0  0.9846
                     0.6  0.9859
                     0.8  0.9873
                     1.2  0.3359
                     1.4  0.3167
                     1.6  0.2817
                     1.8  0.2504
                     2.0  0.2255
                     3.0  0.1579
                     4.0  0.1179
                     5.0  0.0930
                     10.0 0.0264
                     20.0 0.0264
                     30.0 0.0264))


(defn coefficient-of-induced-drag
  "Determine drag caused by lift depending on speed, angle of attack and angle of side slip"
  ^double [^double speed-mach ^double alpha beta]
  (/ (sqr (coefficient-of-lift speed-mach alpha beta)) (* PI ^double (oswald-factor speed-mach) ^double aspect-ratio)))


(defn drag-multiplier
  ^double [^double gear ^double air-brake]
  (+ 1.0 (* 0.2 gear) (* 0.4 air-brake)))


(defn coefficient-of-drag
  "Determine coefficient of drag (negative x in wind system) depending on mach speed, angle of attack, and optionally angle of side-slip"
  ([^double speed-mach alpha]
   (coefficient-of-drag speed-mach alpha 0.0 0.0 0.0))
  ([^double speed-mach alpha beta]
   (coefficient-of-drag speed-mach alpha beta 0.0 0.0))
  ([speed-mach alpha beta gear air-brake]
   (mix (mix (+ (* (drag-multiplier gear air-brake) ^double (c-d-0 speed-mach))
                (coefficient-of-induced-drag speed-mach alpha beta)) ^double (c-d-90 speed-mach) (* 2.0 ^double alpha))
        (* (/ ^double body-length ^double body-width) (drag-multiplier gear 0.0) ^double (c-d-0 speed-mach))
        (* 2.0 ^double beta))))


(def c-y-beta-r (akima-spline
                  0.0   0.0798
                  0.6   0.0875
                  0.8   0.0966
                  1.2   0.1201
                  1.4   0.0813
                  1.6   0.0638
                  1.8   0.0532
                  2.0   0.0460
                  3.0   0.0282
                  4.0   0.0206
                  5.0   0.0163
                  10.0  0.0398
                  20.0  0.0398
                  30.0  0.0398))


(defn c-y-beta
  "Estimated coefficient of side force gradient for side slip angle"
  ^double [^double speed-mach]
  (* -0.25 ^double (c-l-alpha speed-mach)))


(defn c-y-alpha
  "Estimated coefficient of side force gradient for angle of attack"
  ^double [^double speed-mach]
  (* 0.5 ^double (c-l-alpha speed-mach)))


(defn coefficient-of-side-force
  "Determine coefficient of side force (positive y in wind system) depending on angle of attack and optionally angle of side-slip"
  (^double [^double speed-mach ^double beta]
   (* 0.5 (c-y-beta speed-mach) (sin (* 2.0 beta))))
  (^double [^double speed-mach ^double alpha ^double beta]
   (* (mix (* 0.5 (c-y-beta speed-mach)) (* 0.5 (c-y-alpha speed-mach)) (* 2 alpha))
      (sin (* 2.0 beta))))
  (^double [^double speed-mach ^double alpha ^double beta ^double rudder]
   (+ (coefficient-of-side-force speed-mach alpha beta)
      (* 0.5 ^double (c-y-beta-r speed-mach) (cos beta) (sin (* 2.0 rudder))))))


(def x-ref-percent 25.0)
(def x-neutral-percent (akima-spline
                         0.0  25.7408
                         0.6  25.8613
                         0.8  26.0130
                         1.2  26.3814
                         1.4  26.2970
                         1.6  26.0632
                         1.8  25.8788
                         2.0  25.7365
                         3.0  25.4226
                         4.0  25.2999
                         5.0  25.2361
                         10.0 50.0000
                         20.0 50.0000
                         30.0 50.0000))


(defn coefficient-of-pitch-moment
  (^double [^double speed-mach ^double alpha]
   (coefficient-of-pitch-moment speed-mach alpha 0.0))
  (^double [^double speed-mach ^double alpha ^double beta]  ; TODO: side force participation with increasing beta
   (* (coefficient-of-lift speed-mach alpha beta) 0.01 (- ^double x-ref-percent ^double (x-neutral-percent speed-mach)))))


(def c-n-beta (akima-spline
                0.0   0.0369
                0.6   0.0578
                0.8   0.0923
                1.2   0.3199
                1.4   0.1797
                1.6   0.1164
                1.8   0.0782
                2.0   0.0521
                3.0  -0.0123
                4.0  -0.0398
                5.0  -0.0554
                10.0 -0.0290
                20.0 -0.0290
                30.0 -0.0290))


(defn coefficient-of-yaw-moment
  "Determine coefficient of yaw moment depending on speed and angle of side-slip"
  ^double [^double speed-mach ^double beta]
  (* ^double (c-n-beta speed-mach) (sin beta)))


(def c-l-beta-alpha (akima-spline
                      0.0   -2.9217
                      0.6   -3.1333
                      0.8   -3.3667
                      1.2    0.5971
                      1.4   -0.8761
                      1.6   -0.0015
                      1.8   -0.1113
                      2.0   -0.0751
                      3.0   -0.1459
                      4.0    0.0981
                      5.0    0.0463
                      10.0   0.0000
                      20.0   0.0000
                      30.0   0.0000))


(def limit-range (limiting-function 0.3))

(defn coefficient-of-roll-moment
  "Determine coefficient of roll moment depending on speed, angle of side-slip and angle of attack"
  ^double [^double speed-mach ^double alpha ^double beta]
  (* 0.25 ^double (c-l-beta-alpha speed-mach) ^double (limit-range (sin (* 2.0 beta))) ^double (limit-range (sin (* 2.0 alpha)))))


(def speed-data (m/schema [:map [::alpha :double] [::beta :double] [::speed :double]]))


(defn linear-speed-in-body-system
  "Convert airplane linear speed vector to angle of attack, side slip angle, and speed magnitude"
  {:malli/schema [:=> [:cat q/quaternion fvec3] speed-data]}
  [orientation linear-speed]
  (let [speed-rotated (q/rotate-vector (q/inverse orientation) linear-speed)]
    {::alpha (angle-of-attack speed-rotated)
     ::beta  (angle-of-side-slip speed-rotated)
     ::speed (mag linear-speed)}))


(defn wind-to-body-system
  "Convert vector from wind system into body system"
  {:malli/schema [:=> [:cat speed-data fvec3] fvec3]}
  [{::keys [alpha beta _speed]} force-vector]
  (q/rotate-vector (q/* (q/rotation alpha (vec3 0 -1 0)) (q/rotation beta (vec3 0 0 1))) force-vector))


(defn angular-speed-in-body-system
  "Convert airplane angular speed vector to body system"
  {:malli/schema [:=> [:cat q/quaternion fvec3] fvec3]}
  [orientation angular-speed]
  (q/rotate-vector (q/inverse orientation) angular-speed))


(defn dynamic-pressure
  "Compute dynamic pressure for given speed in body system"
  ^double [^double density ^double speed]
  (* 0.5 density (sqr speed)))


(def C-l-q 4.2624)


(defn lift
  "Compute lift for given speed in body system"
  ^double [{::keys [^double alpha ^double beta ^double speed]} ^Vec3 rotation ^double speed-of-sound ^double density]
  (* (+ (coefficient-of-lift (/ speed speed-of-sound) alpha beta) (* ^double C-l-q ^double (rotation 1) (cos alpha) (cos beta)))
     (dynamic-pressure density speed) ^double reference-area))


(defn drag
  "Compute drag for given speed in body system"
  [{::keys [^double alpha ^double beta ^double speed]} speed-of-sound density gear air-brake]
  (* ^double (coefficient-of-drag (/ speed ^double speed-of-sound) alpha beta gear air-brake)
     (dynamic-pressure density speed) ^double reference-area))


(defn side-force
  "Compute side-force for given speed in body system"
  (^double [{::keys [^double alpha ^double beta ^double speed]} ^double _speed-of-sound ^double density]
   (* (coefficient-of-side-force alpha beta) (dynamic-pressure density speed) ^double reference-area))
  (^double [{::keys [^double alpha ^double beta ^double speed]} ^Vec3 control ^double speed-of-sound ^double density]
   (* (coefficient-of-side-force (/ speed speed-of-sound) alpha beta (control 2))
      (dynamic-pressure density speed) ^double reference-area)))


(defn roll-moment
  "Compute roll moment for given speed in body system"
  ^double [{::keys [^double alpha ^double beta ^double speed]} ^double speed-of-sound ^double density]
  (* (coefficient-of-roll-moment (/ speed speed-of-sound) alpha beta) (dynamic-pressure density speed) ^double reference-area
     0.5 ^double wing-span))


(defn pitch-moment
  "Compute pitch moment for given speed in body system"
  ^double [{::keys [^double alpha ^double beta ^double speed]} ^double speed-of-sound ^double density]
  (* (coefficient-of-pitch-moment (/ speed speed-of-sound) alpha beta) (dynamic-pressure density speed) ^double reference-area
     ^double chord))


(defn yaw-moment
  "Compute yaw moment for given speed in body system"
  ^double [{::keys [^double beta ^double speed]} ^double speed-of-sound ^double density]
  (* (coefficient-of-yaw-moment (/ speed speed-of-sound) beta) (dynamic-pressure density speed) ^double reference-area
     0.5 ^double wing-span))


(def c-l-p -0.4228)
(def c-m-p  0.0000)
(def c-n-p  0.0535)
(def c-l-q  0.0000)
(def c-m-q -1.3470)
(def c-n-q  0.0000)
(def c-l-r  0.1929)
(def c-m-r  0.0000)
(def c-n-r -0.2838)


(defn roll-damping
  "Compute roll damping for given roll, pitch, and yaw rates"
  ^double [{::keys [^double speed]} [^double roll-rate ^double pitch-rate ^double yaw-rate] ^double density]
  (* 0.25 density speed ^double reference-area ^double wing-span
     (+ (* ^double c-l-p roll-rate ^double wing-span)
        (* ^double c-l-q pitch-rate ^double chord)
        (* ^double c-l-r yaw-rate ^double wing-span))))


(defn pitch-damping
  "Compute pitch damping for given roll, pitch, and yaw rates"
  ^double [{::keys [^double speed]} [^double roll-rate ^double pitch-rate ^double yaw-rate] ^double density]
  (* 0.25 density speed ^double reference-area ^double chord
     (+ (* ^double c-m-p roll-rate ^double wing-span)
        (* ^double c-m-q pitch-rate ^double chord)
        (* ^double c-m-r yaw-rate ^double wing-span))))


(defn yaw-damping
  "Compute yaw damping for given roll, pitch, and yaw rates"
  ^double [{::keys [^double speed]} [^double roll-rate ^double pitch-rate ^double yaw-rate] ^double density]
  (* 0.25 density speed ^double reference-area ^double wing-span
     (+ (* ^double c-n-p roll-rate ^double wing-span)
        (* ^double c-n-q pitch-rate ^double chord)
        (* ^double c-n-r yaw-rate ^double wing-span))))


(def c-l-xi-a (akima-spline
                0.0   -0.3566
                0.6   -0.3935
                0.8   -0.4379
                1.2   -0.3715
                1.4   -0.2515
                1.6   -0.1973
                1.8   -0.1647
                2.0   -0.1423
                3.0   -0.0871
                4.0   -0.0636
                5.0   -0.0503
                10.0  -0.1154
                20.0  -0.1154
                30.0  -0.1154))


(def c-m-delta-f (akima-spline
                   0.0  -0.3736
                   0.6  -0.4277
                   0.8  -0.4976
                   1.2  -0.4226
                   1.4  -0.2861
                   1.6  -0.2244
                   1.8  -0.1873
                   2.0  -0.1618
                   3.0  -0.0991
                   4.0  -0.0724
                   5.0  -0.0572
                   10.0 -0.1402
                   20.0 -0.1402
                   30.0 -0.1402))


(def c-n-beta-r (akima-spline
                  0.0   -0.0632
                  0.6   -0.0693
                  0.8   -0.0765
                  1.2   -0.0951
                  1.4   -0.0644
                  1.6   -0.0505
                  1.8   -0.0421
                  2.0   -0.0364
                  3.0   -0.0201
                  4.0   -0.0147
                  5.0   -0.0116
                  10.0  -0.0284
                  20.0  -0.0284
                  30.0  -0.0284))


(def c-n-xi-a (akima-spline
                0.0   0.0586
                0.6   0.0653
                0.8   0.0737
                1.2   0.0000
                1.4   0.0000
                1.6   0.0000
                1.8   0.0000
                2.0   0.0000
                3.0   0.0000
                4.0   0.0000
                5.0   0.0000
                10.0  0.0000
                20.0  0.0000
                30.0  0.0000))


(defn coefficient-of-roll-moment-aileron
  "Determine coefficient of roll moment due to ailerons"
  ^double [^double speed-mach ^double ailerons]
  (* 0.5 ^double (c-l-xi-a speed-mach) (sin (* 2.0 ailerons))))


(defn coefficient-of-pitch-moment-flaps
  "Determine coefficient of pitch moment due to flaps"
  ^double [^double speed-mach ^double flaps]
  (* 0.5 ^double (c-m-delta-f speed-mach) (sin (* 2.0 flaps))))


(defn coefficients-of-yaw-moment-rudder-ailerons
  "Determine coefficient of yaw moment due to rudder and ailerons"
  [^double speed-mach ^double rudder ^double ailerons]
  [(* 0.5 ^double (c-n-beta-r speed-mach) (sin (* 2.0 rudder)))
   (* 0.5 ^double (c-n-xi-a speed-mach) (sin (* 2.0 ailerons)))])


(def aileron-scaling 0.25)


(defn roll-moment-control
  "Compute roll moment due to control surfaces"
  ^double [{::keys [^double speed ^double alpha ^double beta]} ^Vec3 control ^double speed-of-sound ^double density]
  (* (coefficient-of-roll-moment-aileron (/ speed speed-of-sound) (* ^double aileron-scaling ^double (control 0)))
     (cos alpha) (cos beta)
     (dynamic-pressure density speed) ^double reference-area 0.5 ^double wing-span))


(def elevator-scaling 0.25)


(defn pitch-moment-control
  "Compute pitch moment due to control surfaces"
  ^double [{::keys [^double speed ^double alpha ^double beta]} ^Vec3 control ^double speed-of-sound ^double density]
  (let [flaps (control 1)]
    (* (coefficient-of-pitch-moment-flaps (/ speed speed-of-sound) (* ^double elevator-scaling ^double flaps))
       (dynamic-pressure density speed) ^double reference-area ^double chord
       (cos (+ alpha (* 0.5 ^double flaps))) (cos beta))))


(def rudder-scaling 0.4)


(defn yaw-moment-control
  "Compute yaw moment for given speed in body system"
  ^double [{::keys [^double speed ^double alpha ^double beta]} ^Vec3 control ^double speed-of-sound ^double density]
  (let [rudder                        (control 2)
        ailerons                      (control 0)
        [coeff-rudder coeff-ailerons] (coefficients-of-yaw-moment-rudder-ailerons (/ speed speed-of-sound)
                                                                                  (* ^double rudder-scaling ^double rudder)
                                                                                  (* ^double aileron-scaling ^double ailerons))]
    (* (+ (* ^double coeff-rudder (cos (- beta (* 0.5 ^double rudder)))) (* ^double coeff-ailerons (cos beta)))
       (cos alpha)
       (dynamic-pressure density speed) ^double reference-area 0.5 ^double wing-span)))


(defn aerodynamic-loads
  "Determine aerodynamic forces and moments"
  {:malli/schema [:=> [:cat :double q/quaternion fvec3 fvec3 fvec3 :double :double] :some]}
  [height orientation linear-speed angular-speed control gear air-brake]
  (let [density             (atmosphere/density-at-height height)
        temperature         (atmosphere/temperature-at-height height)
        speed-of-sound      (atmosphere/speed-of-sound temperature)
        speed               (linear-speed-in-body-system orientation linear-speed)
        rotation            (angular-speed-in-body-system orientation angular-speed)
        forces              (vec3 (- ^double (drag speed speed-of-sound density gear air-brake))
                                  (side-force speed control speed-of-sound density)
                                  (- (lift speed rotation speed-of-sound density)))
        aerodynamic-moments (vec3 (roll-moment speed speed-of-sound density)
                                  (pitch-moment speed speed-of-sound density)
                                  (yaw-moment speed speed-of-sound density))
        damping-moments     (vec3 (roll-damping speed rotation density)
                                  (pitch-damping speed rotation density)
                                  (yaw-damping speed rotation density))
        control-moments     (vec3 (roll-moment-control speed control speed-of-sound density)
                                  (pitch-moment-control speed control speed-of-sound density)
                                  (yaw-moment-control speed control speed-of-sound density))]
    {::forces (q/rotate-vector orientation (wind-to-body-system speed forces))
     ::moments (q/rotate-vector orientation (add (add aerodynamic-moments damping-moments) control-moments))}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
