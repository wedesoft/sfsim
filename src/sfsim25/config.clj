(ns sfsim25.config
    "Configuration values for software"
    (:require [clojure.math :refer (to-radians)]
              [fastmath.vector :refer (vec3)]
              [sfsim25.util :refer (octaves)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def render-config #:sfsim25.render{:amplification 6.0
                                    :specular 1000.0
                                    :fov (to-radians 60.0)})

(def planet-config #:sfsim25.planet{:radius 6378000.0
                                    :max-height 8000.0
                                    :albedo 0.9
                                    :dawn-start -0.2
                                    :dawn-end 0.0
                                    :tilesize 33
                                    :color-tilesize 129
                                    :reflectivity 0.1
                                    :water-color (vec3 0.09 0.11 0.34)})

(def cloud-config #:sfsim25.clouds{:cloud-octaves (octaves 4 0.7)
                                   :perlin-octaves (octaves 4 0.7)
                                   :cloud-bottom 2000.0
                                   :cloud-top 5000.0
                                   :detail-scale 4000.0
                                   :cloud-scale 100000.0
                                   :cloud-multiplier 10.0
                                   :cover-multiplier 26.0
                                   :threshold 18.2
                                   :cap 0.007
                                   :anisotropic 0.25
                                   :cloud-step 400.0
                                   :opacity-cutoff 0.01})

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
