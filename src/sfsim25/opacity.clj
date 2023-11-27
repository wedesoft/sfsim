(ns sfsim25.opacity
    "Rendering of deep opacity maps"
    (:require [sfsim25.render :refer (make-program)]
              [sfsim25.clouds :refer (opacity-vertex opacity-fragment cloud-density cloud-base cloud-cover cloud-noise
                                      cloud-profile linear-sampling sphere-noise)]
              [sfsim25.shaders :as shaders]
              [sfsim25.bluenoise :as bluenoise]))

(defn make-opacity-renderer
  "Initialise an opacity program"
  [num-opacity-layers octaves perlin-octaves]
  (make-program :vertex [opacity-vertex shaders/grow-shadow-index]
                :fragment [(opacity-fragment num-opacity-layers) cloud-density shaders/remap
                           cloud-base cloud-cover cloud-noise
                           (shaders/noise-octaves-lod "cloud_octaves" "lookup_3d" octaves)
                           (shaders/lookup-3d-lod "lookup_3d" "worley")
                           (shaders/noise-octaves "perlin_octaves" "lookup_perlin" perlin-octaves)
                           (sphere-noise "perlin_octaves")
                           (shaders/lookup-3d "lookup_perlin" "perlin") cloud-profile shaders/convert-1d-index
                           shaders/ray-shell shaders/ray-sphere bluenoise/sampling-offset linear-sampling
                           shaders/interpolate-float-cubemap shaders/convert-cubemap-index]))
