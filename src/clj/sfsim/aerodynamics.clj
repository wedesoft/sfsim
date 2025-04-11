(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (PI cos sin sqrt to-radians atan2 hypot)]
      [fastmath.matrix :refer (mat3x3 mulv)]
      [fastmath.vector :refer (vec3 mag)]
      [sfsim.matrix :refer [fvec3]]
      [sfsim.quaternion :as q]
      [sfsim.util :refer (sqr)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


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


(defn coefficient-of-lift
  "Determine coefficient of lift (negative z in wind system) depending on angle of attack and optionally angle of side-slip"
  {:malli/schema [:=> [:cat :double [:? :double]] :double]}
  ([angle-of-attack]
   ((compose (basic-lift 1.1) (glide 0.8 (to-radians 13) 0.5 (to-radians 12)) (tail 0.5 (to-radians 8) (to-radians 12)))
    angle-of-attack))
  ([angle-of-attack angle-of-side-slip]
   (* (mix (coefficient-of-lift angle-of-attack) (- (coefficient-of-lift (mirror angle-of-attack))) angle-of-side-slip)
      (cos angle-of-side-slip))))


(defn coefficient-of-drag
  "Determine coefficient of drag (negative x in wind system) depending on angle of attack and optionally angle of side-slip"
  {:malli/schema [:=> [:cat :double [:? :double]] :double]}
  ([angle-of-attack] ((compose (basic-drag 0.1 2.0) (bumps 0.04 (to-radians 20))) angle-of-attack))
  ([angle-of-attack angle-of-side-slip]
   (mix (coefficient-of-drag angle-of-attack) 0.5 (* 2 angle-of-side-slip))))


(defn coefficient-of-side-force
  "Determine coefficient of side force (positive y in wind system) depending on angle of attack and optionally angle of side-slip"
  {:malli/schema [:=> [:cat :double [:? :double]] :double]}
  ([angle-of-side-slip] ((basic-lift -0.4) angle-of-side-slip))
  ([angle-of-attack angle-of-side-slip] (+ (* (sin angle-of-attack) ((basic-lift 2.0) angle-of-side-slip))
                                           (* (cos angle-of-attack) (coefficient-of-side-force angle-of-side-slip)))))


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


(defn coefficient-of-pitch-moment
  "Determine coefficient of pitch moment depending on angle of attack and optionally angle of side-slip"
  {:malli/schema [:=> [:cat :double [:? :double]] :double]}
  ([angle-of-attack]
   (+ (* -0.6 (sin angle-of-attack))
      ((spike 0.2 (to-radians 10) (to-radians 15)) (- angle-of-attack (to-radians 180)))
      ((spike 0.2 (to-radians 10) (to-radians 15)) (+ angle-of-attack (to-radians 180)))))
  ([angle-of-attack angle-of-side-slip]
   (* (mix (coefficient-of-pitch-moment (identity angle-of-attack))
           (coefficient-of-pitch-moment (mirror   angle-of-attack))
           angle-of-side-slip)
      (cos angle-of-side-slip))))


(defn coefficient-of-yaw-moment
  "Determine coefficient of yaw moment depending on angle of side-slip and optionally angle of attack"
  {:malli/schema [:=> [:cat :double] :double]}
  [angle-of-side-slip]
  (* 2.0 (sin angle-of-side-slip)))


(defn coefficient-of-roll-moment
  "Determine coefficient of roll moment depending on angle of side-slip and optionally angle of attack"
  {:malli/schema [:=> [:cat :double] :double]}
  [angle-of-side-slip]
  (* -0.5 (sin angle-of-side-slip)))


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


(defn speed-in-body-system
  "Convert airplane speed vector to angle of attack, side slip angle, and speed magnitude"
  {:malli/schema [:=> [:cat q/quaternion fvec3] [:map [::alpha :double] [::beta :double] [::speed :double]]]}
  [orientation speed-vector]
  (let [speed-rotated (q/rotate-vector orientation speed-vector)]
    {::alpha (angle-of-attack speed-rotated)
     ::beta  (angle-of-side-slip speed-rotated)
     ::speed (mag speed-vector)}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
