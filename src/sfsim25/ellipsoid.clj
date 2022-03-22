(ns sfsim25.ellipsoid
  "Functions dealing with ellipsoids"
  (:require [clojure.core.matrix :refer (matrix mget)]
            [sfsim25.sphere :refer :all]))

(defn ray-ellipsoid-intersection
  "Compute intersection of line with ellipsoid"
  [{:sfsim25.ellipsoid/keys [centre major-radius minor-radius]} {:sfsim25.ray/keys [origin direction]}]
  (let [factor (/ major-radius minor-radius)
        scale  (fn [v] (matrix [(mget v 0) (mget v 1) (* factor (mget v 2))]))]
  (ray-sphere-intersection #:sfsim25.sphere{:centre (scale centre) :radius major-radius}
                           #:sfsim25.ray{:origin (scale origin) :direction (scale direction)})))
