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
    [fastmath.vector :refer (vec4 vec3 mag)]
    [fastmath.matrix :refer (mulm mulv inverse)]
    [malli.core :as m]
    [sfsim.matrix :refer (transformation-matrix vec4->vec3 quaternion->matrix projection-matrix)]
    [sfsim.atmosphere :as atmosphere]
    [sfsim.bluenoise :refer (noise-size) :as bluenoise]
    [sfsim.render :refer (destroy-program destroy-vertex-array-object framebuffer-render make-program use-textures
                          make-vertex-array-object render-quads uniform-float uniform-int uniform-sampler
                          uniform-vector3 uniform-matrix4 use-program clear with-stencils with-stencil-op-ref-and-mask
                          with-underlay-blending setup-shadow-matrices) :as render]
    [sfsim.shaders :as shaders]
    [sfsim.plume :refer (plume-outer plume-point plume-indices plume-vertices plume-box-size) :as plume]
    [sfsim.texture :refer (make-empty-float-cubemap make-empty-vector-cubemap make-float-texture-2d make-float-texture-3d
                           make-empty-float-texture-3d generate-mipmap make-float-cubemap destroy-texture texture-3d
                           texture-2d make-empty-texture-2d make-empty-float-texture-2d make-empty-depth-stencil-texture-2d)]
    [sfsim.util :refer (slurp-floats N N0)]
    [sfsim.worley :refer (worley-size)])
  (:import
    (org.lwjgl.opengl
      GL11 GL30)))


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
  [(planet-and-cloud-shadows num-steps) atmosphere/transmittance-outer atmosphere/attenuate powder-shader
   (slurp "resources/shaders/clouds/cloud-transfer.glsl")])


(defn sample-cloud
  "Shader to sample the cloud layer and apply cloud scattering update steps"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  [linear-sampling lod-at-distance bluenoise/sampling-offset atmosphere/phase-function
   (cloud-density perlin-octaves cloud-octaves) (cloud-transfer num-steps) (slurp "resources/shaders/clouds/sample-cloud.glsl")])


(defn cloud-segment
  "Shader to compute segment of cloud layer"
  [num-steps perlin-octaves cloud-octaves outer]
  [shaders/ray-shell shaders/ray-sphere shaders/limit-interval shaders/clip-interval
   (sample-cloud num-steps perlin-octaves cloud-octaves) shaders/clip-shell-intersections
   (template/eval (slurp "resources/shaders/clouds/cloud-segment.glsl") {:outer outer})])


(defn cloud-point
  "Shader to compute pixel of cloud foreground overlay for planet"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  (cloud-segment num-steps perlin-octaves cloud-octaves false))


(defn cloud-outer
  "Shader to compute pixel of cloud foreground overlay for atmosphere"
  {:malli/schema [:=> [:cat N [:vector :double] [:vector :double]] render/shaders]}
  [num-steps perlin-octaves cloud-octaves]
  (cloud-segment num-steps perlin-octaves cloud-octaves true))


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


(defmacro render-cloud-geometry
  [overlay-width overlay-height & body]
  `(let [depth#            (make-empty-depth-stencil-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp
                                                                ~overlay-width ~overlay-height)
         point-texture#    (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F
                                                  ~overlay-width ~overlay-height)
         distance-texture# (make-empty-float-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp
                                                        ~overlay-width ~overlay-height)]
     (framebuffer-render ~overlay-width ~overlay-height :sfsim.render/cullback depth# [point-texture# distance-texture#]
                         (clear (vec3 0.0 0.0 0.0) 0.0 0)
                         ~@body)
     {::depth-stencil depth#
      ::points point-texture#
      ::distance distance-texture#}))


(defn destroy-cloud-geometry
  [{::keys [depth-stencil points distance]}]
  (destroy-texture depth-stencil)
  (destroy-texture distance)
  (destroy-texture points))


(def geometry-point
  (slurp "resources/shaders/clouds/geometry-point.glsl"))


(def geometry-distance
  (slurp "resources/shaders/clouds/geometry-distance.glsl"))


(defn fragment-cloud-atmosphere
  [num-steps perlin-octaves cloud-octaves front]
  [geometry-point (cloud-point num-steps perlin-octaves cloud-octaves) (cloud-outer num-steps perlin-octaves cloud-octaves)
   (template/eval (slurp "resources/shaders/clouds/fragment-cloud-atmosphere.glsl") {:front front})])


(defn fragment-cloud-planet
  [num-steps perlin-octaves cloud-octaves front]
  [geometry-distance geometry-point (cloud-point num-steps perlin-octaves cloud-octaves)
   (template/eval (slurp "resources/shaders/clouds/fragment-cloud-planet.glsl") {:front front})])


(defn fragment-cloud-scene
  [num-steps perlin-octaves cloud-octaves]
  [geometry-distance geometry-point (cloud-point num-steps perlin-octaves cloud-octaves)
   (slurp "resources/shaders/clouds/fragment-cloud-scene.glsl")])


(defn fragment-plume
  [outer]
  [geometry-distance geometry-point plume-outer plume-point
   (template/eval (slurp "resources/shaders/plume/fragment.glsl") {:outer outer})])


(def vertex-plume
  [plume-box-size (slurp "resources/shaders/plume/vertex.glsl")])


(defn cloud-fragment-shaders
  [num-steps perlin-octaves cloud-octaves]
  {::atmosphere-front [shaders/vertex-passthrough (fragment-cloud-atmosphere num-steps perlin-octaves cloud-octaves true)]
   ::atmosphere-back [shaders/vertex-passthrough (fragment-cloud-atmosphere num-steps perlin-octaves cloud-octaves false)]
   ::planet-front [shaders/vertex-passthrough (fragment-cloud-planet num-steps perlin-octaves cloud-octaves true)]
   ::planet-back [shaders/vertex-passthrough (fragment-cloud-planet num-steps perlin-octaves cloud-octaves false)]
   ::scene-front [shaders/vertex-passthrough (fragment-cloud-scene num-steps perlin-octaves cloud-octaves)]
   ::plume-outer [shaders/vertex-passthrough (fragment-plume true)]
   ::plume-point [shaders/vertex-passthrough (fragment-plume false)]})


(defn make-cloud-render-vars
  [render-config scene-render-vars width height camera-position camera-orientation light-direction object-position object-orientation]
  (let [fov                (:sfsim.render/fov render-config)
        cloud-subsampling  (:sfsim.render/cloud-subsampling render-config)
        overlay-width      (quot ^long width ^long cloud-subsampling)
        overlay-height     (quot ^long height ^long cloud-subsampling)
        camera-to-world    (transformation-matrix (quaternion->matrix camera-orientation) camera-position)
        world-to-object    (inverse (transformation-matrix (quaternion->matrix object-orientation) object-position))
        camera-to-object   (mulm world-to-object camera-to-world)
        object-origin      (vec4->vec3 (mulv camera-to-object (vec4 0 0 0 1)))
        z-near             (:sfsim.render/z-near scene-render-vars)
        z-far              (:sfsim.render/z-far scene-render-vars)
        z-offset           1.0
        overlay-projection (projection-matrix overlay-width overlay-height z-near (+ ^double z-far ^double z-offset) fov)]
    {:sfsim.render/window-width width
     :sfsim.render/window-height height
     :sfsim.render/overlay-width overlay-width
     :sfsim.render/overlay-height overlay-height
     :sfsim.render/overlay-projection overlay-projection
     :sfsim.render/origin camera-position
     :sfsim.render/light-direction light-direction
     :sfsim.render/object-origin object-origin
     :sfsim.render/camera-to-world camera-to-world
     :sfsim.render/camera-to-object camera-to-object
     :sfsim.render/object-distance (mag object-origin)}))


(defn make-cloud-program
  [vertex-shader fragment-shader]
  (make-program :sfsim.render/vertex [vertex-shader]
                :sfsim.render/fragment [fragment-shader]))


(defn setup-geometry-uniforms
  [program other]
  (let [render-config   (:sfsim.render/config other)
        atmosphere-luts (:sfsim.atmosphere/luts other)
        model-data      (:sfsim.model/data other)
        planet-config   (:sfsim.planet/config other)
        shadow-data     (:sfsim.opacity/data other)
        data            (:sfsim.clouds/data other)]
    (use-program program)
    (uniform-sampler program "camera_point" 0)
    (uniform-sampler program "dist" 1)
    (atmosphere/setup-atmosphere-uniforms program atmosphere-luts 2 false)
    (setup-cloud-render-uniforms program data 5)
    (setup-cloud-sampling-uniforms program data 8)
    (render/setup-shadow-and-opacity-maps program shadow-data 9)
    (plume/setup-static-plume-uniforms program model-data)
    (uniform-float program "radius" (:sfsim.planet/radius planet-config))
    (uniform-float program "amplification" (:sfsim.render/amplification render-config))))


(defn make-cloud-renderer
  [data]
  (let [shadow-config    (:sfsim.opacity/data data)
        cloud-config     (:sfsim.clouds/data data)
        render-config    (:sfsim.render/config data)
        num-steps        (:sfsim.opacity/num-steps shadow-config)
        cloud-octaves    (:sfsim.clouds/cloud-octaves cloud-config)
        perlin-octaves   (:sfsim.clouds/perlin-octaves cloud-config)
        atmosphere-luts  (:sfsim.atmosphere/luts data)
        programs         (into {} (map (fn [[k shaders]] [k (apply make-cloud-program shaders)])
                                       (cloud-fragment-shaders num-steps perlin-octaves cloud-octaves)))
        indices          [0 1 3 2]
        vertices         [-1.0 -1.0 0.0, 1.0 -1.0 0.0, -1.0 1.0 0.0, 1.0 1.0 0.0]
        vao              (make-vertex-array-object (first (vals programs)) indices vertices ["point" 3])
        plume-vao        (make-vertex-array-object (first (vals programs)) plume-indices plume-vertices ["point" 3])]
    (doseq [program (vals programs)] (setup-geometry-uniforms program data))
    {:sfsim.clouds/programs programs
     :sfsim.atmosphere/luts atmosphere-luts
     :sfsim.render/config render-config
     :sfsim.clouds/data cloud-config
     :sfsim.clouds/vao vao
     :sfsim.clouds/plume-vao plume-vao}))


(defn destroy-cloud-renderer
  [{:sfsim.clouds/keys [programs vao plume-vao]}]
  (destroy-vertex-array-object vao)
  (destroy-vertex-array-object plume-vao)
  (doseq [program (vals programs)] (destroy-program program)))


(defn setup-dynamic-cloud-uniforms
  [program other cloud-render-vars model-vars shadow-vars]
  (let [render-config   (:sfsim.render/config other)
        cloud-data      (:sfsim.clouds/data other)
        atmosphere-luts (:sfsim.atmosphere/luts other)]
    (uniform-float program "lod_offset" (lod-offset render-config cloud-data cloud-render-vars))
    (plume/setup-dynamic-plume-uniforms program cloud-render-vars model-vars)
    (uniform-float program "opacity_step" (:sfsim.opacity/opacity-step shadow-vars))
    (uniform-float program "opacity_cutoff" (:sfsim.opacity/opacity-cutoff shadow-vars))
    (setup-shadow-matrices program shadow-vars)
    (use-textures {2 (:sfsim.atmosphere/transmittance atmosphere-luts) 3 (:sfsim.atmosphere/scatter atmosphere-luts)
                   4 (:sfsim.atmosphere/mie atmosphere-luts) 5 (:sfsim.clouds/worley cloud-data) 6 (:sfsim.clouds/perlin-worley cloud-data)
                   7 (:sfsim.clouds/cloud-cover cloud-data) 8 (:sfsim.clouds/bluenoise cloud-data)})
    (use-textures (zipmap (drop 9 (range))
                          (concat (:sfsim.opacity/shadows shadow-vars) (:sfsim.opacity/opacities shadow-vars))))))


(defn render-cloud-overlay
  ([cloud-renderer cloud-render-vars model-vars shadow-vars geometry]
   (render-cloud-overlay cloud-renderer cloud-render-vars model-vars shadow-vars geometry true true true))
  ([{:sfsim.clouds/keys [programs vao plume-vao] :as other} cloud-render-vars model-vars shadow-vars geometry front plume back]
   (let [overlay-width   (:sfsim.render/overlay-width cloud-render-vars)
         overlay-height  (:sfsim.render/overlay-height cloud-render-vars)
         overlay         (make-empty-texture-2d :sfsim.texture/nearest :sfsim.texture/clamp GL30/GL_RGBA32F
                                                overlay-width overlay-height)]
     (doseq [program (vals programs)]
            (use-program program)
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
            (uniform-vector3 program "light_direction" (:sfsim.render/light-direction cloud-render-vars))
            (setup-dynamic-cloud-uniforms program other cloud-render-vars model-vars shadow-vars)
            (use-textures {0  (:sfsim.clouds/points geometry) 1 (:sfsim.clouds/distance geometry)}))
     (framebuffer-render overlay-width overlay-height :sfsim.render/cullback (:sfsim.clouds/depth-stencil geometry) [overlay]
                         (clear (vec3 0.0 0.0 0.0) 0.0)
                         (with-stencils
                           (when front
                             (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                               (use-program (:sfsim.clouds/atmosphere-front programs))
                               (render-quads vao))
                             (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                               (use-program (:sfsim.clouds/planet-front programs))
                               (render-quads vao))
                             (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
                               (use-program (:sfsim.clouds/scene-front programs))
                               (render-quads vao)))
                           (with-underlay-blending
                             (when plume
                               (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                                 (use-program (:sfsim.clouds/plume-outer programs))
                                 (render-quads vao))
                               (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                                 (use-program (:sfsim.clouds/plume-point programs))
                                 (render-quads vao))
                               (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x4 0x4
                                 (use-program (:sfsim.clouds/plume-point programs))
                                 (render-quads vao)))
                             (when back
                               (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x1 0x1
                                 (use-program (:sfsim.clouds/atmosphere-back programs))
                                 (render-quads vao))
                               (with-stencil-op-ref-and-mask GL11/GL_EQUAL 0x2 0x2
                                 (use-program (:sfsim.clouds/planet-back programs))
                                 (render-quads vao))))))
     overlay)))


(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
