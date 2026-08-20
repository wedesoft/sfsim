;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.t-decal
  (:require
    [malli.dev.pretty :as pretty]
    [malli.instrument :as mi]
    [midje.sweet :refer :all]
    [sfsim.render :refer (with-invisible-window)]
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
              ]
          (model/destroy-scene scene)
          (model/destroy-scene-geometry-renderer geometry-renderer))))


(GLFW/glfwTerminate)
