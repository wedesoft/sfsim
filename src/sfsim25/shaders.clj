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

(def convert-2d-index
  "Convert 2D index to 2D texture lookup index"
  (slurp "resources/shaders/convert_2d_index.glsl"))

(def convert-4d-index
  "Convert 4D index to 2D indices for part-manual interpolation"
  (slurp "resources/shaders/convert_4d_index.glsl"))

(def transmittance-forward
  "Convert point and direction to 2D lookup index in transmittance table"
  (slurp "resources/shaders/transmittance_forward.glsl"))

(def interpolate-2d
  "Perform 2D interpolation"
  (slurp "resources/shaders/interpolate_2d.glsl"))

(def interpolate-4d
  "Perform 4D interpolation"
  (slurp "resources/shaders/interpolate_4d.glsl"))

(def ray-scatter-forward
  "Get 4D lookup index for ray scattering"
  (slurp "resources/shaders/ray_scatter_forward.glsl"))
