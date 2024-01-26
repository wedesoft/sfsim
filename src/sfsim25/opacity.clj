(ns sfsim25.opacity
    "Rendering of deep opacity maps for cloud shadows"
    (:require [clojure.math :refer (sqrt)]
              [fastmath.vector :refer (mag dot)]
              [sfsim25.matrix :refer (split-list shadow-matrix-cascade)]
              [sfsim25.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                      use-program uniform-int uniform-float use-textures uniform-vector3 render-quads
                                      destroy-texture render-depth)]
              [sfsim25.worley :refer (worley-size)]
              [sfsim25.clouds :refer (opacity-vertex opacity-fragment opacity-cascade setup-cloud-render-uniforms)]
              [sfsim25.planet :refer (render-shadow-cascade destroy-shadow-cascade)]
              [sfsim25.atmosphere :refer (phase)]
              [sfsim25.util :refer (sqr)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(defn make-shadow-data
  "Create hash map with shadow parameters"
  {:malli/schema [:=> [:cat :map :map :map] :map]}
  [data planet-config cloud-data]
  (assoc data
         ::depth (render-depth (:sfsim25.planet/radius planet-config)
                               (:sfsim25.planet/max-height planet-config)
                               (:sfsim25.clouds/cloud-top cloud-data))))

(defn make-opacity-renderer
  "Initialise an opacity program (untested)"
  [& {:keys [planet-config shadow-data cloud-data]}]
  (let [program  (make-program :sfsim25.render/vertex [opacity-vertex]
                               :sfsim25.render/fragment [(opacity-fragment (::num-opacity-layers shadow-data)
                                                                           (:sfsim25.clouds/perlin-octaves cloud-data)
                                                                           (:sfsim25.clouds/cloud-octaves cloud-data))])
        indices  [0 1 3 2]
        vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao              (make-vertex-array-object program indices vertices ["point" 2])]
    (use-program program)
    (setup-cloud-render-uniforms program cloud-data 0)
    (uniform-int program "shadow_size" (::shadow-size shadow-data))
    (uniform-float program "radius" (:sfsim25.planet/radius planet-config))
    {:program program
     :cloud-data cloud-data
     :shadow-data shadow-data
     :vao vao}))

(defn render-opacity-cascade
  "Render a cascade of opacity maps and return it as a list of 3D textures (untested)"
  [{:keys [program vao shadow-data cloud-data]} matrix-cas light-direction scatter-amount opacity-step]
  (use-textures {0 (:sfsim25.clouds/worley cloud-data) 1 (:sfsim25.clouds/perlin-worley cloud-data)
                 2 (:sfsim25.clouds/cloud-cover cloud-data)})
  (opacity-cascade (::shadow-size shadow-data) (::num-opacity-layers shadow-data) matrix-cas
                   (/ (:sfsim25.clouds/detail-scale cloud-data) worley-size) program
                   (uniform-vector3 program "light_direction" light-direction)
                   (uniform-float program "scatter_amount" scatter-amount)
                   (uniform-float program "opacity_step" opacity-step)
                   (uniform-float program "cloud_max_step" (* 0.5 opacity-step))
                   (render-quads vao)))

(defn destroy-opacity-cascade
  "Destroy cascade of opacity maps (untested)"
  [opacities]
  (doseq [layer opacities]
         (destroy-texture layer)))

(defn destroy-opacity-renderer
  "Delete opacity renderer objects (untested)"
  [{:keys [vao program]}]
  (destroy-vertex-array-object vao)
  (destroy-program program))

(defn opacity-and-shadow-cascade
  "Compute deep opacity map cascade and shadow cascade"
  [opacity-renderer planet-shadow-renderer shadow-data cloud-data render-vars tree opacity-base]
  (let [splits          (split-list shadow-data render-vars)
        matrix-cascade  (shadow-matrix-cascade shadow-data render-vars)
        position        (:sfsim25.render/origin render-vars)
        cos-light       (/ (dot (:sfsim25.render/light-direction render-vars) position) (mag position))
        sin-light       (sqrt (- 1 (sqr cos-light)))
        opacity-step    (* (+ cos-light (* 10 sin-light)) opacity-base)
        scatter-amount  (+ (* (:sfsim25.clouds/anisotropic cloud-data) (phase {:sfsim25.atmosphere/scatter-g 0.76} -1.0))
                          (- 1 (:sfsim25.clouds/anisotropic cloud-data)))
        light-direction (:sfsim25.render/light-direction render-vars)
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
