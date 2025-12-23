;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.plume
    "Module with shader functions for plume rendering"
    (:require
      [malli.core :as m]
      [comb.template :as template]
      [sfsim.matrix :refer (fvec3 fmat4)]
      [sfsim.shaders :as shaders]
      [sfsim.atmosphere :as atmosphere]
      [sfsim.bluenoise :refer (sampling-offset)]
      [sfsim.render :refer (uniform-float uniform-float uniform-matrix4 uniform-vector3)]))


(def plume-phase
  "Shader function for phase function of mach cone positions"
  (slurp "resources/shaders/plume/plume-phase.glsl"))


(def diamond-phase
  "Shader function to determine phase of Mach diamonds in rocket exhaust plume"
  [plume-phase (slurp "resources/shaders/plume/diamond-phase.glsl")])


(def plume-limit
  "Shader function to get extent of rocket plume"
  (slurp "resources/shaders/plume/limit.glsl"))


(def bulge
  "Shader function to determine shape of rocket exhaust plume"
  [plume-limit plume-phase (slurp "resources/shaders/plume/bulge.glsl")])


(defn diamond
  "Shader function for volumetric Mach diamonds"
  [fringe]
  [plume-limit diamond-phase plume-phase (template/eval (slurp "resources/shaders/plume/diamond.glsl") {:fringe fringe})])


(def plume-start -7.5047)
(def plume-end -70.0)
(def plume-width-2 7.4266)


(defn plume-transfer
  "Shader for computing engine plume light transfer at a point"
  [fringe]
  [plume-limit bulge (diamond fringe) shaders/noise3d shaders/sdf-circle shaders/sdf-rectangle
   (template/eval (slurp "resources/shaders/plume/plume-transfer.glsl")
                  {:plume-start plume-start :plume-end plume-end :plume-width-2 plume-width-2})])


(def plume-box-size
  [plume-limit
   (template/eval (slurp "resources/shaders/plume/plume-box-size.glsl")
                  {:plume-start plume-start :plume-end plume-end :plume-width-2 plume-width-2})])


(def plume-box
  [plume-box-size shaders/ray-box (slurp "resources/shaders/plume/plume-box.glsl")])


(def plume-fringe 0.05)


(defn sample-plume-segment
  [outer]
  [sampling-offset plume-box (plume-transfer plume-fringe) shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/sample-plume-segment.glsl") {:outer outer})])


(def sample-plume-point
  (sample-plume-segment false))


(def sample-plume-outer
  (sample-plume-segment true))


(defn plume-segment
  [outer]
  [(sample-plume-segment outer) shaders/ray-sphere atmosphere/attenuation-track shaders/limit-interval
   (template/eval (slurp "resources/shaders/plume/plume-segment.glsl") {:outer outer})])


(def plume-outer
  (plume-segment true))


(def plume-point
  (plume-segment false))


(def model-data
  (m/schema [:map [:sfsim.model/object-radius :double]
                  [:sfsim.model/nozzle :double]
                  [:sfsim.model/min-limit :double]
                  [:sfsim.model/max-slope :double]
                  [:sfsim.model/omega-factor :double]
                  [:sfsim.model/diamond-strength :double]
                  [:sfsim.model/engine-step :double]]))


(defn setup-static-plume-uniforms
  {:malli/schema [:=> [:cat :int model-data] :nil]}
  [program model-data]
  (uniform-float program "nozzle" (:sfsim.model/nozzle model-data))
  (uniform-float program "min_limit" (:sfsim.model/min-limit model-data))
  (uniform-float program "max_slope" (:sfsim.model/max-slope model-data))
  (uniform-float program "omega_factor" (:sfsim.model/omega-factor model-data))
  (uniform-float program "diamond_strength" (:sfsim.model/diamond-strength model-data))
  (uniform-float program "engine_step" (:sfsim.model/engine-step model-data)))


(def plume-vars (m/schema [:map [:sfsim.render/object-origin fvec3]
                                [:sfsim.render/object-distance :double]
                                [:sfsim.render/camera-to-object fmat4]]))
(def model-vars (m/schema [:map [:sfsim.model/time :double]
                                [:sfsim.model/pressure :double]
                                [:sfsim.model/throttle :double]]))


(defn setup-dynamic-plume-uniforms
  {:malli/schema [:=> [:cat :int plume-vars model-vars] :nil]}
  [program render-vars model-vars]
  (uniform-vector3 program "object_origin" (:sfsim.render/object-origin render-vars))
  (uniform-float program "object_distance" (:sfsim.render/object-distance render-vars))
  (uniform-matrix4 program "camera_to_object" (:sfsim.render/camera-to-object render-vars))
  (uniform-float program "pressure" (:sfsim.model/pressure model-vars))
  (uniform-float program "time" (:sfsim.model/time model-vars))
  (uniform-float program "throttle" (:sfsim.model/throttle model-vars)))


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
