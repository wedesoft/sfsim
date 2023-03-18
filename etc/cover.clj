(require '[clojure.math :refer (sin cos PI sqrt)]
         '[clojure.core.matrix :refer (matrix)]
         '[sfsim25.render :refer :all]
         '[sfsim25.worley :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.clouds :refer :all]
         '[sfsim25.util :refer :all])

(import '[org.lwjgl.opengl Display DisplayMode]
        '[org.lwjgl.input Keyboard])

(Display/setTitle "scratch")
(Display/setDisplayMode (DisplayMode. (/ 1080 2) (/ 1080 2)))
(Display/create)

(Keyboard/create)

(def worley-size 64)

(def data (float-array (worley-noise 8 64 true)))
(def W1 (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))
(def data (float-array (worley-noise 8 64 true)))
(def W2 (make-float-texture-3d :linear :repeat {:width worley-size :height worley-size :depth worley-size :data data}))

(def cubemap-size 512)
(def warp (atom (identity-cubemap cubemap-size)))

(def potential (shaders/noise-octaves "potential" [0.5 0.25 0.125]))
; TODO: scale potential
(def noise
"#version 410 core
uniform sampler3D worley;
float potential(sampler3D noise, vec3 idx, float lod);
float noise(vec3 point)
{
  return potential(worley, point, 0.0);
}")


(def curl-adapter
"#version 410 core
uniform float epsilon;
vec3 curl(vec3 point, float epsilon);
vec3 curl_adapter(vec3 point)
{
  return curl(point, epsilon);
}")

(def update-warp
  (make-iterate-cubemap-warp-program "current" "curl_adapter"
                                     [curl-adapter (curl-vector "curl" "gradient") shaders/rotate-vector shaders/oriented-matrix
                                      shaders/orthogonal-vector (shaders/gradient-3d "gradient" "noise")
                                      shaders/project-vector noise potential]))

(def clouds (shaders/noise-octaves "clouds" [0.5 0.25 0.125]))
; TODO: scale clouds
(def noise
"#version 410 core
uniform sampler3D worley;
float clouds(sampler3D noise, vec3 idx, float lod);
float noise(vec3 point)
{
  return clouds(worley, point, 0.0);
}")

(def lookup (make-cubemap-warp-program "current" "noise" [clouds noise]))

(def warped (cubemap-warp cubemap-size lookup
                          (uniform-sampler lookup :current 0)
                          (uniform-sampler lookup :worley 1)
                          (use-textures @warp W2)))

(def vertex-shader
"#version 410 core
in vec3 point;
out VS_OUT
{
  vec3 point;
} vs_out;
void main()
{
  vs_out.point = point;
  gl_Position = vec4(point, 1);
}")

(def fragment-shader
"#version 410 core
uniform samplerCube cubemap;
in VS_OUT
{
  vec3 point;
} fs_in;
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
out vec3 fragColor;
void main()
{
  vec2 intersection = ray_sphere(vec3(0, 0, 0), 1, vec3(fs_in.point.xy, -1), vec3(0, 0, 1));
  if (intersection.y > 0) {
    vec3 p = vec3(fs_in.point.xy, -1 + intersection.x);
    float value = texture(cubemap, p).r;
    fragColor = vec3(value, value, value);
  } else
    fragColor = vec3(0, 0, 0);
}")

(def program (make-program :vertex [vertex-shader] :fragment [fragment-shader shaders/ray-sphere]))
(def indices [0 1 3 2])
(def vertices [-1 -1 0, 1 -1 0, -1 1 0, 1  1 0])
(def vao (make-vertex-array-object program indices vertices [:point 3]))

(onscreen-render (Display/getWidth) (Display/getHeight)
                 (clear (matrix [0 0 0]))
                 (use-program program)
                 (uniform-sampler program :cubemap 0)
                 (use-textures warped)
                 (render-quads vao))

(destroy-texture warped)
(destroy-program program)
(destroy-program lookup)
(destroy-program update-warp)
(destroy-texture @warp)
(destroy-texture W2)
(destroy-texture W1)

(Display/destroy)
