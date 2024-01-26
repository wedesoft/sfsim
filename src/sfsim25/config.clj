(ns sfsim25.config
    "Configuration values for software"
    (:require [clojure.math :refer (to-radians)]
              [fastmath.vector :refer (vec3)]))

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

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
