(ns sfsim25.opacity
    "Rendering of deep opacity maps for cloud shadows"
    (:require [sfsim25.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                      use-program uniform-sampler uniform-int uniform-float use-textures uniform-vector3
                                      render-quads destroy-texture)]
              [sfsim25.worley :refer (worley-size)]
              [sfsim25.clouds :refer (opacity-vertex opacity-fragment opacity-cascade)]))

(defn make-shadow-data
  "Collect information for opacity and shadow cascade"
  [& {:keys [num-opacity-layers shadow-size num-steps depth]}]
  {:num-opacity-layers num-opacity-layers
   :shadow-size shadow-size
   :num-steps num-steps
   :depth depth})

(defn make-opacity-renderer
  "Initialise an opacity program (untested)"
  [& {:keys [planet-data shadow-data cloud-data]}]
  (let [program  (make-program :vertex [opacity-vertex]
                               :fragment [(opacity-fragment (:num-opacity-layers shadow-data)
                                                            (:perlin-octaves cloud-data)
                                                            (:cloud-octaves cloud-data))])
        indices  [0 1 3 2]
        vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao              (make-vertex-array-object program indices vertices ["point" 2])]
    (use-program program)
    (uniform-sampler program "worley" 0)
    (uniform-sampler program "perlin" 1)
    (uniform-sampler program "cover" 2)
    (uniform-int program "cover_size" (:width (:cloud-cover cloud-data)))
    (uniform-int program "shadow_size" (:shadow-size shadow-data))
    (uniform-float program "radius" (:radius planet-data))
    (uniform-float program "cloud_bottom" (:cloud-bottom cloud-data))
    (uniform-float program "cloud_top" (:cloud-top cloud-data))
    (uniform-float program "detail_scale" (:detail-scale cloud-data))
    (uniform-float program "cloud_scale" (:cloud-scale cloud-data))
    (uniform-float program "cloud_multiplier" (:cloud-multiplier cloud-data))
    (uniform-float program "cover_multiplier" (:cover-multiplier cloud-data))
    (uniform-float program "cap" (:cap cloud-data))
    {:program program
     :cloud-data cloud-data
     :shadow-data shadow-data
     :vao vao}))

(defn render-opacity-cascade
  "Render a cascade of opacity maps and return it as a list of 3D textures (untested)"
  [{:keys [program vao shadow-data cloud-data]}
   matrix-cas light-direction cloud-threshold scatter-amount opacity-step]
  (use-textures {0 (:worley cloud-data) 1 (:perlin-worley cloud-data) 2 (:cloud-cover cloud-data)})
  (opacity-cascade (:shadow-size shadow-data) (:num-opacity-layers shadow-data) matrix-cas
                   (/ (:detail-scale cloud-data) worley-size) program
                   (uniform-vector3 program "light_direction" light-direction)
                   (uniform-float program "cloud_threshold" cloud-threshold)
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
