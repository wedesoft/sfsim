(ns sfsim25.shaders
  "Module with functions to use in shaders")

(def ray-sphere
  "Shader function for computing intersection of ray with sphere"
  (slurp "resources/shaders/ray_sphere.glsl"))
