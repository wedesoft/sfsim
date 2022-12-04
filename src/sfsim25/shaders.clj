(ns sfsim25.shaders
  "Module with functions to use in shaders")

(def ray-sphere
  "Shader function for computing intersection of ray with sphere"
  (slurp "resources/shaders/core/ray-sphere.glsl"))

(def convert-2d-index
  "Convert 2D index to 2D texture lookup index avoiding clamping region"
  (slurp "resources/shaders/core/convert-2d-index.glsl"))

(def convert-3d-index
  "Convert 3D index to 3D texture lookup index avoiding clamping region"
  (slurp "resources/shaders/core/convert-3d-index.glsl"))

(def make-2d-index-from-4d
  "Convert 4D index to 2D indices for part-manual interpolation"
  (slurp "resources/shaders/core/make-2d-index-from-4d.glsl"))

(def interpolate-2d
  "Perform 2D interpolation"
  (slurp "resources/shaders/core/interpolate-2d.glsl"))

(def interpolate-4d
  "Perform 4D interpolation"
  (slurp "resources/shaders/core/interpolate-4d.glsl"))

(def is-above-horizon
  "Check whether a ray hits the ground or stays in the sky"
  (slurp "resources/shaders/core/is-above-horizon.glsl"))

(def ray-box
  "Shader function for computing intersection of ray with box"
  (slurp "resources/shaders/core/ray-box.glsl"))

(def lookup-3d
  "Perform lookup on wrapping 3D texture"
  (slurp "resources/shaders/core/lookup-3d.glsl"))

(def ray-shell
  "Shader function for computing intersections of ray with a shell"
  (slurp "resources/shaders/core/ray-shell.glsl"))

(def clip-shell-intersections
  "Clip the intersection information of ray and shell using given limit"
  (slurp "resources/shaders/core/clip-shell-intersections.glsl"))

(def horizon-distance
  "Distance from point with specified radius to horizon of planet"
  (slurp "resources/shaders/core/horizon-distance.glsl"))

(def height-to-index
  "Shader for converting height to index"
  (slurp "resources/shaders/core/height-to-index.glsl"))

(def sun-elevation-to-index
  "Shader for converting sun elevation to index"
  (slurp "resources/shaders/core/sun-elevation-to-index.glsl"))

(def sun-angle-to-index
  "Shader for converting sun angle to index"
  (slurp "resources/shaders/core/sun-angle-to-index.glsl"))

(def limit-quot
  "Shader for computing quotient and keeping it between bounds"
  (slurp "resources/shaders/core/limit-quot.glsl"))

(def elevation-to-index
  "Shader function to convert elevation angle to an index for texture lookup"
  (slurp "resources/shaders/core/elevation-to-index.glsl"))

(def transmittance-forward
  "Convert point and direction to 2D lookup index in transmittance table"
  (slurp "resources/shaders/core/transmittance-forward.glsl"))

(def surface-radiance-forward
  "Convert point and direction to 2D lookup index in surface radiance table"
  (slurp "resources/shaders/core/surface-radiance-forward.glsl"))

(def ray-scatter-forward
  "Get 4D lookup index for ray scattering"
  (slurp "resources/shaders/core/ray-scatter-forward.glsl"))
