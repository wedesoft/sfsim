;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(require '[clojure.math :refer (to-radians)]
         '[sfsim.aerodynamics :refer :all])

(def cl (compose (basic-lift 1.1) (glide 0.8 (to-radians 13) 0.5 (to-radians 12)) (tail 0.5 (to-radians 8) (to-radians 12))))

(def cd (compose (basic-drag 0.1 2.0) (bumps 0.04 (to-radians 20))))

; Create GNUplot Dat file
(spit "etc/cl.dat"
      (with-out-str
        (doseq [alpha (range -180.0 180.1 0.1)]
               (println alpha (cl (to-radians alpha))))))

; Create GNUplot Dat file
(spit "etc/cd.dat"
      (with-out-str
        (doseq [alpha (range -180.0 180.1 0.1)]
               (println alpha (cd (to-radians alpha))))))
