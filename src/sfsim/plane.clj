(ns sfsim.plane
    "Functions dealing with planar surfaces"
    (:require [malli.core :as m]
              [fastmath.vector :refer (cross normalize sub dot add mult)]
              [sfsim.matrix :refer (fvec3)]
              [sfsim.ray :refer (ray)]))

(def plane (m/schema [:map [::point fvec3] [::normal fvec3]]))

(defn points->plane
  "Determine plane through three points"
  {:malli/schema [:=> [:cat fvec3 fvec3 fvec3] plane]}
  [p q r]
  {::point p
   ::normal (normalize (cross (sub q p) (sub r p)))})

(defn ray-plane-intersection
  "Compute intersection of ray with plane"
  {:malli/schema [:=> [:cat plane ray] fvec3]}
  [plane ray]
  (let [p (:sfsim.plane/point plane)
        n (:sfsim.plane/normal plane)
        q (:sfsim.ray/origin ray)
        v (:sfsim.ray/direction ray)
        t (/ (dot n (sub p q)) (dot n v))]
    (add q (mult v t))))
