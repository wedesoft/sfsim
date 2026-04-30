(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [clojure.math :refer (sqrt cos hypot atan2)]
      [fastmath.vector :refer (vec3 mult add sub mag div cross)]
      [sfsim.util :refer (sqr sign)]
      [sfsim.physics :refer (geographic->vector gravitation state-add state-scale runge-kutta gravitational-constant)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn orbital-speed
  "Orbital speed"
  ^double
  ([^double radius ^double mass ^double gravitational-constant]
   (sqrt (/ (* mass gravitational-constant) radius)))
  ([^double radius ^double mass]
   (orbital-speed radius mass gravitational-constant)))


(def config
  {:radius 6378000.0
   :orbit 160000.0
   :mass 5.9742e+24
   :dt 1.0
   :max-thrust 25.0})


(defn setup
  "Setup rocket launch"
  [config & {:keys [latitude longitude height]}]
  (let [radius (:radius config)
        point  (geographic->vector longitude latitude)]
    {:position (mult point (+ ^double radius ^double height))
     :speed (vec3 0 0 0)
     :t 0.0}))


(defn state-change
  "State change function returning derivative of state to be used with Runge-Kutta integration"
  [acceleration]
  (fn [{:keys [position speed]} _dt]
      {:position speed :speed (acceleration position speed)}))


(defn update-state
  "Perform simulation step for spacecraft"
  [{:keys [t] :as state} {:keys [control]} {:keys [dt mass max-thrust]}]
  (let [gravitation  (gravitation (vec3 0 0 0) mass)
        acceleration (fn [position speed] (add (gravitation position speed) (mult control max-thrust)))
        state        (runge-kutta state dt (state-change acceleration) state-add state-scale)
        t            (+ ^double t ^double dt)]
    (assoc state :t t)))


(defn action
  "Convert array to action with length of direction vector as latent variable"
  [array]
  (let [direction (vec3 (array 0) (array 1) (array 2))
        length    (mag direction)
        scale     (if (pos? length) (/ ^double (array 3) (mag direction)) 0.0)]
    {:control (mult direction scale)}))


(defn observation
  "Get observation of state"
  [{:keys [position speed]} {:keys [radius orbit mass]}]
  (let [distance          (mag position)
        normalised-height (/ (- distance ^double radius) ^double orbit)
        normalised-pos    (mult position (/ (+ 0.5 (* 0.5 normalised-height)) distance))
        orbital-speed     (orbital-speed (+ ^double radius ^double orbit) mass)
        normalised-speed  (div speed orbital-speed)]
    [(normalised-pos 0) (normalised-pos 1) (normalised-pos 2) (normalised-speed 0) (normalised-speed 1) (normalised-speed 2)]))


(defn done?
  "An orbit is never finished"
  ([& _args]
   false))


(defn truncate?
  "Decide whether a run should be aborted"
  ([state]
   (truncate? state config))
  ([{:keys [t]} {:keys [timeout]}]
   (>= ^double t ^double timeout)))


(defn orbital-vector
  "Get target orbital vector given position and inclination target"
  [{:keys [position]} inclination-target]
  (let [a        (position 0)
        b        (position 1)
        l2       (+ (sqr a) (sqr b))
        l        (sqrt l2)
        latitude (atan2 (position 2) l)
        cos-lat  (cos latitude)
        cos-incl (cos inclination-target)
        z        (if (<= (abs cos-incl) (abs cos-lat)) cos-incl (* (sign cos-incl) (abs cos-lat)))
        r2       (- 1.0 (sqr z))  ; x^2 + y^2 = r^2
        d        (* z ^double (position 2)) ; a x + b y + d = 0
        k        (- (/ d l2))  ; does not work if position is at a pole (on the z-axis)
        xc       (* k ^double a)  ; a xc + b yc + d = 0
        yc       (* k ^double b)  ; and xc^2 + yc^2 minimal
        s        (sqrt (max 0.0 (- r2 (sqr xc) (sqr yc))))
        v        (div (vec3 (- ^double b) a 0) l)
        result [(add (vec3 xc yc z) (mult v s))
                (sub (vec3 xc yc z) (mult v s))]]
    result))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
