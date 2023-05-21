(ns sfsim25.ellipsoid
  "Functions dealing with ellipsoids"
  (:require [fastmath.vector :refer (vec3)]
            [sfsim25.sphere :refer :all]))

(defn ray-ellipsoid-intersection
  "Compute intersection of line with ellipsoid"
  [{:sfsim25.ellipsoid/keys [centre major-radius minor-radius]} {:sfsim25.ray/keys [origin direction]}]
  (let [factor (/ major-radius minor-radius)
        scale  (fn [v] (vec3 (v 0) (v 1) (* factor (v 2))))]
  (ray-sphere-intersection #:sfsim25.sphere{:centre (scale centre) :radius major-radius}
                           #:sfsim25.ray{:origin (scale origin) :direction (scale direction)})))
