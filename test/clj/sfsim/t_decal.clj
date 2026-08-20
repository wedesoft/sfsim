;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-decal
  (:require
    [clojure.math :refer (to-radians)]
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [fastmath.vector :refer (vec3)]
    [fastmath.matrix :refer (inverse mulm eye rotation-matrix-3d-x)]
    [sfsim.matrix :as matrix]
    [sfsim.render :refer (with-invisible-window clear)]
    [sfsim.model :as model])
  (:import
    (org.lwjgl.glfw
      GLFW)))


(mi/collect! {:ns (all-ns)})
(mi/instrument! {:report (pretty/thrower)})

(GLFW/glfwInit)

(fact "Render a square with a decal"
      (with-invisible-window
        (let [geometry-renderer (model/make-scene-geometry-renderer true)
              plane             (model/read-gltf "test/clj/sfsim/fixtures/decal/plane.glb")
              scene             (model/load-scene-into-opengl (model/geometry-program-selection geometry-renderer) plane)
              translation       (matrix/translation-matrix (vec3 0 0 -4))
              rotation          (matrix/rotation-matrix (rotation-matrix-3d-x (to-radians 60.0)))
              camera-to-world   (mulm rotation translation)
              projection        (matrix/projection-matrix 160 120 0.1 10.0 (to-radians 60))
              light-direction   (vec3 0 1 0)
              geometry-buffers  (model/make-geometry-buffers 160 120)]
          (model/render-geometry geometry-buffers
                                 (clear)
                                 (model/render-scene-geometry2 geometry-renderer projection
                                                               {:sfsim.render/camera-to-world camera-to-world}
                                                               scene))
          (model/destroy-geometry-buffers geometry-buffers)
          (model/destroy-scene scene)
          (model/destroy-scene-geometry-renderer geometry-renderer))))


(GLFW/glfwTerminate)
