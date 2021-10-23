(ns sfsim25.sphere
  "Functions dealing with spheres"
  (:require [clojure.core.matrix :refer :all]
            [sfsim25.util :refer :all]))

(defn ray-sphere-intersection
  "Compute intersection of line with sphere"
  ^clojure.lang.PersistentArrayMap
  [{:sfsim25.sphere/keys [sphere-centre sphere-radius]} {:sfsim25.ray/keys [origin direction]}]
  (let [offset        (sub origin sphere-centre)
        direction-sqr (dot direction direction)
        discriminant  (- (sqr (dot direction offset)) (* direction-sqr (- (dot offset offset) (sqr sphere-radius))))]
    (if (> discriminant 0)
      (let [length2 (/ (Math/sqrt discriminant) direction-sqr)
            middle  (- (/ (dot direction offset) direction-sqr))]
        (if (< middle length2)
          {:distance 0.0 :length (max 0.0 (+ middle length2))}
          {:distance (- middle length2) :length (* 2 length2)}))
      {:distance 0.0 :length 0.0})))
