;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-unit
    (:require
      [midje.sweet :refer :all]
      [sfsim.units :refer :all]))


(fact "Convert lb/ft^2 to N/m^2"
      (/ pound-force (* foot foot)) => (roughly 47.880258888889 1e-6))
