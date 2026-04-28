(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [clojure.math :refer (sqrt)]
      [fastmath.vector :refer (vec3 mult add mag div)]
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


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
