(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (PI cos sin to-radians atan2 hypot)]
      [fastmath.matrix :refer (mat3x3 mulv)]
      [fastmath.vector :refer (vec3 mag)]
      [fastmath.interpolation :as interpolation]
      [malli.core :as m]
      [sfsim.matrix :refer [fvec3]]
      [sfsim.quaternion :as q]
      [sfsim.util :refer (sqr cube)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn cubic-hermite-spline
  "Create a cubic Hermite spline from two points with derivative"
  {:malli/schema [:=> [:cat :double :double :double :double :double :double] [:=> [:cat :double] :double]]}
  [x0 y0 dy0 x1 y1 dy1]
  (let [h (- x1 x0)]
    (fn cubic-spline-function [x]
        (let [t (/ (- x x0) h)
              t2 (sqr t)
              t3 (cube t)]
          (+ (* (+ (*  2 t3) (* -3 t2)   1) y0)
             (* (+       t3  (* -2 t2) t  ) h dy0)
             (* (+ (* -2 t3) (*  3 t2)    ) y1)
             (* (+       t3  (* -1 t2)    ) h dy1))))))


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
  {:malli/schema [:=> [:cat :double :double :double] :double]}
  [a b angle]
  (let [cos-angle (cos angle)]
    (* 0.5 (+ (* a (+ 1 cos-angle)) (* b (- 1 cos-angle))))))


(defn mirror
  "Mirror values at 90 degrees"
  {:malli/schema [:=> [:cat :double] :double]}
  [angle]
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
(def wing-span (* 2 21.1480))
(def chord 22.5714)
(def aspect-ratio (/ (sqr wing-span) wing-area))

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


(def c-l-lin (comp #(* 0.75 %) (akima-spline 0.0 20, 0.6 22, 0.8 24, 5.0 24, 10.0 30, 20.0 30, 30.0 30)))


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
  ([speed-mach alpha]
   (cond
     (neg? alpha)
     (- (coefficient-of-lift speed-mach (- alpha)))
     (< (to-radians 90.0) alpha)
     (- (coefficient-of-lift speed-mach (- (to-radians 180.0) alpha)))
     :else
     (let [[gradient linear alpha-peak peak] ((juxt c-l-alpha c-l-lin c-l-alpha-max c-l-max) speed-mach)]
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
  ([speed-mach alpha beta]
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
  [speed-mach alpha beta]
  (/ (sqr (coefficient-of-lift speed-mach alpha beta)) (* PI (oswald-factor speed-mach) aspect-ratio)))


(defn coefficient-of-drag
  "Determine coefficient of drag (negative x in wind system) depending on mach speed, angle of attack, and optionally angle of side-slip"
  ([speed-mach alpha]
   (coefficient-of-drag speed-mach alpha 0.0))
  ([speed-mach alpha beta]
   (mix (mix (+ (c-d-0 speed-mach) (coefficient-of-induced-drag speed-mach alpha beta)) (c-d-90 speed-mach) (* 2 alpha))
        (* (/ body-length body-width) (c-d-0 speed-mach))
        (* 2 beta))))


(def c-y-beta -0.05)
(def c-y-alpha 0.1)


(defn coefficient-of-side-force
  "Determine coefficient of side force (positive y in wind system) depending on angle of attack and optionally angle of side-slip"
  ([beta]
   (* 0.5 c-y-beta (sin (* 2 beta))))
  ([alpha beta]
   (* (+ (* 0.5 c-y-beta) (* c-y-alpha (sin alpha))) (sin (* 2 beta)))))  ; TODO: negative alpha same effect as positive alpha?


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
  ([speed-mach alpha]
   (* (coefficient-of-lift speed-mach alpha) 0.01 (- (x-neutral-percent speed-mach) x-ref-percent)))
  ([speed-mach alpha beta]  ; TODO: side force participation with increasing beta
   (* (coefficient-of-lift speed-mach alpha beta) 0.01 (- (x-neutral-percent speed-mach) x-ref-percent))))


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
  {:malli/schema [:=> [:cat :double :double] :double]}
  [speed-mach beta]
  (* (c-n-beta speed-mach) (sin beta)))


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


(defn coefficient-of-roll-moment
  "Determine coefficient of roll moment depending on speed, angle of side-slip and angle of attack"
  {:malli/schema [:=> [:cat :double :double :double] :double]}
  [speed-mach alpha beta]
  (* 0.5 (c-l-beta-alpha speed-mach) (sin (* 2 beta)) (sin alpha)))


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
  [density speed]
  (* 0.5 density (sqr speed)))


(defn lift
  "Compute lift for given speed in body system"
  {:malli/schema [:=> [:cat speed-data :double :double] :double]}
  [{::keys [alpha beta speed]} speed-of-sound density]
  (* (coefficient-of-lift (/ speed speed-of-sound) alpha beta) (dynamic-pressure density speed) reference-area))


(defn drag
  "Compute drag for given speed in body system"
  {:malli/schema [:=> [:cat speed-data :double :double] :double]}
  [{::keys [alpha beta speed]} speed-of-sound density]
  (* (coefficient-of-drag (/ speed speed-of-sound) alpha beta) (dynamic-pressure density speed) reference-area))


(defn side-force
  "Compute side-force for given speed in body system"
  {:malli/schema [:=> [:cat speed-data :double :double] :double]}
  [{::keys [alpha beta speed]} _speed-of-sound density]
  (* (coefficient-of-side-force alpha beta) (dynamic-pressure density speed) reference-area))


; (defn pitch-moment
;   "Compute pitch moment for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double :double] :double]}
;   [{::keys [alpha beta speed]} density surface chord]
;   (* 0.5 (coefficient-of-pitch-moment alpha beta) density (sqr speed) surface chord))
;
;
; (defn yaw-moment
;   "Compute yaw moment for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double :double] :double]}
;   [{::keys [beta speed]} density surface wingspan]
;   (* 0.5 (coefficient-of-yaw-moment beta) density (sqr speed) surface wingspan))
;
;
; (defn roll-moment
;   "Compute roll moment for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double :double] :double]}
;   [{::keys [beta speed]} density surface wingspan]
;   (* 0.5 (coefficient-of-roll-moment beta) density (sqr speed) surface wingspan))


(def C-l-p -0.4228)
(def C-m-p  0.0000)
(def C-n-p  0.0535)
(def C-l-q  0.0000)
(def C-m-q -1.3470)
(def C-n-q  0.0000)
(def C-l-r  0.1929)
(def C-m-r  0.0000)
(def C-n-r -0.2838)

(defn roll-damping
  "Compute roll damping for given roll, pitch, and yaw rates"
  [density speed roll-rate pitch-rate yaw-rate]
  (* 0.25 density speed reference-area wing-span
     (+ (* C-l-p roll-rate wing-span) (* C-l-q pitch-rate chord) (* C-l-r yaw-rate wing-span))))


(defn pitch-damping
  "Compute pitch damping for given roll, pitch, and yaw rates"
  [density speed roll-rate pitch-rate yaw-rate]
  (* 0.25 density speed reference-area chord
     (+ (* C-m-p roll-rate wing-span) (* C-m-q pitch-rate chord) (* C-m-r yaw-rate wing-span))))


(defn yaw-damping
  "Compute yaw damping for given roll, pitch, and yaw rates"
  [density speed roll-rate pitch-rate yaw-rate]
  (* 0.25 density speed reference-area wing-span
     (+ (* C-n-p roll-rate wing-span) (* C-n-q pitch-rate chord) (* C-n-r yaw-rate wing-span))))


; (defn aerodynamic-loads
;   "Determine aerodynamic forces and moments"
;   {:malli/schema [:=> [:cat :double q/quaternion fvec3 fvec3 :double :double :double] :some]}
;   [height orientation linear-speed angular-speed surface wingspan chord]
;   (let [density             (density-at-height height)
;         speed               (linear-speed-in-body-system orientation linear-speed)
;         rotation            (angular-speed-in-body-system orientation angular-speed)
;         forces              (vec3 (- (drag speed density surface))
;                                   (side-force speed density surface)
;                                   (- (lift speed density surface)))
;         aerodynamic-moments (vec3 (roll-moment speed density surface wingspan)
;                                   (pitch-moment speed density surface chord)
;                                   (yaw-moment speed density surface wingspan))
;         damping-moments     (vec3 (roll-damping speed (rotation 0) density surface wingspan)
;                                   (pitch-damping speed (rotation 1) density surface chord)
;                                   (yaw-damping speed (rotation 2) density surface wingspan))]
;     {::forces (q/rotate-vector orientation (wind-to-body-system speed forces))
;      ::moments (q/rotate-vector orientation (add aerodynamic-moments damping-moments))}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
