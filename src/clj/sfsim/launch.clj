;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.launch
    "Optimize launch trajectory"
    (:gen-class)
    (:require
      [clojure.math :refer (PI sqrt cos sin atan2 acos asin exp)]
      [fastmath.vector :refer (vec3 mult add sub mag div normalize dot cross)]
      [fastmath.matrix :refer (mulv inverse cols->mat)]
      [libpython-clj2.require :refer (require-python)]
      [libpython-clj2.python :refer (py. py.-) :as py]
      [sfsim.util :refer (sqr sign)]
      [sfsim.quaternion :refer (orthogonal)]
      [sfsim.physics :refer (geographic->vector state-add state-scale runge-kutta gravitational-constant) :as physics]
      [sfsim.atmosphere :refer (temperature-at-height speed-of-sound density-at-height)]
      [sfsim.aerodynamics :refer (lift drag wind-to-body-system reference-area c-d-0 max-q dynamic-pressure)]
      [sfsim.environment :refer (Environment)]
      [sfsim.mlp :refer (Critic adam-optimizer tensor toitem tolist without-gradient)]
      [sfsim.ppo :refer (sample-with-advantage-and-critic-target actor-loss critic-loss)]))


(require-python '[torch :as torch]
                '[torch.linalg :as linalg]
                '[torch.nn :as nn]
                '[torch.nn.utils :as utils]
                '[torch.nn.functional :as F]
                '[torch.distributions :refer (Normal)])


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
   :inclination-target 0.0
   :ascending true
   :planet-mass 5.9742e+24
   :mass 100000.0
   :dt 5.0
   :steps 50
   :max-thrust 2500000.0
   :timeout 820.0
   :max-climb 600.0
   :max-speed 9000.0
   :sigma-height 1000.0
   :sigma-speed 100.0
   :weight-height-reward 1.0
   :weight-speed-reward 1.0
   :weight-fuel-reward 0.001
   :weight-angle-reward 0.1
   :weight-orbit-reward 1.0
   :weight-dynamic-pressure-reward 10.0})


(defn setup
  "Setup rocket launch"
  [{:keys [radius]} & {:keys [latitude longitude height]}]
  (let [point  (geographic->vector longitude latitude)]
    {:position (mult point (+ ^double radius ^double height))
     :speed    (vec3 0 0 0)
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
        result [(sub (vec3 xc yc z) (mult v s))
                (add (vec3 xc yc z) (mult v s))]]
    result))


(defn horizon-forward
  "Forward vector of horizon for orbit"
  [{:keys [position] :as state} {:keys [inclination-target ascending]}]
  (let [orbital-vectors (orbital-vector state inclination-target)]
    (normalize (cross ((if ascending first second) orbital-vectors) position))))


(defn horizon-matrix
  "Matrix for horizon of orbit"
  [{:keys [position] :as state} config]
  (let [forward (horizon-forward state config)
        up      (normalize position)
        left   (cross up forward)]
    (cols->mat up forward left)))


(defn thrust
  "Return function returning thrust vector"
  [state {:keys [control]} {:keys [mass max-thrust] :as config}]
  (let [horizon (horizon-matrix state config)]
    (mulv horizon (mult control (/ ^double max-thrust ^double mass)))))


(defn spacecraft-forward
  "Forward vector of space craft depending on speed and thrust vector"
  [{:keys [speed]} thrust]
  (let [thrust-mag (mag thrust)
        speed-mag  (mag speed)]
    (cond
      (pos? thrust-mag) (div thrust thrust-mag)
      (pos? speed-mag)  (div speed speed-mag)
      :else             (vec3 0 0 1))))


(defn spacecraft-up
  "Up vector of space craft depending on speed and thrust vector"
  [{:keys [speed]} thrust]
  (let [thrust-sqr (dot thrust thrust)]
    (if (pos? thrust-sqr)
      (let [projection (/ (dot speed thrust) thrust-sqr)]
        (normalize (sub (mult thrust projection) speed)))
      (let [speed-sqr (dot speed speed)]
        (if (pos? speed-sqr)
          (orthogonal speed)
          (vec3 1 0 0))))))


(defn drag-and-lift
  "Get acceleration due to drag and lift"
  [action {:keys [mass radius] :as config}]
  (fn [position speed]
      (let [distance       (mag position)
            height         (- distance ^double radius)
            state          {:position position :speed speed}
            thrust         (thrust {:position position :speed speed} action config)
            forward        (spacecraft-forward state thrust)
            up             (spacecraft-up state thrust)
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
  [{:keys [t] :as state} action {:keys [dt steps] :as config}]
   (let [gravitation   (gravitation config)
         drag-and-lift (drag-and-lift action config)
         thrust        (thrust state action config)
         acceleration  (fn [position speed] (reduce add [(gravitation position speed)
                                                         (drag-and-lift position speed)
                                                         thrust]))
         dt-frac       (/ ^double dt ^long steps)
         state         (nth (iterate #(runge-kutta % dt-frac (state-change acceleration) state-add state-scale) state) steps)
         t             (+ ^double t ^double dt)]
     (assoc state :t t)))


(defn action
  "Convert array to action with length of direction vector as latent variable"
  [array]
  (let [direction (vec3 (nth array 0) (nth array 1) (nth array 2 0))]
    {:control direction}))


(defn observation
  "Get observation of state"
  [{:keys [position speed] :as state} {:keys [radius orbit planet-mass] :as config}]
  (let [distance          (mag position)
        normalised-height (/ (- distance ^double radius) ^double orbit)
        orbital-speed     (orbital-speed (+ ^double radius ^double orbit) planet-mass)
        horizon           (horizon-matrix state config)
        normalised-speed  (div (mulv (inverse horizon) speed) orbital-speed)]
    [normalised-height (normalised-speed 0) (normalised-speed 1) (normalised-speed 2)]))


(defn target-speeds
  "Determine two possible speed vectors with desired inclination and orbital speed"
  [{:keys [position] :as state} {:keys [radius orbit planet-mass inclination-target]}]
  (let [orbital-speed   (orbital-speed (+ ^double radius ^double orbit) planet-mass)
        orbital-vectors (orbital-vector state inclination-target)
        target-speeds   (map #(mult (cross % (normalize position)) orbital-speed) orbital-vectors)]
    target-speeds))


(defn speed-deviation
  "Determine deviation of speed from nearest speed vector with desired inclination and orbital speed"
  [{:keys [speed] :as state} config]
  (let [target-speeds (target-speeds state config)]
    (apply min (map #(mag (sub speed %)) target-speeds))))


(defn orbit-deviation
  "Determine deviation of position from orbital plane"
  [{:keys [position]} {:keys [radius orbit]}]
  (let [distance (mag position)]
    (abs (- ^double distance ^double radius ^double orbit))))


(defn done?
  "An orbit is never finished"
  [_state _config]
  false)


(defn truncate?
  "Decide whether a run should be aborted"
  ([state]
   (truncate? state config))
  ([{:keys [t speed]} {:keys [timeout max-speed]}]
   (or (>= ^double t ^double timeout) (>= (mag speed) ^double max-speed))))


(defn reward-height
  "Penalty for deviations from orbit height"
  [state {:keys [orbit] :as config}]
  (-> ^double (orbit-deviation state config) (/ ^double orbit) sqr -))


(defn reward-speed
  "Reward for approaching orbital speed"
  [state {:keys [radius orbit planet-mass] :as config}]
  (let [orbital-speed   (orbital-speed (+ ^double radius ^double orbit) planet-mass)
        speed-deviation (speed-deviation state config)]
    (- (/ ^double speed-deviation ^double orbital-speed))))


(defn reward-fuel
  "Penalise using fuel"
  [{:keys [control]}]
  (- (mag control)))


(defn reward-angle
  "Penalise angle of attack"
  [{:keys [speed position] :as state} action {:keys [radius] :as config}]
  (let [thrust           (thrust state action config)
        mag-speed        (mag speed)
        mag-thrust       (mag thrust)
        distance         (mag position)
        height           (- distance ^double radius)
        relative-density (/ (density-at-height height) (density-at-height 0.0))]
    (if (or (zero? mag-speed) (zero? mag-thrust))
      0.0
      (let [cos-angle (/ (dot speed thrust) (* mag-speed mag-thrust))]
        (- (* relative-density (/ (acos cos-angle) PI)))))))



(defn reward-orbit
  "Reward closeness to orbit"
  [state {:keys [sigma-height sigma-speed] :as config}]
  (let [orbit-deviation (orbit-deviation state config)
        speed-deviation (speed-deviation state config)]
    (exp (- (+ (/ (sqr orbit-deviation) (sqr sigma-height)) (/ (sqr speed-deviation) (sqr sigma-speed)))))))


(defn reward-dynamic-pressure
  "Penalise exceeding Max Q"
  [{:keys [speed position] :as state} {:keys [radius] :as config}]
  (let [height           (- (mag position) ^double radius)
        density          (density-at-height height)
        dynamic-pressure (dynamic-pressure density (mag speed))]
    (min 0.0 (/ (- max-q dynamic-pressure ) max-q))))


(defn reward
  "Overall reward function"
  [state action
   {:keys [weight-height-reward weight-speed-reward weight-fuel-reward weight-angle-reward weight-orbit-reward
           weight-dynamic-pressure-reward] :as config}]
  (+ (* ^double weight-height-reward ^double (reward-height state config))
     (* ^double weight-speed-reward ^double (reward-speed state config))
     (* ^double weight-orbit-reward ^double (reward-orbit state config))
     (* ^double weight-angle-reward ^double (reward-angle state action config))
     (* ^double weight-fuel-reward ^double (reward-fuel action))
     (* ^double weight-dynamic-pressure-reward ^double (reward-dynamic-pressure state config))))


(defn speed-limit-at-height
  "Determine maximum speed possible due to drag"
  [max-thrust height]
  (let [density        (density-at-height height)
        coefficient    (c-d-0 0.0)]
    (sqrt (/ ^double max-thrust ^double coefficient ^double reference-area 0.5 density))))


(defn random-position
  "Create a random position vector"
  ([config]
   (random-position config rand))
  ([{:keys [radius orbit]} rand-fn]
   (vec3 (+ ^double radius (* ^double orbit ^double (rand-fn))) 0 0)))


(defn random-speed
  "Create a random speed vector"
  ([config]
   (random-speed config rand rand))
  ([{:keys [max-speed max-climb]} rand-fn1 rand-fn2]
   (let [speed     (* ^double max-speed ^double (rand-fn1))
         max-angle (if (> speed ^double max-climb) (asin (/ ^double max-climb speed)) (/ PI 2))
         angle     (* ^double max-angle ^double (rand-fn2))]
     (vec3 (* (sin angle) speed) (* (cos angle) speed) 0))))


(defn random-state
  "Create random initial state"
  [{:keys [max-speed max-thrust radius] :as config}]
  (let [position  (random-position config)
        height    (- ^double (mag position) ^double radius)
        max-speed (min ^double max-speed ^double (speed-limit-at-height max-thrust height))
        speed     (random-speed (assoc config :max-speed max-speed))]
    {:position position :speed speed :t 0.0}))


(defrecord Launch [config state]
  Environment
  (environment-update [_this input]
    (->Launch config (update-state state (action input) config)))
  (environment-observation [_this]
    (observation state config))
  (environment-done? [_this]
    (done? state config))
  (environment-truncate? [_this]
    (truncate? state config))
  (environment-reward [_this input]
    (reward state (action input) config)))


(defn launch-factory
  []
  (->Launch config (random-state config)))


(def ThrustVector
  (py/create-class
    "ThrustVector" nil
    {"__init__"
     (py/make-instance-fn
       (fn [self mu sigma]
           (py/set-attrs!
             self
             {"mu"     mu
              "sigma"  sigma
              "normal" (Normal mu sigma)})
           nil))
     "ratio"
     (py/make-instance-fn
       (fn [_self a b]
           (torch/where (torch/ne b 0.0) (torch/div a b) 1.0)))
     "scale"
     (py/make-instance-fn
       (fn [self z]
           (let [z-mag (linalg/norm z)
                 s     (py. self ratio (torch/tanh z-mag) z-mag)]
             (torch/mul z s))))
     "sample"
     (py/make-instance-fn
       (fn [self]
           (let [z     (py. (py.- self normal) sample)]
             (py. self scale z))))
     "mean"
     (py/make-instance-fn
       (fn [self]
           (py. self scale (py.- self mu))))
     "correction"
     (py/make-instance-fn
       (fn [self a-mag z-mag]
           (let [sphere-area-correction (torch/mul 2.0 (torch/log (py. self ratio a-mag z-mag)))
                 radial-correction      (torch/log (torch/sub 1.0 (torch/square a-mag)))]
             (torch/add sphere-area-correction radial-correction))))
     "log_prob"
     (py/make-instance-fn
       (fn [self action]
           (let [a-mag      (torch/clamp (linalg/norm action :dim -1 :keepdim true) 0.0 0.999)
                 z-mag      (torch/atanh a-mag)
                 z          (torch/mul (py. self ratio z-mag a-mag) action)
                 log-prob-z (torch/sum (py. (py.- self normal) log_prob z) -1 :keepdim true)
                 correction (py. self correction a-mag z-mag)]
             (torch/sub log-prob-z correction))))
     "entropy"
     (py/make-instance-fn
       (fn [self]  ; This only approximates the entropy assuming small sigma
           (let [normal-entropy (torch/sum (py. (py.- self normal) entropy) -1 :keepdim true)
                 z-mag          (linalg/norm (py.- self mu) :dim -1 :keepdim true)
                 a-mag          (torch/tanh z-mag)
                 correction     (py. self correction a-mag z-mag)]
             (torch/add normal-entropy correction))))}))


(def LaunchActor
  (py/create-class
    "Actor" [nn/Module]
    {"__init__"
     (py/make-instance-fn
       (fn [self observation-size hidden-units action-size]
           (py. nn/Module __init__ self)
           (py/set-attrs!
             self
             {"fc1"     (nn/Linear observation-size hidden-units)
              "fc2"     (nn/Linear hidden-units hidden-units)
              "fcmu"    (nn/Linear hidden-units action-size)
              "fcsigma" (nn/Linear hidden-units action-size)})
           nil))
     "forward"
     (py/make-instance-fn
       (fn [self x]
           (let [x     (py. self fc1 x)
                 x     (torch/tanh x)
                 x     (py. self fc2 x)
                 x     (torch/tanh x)
                 mu    (py. self fcmu x)
                 sigma (F/softplus (py. self fcsigma x))]
             [mu sigma])))
     "deterministic_act"
     (py/make-instance-fn
       (fn [self x]
           (let [dist (py. self get_dist x)]
             (py. dist mean))))
     "get_dist"
     (py/make-instance-fn
       (fn [self x]
           (let [[mu sigma] (py. self forward x)]
             (ThrustVector mu sigma))))}))


(defn -main [& _args]
  (let [factory            launch-factory
        actor              (LaunchActor 4 64 2)
        critic             (Critic 4 64)
        n-epochs           100000
        n-updates          10
        gamma              0.95
        lambda             1.0
        epsilon            0.2
        n-batches          16
        batch-size         64
        checkpoint         100
        entropy-factor     (atom 0.01)
        entropy-decay      0.999
        lr                 2e-5
        weight-decay       5e-5
        smooth-actor-loss  (atom 0.0)
        smooth-critic-loss (atom 0.0)
        actor-optimizer    (adam-optimizer actor lr weight-decay)
        critic-optimizer   (adam-optimizer critic lr weight-decay)]
    (when (.exists (java.io.File. "actor.pt"))
      (py. actor load_state_dict (torch/load "actor.pt")))
    (when (.exists (java.io.File. "critic.pt"))
      (py. critic load_state_dict (torch/load "critic.pt")))
    (when (.exists (java.io.File. "entropy.edn"))
      (reset! entropy-factor (read-string (slurp "entropy.edn"))))
    (doseq [epoch (range n-epochs)]
           (let [samples (sample-with-advantage-and-critic-target factory actor critic (* batch-size n-batches)
                                                                  batch-size gamma lambda)]
             (doseq [_k (range n-updates)]
                    (doseq [batch samples]
                           (let [loss (actor-loss batch actor epsilon @entropy-factor)]
                             (py. actor-optimizer zero_grad)
                             (py. loss backward)
                             (utils/clip_grad_norm_(py. actor parameters) 0.5)
                             (py. actor-optimizer step)
                             (swap! smooth-actor-loss (fn [x] (+ (* 0.999 ^double x) (* 0.001 ^double (toitem loss)))))))
                    (doseq [batch samples]
                           (let [loss (critic-loss batch critic)]
                             (py. critic-optimizer zero_grad)
                             (py. loss backward)
                             (py. critic-optimizer step)
                             (swap! smooth-critic-loss (fn [x] (+ (* 0.999 ^double x) (* 0.001 ^double (toitem loss))))))))
             (println "Epoch:" epoch
                      "Actor Loss:" @smooth-actor-loss
                      "Critic Loss:" @smooth-critic-loss
                      "Entropy Factor:" @entropy-factor))
           (without-gradient
             (doseq [input [[0 0 0 0]]]
                    (println "input:" input)
                    (println "deterministic_act:" (action (tolist (py. actor deterministic_act (tensor input)))))
                    (println "z-distribution:" (map tolist (py. actor forward (tensor input))))))
           (swap! entropy-factor * entropy-decay)
           (when (= (mod epoch checkpoint) (dec checkpoint))
             (println "Saving models")
             (torch/save (py. actor state_dict) "actor.pt")
             (torch/save (py. critic state_dict) "critic.pt")
             (spit "entropy.edn" (pr-str @entropy-factor))))
    (System/exit 0)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
