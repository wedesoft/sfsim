;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.ray
  "Functions dealing with rays"
  (:require
    [fastmath.vector :refer (add mult mag vec3)]
    [malli.core :as m]))


(def ray (m/schema [:map [::origin [:vector :double]] [::direction [:vector :double]]]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn integral-ray
  "Integrate given function over a ray in 3D space"
  [{::keys [origin direction]} ^long steps ^double distance fun]
  (let [stepsize      (/ distance steps)
        samples       (->Eduction (map (fn ^double [^long n] (* (+ 0.5 n) stepsize))) (range steps))
        interpolate   (fn interpolate [s] (add origin (mult direction s)))
        direction-len (mag direction)
        a (* stepsize direction-len)]
    (reduce
     add
     (vec3 0 0 0)
     (->Eduction (map #(-> % interpolate fun (mult a))) samples))))

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
