(ns sfsim25.clouds
    "Rendering of clouds")

(def cloud-track
  "Shader for putting volumetric clouds into the atmosphere"
  (slurp "resources/shaders/clouds/cloud_track.glsl"))

(def cloud-track-base
  "Shader for determining shadowing (or lack of shadowing) by clouds"
  (slurp "resources/shaders/clouds/cloud_track_base.glsl"))

(def sky-outer
  "Shader for determining lighting of atmosphere including clouds coming from space"
  (slurp "resources/shaders/clouds/sky_outer.glsl"))

(def sky-track
  "Shader for determining lighting of atmosphere including clouds between to points"
  (slurp "resources/shaders/clouds/sky_track.glsl"))

(def cloud-shadow
  "Shader for determining illumination of clouds"
  (slurp "resources/shaders/clouds/cloud_shadow.glsl"))

(def cloud-density
  "Shader for determining cloud density at specified point"
  (slurp "resources/shaders/clouds/cloud_density.glsl"))

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear_sampling.glsl"))

(def exponential-sampling
  "Shader functions for defining exponential sampling"
  (slurp "resources/shaders/clouds/exponential_sampling.glsl"))
