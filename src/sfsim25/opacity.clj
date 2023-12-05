(ns sfsim25.opacity
    "Rendering of deep opacity maps for cloud shadows"
    (:require [sfsim25.render :refer (make-program destroy-program make-vertex-array-object destroy-vertex-array-object
                                      use-program uniform-sampler uniform-int uniform-float use-textures uniform-vector3
                                      render-quads destroy-texture)]
              [sfsim25.clouds :refer (opacity-vertex opacity-fragment opacity-cascade)]))

(defn make-opacity-renderer
  "Initialise an opacity program"
  [& {:keys [num-opacity-layers cloud-octaves perlin-octaves cover-size shadow-size noise-size worley-size radius cloud-bottom
             cloud-top detail-scale cloud-scale worley-tex perlin-worley-tex cloud-cover-tex cloud-multiplier
             cover-multiplier cap]}]
  (let [program  (make-program :vertex [opacity-vertex]
                               :fragment [(opacity-fragment num-opacity-layers perlin-octaves cloud-octaves)])
        indices  [0 1 3 2]
        vertices [-1.0 -1.0, 1.0 -1.0, -1.0 1.0, 1.0 1.0]
        vao              (make-vertex-array-object program indices vertices ["point" 2])]
    (use-program program)
    (uniform-sampler program "worley" 0)
    (uniform-sampler program "perlin" 1)
    (uniform-sampler program "cover" 2)
    (uniform-int program "cover_size" cover-size)
    (uniform-int program "shadow_size" shadow-size)
    (uniform-int program "noise_size" noise-size)
    (uniform-float program "radius" radius)
    (uniform-float program "cloud_bottom" cloud-bottom)
    (uniform-float program "cloud_top" cloud-top)
    (uniform-float program "detail_scale" detail-scale)
    (uniform-float program "cloud_scale" cloud-scale)
    (uniform-float program "cloud_multiplier" cloud-multiplier)
    (uniform-float program "cover_multiplier" cover-multiplier)
    (uniform-float program "cap" cap)
    (use-textures worley-tex perlin-worley-tex cloud-cover-tex)
    {:program program
     :vao vao
     :shadow-size shadow-size
     :worley-size worley-size
     :num-opacity-layers num-opacity-layers
     :detail-scale detail-scale}))

(defn render-cascade
  "Render a cascade of opacity maps"
  [{:keys [program vao detail-scale shadow-size worley-size num-opacity-layers]}
   matrix-cas light-direction cloud-threshold scatter-amount opacity-step]
  (opacity-cascade shadow-size num-opacity-layers matrix-cas (/ detail-scale worley-size) program
                   (uniform-vector3 program "light_direction" light-direction)
                   (uniform-float program "cloud_threshold" cloud-threshold)
                   (uniform-float program "scatter_amount" scatter-amount)
                   (uniform-float program "opacity_step" opacity-step)
                   (uniform-float program "cloud_max_step" (* 0.5 opacity-step))
                   (render-quads vao)))

(defn destroy-cascade
  "Destroy cascade of opacity maps"
  [opacities]
  (doseq [layer opacities]
         (destroy-texture layer)))

(defn destroy
  "Delete opacity renderer objects"
  [{:keys [vao program]}]
  (destroy-vertex-array-object vao)
  (destroy-program program))
