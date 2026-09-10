;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-shockwave
    (:require
      [clojure.math :refer (sqrt exp)]
      [malli.dev.pretty :as pretty]
      [malli.instrument :as mi]
      [midje.sweet :refer :all]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test is-image)]
      [sfsim.render :refer (uniform-float)]
      [sfsim.shockwave :refer :all])
    (:import
      (org.lwjgl.glfw
        GLFW)))

(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)


(def shockfront-probe
  (template/fn [radial-distance curvature-radius]
"#version 450 core
out vec3 fragColor;
float shockfront(float radial_distance, float curvature_radius);
void main()
{
  float result = shockfront(<%= radial-distance  %>, <%= curvature-radius %>);
  fragColor = vec3(result);
}"))


(def shockfront-test (shader-test (fn [program mach] (uniform-float program "mach" mach))
                                  shockfront-probe shockfront))


(tabular "Shockfront shape depending on curvature radius"
         (fact (first (shockfront-test [?mach] [?y ?radius])) => (roughly ?result 1e-6))
         ?mach    ?y  ?radius ?result
         (sqrt 2) 0.0 1.0     0.722592
         (sqrt 2) 1.0 1.0     0.630982
         (sqrt 2) 2.0 1.0     0.364871
         10.0     0.0 1.0     0.147709
         10.0     1.0 1.0    -0.272397
         10.0     2.0 1.0    -1.523827)


(GLFW/glfwTerminate)
