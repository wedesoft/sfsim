(ns sfsim25.ray
  "Functions dealing with rays"
  (:require [clojure.core.matrix :refer (add mul length)]))

(defn integral-ray
  "Integrate given function over a ray in 3D space"
  [{:sfsim25.ray/keys [origin direction]} steps distance fun]
  (let [stepsize      (/ distance steps)
        samples       (map #(* (+ 0.5 %) stepsize) (range steps))
        interpolate   (fn [s] (add origin (mul s direction)))
        direction-len (length direction)]
    (apply add (map #(->> % interpolate fun (mul stepsize direction-len)) samples))))
