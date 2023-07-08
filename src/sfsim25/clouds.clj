(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [clojure.java.io :as io]
              [comb.template :as template]
              [clojure.math :refer (pow)]
              [sfsim25.render :refer (destroy-program destroy-texture destroy-vertex-array-object framebuffer-render
                                      make-empty-float-cubemap make-empty-vector-cubemap make-program make-vertex-array-object
                                      render-quads uniform-float uniform-int uniform-sampler use-program use-textures)]
              [sfsim25.shaders :as shaders]))

(def cloud-shadow
  "Shader for determining illumination of clouds"
  (slurp (io/resource "shaders/clouds/cloud-shadow.glsl")))

(def cloud-noise
  "Shader for sampling 3D cloud noise"
  (slurp (io/resource "shaders/clouds/cloud-noise.glsl")))

(def cloud-base
  "Shader for determining cloud density at specified point"
  (slurp (io/resource "shaders/clouds/cloud-base.glsl")))

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp (io/resource "shaders/clouds/linear-sampling.glsl")))

(def opacity-vertex
  "Vertex shader for rendering deep opacity map"
  (slurp (io/resource "shaders/clouds/opacity-vertex.glsl")))

(def opacity-fragment
  "Fragment shader for creating deep opacity map consisting of offset texture and 3D opacity texture"
  (template/fn [num-layers] (slurp (io/resource "shaders/clouds/opacity-fragment.glsl"))))

(def opacity-lookup
  "Shader function for looking up transmittance value from deep opacity map"
  (slurp (io/resource "shaders/clouds/opacity-lookup.glsl")))

(def opacity-cascade-lookup
  "Perform opacity (transparency) lookup in cascade of deep opacity maps"
  (template/fn [n base-function] (slurp (io/resource "shaders/clouds/opacity-cascade-lookup.glsl"))))

(def identity-cubemap-fragment
  "Fragment shader to render identity cubemap"
  (slurp (io/resource "shaders/clouds/identity-cubemap-fragment.glsl")))

(defn identity-cubemap
  "Create identity cubemap"
  [size]
  (let [result   (make-empty-vector-cubemap :linear :clamp size)
        indices  [0 1 3 2]
        vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        program (make-program :vertex [shaders/vertex-passthrough]
                              :fragment [identity-cubemap-fragment shaders/cubemap-vectors])
        vao     (make-vertex-array-object program indices vertices [:point 3])]
    (framebuffer-render size size :cullback nil [result]
                        (use-program program)
                        (uniform-int program "size" size)
                        (render-quads vao))
    (destroy-program program)
    result))

(def iterate-cubemap-warp-fragment
  "Fragment shader for iterating cubemap warp"
  (template/fn [current-name field-method-name] (slurp (io/resource "shaders/clouds/iterate-cubemap-warp-fragment.glsl"))))

(defn make-iterate-cubemap-warp-program
  "Create program to iteratively update cubemap warp vector field"
  [current-name field-method-name shaders]
  (make-program :vertex [shaders/vertex-passthrough]
                :fragment (into shaders [(iterate-cubemap-warp-fragment current-name field-method-name)
                                         shaders/cubemap-vectors shaders/interpolate-vector-cubemap
                                         shaders/convert-cubemap-index])))

(defmacro iterate-cubemap
  "Macro to run program to update cubemap"
  [size scale program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# [:point 3])
         result#   (make-empty-vector-cubemap :linear :clamp ~size)]
     (framebuffer-render ~size ~size :cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         (uniform-float ~program "scale" ~scale)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))

(def cubemap-warp-fragment
  "Fragment shader for looking up values using a cubemap warp vector field"
  (template/fn [current-name lookup-name] (slurp (io/resource "shaders/clouds/cubemap-warp-fragment.glsl"))))

(defn make-cubemap-warp-program
  "Create program to look up values using a given cubemap warp vector field"
  [current-name lookup-name shaders]
  (make-program :vertex [shaders/vertex-passthrough]
                :fragment (into shaders [(cubemap-warp-fragment current-name lookup-name)
                                         shaders/cubemap-vectors shaders/interpolate-vector-cubemap
                                         shaders/convert-cubemap-index])))

(defmacro cubemap-warp
  "Macro to run program to look up values using cubemap warp vector field"
  [size program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# [:point 3])
         result#   (make-empty-float-cubemap :linear :clamp ~size)]
     (framebuffer-render ~size ~size :cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))

(def curl-vector
  "Shader for computing curl vectors from a noise function"
  (template/fn [method-name gradient-name] (slurp (io/resource "shaders/clouds/curl-vector.glsl"))))

(def flow-field
  "Shader to create potential field for generating curl noise for global cloud cover"
  (slurp (io/resource "shaders/clouds/flow-field.glsl")))

(defn cloud-cover-cubemap
  "Program to generate planetary cloud cover using curl noise"
  [& {:keys [size worley-size worley-south worley-north worley-cover flow-octaves cloud-octaves
             whirl prevailing curl-scale cover-scale num-iterations flow-scale]}]
  (let [warp        (atom (identity-cubemap size))
        update-warp (make-iterate-cubemap-warp-program
                      "current" "curl"
                      [(curl-vector "curl" "gradient") (shaders/gradient-3d "gradient" "flow_field" "epsilon")
                       (shaders/noise-octaves "octaves_north" "lookup_north" flow-octaves)
                       (shaders/noise-octaves "octaves_south" "lookup_south" flow-octaves)
                       (shaders/lookup-3d "lookup_north" "worley_north") (shaders/lookup-3d "lookup_south" "worley_south")
                       shaders/rotate-vector shaders/oriented-matrix shaders/orthogonal-vector shaders/project-vector
                       flow-field])
        lookup      (make-cubemap-warp-program
                      "current" "cover"
                      [(shaders/noise-octaves "clouds" "noise" cloud-octaves)
                       (shaders/lookup-3d "noise" "worley")
                       (shaders/scale-noise "cover" "factor" "clouds")])
        epsilon       (/ 1.0 worley-size (pow 2.0 (count flow-octaves)))]
    (use-program update-warp)
    (uniform-sampler update-warp "current" 0)
    (uniform-sampler update-warp "worley_north" 1)
    (uniform-sampler update-warp "worley_south" 2)
    (uniform-float update-warp "epsilon" epsilon)
    (uniform-float update-warp "whirl" whirl)
    (uniform-float update-warp "prevailing" prevailing)
    (uniform-float update-warp "curl_scale" curl-scale)
    (dotimes [_iteration num-iterations]
      (let [updated (iterate-cubemap size flow-scale update-warp (use-textures @warp worley-north worley-south))]
        (destroy-texture @warp)
        (reset! warp updated)))
    (let [result (cubemap-warp size lookup
                               (uniform-sampler lookup "current" 0)
                               (uniform-sampler lookup "worley" 1)
                               (uniform-float lookup "factor" (/ 1.0 2.0 cover-scale))
                               (use-textures @warp worley-cover))]
      (destroy-program lookup)
      (destroy-program update-warp)
      (destroy-texture @warp)
      result)))

(def cloud-profile
  "Shader for looking up vertical cloud profile"
  (slurp (io/resource "shaders/clouds/cloud-profile.glsl")))

(def sphere-noise
  "Sample 3D noise on the surface of a sphere"
  (template/fn [base-noise] (slurp (io/resource "shaders/clouds/sphere-noise.glsl"))))

(def cloud-cover
  "Perform cloud cover lookup in cube map"
  (slurp (io/resource "shaders/clouds/cloud-cover.glsl")))

(def cloud-density
  "Compute cloud density at given point"
  (slurp (io/resource "shaders/clouds/cloud-density.glsl")))

(def cloud-transfer
  "Single cloud scattering update step"
  (slurp (io/resource "shaders/clouds/cloud-transfer.glsl")))

(def sample-cloud
  "Shader to sample the cloud layer and apply cloud scattering update steps"
  (slurp (io/resource "shaders/clouds/sample-cloud.glsl")))

(def cloud-planet
  "Shader to compute pixel of cloud foreground overlay for planet"
  (slurp (io/resource "shaders/clouds/cloud-planet.glsl")))

(def cloud-atmosphere
  "Shader to compute pixel of cloud foreground overlay for atmosphere"
  (slurp (io/resource "shaders/clouds/cloud-atmosphere.glsl")))
