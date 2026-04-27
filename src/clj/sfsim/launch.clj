(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [fastmath.vector :refer (vec3 mult add)]
      [sfsim.physics :refer (geographic->vector gravitation state-add state-scale runge-kutta)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def config
  {:radius 6378000.0
   :mass 5.9742e+24
   :dt 1.0})


(defn setup
  "Setup rocket launch"
  [config & {:keys [latitude longitude height]}]
  (let [radius (:radius config)
        point  (geographic->vector longitude latitude)]
    {:position (mult point (+ ^double radius ^double height))
     :speed (vec3 0 0 0)}))


(defn state-change
  [acceleration]
  (fn [{:keys [position speed]} _dt]
      {:position speed :speed (acceleration position speed)}))


(defn update-state
  "Perform simulation step for spacecraft"
  [{:keys [position speed] :as state} {:keys []} {:keys [dt mass]}]
  (let [gravitation (gravitation (vec3 0 0 0) mass)]
    (runge-kutta state dt (state-change gravitation) state-add state-scale)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
