(ns sfsim25.clouds
    "Rendering of clouds"
    (:require [comb.template :as template]))

(def cloud-track
  "Shader for putting volumetric clouds into the atmosphere"
  (slurp "resources/shaders/clouds/cloud-track.glsl"))

(def cloud-track-base
  "Shader for determining shadowing (or lack of shadowing) by clouds"
  (slurp "resources/shaders/clouds/cloud-track-base.glsl"))

(def sky-outer
  "Shader for determining lighting of atmosphere including clouds coming from space"
  (slurp "resources/shaders/clouds/sky-outer.glsl"))

(def sky-track
  "Shader for determining lighting of atmosphere including clouds between to points"
  (slurp "resources/shaders/clouds/sky-track.glsl"))

(def cloud-shadow
  "Shader for determining illumination of clouds"
  (slurp "resources/shaders/clouds/cloud-shadow.glsl"))

(def cloud-density
  "Shader for determining cloud density at specified point"
  (slurp "resources/shaders/clouds/cloud-density.glsl"))

(def linear-sampling
  "Shader functions for defining linear sampling"
  (slurp "resources/shaders/clouds/linear-sampling.glsl"))

(def exponential-sampling
  "Shader functions for defining exponential sampling"
  (slurp "resources/shaders/clouds/exponential-sampling.glsl"))

(def opacity-vertex
  "Vertex shader for rendering deep opacity map"
  (slurp "resources/shaders/clouds/opacity-vertex.glsl"))

(def declare-opacity-layer
  (template/fn [layer]
"layout (location = <%= (inc layer) %>) out float opacity_layer_<%= layer %>;
"))

(def opacity-layer-update
  (template/fn [layer]
"  if (opacity_interval_begin < <%= layer %> * opacity_step && opacity_interval_end >= <%= layer %> * opacity_step) {
    opacity_layer_<%= layer %> = mix(previous_transmittance, transmittance, (<%= layer %> * opacity_step - opacity_interval_begin) / stepsize);
  };
"))

(def opacity-fragment
  (template/fn [num-layers]
    (slurp "resources/shaders/clouds/opacity-fragment.glsl")))
