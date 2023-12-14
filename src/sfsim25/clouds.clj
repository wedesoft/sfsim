(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [comb.template :as template]
              [clojure.math :refer (pow log)]
              [fastmath.matrix :refer (inverse)]
              [sfsim25.render :refer (destroy-program destroy-texture destroy-vertex-array-object framebuffer-render
                                      make-empty-float-cubemap make-empty-vector-cubemap make-program make-vertex-array-object
                                      render-quads uniform-float uniform-int uniform-sampler uniform-matrix4 use-program
                                      use-textures make-empty-float-texture-3d uniform-vector3)]
              [sfsim25.shaders :as shaders]
              [sfsim25.bluenoise :as bluenoise]
              [sfsim25.atmosphere :refer (vertex-atmosphere) :as atmosphere]))

(defn cloud-noise
  "Shader for sampling 3D cloud noise"
  [cloud-octaves]
  [(shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" cloud-octaves) (shaders/lookup-3d-lod "lookup_3d" "worley")
   (slurp "resources/shaders/clouds/cloud-noise.glsl")])

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear-sampling.glsl"))

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
  [size]
  (let [result   (make-empty-vector-cubemap :linear :clamp size)
        indices  [0 1 3 2]
        vertices [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
        program (make-program :vertex [shaders/vertex-passthrough]
                              :fragment [identity-cubemap-fragment])
        vao     (make-vertex-array-object program indices vertices ["point" 3])]
    (framebuffer-render size size :cullback nil [result]
                        (use-program program)
                        (uniform-int program "size" size)
                        (render-quads vao))
    (destroy-program program)
    result))

(defn iterate-cubemap-warp-fragment
  "Fragment shader for iterating cubemap warp"
  [current-name field-method-name]
  [shaders/cubemap-vectors shaders/interpolate-vector-cubemap
   (template/eval (slurp "resources/shaders/clouds/iterate-cubemap-warp-fragment.glsl")
                  {:current-name current-name :field-method-name field-method-name})])

(defn make-iterate-cubemap-warp-program
  "Create program to iteratively update cubemap warp vector field"
  [current-name field-method-name shaders]
  (make-program :vertex [shaders/vertex-passthrough]
                :fragment (conj shaders (iterate-cubemap-warp-fragment current-name field-method-name))))

(defmacro iterate-cubemap
  "Macro to run program to update cubemap"
  [size scale program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# ["point" 3])
         result#   (make-empty-vector-cubemap :linear :clamp ~size)]
     (framebuffer-render ~size ~size :cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         (uniform-float ~program "scale" ~scale)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))

(defn cubemap-warp-fragment
  "Fragment shader for looking up values using a cubemap warp vector field"
  [current-name lookup-name]
  [shaders/cubemap-vectors shaders/interpolate-vector-cubemap
   (template/eval (slurp "resources/shaders/clouds/cubemap-warp-fragment.glsl")
                  {:current-name current-name :lookup-name lookup-name})])

(defn make-cubemap-warp-program
  "Create program to look up values using a given cubemap warp vector field"
  [current-name lookup-name shaders]
  (make-program :vertex [shaders/vertex-passthrough]
                :fragment (conj shaders (cubemap-warp-fragment current-name lookup-name))))

(defmacro cubemap-warp
  "Macro to run program to look up values using cubemap warp vector field"
  [size program & body]
  `(let [indices#  [0 1 3 2]
         vertices# [-1.0 -1.0 0.5, 1.0 -1.0 0.5, -1.0 1.0 0.5, 1.0 1.0 0.5]
         vao#      (make-vertex-array-object ~program indices# vertices# ["point" 3])
         result#   (make-empty-float-cubemap :linear :clamp ~size)]
     (framebuffer-render ~size ~size :cullback nil [result#]
                         (use-program ~program)
                         (uniform-int ~program "size" ~size)
                         ~@body
                         (render-quads vao#))
     (destroy-vertex-array-object vao#)
     result#))

(defn curl-vector
  "Shader for computing curl vectors from a noise function"
  [method-name gradient-name]
  [shaders/rotate-vector shaders/project-vector
   (template/eval (slurp "resources/shaders/clouds/curl-vector.glsl") {:method-name method-name :gradient-name gradient-name})])

(defn flow-field
  "Shader to create potential field for generating curl noise for global cloud cover"
  [flow-octaves]
  [(shaders/noise-octaves "octaves_north" "lookup_north" flow-octaves) (shaders/lookup-3d "lookup_north" "worley_north")
   (shaders/noise-octaves "octaves_south" "lookup_south" flow-octaves) (shaders/lookup-3d "lookup_south" "worley_south")
   (slurp "resources/shaders/clouds/flow-field.glsl")])

(defn cloud-cover-cubemap
  "Program to generate planetary cloud cover using curl noise"
  [& {:keys [size worley-size worley-south worley-north worley-cover flow-octaves cloud-octaves
             whirl prevailing curl-scale cover-scale num-iterations flow-scale]}]
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
      (let [updated (iterate-cubemap size flow-scale update-warp (use-textures {0 @warp 1 worley-north 2 worley-south}))]
        (destroy-texture @warp)
        (reset! warp updated)))
    (let [result (cubemap-warp size lookup
                               (uniform-sampler lookup "current" 0)
                               (uniform-sampler lookup "worley" 1)
                               (uniform-float lookup "factor" (/ 1.0 2.0 cover-scale))
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
  [perlin-octaves]
  [cloud-cover cloud-profile (sphere-noise "perlin_octaves") (shaders/lookup-3d "lookup_perlin" "perlin")
   (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves) (slurp "resources/shaders/clouds/cloud-base.glsl")])

(defn cloud-density
  "Compute cloud density at given point"
  [perlin-octaves cloud-octaves]
  [(cloud-base perlin-octaves) (cloud-noise cloud-octaves) shaders/remap (slurp "resources/shaders/clouds/cloud-density.glsl")])

(def opacity-vertex
  "Vertex shader for rendering deep opacity map"
  [shaders/grow-shadow-index (slurp "resources/shaders/clouds/opacity-vertex.glsl")])

(defn opacity-fragment
  "Fragment shader for creating deep opacity map consisting of offset texture and 3D opacity texture"
  [num-layers perlin-octaves cloud-octaves]
  [shaders/ray-shell (cloud-density perlin-octaves cloud-octaves) linear-sampling
   (template/eval (slurp "resources/shaders/clouds/opacity-fragment.glsl") {:num-layers num-layers})])

(defn overall-shadow
  "Multiply shadows to get overall shadow"
  [num-steps]
  [(opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
   (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup" [["sampler3D" "layers"] ["float" "depth"]])
   (shaders/shadow-cascade-lookup num-steps "average_shadow") shaders/shadow-lookup
   (shaders/percentage-closer-filtering "average_shadow" "shadow_lookup" [["sampler2DShadow" "shadow_map"]])
   (slurp "resources/shaders/clouds/overall-shadow.glsl")])

(defn cloud-transfer
  "Single cloud scattering update step"
  [num-steps]
  [(overall-shadow num-steps) atmosphere/transmittance-outer atmosphere/transmittance-track atmosphere/ray-scatter-track
   (slurp "resources/shaders/clouds/cloud-transfer.glsl")])

(defn sample-cloud
  "Shader to sample the cloud layer and apply cloud scattering update steps"
  [num-steps perlin-octaves cloud-octaves]
  [linear-sampling bluenoise/sampling-offset atmosphere/phase-function (cloud-density perlin-octaves cloud-octaves)
   (cloud-transfer num-steps) (slurp "resources/shaders/clouds/sample-cloud.glsl")])

(defn cloud-planet
  "Shader to compute pixel of cloud foreground overlay for planet"
  [num-steps perlin-octaves cloud-octaves]
  [shaders/ray-sphere shaders/ray-shell (sample-cloud num-steps perlin-octaves cloud-octaves) shaders/clip-shell-intersections
   (slurp "resources/shaders/clouds/cloud-planet.glsl")])

(defn cloud-atmosphere
  "Shader to compute pixel of cloud foreground overlay for atmosphere"
  [num-steps perlin-octaves cloud-octaves]
  [shaders/ray-sphere shaders/ray-shell (sample-cloud num-steps perlin-octaves cloud-octaves)
   (slurp "resources/shaders/clouds/cloud-atmosphere.glsl")])

(defmacro opacity-cascade
  "Render cascade of deep opacity maps"
  [size num-opacity-layers matrix-cascade voxel-size program & body]
  `(mapv
     (fn [opacity-level#]
         (let [opacity-layers#  (make-empty-float-texture-3d :linear :clamp ~size ~size (inc ~num-opacity-layers))
               level-of-detail# (/ (log (/ (/ (:scale opacity-level#) ~size) ~voxel-size)) (log 2))]
           (framebuffer-render ~size ~size :cullback nil [opacity-layers#]
                               (use-program ~program)
                               (uniform-int ~program "shadow_size" ~size)
                               (uniform-float ~program "level_of_detail" level-of-detail#)
                               (uniform-matrix4 ~program "ndc_to_shadow" (inverse (:shadow-ndc-matrix opacity-level#)))
                               (uniform-float ~program "depth" (:depth opacity-level#))
                               ~@body)
           opacity-layers#))
     ~matrix-cascade))

(defn cloud-density-shaders
  "List of cloud shaders to sample density values"
  [cloud-octaves perlin-octaves]
  [(cloud-density perlin-octaves cloud-octaves) shaders/interpolate-float-cubemap shaders/convert-cubemap-index cloud-cover])

(defn opacity-lookup-shaders
  "List of opacity lookup shaders"
  [num-steps]
  [(opacity-cascade-lookup num-steps "average_opacity") opacity-lookup
   (shaders/percentage-closer-filtering "average_opacity" "opacity_lookup" [["sampler3D" "layers"] ["float" "depth"]])
   shaders/convert-2d-index shaders/convert-3d-index])

(defn fragment-atmosphere-clouds
  "Shader for rendering clouds above horizon"
  [num-steps perlin-octaves cloud-octaves]
  [(cloud-atmosphere num-steps perlin-octaves cloud-octaves) (slurp "resources/shaders/clouds/fragment-atmosphere.glsl")])

(defn make-cloud-atmosphere-renderer
  "Make renderer to render clouds above horizon"
  [& {:keys [num-steps perlin-octaves cloud-octaves radius max-height cloud-bottom cloud-top cloud-scale detail-scale depth
             tilesize height-size elevation-size light-elevation-size heading-size
             surface-height-size surface-sun-elevation-size albedo
             reflectivity specular cloud-multiplier cover-multiplier cap anisotropic water-color amplification
             opacity-cutoff num-opacity-layers shadow-size transmittance scatter-tex mie-tex worley perlin-worley-tex
             bluenoise cloud-cover]}]
  (let [program (make-program :vertex [vertex-atmosphere]
                              :fragment [(fragment-atmosphere-clouds num-steps perlin-octaves cloud-octaves)])]
    (use-program program)
    (uniform-sampler program "transmittance"    0)
    (uniform-sampler program "ray_scatter"      1)
    (uniform-sampler program "mie_strength"     2)
    (uniform-sampler program "worley"           3)
    (uniform-sampler program "perlin"           4)
    (uniform-sampler program "bluenoise"        5)
    (uniform-sampler program "cover"            6)
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "shadow_map" i) (+ i 7)))
    (doseq [i (range num-steps)]
           (uniform-sampler program (str "opacity" i) (+ i 7 num-steps)))
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-float program "cloud_bottom" cloud-bottom)
    (uniform-float program "cloud_top" cloud-top)
    (uniform-float program "cloud_scale" cloud-scale)
    (uniform-float program "detail_scale" detail-scale)
    (uniform-float program "depth" depth)
    (uniform-int program "cover_size" (:width cloud-cover))
    (uniform-int program "noise_size" (:width bluenoise))
    (uniform-int program "high_detail" (dec tilesize))
    (uniform-int program "low_detail" (quot (dec tilesize) 2))
    (uniform-int program "height_size" height-size)
    (uniform-int program "elevation_size" elevation-size)
    (uniform-int program "light_elevation_size" light-elevation-size)
    (uniform-int program "heading_size" heading-size)
    (uniform-int program "transmittance_height_size" (:height transmittance))
    (uniform-int program "transmittance_elevation_size" (:width transmittance))
    (uniform-int program "surface_height_size" surface-height-size)
    (uniform-int program "surface_sun_elevation_size" surface-sun-elevation-size)
    (uniform-float program "albedo" albedo)
    (uniform-float program "reflectivity" reflectivity)
    (uniform-float program "specular" specular)
    (uniform-float program "cloud_multiplier" cloud-multiplier)
    (uniform-float program "cover_multiplier" cover-multiplier)
    (uniform-float program "cap" cap)
    (uniform-float program "anisotropic" anisotropic)
    (uniform-float program "radius" radius)
    (uniform-float program "max_height" max-height)
    (uniform-vector3 program "water_color" water-color)
    (uniform-float program "amplification" amplification)
    (uniform-float program "opacity_cutoff" opacity-cutoff)
    (uniform-int program "num_opacity_layers" num-opacity-layers)
    (uniform-int program "shadow_size" shadow-size)
    {:program program
     :transmittance transmittance
     :scatter-tex scatter-tex
     :mie-tex mie-tex
     :worley worley
     :perlin-worley-tex perlin-worley-tex
     :bluenoise bluenoise
     :cloud-cover cloud-cover}))

(defn render-cloud-atmosphere
  "Render clouds above horizon"
  [{:keys [program transmittance scatter-tex mie-tex worley perlin-worley-tex bluenoise cloud-cover]}
   & {:keys [cloud-step cloud-threshold lod-offset projection origin transform light-direction z-far opacity-step splits
             matrix-cascade shadows opacities]}]
  (let [indices  [0 1 3 2]
        vertices (map #(* % z-far) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1])
        vao      (make-vertex-array-object program indices vertices ["point" 3])]
    (use-program program)
    (uniform-float program "cloud_step" cloud-step)
    (uniform-float program "cloud_threshold" cloud-threshold)
    (uniform-float program "lod_offset" lod-offset)
    (uniform-matrix4 program "projection" projection)
    (uniform-vector3 program "origin" origin)
    (uniform-matrix4 program "extrinsics" (inverse transform))
    (uniform-matrix4 program "transform" transform)
    (uniform-vector3 program "light_direction" light-direction)
    (uniform-float program "opacity_step" opacity-step)
    (doseq [[idx item] (map-indexed vector splits)]
           (uniform-float program (str "split" idx) item))
    (doseq [[idx item] (map-indexed vector matrix-cascade)]
           (uniform-matrix4 program (str "shadow_map_matrix" idx) (:shadow-map-matrix item))
           (uniform-float program (str "depth" idx) (:depth item)))
    (use-textures {0 (:texture transmittance) 1 scatter-tex 2 mie-tex 3 (:texture worley) 4 perlin-worley-tex 5 (:texture bluenoise)
                   6 (:texture cloud-cover)})
    (use-textures (zipmap (drop 7 (range)) (concat shadows opacities)))
    (render-quads vao)
    (destroy-vertex-array-object vao)))

(defn destroy-cloud-atmosphere-renderer
  [{:keys [program]}]
  (destroy-program program))
