(ns sfsim.aerodynamics
    (:require
      [clojure.math :refer (PI cos sin sqrt to-radians)]
      [sfsim.util :refer (sqr)]))


(set! *unchecked-math* true)
(set! *warn-on-reflection* true)


(defn mix
  "Mix two values depending on angle"
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
  [max-increase interval]
  (fn [angle-of-attack]
      (let [relative-position (/ (- interval angle-of-attack) interval)]
        (if (>= relative-position 0.0)
          (* max-increase (- 1.0 (sqrt (- 1.0 (sqr relative-position)))))
          0.0))))


(defn glide
  "Increase of lift for small angles of attack before stall"
  [max-increase stall-angle reduced-increase fall-off-interval]
  (fn glide-fn [angle-of-attack]
      (if (neg? angle-of-attack)
        (- (glide-fn (- angle-of-attack)))
        (if (<= angle-of-attack stall-angle)
          (* (/ max-increase stall-angle) angle-of-attack)
          ((fall-off reduced-increase fall-off-interval) (- angle-of-attack stall-angle))))))


(defn bumps
  "Bumps to add to drag before 180 and -180 degrees"
  [max-increase interval]
  (fn [angle-of-attack]
      (let [relative-pos (/ (- (abs angle-of-attack) (- PI interval)) interval)]
        (if (pos? relative-pos)
          (* 0.5 max-increase (- 1 (cos (* 2 PI relative-pos))))
          0.0))))


(defn tail
  "Lift increase to add near 180 and -180 degrees"
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
  [& funs]
  (comp (partial apply +) (apply juxt funs)))


(defn mirror
  "Mirror values at 90 degrees"
  [angle]
  (if (>= angle 0.0)
    (- PI angle)
    (- (- PI) angle)))


(defn coefficient-of-lift
  "Determine coefficient of lift depending on angle of attack and optionally angle of sideslip"
  ([angle-of-attack]
   ((compose (basic-lift 1.1)
             (glide 0.8 (to-radians 13) 0.5 (to-radians 12))
             (tail 0.5 (to-radians 8) (to-radians 12)))
    angle-of-attack))
  ([angle-of-attack angle-of-sideslip]
   (mix (coefficient-of-lift angle-of-attack) (- (coefficient-of-lift (mirror angle-of-attack))) angle-of-sideslip)))


(defn coefficient-of-drag
  "Determine coefficient of drag depending on angle of attack and optionally angle of sideslip"
  ([angle-of-attack] ((compose (basic-drag 0.1 2.0) (bumps 0.04 (to-radians 20))) angle-of-attack))
  ([angle-of-attack angle-of-sideslip]
   (mix (coefficient-of-drag angle-of-attack) ((basic-drag 0.5 2.0) angle-of-attack) (* 2 angle-of-sideslip))))


(defn coefficient-of-side-force
  "Determine coefficient of side force depending on angle of attack and optionally angle of sideslip"
  ([angle-of-side-slip] ((basic-lift 0.4) angle-of-side-slip))
  ([angle-of-attack angle-of-sideslip] (* (cos angle-of-attack) (coefficient-of-side-force angle-of-sideslip))))


(defn spike
  "Spike function with linear and sinusoidal ramp"
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
  "Airplane x-coordinate of speed vector in body system for angle of attack and side slip angle"
  [angle-of-attack angle-of-sideslip]
  (* (cos angle-of-attack) (cos angle-of-sideslip)))


(defn speed-y
  "Airplane y-coordinate of speed vector in body system for angle of attack and side slip angle"
  [angle-of-attack angle-of-sideslip]
  (sin angle-of-sideslip))


(defn speed-z
  "Airplane z-coordinate of speed vector in body system for angle of attack and side slip angle"
  [angle-of-attack angle-of-sideslip]
  (* (sin angle-of-attack) (cos angle-of-sideslip)))


(defn coefficient-of-pitch-moment
  "Determine coefficient of pitch moment depending on angle of attack and optionally angle of sideslip"
  [angle-of-attack]
  (+ (* -0.6 (sin angle-of-attack))
     ((spike 0.2 (to-radians 10) (to-radians 15)) (- angle-of-attack (to-radians 180)))
     ((spike 0.2 (to-radians 10) (to-radians 15)) (+ angle-of-attack (to-radians 180)))))


(defn coefficient-of-yaw-moment
  "Determine coefficient of yaw moment depending on angle of sideslip and optionally angle of attack"
  [angle-of-side-slip]
  (* 2.0 (sin angle-of-side-slip)))


(defn coefficient-of-roll-moment
  "Determine coefficient of roll moment depending on angle of sideslip and optionally angle of attack"
  [angle-of-side-slip]
  (* -0.5 (sin angle-of-side-slip)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
