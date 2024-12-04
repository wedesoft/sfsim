(ns sfsim.config
    "Configuration values for software"
    (:require [clojure.math :refer (to-radians)]
              [fastmath.vector :refer (vec3)]
              [sfsim.util :refer (octaves)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def max-height 35000.0)

(def render-config #:sfsim.render{:amplification 6.0
                                  :specular 1000.0
                                  :fov (to-radians 60.0)
                                  :min-z-near 1.0})

(def planet-config #:sfsim.planet{:radius 6378000.0
                                  :max-height 8000.0
                                  :albedo 0.9
                                  :dawn-start -0.2
                                  :dawn-end 0.0
                                  :tilesize 65
                                  :level 7
                                  :color-tilesize 129
                                  :reflectivity 0.1
                                  :water-threshold 0.8
                                  :water-color (vec3 0.09 0.11 0.34)})

(def cloud-config #:sfsim.clouds{:cloud-octaves (octaves 4 0.7)
                                 :perlin-octaves (octaves 4 0.7)
                                 :cloud-bottom 1500.0
                                 :cloud-top 3000.0
                                 :detail-scale 2000.0
                                 :cloud-scale 50000.0
                                 :cloud-multiplier 10.0
                                 :cover-multiplier 26.0
                                 :threshold 18.2
                                 :cap 0.01
                                 :anisotropic 0.25
                                 :cloud-step 400.0
                                 :opacity-cutoff 0.01})

(def shadow-config #:sfsim.opacity{:num-opacity-layers 7
                                   :shadow-size 128
                                   :num-steps 3
                                   :scene-shadow-size 512
                                   :scene-shadow-counts [0 1]
                                   :mix 0.8
                                   :shadow-bias 1e-6})

(def object-radius 30.0)

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
