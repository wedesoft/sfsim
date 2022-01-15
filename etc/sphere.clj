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
  pos = (itransform * vec4(point, 0)).xyz;
  orig = (itransform * vec4(0, 0, 0, 1)).xyz;
}")

(def fragment-source-atmosphere
  (template/eval "#version 130
in highp vec3 pos;
in highp vec3 orig;
uniform vec3 light;
out lowp vec3 fragColor;
uniform sampler2D surface_radiance;
uniform sampler2D transmittance;
uniform sampler2D ray_scatter;

<%= shaders/ray-sphere %>

float M_PI = 3.14159265358;

float elevation_to_index(float elevation) {
  float result;
  if (elevation <= 0.5 * M_PI) {
    result = (0.5 + (1 - pow(1.0 - elevation / (0.5 * M_PI), 0.5)) * 8.0) / 17.0;
  } else {
    result = (0.5 + 9.0 + pow((elevation - (0.5 * M_PI)) / (0.5 * M_PI), 0.5) * 7.0) / 17.0;
  };
  return result;
}

vec3 orthogonal(vec3 n) {
  vec3 v;
  if (abs(n.x) <= min(abs(n.y), abs(n.z))) v = vec3(1, 0, 0);
  if (abs(n.y) <= min(abs(n.x), abs(n.z))) v = vec3(0, 1, 0);
  if (abs(n.z) <= min(abs(n.x), abs(n.y))) v = vec3(0, 0, 1);
  return normalize(cross(n, v));
}

mat3 oriented_matrix(vec3 n) {
  vec3 o1 = orthogonal(n);
  vec3 o2 = cross(n, o1);
  return mat3(n, o1, o2);
}

float clip_angle(float angle) {
  if (angle < -M_PI)
    return angle + 2 * M_PI;
  else if (angle >= M_PI)
    return angle - 2 * M_PI;
  else
    return angle;
}

void main()
{
  vec3 direction = normalize(pos - orig);
  vec2 surface = ray_sphere(vec3(0, 0, 0), 6378000, orig, direction);
  vec2 air = ray_sphere(vec3(0, 0, 0), 6378000 + 100000, orig, direction);
  if (surface.y > 0) {
    vec3 point = orig + surface.x * direction;
    vec3 normal = normalize(point);
    float cos_elevation = dot(normal, light);
    float elevation = acos(cos_elevation);
    float idx = elevation_to_index(elevation);
    float height = 0.0;
    vec2 uv = vec2(height, idx);
    fragColor = max(0, cos_elevation) * texture(transmittance, uv).bgr + texture(surface_radiance, uv).bgr / M_PI;
  } else {
    if (air.y > 0) {
      vec3 point = orig + air.x * direction;
      vec3 normal = normalize(point);
      float cos_sun_elevation = dot(normal, light);
      float sun_elevation = acos(cos_sun_elevation); // 3rd
      float cos_elevation = dot(normal, direction);
      float elevation = acos(cos_elevation); // 2nd
      float height = length(point) - 6378000; // 1st
      mat3 oriented = oriented_matrix(normal);
      vec3 direction_rotated = oriented * direction;
      vec3 light_rotated = oriented * light;
      float direction_azimuth = atan(direction_rotated.z, direction_rotated.y);
      float sun_azimuth = atan(light_rotated.z, light_rotated.y);
      float sun_heading = abs(clip_angle(sun_azimuth - direction_azimuth)); // 4th
      fragColor = vec3(0.5, 0.5, 0.5);
    } else
      fragColor = vec3(0, 0, 0);
  };
}
"))

(Display/setTitle "scratch")
(def desktop (DisplayMode. 640 480))
(Display/setDisplayMode desktop)
(Display/create)

(def program-atmosphere
  (make-program :vertex vertex-source-atmosphere
                :fragment fragment-source-atmosphere))

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

(def light (atom 0))
(def position (atom (matrix [0 (* -3 radius) (* 0.7 6378000)])))
(def orientation (atom (q/rotation (/ Math/PI 2) (matrix [1 0 0]))))

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
         (swap! light + (* 0.001 dt))
         (Display/update)))

(destroy-texture surface-radiance)
(destroy-texture transmittance)
(destroy-texture ray-scatter)
(destroy-vertex-array-object vao)
(destroy-program program-atmosphere)
(Display/destroy)

(set! *unchecked-math* false)
