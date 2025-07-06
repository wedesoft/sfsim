;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.ray
  "Functions dealing with rays"
  (:require
    [fastmath.vector :refer (add mult mag)]
    [malli.core :as m]
    [sfsim.util :refer (N)]))


(def ray (m/schema [:map [::origin [:vector :double]] [::direction [:vector :double]]]))


(defn integral-ray
  "Integrate given function over a ray in 3D space"
  {:malli/schema [:=> [:cat ray N :double [:=> [:cat [:vector :double]] :some]] :some]}
  [{::keys [origin direction]} steps distance fun]
  (let [stepsize      (/ distance steps)
        samples       (mapv #(* (+ 0.5 %) stepsize) (range steps))
        interpolate   (fn interpolate [s] (add origin (mult direction s)))
        direction-len (mag direction)]
    (reduce add (mapv #(-> % interpolate fun (mult (* stepsize direction-len))) samples))))
