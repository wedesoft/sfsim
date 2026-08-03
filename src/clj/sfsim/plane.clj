;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.plane
  "Functions dealing with planar surfaces"
  (:require
    [fastmath.vector :refer (cross normalize sub dot)]
    [malli.core :as m]
    [sfsim.matrix :refer (fvec3)]
    [sfsim.ray :refer (ray)]))


(def plane (m/schema [:map [::point fvec3] [::normal fvec3]]))


(defn points->plane
  "Determine plane through three points"
  {:malli/schema [:=> [:cat fvec3 fvec3 fvec3] plane]}
  [p q r]
  {::point p
   ::normal (normalize (cross (sub q p) (sub r p)))})


(defn ray-plane-intersection-parameter
  "Get parameter of plane intersection for ray"
  {:malli/schema [:=> [:cat plane ray] :double]}
  [plane ray]
  (let [p (:sfsim.plane/point plane)
        n (:sfsim.plane/normal plane)
        q (:sfsim.ray/origin ray)
        v (:sfsim.ray/direction ray)]
    (/ (dot n (sub p q)) (dot n v))))
