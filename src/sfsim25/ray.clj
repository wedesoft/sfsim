(ns sfsim25.ray
  "Functions dealing with rays"
  (:require [fastmath.vector :refer (add mult mag)]))

(defn integral-ray
  "Integrate given function over a ray in 3D space"
  [{:sfsim25.ray/keys [origin direction]} steps distance fun]
  (let [stepsize      (/ distance steps)
        samples       (map #(* (+ 0.5 %) stepsize) (range steps))
        interpolate   (fn [s] (add origin (mult direction s)))
        direction-len (mag direction)]
    (reduce add (map #(-> % interpolate fun (mult (* stepsize direction-len))) samples))))
