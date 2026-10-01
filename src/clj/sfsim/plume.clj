;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.plume
    "Module with shader functions for plume rendering"
    (:require
      [clojure.string :refer (split)]
      [malli.core :as m]
      [comb.template :as template]
      [fastmath.matrix :refer (inverse)]
      [sfsim.shaders :as shaders]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.bluenoise :refer (sampling-offset)]
      [sfsim.render :refer (use-program uniform-matrix4 uniform-float with-culling with-stencil-op-ref-and-mask render-quads
                            uniform-int uniform-vector3 use-textures)])
    (:import
      (org.lwjgl.opengl
        GL11)))


(def plume-phase
  "Shader function for phase function of mach cone positions"
  (slurp "resources/shaders/plume/plume-phase.glsl"))


(def diamond-phase
  "Shader function to determine phase of Mach diamonds in rocket exhaust plume"
  [plume-phase (slurp "resources/shaders/plume/diamond-phase.glsl")])


(defn plume-limit
  "Shader function to get extent of rocket plume"
  [method-name min-limit]
  (template/eval (slurp "resources/shaders/plume/limit.glsl") {:method-name method-name :min-limit min-limit}))


(def rcs-bulge
  "Shader function to determine shape of RCS thruster exhaust plume"
  [(plume-limit "rcs_limit" "rcs_min_limit") (slurp "resources/shaders/plume/rcs-bulge.glsl")])


(def plume-bulge
  "Shader function to determine shape of rocket exhaust plume"
  [(plume-limit "plume_limit" "plume_min_limit") plume-phase (slurp "resources/shaders/plume/plume-bulge.glsl")])


(defn diamond
  "Shader function for volumetric Mach diamonds"
  [fringe]
  [(plume-limit "plume_limit" "plume_min_limit") diamond-phase plume-phase
   (template/eval (slurp "resources/shaders/plume/diamond.glsl") {:fringe fringe})])


(def plume-end -60.0)
(def plume-width-2 7.4266)


(defn plume-transfer
  "Shader for computing engine plume light transfer at a point"
  [fringe]
  [(plume-limit "plume_limit" "plume_min_limit") plume-bulge (diamond fringe) shaders/noise3d shaders/sdf-circle shaders/sdf-rectangle
   (template/eval (slurp "resources/shaders/plume/plume-transfer.glsl") {:plume-end plume-end :plume-width-2 plume-width-2})])


(def rcs-end -3.0)


(defn rcs-transfer
  "Shader for computing RCS thruster plume light transfer at a point"
  [base-density]
  [(plume-limit "rcs_limit" "rcs_min_limit") rcs-bulge shaders/noise3d
   (template/eval (slurp "resources/shaders/plume/rcs-transfer.glsl") {:base-density base-density :rcs-end rcs-end})])


(def plume-box-size
  [(plume-limit "plume_limit" "plume_min_limit")
   (template/eval (slurp "resources/shaders/plume/plume-box-size.glsl") {:plume-end plume-end :plume-width-2 plume-width-2})])


(def plume-box
  [plume-box-size shaders/ray-box (template/eval (slurp "resources/shaders/plume/box.glsl") {:type "plume"})])


(def rcs-box-size
  [(plume-limit "rcs_limit" "rcs_min_limit")
   (template/eval (slurp "resources/shaders/plume/rcs-box-size.glsl") {:rcs-end rcs-end})])


(def rcs-box
  [rcs-box-size shaders/ray-box (template/eval (slurp "resources/shaders/plume/box.glsl") {:type "rcs"})])


(def plume-fringe 0.05)


(defn sample-plume-segment
  [outer]
  [sampling-offset plume-box (plume-transfer plume-fringe) shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/sample-segment.glsl") {:type "plume" :outer outer})])


(def rcs-base-density 0.1)


(defn sample-rcs-segment
  [outer]
  [sampling-offset rcs-box (rcs-transfer rcs-base-density) shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/sample-segment.glsl") {:type "rcs" :outer outer})])


(defn plume-segment
  [outer]
  [(sample-plume-segment outer) shaders/ray-sphere atmosphere/attenuation-track shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/segment.glsl") {:type "plume" :outer outer})])


(def plume-outer
  (plume-segment true))


(def plume-point
  (plume-segment false))


(defn rcs-segment
  [outer]
  [(sample-rcs-segment outer) shaders/ray-sphere atmosphere/attenuation-track shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/segment.glsl") {:type "rcs" :outer outer})])


(def rcs-outer
  (rcs-segment true))


(def rcs-point
  (rcs-segment false))


(def model-data
  (m/schema [:map [:sfsim.model/object-radius :double]
                  [:sfsim.model/plume-nozzle :double]
                  [:sfsim.model/plume-min-limit :double]
                  [:sfsim.model/plume-max-slope :double]
                  [:sfsim.model/omega-factor :double]
                  [:sfsim.model/diamond-strength :double]
                  [:sfsim.model/plume-step :double]]))


(defn setup-static-plume-uniforms
  {:malli/schema [:=> [:cat :int model-data] :nil]}
  [program model-data]
  (uniform-float program "plume_nozzle" (:sfsim.model/plume-nozzle model-data))
  (uniform-float program "plume_min_limit" (:sfsim.model/plume-min-limit model-data))
  (uniform-float program "plume_max_slope" (:sfsim.model/plume-max-slope model-data))
  (uniform-float program "plume_step" (:sfsim.model/plume-step model-data))
  (uniform-float program "omega_factor" (:sfsim.model/omega-factor model-data))
  (uniform-float program "diamond_strength" (:sfsim.model/diamond-strength model-data))
  (uniform-float program "rcs_nozzle" (:sfsim.model/rcs-nozzle model-data))
  (uniform-float program "rcs_min_limit" (:sfsim.model/rcs-min-limit model-data))
  (uniform-float program "rcs_max_slope" (:sfsim.model/rcs-max-slope model-data))
  (uniform-float program "rcs_step" (:sfsim.model/rcs-step model-data)))


(def model-vars (m/schema [:map [:sfsim.model/time :double]
                                [:sfsim.model/pressure :double]
                                [:sfsim.model/throttle :double]]))


(def plume-indices
  [4 5 7 6    ; front (+z)
   1 0 2 3    ; back  (-z)
   0 4 6 2    ; left  (-x)
   5 1 3 7    ; right (+x)
   2 6 7 3    ; top   (+y)
   0 1 5 4])  ; bottom (-y)


(def plume-vertices
  [-1.0 -1.0 -1.0
    1.0 -1.0 -1.0
   -1.0  1.0 -1.0
    1.0  1.0 -1.0
   -1.0 -1.0  1.0
    1.0 -1.0  1.0
   -1.0  1.0  1.0
    1.0  1.0  1.0])


(defn render-plume-overlay-basic
  [program-outer program-point plume-vao transform throttle]
  (with-culling :sfsim.render/cullfront
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
      (use-program program-outer)
      (uniform-matrix4 program-outer "plume_to_object" transform)
      (uniform-matrix4 program-outer "object_to_plume" (inverse transform))
      (uniform-float program-outer "plume_throttle" throttle)
      (render-quads plume-vao))
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
      (use-program program-point)
      (uniform-matrix4 program-point "plume_to_object" transform)
      (uniform-matrix4 program-point "object_to_plume" (inverse transform))
      (uniform-float program-point "plume_throttle" throttle)
      (render-quads plume-vao))
    (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
      (use-program program-point)
      (render-quads plume-vao))))


(defmulti render-plume-overlay (fn [_cloud-renderer plume-name _model-vars _transform] (first (split plume-name #" "))))


(defmethod render-plume-overlay "Plume"
  [{:sfsim.clouds/keys [programs plume-vao]} _plume-name model-vars transform]
  (render-plume-overlay-basic (:sfsim.clouds/plume-outer programs) (:sfsim.clouds/plume-point programs) plume-vao transform
                              (:sfsim.model/throttle model-vars)))


(defmethod render-plume-overlay "RCS"
  [{:sfsim.clouds/keys [programs plume-vao]} _plume-name _model-vars transform]
  (render-plume-overlay-basic (:sfsim.clouds/rcs-outer programs) (:sfsim.clouds/rcs-point programs) plume-vao transform 1.0))


(defn setup-dynamic-overlay-uniforms
  [program cloud-render-vars]
  (let [overlay-width  (:sfsim.render/overlay-width cloud-render-vars)
        overlay-height (:sfsim.render/overlay-height cloud-render-vars)]
    (uniform-int program "overlay_width" overlay-width)
    (uniform-int program "overlay_height" overlay-height)
    (uniform-vector3 program "origin" (:sfsim.render/origin cloud-render-vars))
    (uniform-vector3 program "object_origin" (:sfsim.render/object-origin cloud-render-vars))
    (uniform-matrix4 program "camera_to_world" (:sfsim.render/camera-to-world cloud-render-vars))
    (uniform-matrix4 program "world_to_camera" (inverse (:sfsim.render/camera-to-world cloud-render-vars)))
    (uniform-matrix4 program "camera_to_object" (:sfsim.render/camera-to-object cloud-render-vars))
    (uniform-matrix4 program "object_to_camera" (inverse (:sfsim.render/camera-to-object cloud-render-vars)))
    (uniform-matrix4 program "projection" (:sfsim.render/overlay-projection cloud-render-vars))
    (uniform-float program "object_distance" (:sfsim.render/object-distance cloud-render-vars))
    (uniform-vector3 program "light_direction" (:sfsim.render/light-direction cloud-render-vars))))


(defn render-plume-overlays
  [cloud-renderer plume-transforms cloud-render-vars model-vars geometry]
  (let [programs (:sfsim.clouds/programs cloud-renderer)]
    (doseq [program [(:sfsim.clouds/plume-outer programs) (:sfsim.clouds/plume-point programs)
                     (:sfsim.clouds/rcs-outer programs) (:sfsim.clouds/rcs-point programs)]]
           (use-program program)
           (setup-dynamic-overlay-uniforms program cloud-render-vars)
           (uniform-float program "pressure" (:sfsim.model/pressure model-vars))
           (uniform-float program "time" (:sfsim.model/time model-vars))
           (use-textures {0  (:sfsim.clouds/points geometry) 1 (:sfsim.clouds/distance geometry)}))
    (doseq [[thruster transform] plume-transforms]
           (render-plume-overlay cloud-renderer thruster model-vars transform))))
