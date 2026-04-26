(ns sfsim.launch
    "Optimize launch trajectory"
    (:require
      [fastmath.vector :refer (vec3 mult)]
      [sfsim.physics :refer (geographic->vector)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(defn setup
  "Setup rocket launch"
  [config & {:keys [latitude longitude height]}]
  (let [radius (:radius config)
        point  (geographic->vector longitude latitude)]
    {:position (mult point (+ ^double radius ^double height))
     :speed (vec3 0 0 0)}))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
