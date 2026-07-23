;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.lighting
    "Shaders and methods for lighting pass"
    (:require
      [comb.template :as template]))


(defn fragment-lighting
  [num-scene-shadows]
  (template/eval (slurp "resources/shaders/lighting/fragment.glsl") {:num-scene-shadows num-scene-shadows}))
