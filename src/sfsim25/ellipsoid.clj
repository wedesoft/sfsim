(ns sfsim25.ellipsoid
  "Functions dealing with ellipsoids"
  (:require [clojure.core.matrix :refer :all]
            [sfsim25.sphere :refer :all]))

(defn ray-ellipsoid-intersection
  "Compute intersection of line with ellipsoid"
  [{:sfsim25.ellipsoid/keys [ellipsoid-centre ellipsoid-radius1 ellipsoid-radius2]} {:sfsim25.ray/keys [origin direction]}]
  (let [factor (/ ellipsoid-radius1 ellipsoid-radius2)
        scale  (fn [v] (matrix [(mget v 0) (mget v 1) (* factor (mget v 2))]))]
  (ray-sphere-intersection #:sfsim25.sphere{:sphere-centre (scale ellipsoid-centre) :sphere-radius ellipsoid-radius1}
                           #:sfsim25.ray{:origin (scale origin) :direction (scale direction)})))
