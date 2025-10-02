;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de> SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html
(require '[clojure.math :refer (sqrt pow atan to-degrees)])

(def gamma 1.25)



; Prandtl-Meyer function
(defn v [M] (- (* (sqrt (/ (+ gamma 1) (- gamma 1))) (atan (sqrt (* (/ (- gamma 1) (+ gamma 1)) (- (* M M) 1))) )) (atan (sqrt (- (* M M) 1)))))

(def M0 10)
(def p0 (* 200 101325))
(def p (* 1.0 101325))
(def M (sqrt (* (/ 2 (- gamma 1)) (- (pow (/ p0 p) (/ (- gamma 1) gamma)) 1))))
(to-degrees (- (v M) (v M0)))


; import ImageIO
(def path "tmp/ldem_64_uint.tif")
(defn load-image [path] (javax.imageio.ImageIO/read (java.io.File. path)))
(def img (load-image path))
