;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html
(require '[clojure.math :refer (sqrt pow atan to-degrees)])

(def air 1.400)
(def water 1.310)

(def gamma (/ air water))

; Prandtl-Meyer function
(defn v [M] (- (* (sqrt (/ (+ gamma 1) (- gamma 1))) (atan (sqrt (* (/ (- gamma 1) (+ gamma 1)) (- (* M M) 1))) )) (atan (sqrt (- (* M M) 1)))))

(def p0 (* 200 101325))
(def p (* 0.1 101325))
(def M (sqrt (* (/ 2 (- gamma 1)) (- (pow (/ p0 p) (/ (- gamma 1) gamma)) 1))))
(to-degrees (- (v M) (v 3.5)))
