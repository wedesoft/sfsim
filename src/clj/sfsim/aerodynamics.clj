(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (PI cos sin sqrt to-radians atan2 hypot)]
      [fastmath.matrix :refer (mat3x3 mulv)]
      [fastmath.vector :refer (vec3 mag add)]
      [fastmath.interpolation :as interpolation]
      [malli.core :as m]
      [sfsim.matrix :refer [fvec3]]
      [sfsim.quaternion :as q]
      [sfsim.atmosphere :refer (density-at-height)]
      [sfsim.util :refer (sqr cube)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn linear
  "Create linear function from two points"
  {:malli/schema [:=> [:cat :double :double :double :double] [:=> [:cat :double] :double]]}
  [x0 y0 x1 y1]
  (fn [x] (/ (+ (* y0 (- x1 x)) (* y1 (- x x0))) (- x1 x0))))


(defn cubic-hermite-spline
  "Create a cubic Hermite spline from two points with derivative"
  {:malli/schema [:=> [:cat :double :double :double :double :double :double] [:=> [:cat :double] :double]]}
  [x0 y0 dy0 x1 y1 dy1]
  (let [h (- x1 x0)]
    (fn [x]
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
  (fn [x]
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


(defn basic-drag
  "Basic cosine shaped drag function"
  {:malli/schema [:=> [:cat :double :double] [:=> [:cat :double] :double]]}
  [min-drag max-drag]
  (fn [angle-of-attack] (mix min-drag max-drag (* 2 angle-of-attack))))


(defn basic-lift
  "Basic sinus shaped lift function"
  {:malli/schema [:=> [:cat :double] [:=> [:cat :double] :double]]}
  [max-lift]
  (fn [angle] (* max-lift (sin (* 2 angle)))))


(defn fall-off
  "Ellipse-like fall-off function"
  {:malli/schema [:=> [:cat :double :double] [:=> [:cat :double] :double]]}
  [max-increase interval]
  (fn [angle-of-attack]
      (let [relative-position (/ (- interval angle-of-attack) interval)]
        (if (>= relative-position 0.0)
          (* max-increase (- 1.0 (sqrt (- 1.0 (sqr relative-position)))))
          0.0))))


(defn glide
  "Increase of lift for small angles of attack before stall"
  {:malli/schema [:=> [:cat :double :double :double :double] [:=> [:cat :double] :double]]}
  [max-increase stall-angle reduced-increase fall-off-interval]
  (fn glide-fn [angle-of-attack]
      (if (neg? angle-of-attack)
        (- (glide-fn (- angle-of-attack)))
        (if (<= angle-of-attack stall-angle)
          (* (/ max-increase stall-angle) angle-of-attack)
          ((fall-off reduced-increase fall-off-interval) (- angle-of-attack stall-angle))))))


(defn bumps
  "Bumps to add to drag before 180 and -180 degrees"
  {:malli/schema [:=> [:cat :double :double] [:=> [:cat :double] :double]]}
  [max-increase interval]
  (fn [angle-of-attack]
      (let [relative-pos (/ (- (abs angle-of-attack) (- PI interval)) interval)]
        (if (pos? relative-pos)
          (* 0.5 max-increase (- 1 (cos (* 2 PI relative-pos))))
          0.0))))


(defn tail
  "Lift increase to add near 180 and -180 degrees"
  {:malli/schema [:=> [:cat :double :double :double] [:=> [:cat :double] :double]]}
  [max-increase ramp-up ramp-down]
  (fn [angle-of-attack]
      (let [relative-pos (if (pos? angle-of-attack) (- angle-of-attack PI) (+ angle-of-attack PI))]
        (if (<= (abs relative-pos) ramp-up)
          (/ (* max-increase relative-pos) ramp-up)
          (if (<= (abs relative-pos) (+ ramp-up ramp-down))
            (if (pos? angle-of-attack)
              (- (* max-increase (/ (- ramp-down (- PI angle-of-attack ramp-up)) ramp-down)))
              (* max-increase (/ (- ramp-down (- (+ angle-of-attack PI) ramp-up)) ramp-down)))
            0.0)))))


(defn compose
  "Compose an aerodynamic curve"
  {:malli/schema [:=> [:cat [:* fn?]] fn?]}
  [& funs]
  (comp (partial apply +) (apply juxt funs)))


(defn mirror
  "Mirror values at 90 degrees"
  {:malli/schema [:=> [:cat :double] :double]}
  [angle]
  (if (>= angle 0.0)
    (- PI angle)
    (- (- PI) angle)))


(defn spike
  "Spike function with linear and sinusoidal ramp"
  {:malli/schema [:=> [:cat :double :double :double] [:=> [:cat :double] :double]]}
  [max-increase ramp-up ramp-down]
  (fn spike-fn [angle]
      (if (>= angle 0)
        (* max-increase
           (if (<= angle ramp-up)
             (/ angle ramp-up)
             (if (<= angle (+ ramp-up ramp-down))
               (- 1.0 (sin (/ (* 0.5 PI (- angle ramp-up)) ramp-down)))
               0.0)))
        (- (spike-fn (- angle))))))


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


(def wing-span (* 2 21.1480))
(def wing-area 682.1415)
(def aspect-ratio (/ (sqr wing-span) wing-area))


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


(defn coefficient-of-drag
  "Determine coefficient of drag (negative x in wind system) depending on mach speed, angle of attack, and optionally angle of side-slip"
  ([speed-mach alpha]
   (coefficient-of-drag speed-mach alpha 0.0))
  ([speed-mach alpha beta]
   (+ (c-d-0 speed-mach) (/ (sqr (coefficient-of-lift speed-mach alpha beta)) (* PI (oswald-factor speed-mach) aspect-ratio)))))


; (defn coefficient-of-drag
;   "Determine coefficient of drag (negative x in wind system) depending on angle of attack and optionally angle of side-slip"
;   {:malli/schema [:=> [:cat :double [:? :double]] :double]}
;   ([angle-of-attack] ((compose (basic-drag 0.1 2.0) (bumps 0.04 (to-radians 20))) angle-of-attack))
;   ([angle-of-attack angle-of-side-slip]
;    (mix (coefficient-of-drag angle-of-attack) 0.5 (* 2 angle-of-side-slip))))
;
;
; (defn coefficient-of-side-force
;   "Determine coefficient of side force (positive y in wind system) depending on angle of attack and optionally angle of side-slip"
;   {:malli/schema [:=> [:cat :double [:? :double]] :double]}
;   ([angle-of-side-slip] ((basic-lift -0.4) angle-of-side-slip))
;   ([angle-of-attack angle-of-side-slip] (+ (* (sin angle-of-attack) ((basic-lift 2.0) angle-of-side-slip))
;                                            (* (cos angle-of-attack) (coefficient-of-side-force angle-of-side-slip)))))


; (defn coefficient-of-pitch-moment
;   "Determine coefficient of pitch moment depending on angle of attack and optionally angle of side-slip"
;   {:malli/schema [:=> [:cat :double [:? :double]] :double]}
;   ([angle-of-attack]
;    (+ (* -0.6 (sin angle-of-attack))
;       ((spike 0.2 (to-radians 10) (to-radians 15)) (- angle-of-attack (to-radians 180)))
;       ((spike 0.2 (to-radians 10) (to-radians 15)) (+ angle-of-attack (to-radians 180)))))
;   ([angle-of-attack angle-of-side-slip]
;    (* (mix (coefficient-of-pitch-moment (identity angle-of-attack))
;            (coefficient-of-pitch-moment (mirror   angle-of-attack))
;            angle-of-side-slip)
;       (cos angle-of-side-slip))))
;
;
; (defn coefficient-of-yaw-moment
;   "Determine coefficient of yaw moment depending on angle of side-slip and optionally angle of attack"
;   {:malli/schema [:=> [:cat :double] :double]}
;   [angle-of-side-slip]
;   (* 2.0 (sin angle-of-side-slip)))
;
;
; (defn coefficient-of-roll-moment
;   "Determine coefficient of roll moment depending on angle of side-slip and optionally angle of attack"
;   {:malli/schema [:=> [:cat :double] :double]}
;   [angle-of-side-slip]
;   (* -0.5 (sin angle-of-side-slip)))


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


; (defn lift
;   "Compute lift for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double] :double]}
;   [{::keys [alpha beta speed]} density surface]
;   (* 0.5 (coefficient-of-lift alpha beta) density (sqr speed) surface))
;
;
; (defn drag
;   "Compute drag for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double] :double]}
;   [{::keys [alpha beta speed]} density surface]
;   (* 0.5 (coefficient-of-drag alpha beta) density (sqr speed) surface))
;
;
; (defn side-force
;   "Compute side force for given speed in body system"
;   {:malli/schema [:=> [:cat speed-data :double :double] :double]}
;   [{::keys [alpha beta speed]} density surface]
;   (* 0.5 (coefficient-of-side-force alpha beta) density (sqr speed) surface))


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


(def coefficient-of-pitch-damping -8.0)
(def coefficient-of-yaw-damping -4.0)
(def coefficient-of-roll-damping -4.0)


(defn pitch-damping
  "Compute pitch damping moment for given pitch rate in body system"
  {:malli/schema [:=> [:cat speed-data :double :double :double :double] :double]}
  [{::keys [speed]} pitch-rate density surface chord]
  (* 0.25 density speed surface pitch-rate (sqr chord) coefficient-of-pitch-damping))


(defn yaw-damping
  "Compute yaw damping moment for given yaw rate in body system"
  {:malli/schema [:=> [:cat speed-data :double :double :double :double] :double]}
  [{::keys [speed]} yaw-rate density surface wingspan]
  (* 0.25 density speed surface yaw-rate (sqr wingspan) coefficient-of-yaw-damping))


(defn roll-damping
  "Compute roll damping moment for given roll rate in body system"
  {:malli/schema [:=> [:cat speed-data :double :double :double :double] :double]}
  [{::keys [speed]} roll-rate density surface wingspan]
  (* 0.25 density speed surface roll-rate (sqr wingspan) coefficient-of-roll-damping))


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
