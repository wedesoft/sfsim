;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.lighting
    "Shaders and methods for lighting pass"
    (:require
      [comb.template :as template]
      [sfsim.render :refer (make-program destroy-program)]
      [sfsim.shaders :as shaders]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.clouds :as clouds]
      [sfsim.planet :as planet]))


(defn fragment-lighting
  [num-scene-shadows]
  (template/eval (slurp "resources/shaders/lighting/fragment.glsl") {:num-scene-shadows num-scene-shadows}))


(defn make-lighting-program
  [num-scene-shadows num-steps]
  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                :sfsim.render/fragment [(fragment-lighting num-scene-shadows) shaders/phong shaders/ray-sphere
                                        atmosphere/attenuation-outer atmosphere/attenuation-point planet/surface-radiance-function
                                        (clouds/overall-shading num-steps (clouds/overall-shading-parameters num-scene-shadows))
                                        atmosphere/cloud-overlay]))


(defn make-lighting-renderer
  [data]
  (let [shadow-config (:sfsim.opacity/data data)
        num-steps     (:sfsim.opacity/num-steps shadow-config)
        program       (make-lighting-program 0 num-steps)]
    {::program program}))


(defn destroy-lighting-renderer
  [renderer]
  (destroy-program (::program renderer)))
