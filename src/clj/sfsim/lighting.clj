;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.lighting
    "Shaders and methods for lighting pass"
    (:require
      [comb.template :as template]
      [sfsim.render :refer (make-program destroy-program setup-shadow-and-opacity-maps uniform-sampler uniform-float uniform-int
                            use-program)]
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


(defn set-static-lighting-uniforms
  [data program]
  (let [render-config      (:sfsim.render/config data)
        planet-config      (:sfsim.planet/config data)
        cloud-data         (:sfsim.clouds/data data)
        shadow-data        (:sfsim.opacity/data data)
        atmosphere-luts    (:sfsim.atmosphere/luts data)
        radius             (:sfsim.planet/radius planet-config)
        amplification      (:sfsim.render/amplification render-config)
        albedo             (:sfsim.planet/albedo planet-config)
        specular           (:sfsim.render/specular render-config)
        cloud-subsampling  (:sfsim.render/cloud-subsampling render-config)
        depth-sigma        (:sfsim.clouds/depth-sigma cloud-data)
        min-depth-exponent (:sfsim.clouds/min-depth-exponent cloud-data)]
    (use-program program)
    (atmosphere/setup-atmosphere-uniforms program atmosphere-luts 0 true)
    (setup-shadow-and-opacity-maps program shadow-data 6)
    (uniform-sampler program "clouds" 4)
    (uniform-sampler program "dist" 5)
    (uniform-float program "albedo" albedo)
    (uniform-float program "amplification" amplification)
    (uniform-float program "specular" specular)
    (uniform-float program "radius" radius)
    (uniform-int program "cloud_subsampling" cloud-subsampling)
    (uniform-float program "depth_sigma" depth-sigma)
    (uniform-float program "min_depth_exponent" min-depth-exponent)))


(defn make-lighting-renderer
  [data]
  (let [shadow-config (:sfsim.opacity/data data)
        num-steps     (:sfsim.opacity/num-steps shadow-config)
        program       (make-lighting-program 0 num-steps)]
    (set-static-lighting-uniforms data program)
    {::program program}))


(defn destroy-lighting-renderer
  [renderer]
  (destroy-program (::program renderer)))
