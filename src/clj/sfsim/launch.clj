(ns sfsim.launch
    "Optimize launch trajectory"
    (:gen-class)
    (:require
      [clojure.math :refer (sqrt cos atan2)]
      [fastmath.vector :refer (vec3 mult add sub mag div normalize dot cross)]
      [libpython-clj2.require :refer (require-python)]
      [libpython-clj2.python :refer (py. py.-) :as py]
      [sfsim.util :refer (sqr sign)]
      [sfsim.quaternion :refer (orthogonal)]
      [sfsim.physics :refer (geographic->vector state-add state-scale runge-kutta gravitational-constant) :as physics]
      [sfsim.atmosphere :refer (temperature-at-height speed-of-sound density-at-height)]
      [sfsim.aerodynamics :refer (lift drag wind-to-body-system)]
      [sfsim.environment :refer (Environment)]
      [sfsim.mlp :refer (Critic adam-optimizer tensor toitem tolist without-gradient entropy-of-distribution)]
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
   :planet-mass 5.9742e+24
   :mass 100000.0
   :dt 0.5
   :max-thrust 2500000.0
   :timeout 1200.0
   :initial-delta-v 12000.0
   :free-delta-v 5000.0
   :weight-height-reward 1.0
   :weight-speed-reward 1.0
   :weight-fuel-reward 0.1})


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
  (let [direction (vec3 (array 0) (array 1) (array 2))]
    {:control direction}))


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
  (environment-reward [_this _input]
    (reward state config)))


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
       (fn [self a-mag z-mag]
           (torch/where (torch/ne z-mag 0.0) (torch/div a-mag z-mag) 1.0)))
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
                 z          (torch/mul z-mag action)
                 log-prob-z (torch/sum (py. (py.- self normal) log_prob z) -1 :keepdim true)
                 correction (py. self correction a-mag z-mag)]
             (torch/sub log-prob-z correction))))
     "entropy"
     (py/make-instance-fn
       (fn [self]  ; This only approximates the entropy assuming small sigma
           (let [normal-entropy (torch/sum (py. (py.- self normal) entropy) -1 :keepdim true)
                 z-mag          (linalg/norm (py.- self mu))
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


(defn launch-factory
  []
  (->Launch config (setup config :latitude 0.0 :longitude 0.0 :height 0.0)))


(defn -main [& _args]
  (let [factory        launch-factory
        actor          (LaunchActor 6 64 3)
        critic         (Critic 6 64)
        n-epochs       1000
        n-updates      10
        gamma          0.99
        lambda         1.0
        epsilon        0.2
        n-batches      8
        batch-size     64
        checkpoint     100
        entropy-factor (atom 0.01)
        entropy-decay  0.999
        lr             5e-5
        weight-decay   1e-4
        smooth-actor-loss  (atom 0.0)
        smooth-critic-loss (atom 0.0)
        actor-optimizer  (adam-optimizer actor lr weight-decay)
        critic-optimizer (adam-optimizer critic lr weight-decay)]
    (when (.exists (java.io.File. "actor.pt"))
      (py. actor load_state_dict (torch/load "actor.pt")))
    (when (.exists (java.io.File. "critic.pt"))
      (py. critic load_state_dict (torch/load "critic.pt")))
    (doseq [epoch (range n-epochs)]
           (let [samples (sample-with-advantage-and-critic-target factory actor critic (* batch-size n-batches)
                                                                  batch-size gamma lambda)]
             (doseq [k (range n-updates)]
                    (doseq [batch samples]
                           (let [loss (actor-loss batch actor epsilon @entropy-factor)]
                             (py. actor-optimizer zero_grad)
                             (py. loss backward)
                             (utils/clip_grad_norm_(py. actor parameters) 0.5)
                             (py. actor-optimizer step)
                             (swap! smooth-actor-loss (fn [x] (+ (* 0.999 ^double x) (* 0.001 ^double (toitem loss))))) ))
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
             (doseq [input [[0.5 0 0 0 0 0]]]
                    (println input
                             "->" (action (tolist (py. actor deterministic_act (tensor input))))
                             "entropy" (toitem (entropy-of-distribution actor (tensor input))))))
           (swap! entropy-factor * entropy-decay)
           (when (= (mod epoch checkpoint) (dec checkpoint))
             (println "Saving models")
             (torch/save (py. actor state_dict) "actor.pt")
             (torch/save (py. critic state_dict) "critic.pt")))
    (System/exit 0)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
