(ns sfsim.plane
    "Functions dealing with planar surfaces"
    (:require [malli.core :as m]
              [fastmath.vector :refer (cross normalize sub)]
              [sfsim.matrix :refer (fvec3)]))

(def plane (m/schema [:map [::point fvec3] [::normal fvec3]]))

(defn points->plane
  "Determine plane through three points"
  {:malli/schema [:=> [:cat fvec3 fvec3 fvec3] plane]}
  [p q r]
  {::point p
   ::normal (normalize (cross (sub q p) (sub r p)))})
