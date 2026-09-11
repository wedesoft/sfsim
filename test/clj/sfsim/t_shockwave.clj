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
      [fastmath.vector :refer (vec3)]
      [comb.template :as template]
      [sfsim.conftest :refer (roughly-vector shader-test is-image)]
      [sfsim.render :refer (offscreen-render make-program uniform-float make-vertex-array-object clear use-program uniform-int
                            render-quads destroy-vertex-array-object destroy-program)]
      [sfsim.shaders :refer (vertex-passthrough)]
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
         10.0     0.1 0.0    -0.994987)


(def fragment-curvature
"#version 450 core
out vec3 fragColor;
uniform int size;
float curvature(vec4 normal, float max_result);
void main()
{
  vec2 uv = gl_FragCoord.xy / size * 2.0 - 1.0;
  vec4 N;
  if (length(uv) < 1.0) {
    N = vec4(uv, sqrt(1.0 - length(uv)), 0.0);
  } else {
    N = vec4(0.0, 0.0, 0.0, 0.0);
  };
  float h = 1.0 / size;
  float c = curvature(N, 1.0 * h) / h;
  fragColor = vec3(c);
}")


(fact "Estimate curvature off surface given normals"
      (offscreen-render
        256 256
        (let [indices  [0 1 3 2]
              vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
              program  (make-program :sfsim.render/vertex [vertex-passthrough] :sfsim.render/fragment [fragment-curvature curvature])
              vao      (make-vertex-array-object program indices vertices ["point" 3])]
          (clear (vec3 0.0 0.0 0.0))
          (use-program program)
          (uniform-int program "size" 256)
          (render-quads vao)
          (destroy-vertex-array-object vao)
          (destroy-program program))) => (is-image "test/clj/sfsim/fixtures/shockwave/curvature.png" 0.1))


(GLFW/glfwTerminate)
