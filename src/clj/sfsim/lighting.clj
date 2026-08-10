;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.lighting
    "Shaders and methods for lighting pass"
    (:require
      [comb.template :as template]
      [fastmath.matrix :refer (mulm)]
      [sfsim.render :refer (make-program destroy-program setup-shadow-and-opacity-maps uniform-sampler uniform-float uniform-int
                            use-program uniform-matrix4 uniform-vector3 setup-shadow-matrices use-textures)]
      [sfsim.shaders :as shaders]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.matrix :as matrix]
      [sfsim.model :as model]
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
                                        (shaders/shadow-lookup "scene_shadow_lookup" "scene_shadow_size")
                                        (shaders/percentage-closer-filtering "average_scene_shadow" "scene_shadow_lookup"
                                                                             "scene_shadow_size" [["sampler2DShadow" "shadow_map"]])
                                        atmosphere/cloud-overlay]))


(defn set-static-lighting-uniforms
  [data program num-scene-shadows]
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
    (uniform-sampler program "clouds" 0)
    (uniform-sampler program "dist" 1)
    (atmosphere/setup-atmosphere-uniforms program atmosphere-luts 2 true)
    (setup-shadow-and-opacity-maps program shadow-data (+ num-scene-shadows 6))
    (uniform-float program "albedo" albedo)
    (uniform-float program "amplification" amplification)
    (uniform-float program "specular" specular)
    (uniform-float program "radius" radius)
    (uniform-int program "cloud_subsampling" cloud-subsampling)
    (uniform-float program "depth_sigma" depth-sigma)
    (uniform-float program "min_depth_exponent" min-depth-exponent)))


(defn make-lighting-renderer
  [data num-scene-shadows]
  (let [shadow-config   (:sfsim.opacity/data data)
        atmosphere-luts (:sfsim.atmosphere/luts data)
        num-steps       (:sfsim.opacity/num-steps shadow-config)
        program         (make-lighting-program num-scene-shadows num-steps)]
    (set-static-lighting-uniforms data program num-scene-shadows)
    {::program program
     ::atmosphere-luts atmosphere-luts
     ::num-scene-shadows num-scene-shadows}))


(defn destroy-lighting-renderer
  [renderer]
  (destroy-program (::program renderer)))


(defn set-dynamic-lighting-uniforms
  [lighting-renderer width height camera-position camera-orientation light-direction planet-render-vars
   cloud-render-vars shadow-vars cloud-geometry clouds object-shadows]
  (let [program           (::program lighting-renderer)
        atmosphere-luts   (::atmosphere-luts lighting-renderer)
        camera-to-world   (matrix/transformation-matrix (matrix/quaternion->matrix camera-orientation) camera-position)
        z-far             (:sfsim.render/z-far planet-render-vars)
        overlay-width     (:sfsim.render/overlay-width cloud-render-vars)
        overlay-height    (:sfsim.render/overlay-height cloud-render-vars)
        num-scene-shadows (::num-scene-shadows lighting-renderer)]
    (setup-shadow-matrices program shadow-vars)
    (uniform-int program "width" width)
    (uniform-int program "height" height)
    (uniform-matrix4 program "camera_to_world" camera-to-world)
    (uniform-vector3 program "origin" camera-position)
    (uniform-vector3 program "light_direction" light-direction)
    (uniform-float program "z_far" z-far)
    (uniform-int program "overlay_width" overlay-width)
    (uniform-int program "overlay_height" overlay-height)
    ;; TODO: shadow_size (sfsim.opacity/shadow-size) not set up?
    (uniform-float program "shadow_bias" 1e-6) ;; TODO: get from config
    (doseq [i (range num-scene-shadows)]
           (let [matrices         (:sfsim.model/matrices (nth object-shadows i))
                 world-to-object  (:sfsim.matrix/world-to-object matrices)
                 object-to-shadow (:sfsim.matrix/object-to-shadow-map matrices)
                 camera-to-shadow (mulm object-to-shadow (mulm world-to-object camera-to-world))]
             (uniform-int program "scene_shadow_size" (:sfsim.texture/width (:sfsim.model/shadows (nth object-shadows i)))) ;; TODO: get from config
             (uniform-matrix4 program (str "camera_to_shadow_map_" (inc ^long i)) camera-to-shadow)
             (uniform-sampler program (str "scene_shadow_map_" (inc ^long i)) (+ 6 i))))  ;; TODO: move this to static uniforms set up
    (use-textures {0 clouds
                   1 (:sfsim.clouds/distance cloud-geometry)
                   2 (:sfsim.atmosphere/transmittance atmosphere-luts)
                   3 (:sfsim.atmosphere/scatter atmosphere-luts)
                   4 (:sfsim.atmosphere/mie atmosphere-luts)
                   5 (:sfsim.atmosphere/surface-radiance atmosphere-luts)})
    (use-textures (zipmap (drop 6 (range))
                          (map :sfsim.model/shadows object-shadows)))
    (use-textures (zipmap (drop (+ num-scene-shadows 6) (range))
                          (concat (:sfsim.opacity/shadows shadow-vars)
                                  (:sfsim.opacity/opacities shadow-vars))))))


(defn render-lighting
  [lighting-renderer width height geometry-buffers shadow-config camera-position camera-orientation
   light-direction planet-render-vars cloud-render-vars shadow-vars cloud-geometry clouds object-shadows]
  (let [program           (::program lighting-renderer)
        num-steps         (:sfsim.opacity/num-steps shadow-config)
        num-scene-shadows (::num-scene-shadows lighting-renderer)]
    (model/render-lighting geometry-buffers program (+ num-scene-shadows 4 2 (* 2 num-steps))
                           (set-dynamic-lighting-uniforms lighting-renderer width height camera-position
                                                          camera-orientation light-direction
                                                          planet-render-vars cloud-render-vars shadow-vars
                                                          cloud-geometry clouds object-shadows))))
