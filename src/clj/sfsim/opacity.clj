;; Copyright (C) 2026 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.opacity
  "Rendering of deep opacity maps for cloud shadows"
  (:require
    [clojure.math :refer (sqrt)]
    [fastmath.vector :refer (mag dot)]
    [malli.core :as m]
    [sfsim.atmosphere :refer (phase)]
    [sfsim.clouds :refer (opacity-vertex opacity-fragment opacity-cascade setup-cloud-render-uniforms cloud-data)]
    [sfsim.matrix :refer (split-list shadow-matrix-cascade shadow-config shadow-data)]
    [sfsim.planet :refer (render-shadow-cascade destroy-shadow-cascade planet-config planet-shadow-renderer
                                                shadow-vars render-depth)]
    [sfsim.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                       use-program uniform-int uniform-float uniform-vector3 render-quads use-textures render-config
                                       vertex-array-object render-vars)]
    [sfsim.texture :refer (destroy-texture)]
    [sfsim.util :refer (sqr)]
    [sfsim.worley :refer (worley-size)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def opacity-renderer
  (m/schema [:map [::program :int] [:sfsim.clouds/data cloud-data] [::data shadow-data]
             [::vao vertex-array-object]]))


(defn make-shadow-data
  "Create hash map with shadow parameters"
  {:malli/schema [:=> [:cat shadow-config planet-config cloud-data] shadow-data]}
  [shadow-config planet-config cloud-data]
  (assoc shadow-config
         ::depth (render-depth (:sfsim.planet/radius planet-config)
                               (:sfsim.planet/max-height planet-config)
                               (:sfsim.clouds/cloud-top cloud-data))))


(defn make-opacity-renderer
  "Initialise an opacity program"
  {:malli/schema [:=> [:cat [:map [:sfsim.render/config render-config] [:sfsim.planet/config planet-config]
                             [:sfsim.clouds/data cloud-data] [::data shadow-data]]] opacity-renderer]}
  [data]
  (let [planet-config (:sfsim.planet/config data)
        shadow-data   (::data data)
        cloud-data    (:sfsim.clouds/data data)
        program       (make-program :sfsim.render/vertex [opacity-vertex]
                                    :sfsim.render/fragment [(opacity-fragment (::num-opacity-layers shadow-data)
                                                                              (:sfsim.clouds/perlin-octaves cloud-data)
                                                                              (:sfsim.clouds/cloud-octaves cloud-data))])
        indices       [0 1 3 2]
        vertices      [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao           (make-vertex-array-object program indices vertices ["point" 2])]
    (use-program program)
    (setup-cloud-render-uniforms program cloud-data 0)
    (uniform-int program "shadow_size" (::shadow-size shadow-data))
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    {::program program
     :sfsim.clouds/data cloud-data
     ::data shadow-data
     ::vao vao}))


(defn render-opacity-cascade
  "Render a cascade of opacity maps and return it as a list of 3D textures"
  [{::keys [program vao data] :as other} matrix-cas light-direction scatter-amount opacity-step]
  (let [cloud-data (:sfsim.clouds/data other)]
    (use-textures {0 (:sfsim.clouds/worley cloud-data) 1 (:sfsim.clouds/perlin-worley cloud-data)
                   2 (:sfsim.clouds/cloud-cover cloud-data)})
    (opacity-cascade (::shadow-size data) (::num-opacity-layers data) matrix-cas
                     (/ ^double (:sfsim.clouds/detail-scale cloud-data) ^long worley-size) program
                     (uniform-vector3 program "light_direction" light-direction)
                     (uniform-float program "scatter_amount" scatter-amount)
                     (uniform-float program "opacity_step" opacity-step)
                     (render-quads vao))))


(defn destroy-opacity-cascade
  "Destroy cascade of opacity maps"
  [opacities]
  (doseq [layer opacities]
    (destroy-texture layer)))


(defn destroy-opacity-renderer
  "Delete opacity renderer objects"
  {:malli/schema [:=> [:cat opacity-renderer] :nil]}
  [{::keys [vao program]}]
  (destroy-vertex-array-object vao)
  (destroy-program program))


(defn opacity-and-shadow-cascade
  "Compute deep opacity map cascade and shadow cascade"
  {:malli/schema [:=> [:cat opacity-renderer planet-shadow-renderer shadow-data cloud-data render-vars [:maybe :map] :double]
                  shadow-vars]}
  [opacity-renderer planet-shadow-renderer shadow-data cloud-data render-vars tree opacity-base]
  (let [splits          (split-list shadow-data render-vars)
        biases          (:sfsim.opacity/opacity-biases shadow-data)
        matrix-cascade  (shadow-matrix-cascade shadow-data render-vars)
        position        (:sfsim.render/origin render-vars)
        cos-light       (/ (dot (:sfsim.render/light-direction render-vars) position) (mag position))
        sin-light       (sqrt (- 1.0 (sqr cos-light)))
        opacity-step    (* (+ cos-light (* 10 sin-light)) ^double opacity-base)
        scatter-amount  (+ ^double (* ^double (:sfsim.clouds/anisotropic cloud-data)
                                      ^double (phase {:sfsim.atmosphere/scatter-g 0.76} -1.0))
                           ^double (- 1.0 ^double (:sfsim.clouds/anisotropic cloud-data)))
        light-direction (:sfsim.render/light-direction render-vars)
        opacities       (render-opacity-cascade opacity-renderer matrix-cascade light-direction scatter-amount opacity-step)
        shadows         (render-shadow-cascade planet-shadow-renderer ::matrix-cascade matrix-cascade :tree tree)]
    {::opacity-step opacity-step
     ::opacity-cutoff (:sfsim.clouds/opacity-cutoff (:sfsim.clouds/data opacity-renderer))
     ::splits splits
     ::biases biases
     ::matrix-cascade matrix-cascade
     ::shadows shadows
     ::opacities opacities}))


(defn destroy-opacity-and-shadow
  "Destroy deep opacity map cascade and shadow cascade"
  [{::keys [shadows opacities]}]
  (destroy-opacity-cascade opacities)
  (destroy-shadow-cascade shadows))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
