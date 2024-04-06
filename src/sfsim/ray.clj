(ns sfsim.ray
  "Functions dealing with rays"
  (:require [malli.core :as m]
            [fastmath.vector :refer (add mult mag)]
            [sfsim.util :refer (N)]))

(def ray (m/schema [:map [::origin [:vector :double]] [::direction [:vector :double]]]))

(defn integral-ray
  "Integrate given function over a ray in 3D space"
  {:malli/schema [:=> [:cat ray N :double [:=> [:cat [:vector :double]] :some]] :some]}
  [{::keys [origin direction]} steps distance fun]
  (let [stepsize      (/ distance steps)
        samples       (map #(* (+ 0.5 %) stepsize) (range steps))
        interpolate   (fn interpolate [s] (add origin (mult direction s)))
        direction-len (mag direction)]
    (reduce add (map #(-> % interpolate fun (mult (* stepsize direction-len))) samples))))
