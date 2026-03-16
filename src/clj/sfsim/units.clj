;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.units)


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def rankin (/ 5.0 9.0))
(def foot 0.3048)
(def gravitation 9.8066500286389)
(def pound-weight 0.45359237)
(def pound-force (* ^double pound-weight ^double gravitation))
(def slugs 14.5939029372)


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
