(ns sfsim.opacity
    "Rendering of deep opacity maps for cloud shadows"
    (:require [clojure.math :refer (sqrt)]
              [malli.core :as m]
              [fastmath.vector :refer (mag dot)]
              [sfsim.matrix :refer (split-list shadow-matrix-cascade shadow-config shadow-data shadow-box)]
              [sfsim.texture :refer (destroy-texture texture-2d texture-3d)]
              [sfsim.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                    use-program uniform-int uniform-float uniform-vector3 render-quads render-depth
                                    use-textures render-config vertex-array-object render-vars)]
              [sfsim.worley :refer (worley-size)]
              [sfsim.clouds :refer (opacity-vertex opacity-fragment opacity-cascade setup-cloud-render-uniforms cloud-data)]
              [sfsim.planet :refer (render-shadow-cascade destroy-shadow-cascade planet-config planet-shadow-renderer)]
              [sfsim.atmosphere :refer (phase)]
              [sfsim.util :refer (sqr)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def opacity-renderer (m/schema [:map [::program :int] [:sfsim.clouds/data cloud-data] [::data shadow-data]
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
  "Initialise an opacity program (untested)"
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
  "Render a cascade of opacity maps and return it as a list of 3D textures (untested)"
  [{::keys [program vao data] :as other} matrix-cas light-direction scatter-amount opacity-step]
  (let [cloud-data (:sfsim.clouds/data other)]
    (use-textures {0 (:sfsim.clouds/worley cloud-data) 1 (:sfsim.clouds/perlin-worley cloud-data)
                   2 (:sfsim.clouds/cloud-cover cloud-data)})
    (opacity-cascade (::shadow-size data) (::num-opacity-layers data) matrix-cas
                     (/ (:sfsim.clouds/detail-scale cloud-data) worley-size) program
                     (uniform-vector3 program "light_direction" light-direction)
                     (uniform-float program "scatter_amount" scatter-amount)
                     (uniform-float program "opacity_step" opacity-step)
                     (uniform-float program "cloud_max_step" (* 0.5 opacity-step))
                     (render-quads vao))))

(defn destroy-opacity-cascade
  "Destroy cascade of opacity maps (untested)"
  [opacities]
  (doseq [layer opacities]
         (destroy-texture layer)))

(defn destroy-opacity-renderer
  "Delete opacity renderer objects (untested)"
  [{::keys [vao program]}]
  (destroy-vertex-array-object vao)
  (destroy-program program))

(def cascades (m/schema [:map [::opacity-step :double] [::splits [:vector :double]] [::matrix-cascade [:vector shadow-box]]
                              [::shadows [:vector texture-2d]] [::opacities [:vector texture-3d]]]))

(defn opacity-and-shadow-cascade
  "Compute deep opacity map cascade and shadow cascade"
  {:malli/schema [:=> [:cat opacity-renderer planet-shadow-renderer shadow-data cloud-data render-vars [:maybe :map] :double]
                      cascades]}
  [opacity-renderer planet-shadow-renderer shadow-data cloud-data render-vars tree opacity-base]
  (let [splits          (split-list shadow-data render-vars)
        matrix-cascade  (shadow-matrix-cascade shadow-data render-vars)
        position        (:sfsim.render/origin render-vars)
        cos-light       (/ (dot (:sfsim.render/light-direction render-vars) position) (mag position))
        sin-light       (sqrt (- 1 (sqr cos-light)))
        opacity-step    (* (+ cos-light (* 10 sin-light)) opacity-base)
        scatter-amount  (+ (* (:sfsim.clouds/anisotropic cloud-data) (phase {:sfsim.atmosphere/scatter-g 0.76} -1.0))
                          (- 1 (:sfsim.clouds/anisotropic cloud-data)))
        light-direction (:sfsim.render/light-direction render-vars)
        opacities       (render-opacity-cascade opacity-renderer matrix-cascade light-direction scatter-amount opacity-step)
        shadows         (render-shadow-cascade planet-shadow-renderer ::matrix-cascade matrix-cascade :tree tree)]
    {::opacity-step opacity-step
     ::splits splits
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
