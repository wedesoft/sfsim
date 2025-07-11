;; Copyright (C) 2025 Jan Wedekind <jan@wedesoft.de>
;; SPDX-License-Identifier: LGPL-3.0-or-later OR EPL-1.0+
;;
;; This source code is licensed under the Eclipse Public License v1.0
;; which you can obtain at https://www.eclipse.org/legal/epl-v10.html

(ns sfsim.clouds
  "Rendering of clouds"
  (:require
    [clojure.math :refer (tan pow log)]
    [comb.template :as template]
    [fastmath.matrix :refer (inverse)]
    [malli.core :as m]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.bluenoise :refer (noise-size) :as bluenoise]
    [sfsim.render :refer (destroy-program destroy-vertex-array-object framebuffer-render make-program use-textures
                          make-vertex-array-object render-quads uniform-float uniform-int uniform-sampler
                          uniform-matrix4 use-program) :as render]
    [sfsim.shaders :as shaders]
    [sfsim.texture :refer (make-empty-float-cubemap make-empty-vector-cubemap make-float-texture-2d make-float-texture-3d
                           make-empty-float-texture-3d generate-mipmap make-float-cubemap destroy-texture texture-3d
                           texture-2d)]
    [sfsim.util :refer (slurp-floats N N0)]
    [sfsim.worley :refer (worley-size)]))


(set! *unchecked-math* :warn-on-boxed)
(set! *warn-on-reflection* true)


(def cover-size 512)


(defn cloud-noise
  "Shader for sampling 3D cloud noise"
  {:malli/schema [:=> [:cat [:vector :double]] [:vector :string]]}
  [cloud-octaves]
  [(shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" cloud-octaves) (shaders/lookup-3d-lod "lookup_3d" "worley")
   (slurp "resources/shaders/clouds/cloud-noise.glsl")])


(def lod-at-distance
  "Shader function for determining LOD at given distance"
  (slurp "resources/shaders/clouds/lod-at-distance.glsl"))


(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear-sampling.glsl"))


(def exponential-sampling
  "Shader functions for defining exponential sampling"
  (slurp "resources/shaders/clouds/exponential-sampling.glsl"))


(def opacity-lookup
  "Shader function for looking up transmittance value from deep opacity map"
  [shaders/convert-2d-index shaders/convert-3d-index (slurp "resources/shaders/clouds/opacity-lookup.glsl")])


(def opacity-cascade-lookup
  "Perform opacity (transparency) lookup in cascade of deep opacity maps"
  (template/fn [n base-function] (slurp "resources/shaders/clouds/opacity-cascade-lookup.glsl")))


(def identity-cubemap-fragment
  "Fragment shader to render identity cubemap"
  [shaders/cubemap-vectors (slurp "resources/shaders/clouds/identity-cubemap-fragment.glsl")])


(defn identity-cubemap
  "Create identity cubemap"
  {:malli/schema [:=> [:cat N] texture-3d]}
  [size]
  (let [result   (make-empty-vector-cubemap :sfsim.texture/linear :sfsim.texture/clamp size)
        indices  [0 1 3 2]
        vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        program (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                              :sfsim.render/fragment [identity-cubemap-fragment])
        vao     (make-vertex-array-object program indices vertices ["point" 3])]
    (framebuffer-render size size :sfsim.render/cullback nil [result]
                        (use-program program)
                        (uniform-int program "size" size)
                        (render-quads vao))
    (destroy-program program)
    result))


(defn iterate-cubemap-warp-fragment
  "Fragment shader for iterating cubemap warp"
  {:malli/schema [:=> [:cat :string :string] render/shaders]}
  [current-name field-method-name]
  [shaders/cubemap-vectors shaders/interpolate-vector-cubemap
   (template/eval (slurp "resources/shaders/clouds/iterate-cubemap-warp-fragment.glsl")
                  {:current-name current-name :field-method-name field-method-name})])


(defn make-iterate-cubemap-warp-program
  "Create program to iteratively update cubemap warp vector field"
  {:malli/schema [:=> [:cat :string :string render/shaders] :int]}
  [current-name field-method-name shaders]
  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                :sfsim.render/fragment (conj shaders (iterate-cubemap-warp-fragment current-name field-method-name))))


(defmacro iterate-cubemap
  "Macro to run program to update cubemap"
  [size scale program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# ["point" 3])
         result#   (make-empty-vector-cubemap :sfsim.texture/linear :sfsim.texture/clamp ~size)]
     (framebuffer-render ~size ~size :sfsim.render/cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         (uniform-float ~program "scale" ~scale)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))


(defn cubemap-warp-fragment
  "Fragment shader for looking up values using a cubemap warp vector field"
  {:malli/schema [:=> [:cat :string :string] render/shaders]}
  [current-name lookup-name]
  [shaders/cubemap-vectors shaders/interpolate-vector-cubemap
   (template/eval (slurp "resources/shaders/clouds/cubemap-warp-fragment.glsl")
                  {:current-name current-name :lookup-name lookup-name})])


(defn make-cubemap-warp-program
  "Create program to look up values using a given cubemap warp vector field"
  {:malli/schema [:=> [:cat :string :string render/shaders] :int]}
  [current-name lookup-name shaders]
  (make-program :sfsim.render/vertex [shaders/vertex-passthrough]
                :sfsim.render/fragment (conj shaders (cubemap-warp-fragment current-name lookup-name))))


(defmacro cubemap-warp
  "Macro to run program to look up values using cubemap warp vector field"
  [size program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# ["point" 3])
         result#   (make-empty-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp ~size)]
     (framebuffer-render ~size ~size :sfsim.render/cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))


(defn curl-vector
  "Shader for computing curl vectors from a noise function"
  {:malli/schema [:=> [:cat :string :string] render/shaders]}
  [method-name gradient-name]
  [shaders/rotate-vector shaders/project-vector
   (template/eval (slurp "resources/shaders/clouds/curl-vector.glsl") {:method-name method-name :gradient-name gradient-name})])


(defn flow-field
  "Shader to create potential field for generating curl noise for global cloud cover"
  {:malli/schema [:=> [:cat [:vector :double]] render/shaders]}
  [flow-octaves]
  [(shaders/noise-octaves "octaves_north" "lookup_north" flow-octaves) (shaders/lookup-3d "lookup_north" "worley_north")
   (shaders/noise-octaves "octaves_south" "lookup_south" flow-octaves) (shaders/lookup-3d "lookup_south" "worley_south")
   (slurp "resources/shaders/clouds/flow-field.glsl")])


(defn cloud-cover-cubemap
  "Program to generate planetary cloud cover using curl noise"
  {:malli/schema [:=> [:cat [:* :any]] texture-3d]}
  [& {::keys [size worley-size worley-south worley-north worley-cover flow-octaves cloud-octaves whirl prevailing curl-scale
              cover-scale num-iterations flow-scale]}]
  (let [warp        (atom (identity-cubemap size))
        update-warp (make-iterate-cubemap-warp-program
                      "current" "curl"
                      [(curl-vector "curl" "gradient") (shaders/gradient-3d "gradient" "flow_field" "epsilon")
                       (flow-field flow-octaves)])
        lookup      (make-cubemap-warp-program
                      "current" "cover"
                      [(shaders/noise-octaves "clouds" "noise" cloud-octaves)
                       (shaders/lookup-3d "noise" "worley")
                       (shaders/scale-noise "cover" "factor" "clouds")])
        epsilon       (/ 1.0 ^long worley-size (pow 2.0 (count flow-octaves)))]
    (use-program update-warp)
    (uniform-sampler update-warp "current" 0)
    (uniform-sampler update-warp "worley_north" 1)
    (uniform-sampler update-warp "worley_south" 2)
    (uniform-float update-warp "epsilon" epsilon)
    (uniform-float update-warp "whirl" whirl)
    (uniform-float update-warp "prevailing" prevailing)
    (uniform-float update-warp "curl_scale" curl-scale)
    (dotimes [_iteration num-iterations]
      (let [updated (iterate-cubemap size flow-scale update-warp (use-textures {0 @warp 1 worley-north 2 worley-south}))]
        (destroy-texture @warp)
        (reset! warp updated)))
    (let [result (cubemap-warp size lookup
                               (uniform-sampler lookup "current" 0)
                               (uniform-sampler lookup "worley" 1)
                               (uniform-float lookup "factor" (/ 1.0 (* 2.0 ^double cover-scale)))
                               (use-textures {0 @warp 1 worley-cover}))]
      (destroy-program lookup)
      (destroy-program update-warp)
      (destroy-texture @warp)
      result)))


(def cloud-profile
  "Shader for looking up vertical cloud profile"
  (slurp "resources/shaders/clouds/cloud-profile.glsl"))


(def sphere-noise
  "Sample 3D noise on the surface of a sphere"
  (template/fn [base-noise] (slurp "resources/shaders/clouds/sphere-noise.glsl")))


(def cloud-cover
  "Perform cloud cover lookup in cube map"
  [shaders/interpolate-float-cubemap (slurp "resources/shaders/clouds/cloud-cover.glsl")])


(defn cloud-base
  "Shader for determining cloud density at specified point"
  {:malli/schema [:=> [:cat [:vector :double]] render/shaders]}
  [perlin-octaves]
  [cloud-cover cloud-profile (sphere-noise "perlin_octaves") (shaders/lookup-3d "lookup_perlin" "perlin")
   (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves) (slurp "resources/shaders/clouds/cloud-base.glsl")])


(defn cloud-density
  "Compute cloud density at given point"
  {:malli/schema [:=> [:cat [:vector :double] [:vector :double]] render/shaders]}
  [perlin-octaves cloud-octaves]
  [(cloud-base perlin-octaves) (cloud-noise cloud-octaves) shaders/remap (slurp "resources/shaders/clouds/cloud-density.glsl")])


(def opacity-vertex
  "Vertex shader for rendering deep opacity map"
  [shaders/grow-shadow-index (slurp "resources/shaders/clouds/opacity-vertex.glsl")])


(defn opacity-fragment
  "Fragment shader for creating deep opacity map consisting of offset texture and 3D opacity texture"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-layers perlin-octaves cloud-octaves]
  [shaders/ray-shell (cloud-density perlin-octaves cloud-octaves)
   (template/eval (slurp "resources/shaders/clouds/opacity-fragment.glsl") {:num-layers num-layers})])


(defn planet-and-cloud-shadows
  "Multiply shadows to get overall shadow"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [(opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
   (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup" "shadow_size"
                                        [["sampler3D" "layers"] ["float" "depth"]])
   (shaders/shadow-cascade-lookup num-steps "average_shadow") (shaders/shadow-lookup "shadow_lookup" "shadow_size")
   (shaders/percentage-closer-filtering "average_shadow" "shadow_lookup" "shadow_size"
                                        [["sampler2DShadow" "shadow_map"]])
   (slurp "resources/shaders/clouds/planet-and-cloud-shadows.glsl")])


(defn environmental-shading
  "Shader function for determining direct light left after atmospheric scattering and shadows"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [shaders/is-above-horizon atmosphere/transmittance-point (planet-and-cloud-shadows num-steps)
   (slurp "resources/shaders/clouds/environmental-shading.glsl")])


(def powder-shader
  "Shader function for making low density areas of clouds darker"
  (slurp "resources/shaders/clouds/powder.glsl"))


(defn cloud-transfer
  "Single cloud scattering update step"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [(planet-and-cloud-shadows num-steps) atmosphere/transmittance-outer atmosphere/transmittance-track atmosphere/ray-scatter-track
   powder-shader (slurp "resources/shaders/clouds/cloud-transfer.glsl")])


(defn sample-cloud
  "Shader to sample the cloud layer and apply cloud scattering update steps"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [linear-sampling lod-at-distance bluenoise/sampling-offset atmosphere/phase-function
   (cloud-density perlin-octaves cloud-octaves) (cloud-transfer num-steps) (slurp "resources/shaders/clouds/sample-cloud.glsl")])


(defn cloud-point
  "Shader to compute pixel of cloud foreground overlay for planet"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [shaders/ray-sphere shaders/ray-shell (sample-cloud num-steps perlin-octaves cloud-octaves) shaders/clip-shell-intersections
   (slurp "resources/shaders/clouds/cloud-point.glsl")])


(defn cloud-outer
  "Shader to compute pixel of cloud foreground overlay for atmosphere"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [shaders/ray-sphere shaders/ray-shell (sample-cloud num-steps perlin-octaves cloud-octaves)
   (slurp "resources/shaders/clouds/cloud-outer.glsl")])


(defmacro opacity-cascade
  "Render cascade of deep opacity maps"
  [size num-opacity-layers matrix-cascade voxel-size program & body]
  `(mapv
     (fn render-deep-opacity-map# [opacity-level#]
       (let [opacity-layers#  (make-empty-float-texture-3d :sfsim.texture/linear :sfsim.texture/clamp (long ~size) (long ~size)
                                                           (inc (long ~num-opacity-layers)))
             level-of-detail# (/ (log (/ (double (/ (double (:sfsim.matrix/scale opacity-level#)) (long ~size)))
                                         (long ~voxel-size)))
                                 (log 2.0))]
         (framebuffer-render ~size ~size :sfsim.render/cullback nil [opacity-layers#]
                             (use-program ~program)
                             (uniform-int ~program "shadow_size" ~size)
                             (uniform-float ~program "level_of_detail" level-of-detail#)
                             (uniform-matrix4 ~program "shadow_ndc_to_world"
                                              (inverse (:sfsim.matrix/world-to-shadow-ndc opacity-level#)))
                             (uniform-float ~program "depth" (:sfsim.matrix/depth opacity-level#))
                             ~@body)
         opacity-layers#))
     ~matrix-cascade))


(defn cloud-density-shaders
  "List of cloud shaders to sample density values"
  {:malli/schema [:=> [:cat [:vector :double] [:vector :double]] render/shaders]}
  [cloud-octaves perlin-octaves]
  [(cloud-density perlin-octaves cloud-octaves) shaders/interpolate-float-cubemap shaders/convert-cubemap-index cloud-cover])


(defn opacity-lookup-shaders
  "List of opacity lookup shaders"
  {:malli/schema [:=> [:cat N] render/shaders]}
  [num-steps]
  [(opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
   (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup" "shadow_size"
                                        [["sampler3D" "layers"] ["float" "depth"]])
   shaders/convert-2d-index shaders/convert-3d-index])


(defn fragment-atmosphere-clouds
  "Shader for rendering clouds above horizon"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [(cloud-outer num-steps perlin-octaves cloud-octaves) (slurp "resources/shaders/clouds/fragment-atmosphere.glsl")])


(defn lod-offset
  "Compute level of detail offset for sampling cloud textures"
  {:malli/schema [:=> [:cat :map :map :map] :double]}
  [render-config cloud-data render-vars]
  (/ (log (/ (tan (* 0.5 ^double (:sfsim.render/fov render-config)))
             (* 0.5 ^long (:sfsim.render/window-width render-vars))
             (/ ^double (::detail-scale cloud-data) ^long worley-size))) (log 2.0)))


(def cloud-config
  (m/schema [:map [::cloud-octaves [:vector :double]] [::perlin-octaves [:vector :double]]
             [::cloud-bottom :double] [::cloud-top :double] [::detail-scale :double]
             [::cloud-scale :double] [::cloud-multiplier :double] [::cover-multiplier :double]
             [::threshold :double] [::cap :double] [::powder-decay :double] [::anisotropic :double]
             [::cloud-step :double] [::opacity-cutoff :double]]))


(def cloud-data
  (m/schema [:and cloud-config
             [:map [::worley texture-3d] [::perlin-worley texture-3d] [::cloud-cover texture-3d]
              [::bluenoise texture-2d]]]))


(defn make-cloud-data
  "Method to load cloud textures and collect cloud data (not tested)"
  {:malli/schema [:=> [:cat cloud-config] cloud-data]}
  [cloud-config]
  (let [worley-floats        (slurp-floats "data/clouds/worley-cover.raw")
        perlin-floats        (slurp-floats "data/clouds/perlin.raw")
        worley-data          #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data worley-floats}
        worley               (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat worley-data)
        perlin-worley-floats (float-array (mapv #(+ (* 0.3 ^double %1) (* 0.7 ^double %2)) perlin-floats worley-floats))
        perlin-worley-data   #:sfsim.image{:width worley-size :height worley-size :depth worley-size :data perlin-worley-floats}
        perlin-worley        (make-float-texture-3d :sfsim.texture/linear :sfsim.texture/repeat perlin-worley-data)
        cover-floats-list    (mapv (fn load-cloud-cubemap-face [i] (slurp-floats (str "data/clouds/cover" i ".raw"))) (range 6))
        cover-data           (mapv (fn make-cloud-cover-image
                                     [cover-floats]
                                     #:sfsim.image{:width cover-size :height cover-size :data cover-floats})
                                   cover-floats-list)
        cloud-cover          (make-float-cubemap :sfsim.texture/linear :sfsim.texture/clamp cover-data)
        bluenoise-floats     (slurp-floats "data/bluenoise.raw")
        bluenoise-data       #:sfsim.image{:width noise-size :height noise-size :data bluenoise-floats}
        bluenoise            (make-float-texture-2d :sfsim.texture/nearest :sfsim.texture/repeat bluenoise-data)]
    (generate-mipmap worley)
    (assoc cloud-config
           ::worley worley
           ::perlin-worley perlin-worley
           ::cloud-cover cloud-cover
           ::bluenoise bluenoise)))


(defn destroy-cloud-data
  "Method to destroy cloud textures (not tested)"
  {:malli/schema [:=> [:cat cloud-data] :nil]}
  [{::keys [worley perlin-worley cloud-cover bluenoise]}]
  (destroy-texture worley)
  (destroy-texture perlin-worley)
  (destroy-texture cloud-cover)
  (destroy-texture bluenoise))


(defn setup-cloud-render-uniforms
  "Method to set up uniform variables for cloud rendering"
  {:malli/schema [:=> [:cat :int :map :int] :nil]}
  [program cloud-data sampler-offset]
  (uniform-sampler program "worley" sampler-offset)
  (uniform-sampler program "perlin" (+ ^long sampler-offset 1))
  (uniform-sampler program "cover" (+ ^long sampler-offset 2))
  (uniform-int program "cover_size" (:sfsim.texture/width (::cloud-cover cloud-data)))
  (uniform-float program "cloud_bottom" (::cloud-bottom cloud-data))
  (uniform-float program "cloud_top" (::cloud-top cloud-data))
  (uniform-float program "detail_scale" (::detail-scale cloud-data))
  (uniform-float program "cloud_scale" (::cloud-scale cloud-data))
  (uniform-float program "cloud_multiplier" (::cloud-multiplier cloud-data))
  (uniform-float program "cover_multiplier" (::cover-multiplier cloud-data))
  (uniform-float program "cap" (::cap cloud-data))
  (uniform-float program "powder_decay" (::powder-decay cloud-data))
  (uniform-float program "cloud_threshold" (::threshold cloud-data)))


(defn setup-cloud-sampling-uniforms
  "Method to set up uniform variables for sampling clouds"
  {:malli/schema [:=> [:cat :int :map :int] :nil]}
  [program cloud-data sampler-offset]
  (uniform-sampler program "bluenoise" sampler-offset)
  (uniform-int program "noise_size" (:sfsim.texture/width (::bluenoise cloud-data)))
  (uniform-float program "anisotropic" (::anisotropic cloud-data))
  (uniform-float program "cloud_step" (::cloud-step cloud-data))
  (uniform-float program "opacity_cutoff" (::opacity-cutoff cloud-data)))


(defn overall-shading-parameters
  {:malli/schema [:=> [:cat N0] [:vector [:tuple :string :string]]]}
  [n]
  (mapv (fn [^long i] ["average_scene_shadow" (str "scene_shadow_map_" (inc i))]) (range n)))


(defn overall-shading
  {:malli/schema [:=> [:cat N [:sequential [:tuple :string :string]]] render/shaders]}
  [num-steps parameters]
  [(environmental-shading num-steps)
   (template/eval (slurp "resources/shaders/clouds/overall-shading.glsl") {:parameters parameters})])


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
