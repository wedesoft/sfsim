;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.shockwave)


(def shockfront (slurp "resources/shaders/shockwave/shockfront.glsl"))


(def curvature (slurp "resources/shaders/shockwave/curvature.glsl"))


(def vertex-quad (slurp "resources/shaders/shockwave/vertex-quad.glsl"))


(def fragment-jump-flooding-init (slurp "resources/shaders/shockwave/fragment-jump-flooding-init.glsl"))


(def fragment-jump-flooding-step (slurp "resources/shaders/shockwave/fragment-jump-flooding-step.glsl"))
