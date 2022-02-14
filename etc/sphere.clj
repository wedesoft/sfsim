(require '[clojure.core.matrix :refer :all]
         '[comb.template :as template]
         '[sfsim25.render :refer :all]
         '[sfsim25.shaders :as shaders]
         '[sfsim25.matrix :refer :all]
         '[sfsim25.quaternion :as q]
         '[sfsim25.util :refer :all])
(import '[org.lwjgl.opengl Display DisplayMode PixelFormat])

(set! *unchecked-math* true)

(def vertex-source-atmosphere "#version 130
in highp vec3 point;
out highp vec3 pos;
out highp vec3 orig;
uniform mat4 projection;
uniform mat4 itransform;
void main()
{
  gl_Position = projection * vec4(point, 1);
  pos = (itransform * vec4(point, 1)).xyz;
  orig = (itransform * vec4(0, 0, 0, 1)).xyz;
}")

(def fragment-source-atmosphere "#version 130
in highp vec3 pos;
in highp vec3 orig;
uniform vec3 light;
out lowp vec3 fragColor;
uniform sampler2D surface_radiance;
uniform sampler2D transmittance;
uniform sampler2D ray_scatter;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power);
vec4 interpolate_4d(sampler2D table, int size, vec4 idx);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size, float power);

float M_PI = 3.14159265358;

void main()
{
  vec3 direction = normalize(pos - orig);
  vec2 surface = ray_sphere(vec3(0, 0, 0), 6378000, orig, direction);
  vec2 air = ray_sphere(vec3(0, 0, 0), 6378000 + 100000, orig, direction);
  if (surface.y > 0) {
    vec3 point = orig + surface.x * direction;
    vec3 normal = normalize(point);
    float cos_sun_elevation = dot(normal, light);
    vec2 uv = transmittance_forward(point, light, 6378000, 100000, 17, 2.0);
    vec3 surf_contrib = 0.3 * (max(0, cos_sun_elevation) * texture(transmittance, uv).rgb + texture(surface_radiance, uv).rgb) / (2 * M_PI);
    point = orig + air.x * direction;
    vec4 ray_scatter_index = ray_scatter_forward(point, direction, light, 6378000, 100000, 17, 2);
    vec3 atm_contrib = interpolate_4d(ray_scatter, 17, ray_scatter_index).rgb;
    fragColor = (surf_contrib + atm_contrib) * 10.0;
  } else {
    if (air.y > 0) {
      vec3 point = orig + air.x * direction;
      vec2 uv = transmittance_forward(point, direction, 6378000, 100000, 17, 2.0);
      vec3 l = 0.1 * max(0, pow(dot(direction, light), 5000)) * texture(transmittance, uv).rgb;
      vec4 ray_scatter_index = ray_scatter_forward(point, direction, light, 6378000, 100000, 17, 2);
      fragColor = (interpolate_4d(ray_scatter, 17, ray_scatter_index).rgb + l) * 10;
    } else {
      float l = 0.1 * max(0, pow(dot(direction, light), 5000));
      fragColor = vec3(l, l, l) * 10.0;
    };
  };
}
")

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(def program-atmosphere
  (make-program :vertex [vertex-source-atmosphere]
                :fragment [shaders/ray-sphere shaders/elevation-to-index shaders/convert-4d-index shaders/clip-angle
                           shaders/oriented-matrix shaders/orthogonal-vector shaders/horizon-angle fragment-source-atmosphere
                           shaders/transmittance-forward shaders/interpolate-4d shaders/ray-scatter-forward]))

(def indices [0 1 3 2])
(def vertices (map #(* % 4 6378000) [-1 -1 -1, 1 -1 -1, -1  1 -1, 1  1 -1]))
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

(def projection (projection-matrix (.getWidth desktop) (.getHeight desktop) 10000 (* 4 6378000) (/ (* 60 Math/PI) 180)))

(def light (atom (* 1.4 Math/PI)))
(def position (atom (matrix [0 (* 1.0 radius) (* 0.01 radius)])))
(def orientation (atom (q/rotation (* 0 (/ Math/PI 180)) (matrix [1 0 0]))))

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
