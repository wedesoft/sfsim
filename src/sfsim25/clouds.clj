(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [comb.template :as template]
              [sfsim25.render :refer :all]
              [sfsim25.shaders :as shaders]))

(def cloud-track
  "Shader for putting volumetric clouds into the atmosphere"
  (slurp "resources/shaders/clouds/cloud-track.glsl"))

(def sky-outer
  "Shader for determining lighting of atmosphere including clouds coming from space"
  (slurp "resources/shaders/clouds/sky-outer.glsl"))

(def sky-track
  "Shader for determining lighting of atmosphere including clouds between to points"
  (slurp "resources/shaders/clouds/sky-track.glsl"))

(def cloud-shadow
  "Shader for determining illumination of clouds"
  (slurp "resources/shaders/clouds/cloud-shadow.glsl"))

(def cloud-density
  "Shader for determining cloud density at specified point"
  (slurp "resources/shaders/clouds/cloud-density.glsl"))

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear-sampling.glsl"))

(def exponential-sampling
  "Shader functions for defining exponential sampling"
  (slurp "resources/shaders/clouds/exponential-sampling.glsl"))

(def opacity-vertex
  "Vertex shader for rendering deep opacity map"
  (slurp "resources/shaders/clouds/opacity-vertex.glsl"))

(def opacity-fragment
  "Fragment shader for creating deep opacity map consisting of offset texture and 3D opacity texture"
  (template/fn [num-layers] (slurp "resources/shaders/clouds/opacity-fragment.glsl")))

(def opacity-lookup
  "Shader function for looking up transmittance value from deep opacity map"
  (slurp "resources/shaders/clouds/opacity-lookup.glsl"))

(def opacity-cascade-lookup
  "Perform opacity (transparency) lookup in cascade of deep opacity maps"
  (template/fn [n] (slurp "resources/shaders/clouds/opacity-cascade-lookup.glsl")))

(def identity-cubemap-fragment
  "Fragment shader to render identity cubemap"
  (slurp "resources/shaders/clouds/identity-cubemap-fragment.glsl"))

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
                        (uniform-int program :size size)
                        (render-quads vao))
    (destroy-program program)
    result))

(defn make-iterate-cubemap-warp-program [current-name field-method-name shaders]
  "Create program to iteratively update cubemap warp vector field"
  nil)

(defmacro iterate-cubemap
  "Macro to run program to update cubemap"
  [size scale program shaders & body]
  `(println "TEST"))
