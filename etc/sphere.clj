(require '[clojure.core.matrix :refer :all]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat])

(set! *unchecked-math* true)

(def vertex-source-atmosphere "#version 410 core
in highp vec3 point;
out VS_OUT
{
  highp vec3 pos;
  highp vec3 orig;
} vs_out;
uniform mat4 projection;
uniform mat4 itransform;
void main()
{
  gl_Position = projection * vec4(point, 1);
  vs_out.pos = (itransform * vec4(point, 1)).xyz;
  vs_out.orig = (itransform * vec4(0, 0, 0, 1)).xyz;
}")

(def fragment-source-atmosphere "#version 410 core
in VS_OUT
{
  highp vec3 pos;
  highp vec3 orig;
} fs_in;
uniform vec3 light;
out lowp vec3 fragColor;
uniform sampler2D surface_radiance;
uniform sampler2D transmittance;
uniform sampler2D ray_scatter;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power);
vec4 interpolate_4d(sampler2D table, int size, vec4 idx);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size, float power);
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);

float M_PI = 3.14159265358;
int size = 35;
float power = 2.0;
float max_height = 35000;
float amplification = 5.0;
float sun = 10.0;

void main()
{
  vec3 direction = normalize(fs_in.pos - fs_in.orig);
  vec2 surface = ray_sphere(vec3(0, 0, 0), 6378000, fs_in.orig, direction);
  vec2 air = ray_sphere(vec3(0, 0, 0), 6378000 + max_height, fs_in.orig, direction);
  if (surface.y > 0) {
    vec3 point = fs_in.orig + surface.x * direction;
    vec3 normal = normalize(point);
    float cos_sun_elevation = dot(normal, light);
    vec2 uv = transmittance_forward(point, light, 6378000, max_height, size, power);
    vec3 surf_contrib = 0.3 * (max(0, cos_sun_elevation) * interpolate_2d(transmittance, size, uv).rgb + interpolate_2d(surface_radiance, size, uv).rgb) / M_PI;
    point = fs_in.orig + air.x * direction;
    vec4 ray_scatter_index = ray_scatter_forward(point, direction, light, 6378000, max_height, size, power);
    vec3 atm_contrib = interpolate_4d(ray_scatter, size, ray_scatter_index).rgb;
    fragColor = (surf_contrib + atm_contrib) * amplification;
  } else {
    if (air.y > 0) {
      vec3 point = fs_in.orig + air.x * direction;
      vec2 uv = transmittance_forward(point, direction, 6378000, max_height, size, power);
      vec3 l = 0.1 * max(0, sun * pow(dot(direction, light), 5000)) * interpolate_2d(transmittance, size, uv).rgb;
      vec4 ray_scatter_index = ray_scatter_forward(point, direction, light, 6378000, max_height, size, power);
      fragColor = (interpolate_4d(ray_scatter, size, ray_scatter_index).rgb + l) * amplification;
    } else {
      float l = 0.1 * max(0, sun * pow(dot(direction, light), 5000));
      fragColor = vec3(l, l, l) * amplification;
    };
  };
}
")

(Display/setTitle "scratch")
(def desktop (DisplayMode. 1280 960))
(Display/setDisplayMode desktop)
(Display/create)

(def program-atmosphere
  (make-program :vertex [vertex-source-atmosphere]
                :fragment [shaders/ray-sphere shaders/elevation-to-index shaders/convert-4d-index shaders/clip-angle
                           shaders/oriented-matrix shaders/orthogonal-vector shaders/horizon-angle fragment-source-atmosphere
                           shaders/transmittance-forward shaders/interpolate-4d shaders/ray-scatter-forward
                           shaders/convert-2d-index shaders/interpolate-2d]))

(def indices [0 1 3 2])
(def vertices (map #(* % 4 6378000) [-4 -4 -1, 4 -4 -1, -4  4 -1, 4  4 -1]))
(def vao (make-vertex-array-object program-atmosphere indices vertices [:point 3]))

(use-program program-atmosphere)

(def data (slurp-floats "data/atmosphere/surface-radiance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def surface-radiance (make-vector-texture-2d {:width size :height size :data data}))
(uniform-sampler program-atmosphere :surface_radiance 0)

(def data (slurp-floats "data/atmosphere/transmittance.scatter"))
(def size (int (Math/sqrt (/ (count data) 3))))
(def transmittance (make-vector-texture-2d {:width size :height size :data data}))
(uniform-sampler program-atmosphere :transmittance 1)

(def data (slurp-floats "data/atmosphere/ray-scatter.scatter"))
(def size (int (Math/pow (/ (count data) 3) 0.25)))
(def ray-scatter (make-vector-texture-2d {:width (* size size) :height (* size size) :data data}))
(uniform-sampler program-atmosphere :ray_scatter 2)

(def radius 6378000.0)

(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) 10000 (* 4 6378000) (/ (* 120 Math/PI) 180)))

(def light (atom (* 1.4 Math/PI)))
(def position (atom (matrix [0 (* 1.0 (+ 400 radius)) (* 0.01 radius)])))
(def orientation (atom (q/rotation (* 45 (/ Math/PI 180)) (matrix [1 0 0]))))

(def t0 (atom (System/currentTimeMillis)))
(while (not (Display/isCloseRequested))
       (let [t1 (System/currentTimeMillis)
             dt (- t1 @t0)]
         (onscreen-render (.getWidth desktop) (.getHeight desktop)
                          (clear (matrix [0.0 0.0 0.0]))
                          (use-program program-atmosphere)
                          (uniform-matrix4 program-atmosphere :projection projection)
                          (uniform-matrix4 program-atmosphere :itransform (transformation-matrix (quaternion->matrix @orientation)
                                                                                                 @position))
                          (uniform-vector3 program-atmosphere :light (matrix [0 (Math/cos @light) (Math/sin @light)]))
                          (use-textures surface-radiance transmittance ray-scatter)
                          (render-quads vao))
         (swap! t0 + dt)
         (swap! light + (* 0.002 0.1 dt))
         (Display/update)))

(destroy-texture surface-radiance)
(destroy-texture transmittance)
(destroy-texture ray-scatter)
(destroy-vertex-array-object vao)
(destroy-program program-atmosphere)
(Display/destroy)

(set! *unchecked-math* false)
