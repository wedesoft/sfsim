(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [fastmath.vector :refer (vec3 mult add mag)]
      [sfsim.physics :refer (geographic->vector gravitation state-add state-scale runge-kutta)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def config
  {:radius 6378000.0
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


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
