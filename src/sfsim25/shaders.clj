(ns sfsim25.shaders
  "Module with functions to use in shaders")

(def ray-sphere
  "Shader function for computing intersection of ray with sphere"
  (slurp "resources/shaders/core/ray_sphere.glsl"))

(def elevation-to-index
  "Shader function to convert elevation angle to an index for texture lookup"
  (slurp "resources/shaders/core/elevation_to_index.glsl"))

(def horizon-angle
  "Shader function to determine a sphere's horizon angle below horizontal plane depending on height"
  (slurp "resources/shaders/core/horizon_angle.glsl"))

(def orthogonal-vector
  "Create normal vector orthogonal to the specified one"
  (slurp "resources/shaders/core/orthogonal_vector.glsl"))

(def oriented-matrix
  "Create normal vector orthogonal to the specified one"
  (slurp "resources/shaders/core/oriented_matrix.glsl"))

(def clip-angle
  "Convert angle to be between -pi and +pi"
  (slurp "resources/shaders/core/clip_angle.glsl"))

(def convert-2d-index
  "Convert 2D index to 2D texture lookup index"
  (slurp "resources/shaders/core/convert_2d_index.glsl"))

(def convert-4d-index
  "Convert 4D index to 2D indices for part-manual interpolation"
  (slurp "resources/shaders/core/convert_4d_index.glsl"))

(def transmittance-forward
  "Convert point and direction to 2D lookup index in transmittance table"
  (slurp "resources/shaders/core/transmittance_forward.glsl"))

(def interpolate-2d
  "Perform 2D interpolation"
  (slurp "resources/shaders/core/interpolate_2d.glsl"))

(def interpolate-4d
  "Perform 4D interpolation"
  (slurp "resources/shaders/core/interpolate_4d.glsl"))

(def ray-scatter-forward
  "Get 4D lookup index for ray scattering"
  (slurp "resources/shaders/core/ray_scatter_forward.glsl"))

(def sky-or-ground
  "Check whether a ray hits the ground or stays in the sky"
  (slurp "resources/shaders/core/sky_or_ground.glsl"))
