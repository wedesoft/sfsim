(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [clojure.math :refer (sqrt cos atan2)]
      [fastmath.vector :refer (vec3 mult add sub mag div normalize dot cross)]
      [sfsim.util :refer (sqr sign)]
      [sfsim.quaternion :refer (orthogonal)]
      [sfsim.physics :refer (geographic->vector state-add state-scale runge-kutta gravitational-constant) :as physics]
      [sfsim.atmosphere :refer (temperature-at-height speed-of-sound density-at-height)]
      [sfsim.aerodynamics :refer (lift drag wind-to-body-system)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn orbital-speed
  "Orbital speed"
  ^double
  ([^double distance ^double planet-mass ^double gravitational-constant]
   (sqrt (/ (* planet-mass gravitational-constant) distance)))
  ([^double distance ^double planet-mass]
   (orbital-speed distance planet-mass gravitational-constant)))


(def config
  {:radius 6378000.0
   :orbit 160000.0
   :planet-mass 5.9742e+24
   :mass 100000.0
   :dt 1.0
   :max-thrust 2500000.0
   :initial-delta-v 12000.0
   :free-delta-v 5000.0
   :weight-height-reward 1.0
   :weight-speed-reward 1.0
   :weight-fuel-reward 1.0})


(defn setup
  "Setup rocket launch"
  [{:keys [radius initial-delta-v]} & {:keys [latitude longitude height]}]
  (let [point  (geographic->vector longitude latitude)]
    {:position (mult point (+ ^double radius ^double height))
     :speed    (vec3 0 0 0)
     :delta-v  initial-delta-v
     :t        0.0}))


(defn state-change
  "State change function returning derivative of state to be used with Runge-Kutta integration"
  [acceleration]
  (fn [{:keys [position speed]} _dt]
      {:position speed :speed (acceleration position speed)}))


(defn gravitation
  "Return gravitation function using planet mass from specified config"
  [{:keys [planet-mass]}]
  (physics/gravitation (vec3 0 0 0) planet-mass))


(defn thrust
  "Return function returning thrust vector"
  [{:keys [control]} {:keys [mass max-thrust]}]
  (mult control (/ ^double max-thrust ^double mass)))


(defn forward
  "Forward vector of space craft depending on speed and thrust vector"
  [{:keys [speed]} {:keys [control]}]
  (let [control-mag (mag control)
        speed-mag   (mag speed)]
    (cond
      (pos? control-mag) (div control control-mag)
      (pos? speed-mag)   (div speed speed-mag)
      :else              (vec3 0 0 1))))


(defn up
  "Up vector of space craft depending on speed and thrust vector"
  [{:keys [speed]} {:keys [control]}]
  (let [control-sqr (dot control control)]
    (if (pos? control-sqr)
      (let [projection (/ (dot speed control) control-sqr)]
        (normalize (sub (mult control projection) speed)))
      (let [speed-sqr (dot speed speed)]
        (if (pos? speed-sqr)
          (orthogonal speed)
          (vec3 1 0 0))))))


(defn drag-and-lift
  "Get acceleration due to drag and lift"
  [action {:keys [mass radius]}]
  (fn [position speed]
      (let [distance       (mag position)
            height         (- distance ^double radius)
            state          {:position position :speed speed}
            forward        (forward state action)
            up             (up state action)
            alpha          (atan2 (- (dot up speed)) (dot forward speed))
            density        (density-at-height height)
            temperature    (temperature-at-height height)
            speed-of-sound (speed-of-sound temperature)
            linear-speed   #:sfsim.aerodynamics{:speed (mag speed) :alpha alpha :beta 0.0}
            drag           (drag linear-speed speed-of-sound density 0.0 0.0)
            lift           (lift linear-speed (vec3 0 0 0) speed-of-sound density)
            wind-acc       (div (vec3 (- ^double drag) 0 (- ^double lift)) mass)
            body-acc       (wind-to-body-system linear-speed wind-acc)]
        (sub (mult forward (body-acc 0)) (mult up (body-acc 2))))))


(defn update-state
  "Perform simulation step for spacecraft"
  [{:keys [t delta-v] :as state} action {:keys [dt] :as config}]
   (let [gravitation   (gravitation config)
         drag-and-lift (drag-and-lift action config)
         thrust        (thrust action config)
         acceleration  (fn [position speed] (reduce add [(gravitation position speed)
                                                         (drag-and-lift position speed)
                                                         thrust]))
         state         (runge-kutta state dt (state-change acceleration) state-add state-scale)
         t             (+ ^double t ^double dt)
         delta-v       (- ^double delta-v (mag thrust))]
     (assoc state :t t :delta-v delta-v)))


(defn action
  "Convert array to action with length of direction vector as latent variable"
  [array]
  (let [direction (vec3 (array 0) (array 1) (array 2))
        length    (mag direction)
        scale     (if (pos? length) (/ ^double (array 3) (mag direction)) 0.0)]
    {:control (mult direction scale)}))


(defn observation
  "Get observation of state"
  [{:keys [position speed]} {:keys [radius orbit planet-mass]}]
  (let [distance          (mag position)
        normalised-height (/ (- distance ^double radius) ^double orbit)
        normalised-pos    (mult position (/ (+ 0.5 (* 0.5 normalised-height)) distance))
        orbital-speed     (orbital-speed (+ ^double radius ^double orbit) planet-mass)
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
        z        (* (sign cos-incl) (min (abs cos-incl) (abs cos-lat)))
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


(defn reward-height
  "Reward for approaching orbital height"
  [{:keys [position]} {:keys [radius orbit]}]
  (-> position mag (- ^double radius) (- ^double orbit) (/ ^double orbit) abs -))


(defn reward-speed
  "Reward for approaching orbital speed"
  [{:keys [position speed] :as state} {:keys [radius orbit planet-mass]} inclination-target]
  (let [orbital-speed   (orbital-speed (+ ^double radius ^double orbit) planet-mass)
        orbital-vectors (orbital-vector state inclination-target)
        target-speeds   (map #(mult (cross % (normalize position)) orbital-speed) orbital-vectors)]
    (- ^double (apply min (map #(/ (mag (sub speed %)) ^double orbital-speed) target-speeds)))))


(defn reward-fuel
  "Reward conserving fuel"
  [{:keys [delta-v]} {:keys [initial-delta-v free-delta-v]}]
  (let [ramp-length (- ^double initial-delta-v ^double free-delta-v)]
    (min 0.0 (/ (- ^double delta-v ramp-length) ramp-length))))


(defn reward
  "Overall reward function"
  [state {:keys [weight-height-reward weight-speed-reward weight-fuel-reward] :as config}]
  (+ (* ^double weight-height-reward ^double (reward-height state config))
     (* ^double weight-speed-reward ^double (reward-speed state config 0.0))
     (* ^double weight-fuel-reward ^double (reward-fuel state config))))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
