(ns sfsim25.opacity
    "Rendering of deep opacity maps for cloud shadows"
    (:require [sfsim25.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                      use-program uniform-sampler uniform-int uniform-float use-textures uniform-vector3
                                      render-quads destroy-texture)]
              [sfsim25.worley :refer (worley-size)]
              [sfsim25.clouds :refer (opacity-vertex opacity-fragment opacity-cascade)]))

(defn make-shadow-data
  "Collect information for opacity and shadow cascade"
  [& {::keys [num-opacity-layers shadow-size num-steps depth shadow-bias]}]
  {::num-opacity-layers num-opacity-layers
   ::shadow-size shadow-size
   ::num-steps num-steps
   ::depth depth
   ::shadow-bias shadow-bias})

(defn make-opacity-renderer
  "Initialise an opacity program (untested)"
  [& {:keys [planet-data shadow-data cloud-data]}]
  (let [program  (make-program :vertex [opacity-vertex]
                               :fragment [(opacity-fragment (::num-opacity-layers shadow-data)
                                                            (:sfsim25.clouds/perlin-octaves cloud-data)
                                                            (:sfsim25.clouds/cloud-octaves cloud-data))])
        indices  [0 1 3 2]
        vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao              (make-vertex-array-object program indices vertices ["point" 2])]
    (use-program program)
    (uniform-sampler program "worley" 0)
    (uniform-sampler program "perlin" 1)
    (uniform-sampler program "cover" 2)
    (uniform-int program "cover_size" (:width (:sfsim25.clouds/cloud-cover cloud-data)))
    (uniform-int program "shadow_size" (::shadow-size shadow-data))
    (uniform-float program "radius" (:sfsim25.planet/radius planet-data))
    (uniform-float program "cloud_bottom" (:sfsim25.clouds/cloud-bottom cloud-data))
    (uniform-float program "cloud_top" (:sfsim25.clouds/cloud-top cloud-data))
    (uniform-float program "detail_scale" (:sfsim25.clouds/detail-scale cloud-data))
    (uniform-float program "cloud_scale" (:sfsim25.clouds/cloud-scale cloud-data))
    (uniform-float program "cloud_multiplier" (:sfsim25.clouds/cloud-multiplier cloud-data))
    (uniform-float program "cover_multiplier" (:sfsim25.clouds/cover-multiplier cloud-data))
    (uniform-float program "cap" (:sfsim25.clouds/cap cloud-data))
    {:program program
     :cloud-data cloud-data
     :shadow-data shadow-data
     :vao vao}))

(defn render-opacity-cascade
  "Render a cascade of opacity maps and return it as a list of 3D textures (untested)"
  [{:keys [program vao shadow-data cloud-data]}
   matrix-cas light-direction cloud-threshold scatter-amount opacity-step]
  (use-textures {0 (:sfsim25.clouds/worley cloud-data) 1 (:sfsim25.clouds/perlin-worley cloud-data)
                 2 (:sfsim25.clouds/cloud-cover cloud-data)})
  (opacity-cascade (::shadow-size shadow-data) (::num-opacity-layers shadow-data) matrix-cas
                   (/ (:sfsim25.clouds/detail-scale cloud-data) worley-size) program
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
