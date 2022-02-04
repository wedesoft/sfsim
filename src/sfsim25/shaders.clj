(ns sfsim25.shaders
  "Module with functions to use in shaders")

(def ray-sphere
  "Shader function for computing intersection of ray with sphere"
  (slurp "resources/shaders/ray_sphere.glsl"))

(def elevation-to-index
  "Shader function to convert elevation angle to an index for texture lookup"
  (slurp "resources/shaders/elevation_to_index.glsl"))

(def horizon-angle
  "Shader function to determine a sphere's horizon angle below horizontal plane depending on height"
  (slurp "resources/shaders/horizon_angle.glsl"))

(def orthogonal-vector
  "Create normal vector orthogonal to the specified one"
  (slurp "resources/shaders/orthogonal_vector.glsl"))

(def oriented-matrix
  "Create normal vector orthogonal to the specified one"
  (slurp "resources/shaders/oriented_matrix.glsl"))

(def clip-angle
  "Convert angle to be between -pi and +pi"
  (slurp "resources/shaders/clip_angle.glsl"))
