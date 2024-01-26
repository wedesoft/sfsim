(ns sfsim25.config
    "Configuration values for software"
    (:require [clojure.math :refer (to-radians)]))

(set! *unchecked-math* true)
(set! *warn-on-reflection* true)

(def render-config #:sfsim25.render{:amplification 6.0
                                    :specular 1000.0
                                    :fov (to-radians 60.0)})

(set! *warn-on-reflection* false)
(set! *unchecked-math* false)
