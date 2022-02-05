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

(def fragment-source-atmosphere "#version 130
in highp vec3 pos;
in highp vec3 orig;
uniform vec3 light;
out lowp vec3 fragColor;
uniform sampler2D surface_radiance;
uniform sampler2D transmittance;
uniform sampler2D ray_scatter;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);

float M_PI = 3.14159265358;

float elevation_to_index(float elevation, float horizon_angle) {
  float result;
  if (elevation <= 0.5 * M_PI + horizon_angle) {
    result = (0.5 + (1 - pow(1.0 - elevation / (0.5 * M_PI + horizon_angle), 0.5)) * 8.0) / 17.0;
  } else {
    result = (0.5 + 9.0 + pow((elevation - (0.5 * M_PI + horizon_angle)) / (0.5 * M_PI - horizon_angle), 0.5) * 7.0) / 17.0;
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
    float cos_sun_elevation = dot(normal, light);
    float sun_elevation = acos(cos_sun_elevation);
    float sun_elevation_index = elevation_to_index(sun_elevation, 0);
    float height = 0.0;
    vec2 uv = vec2(height, sun_elevation_index);
    vec3 surf_contrib = 0.3 * (max(0, cos_sun_elevation) * texture(transmittance, uv).rgb + texture(surface_radiance, uv).rgb) / (2 * M_PI);
    point = orig + air.x * direction;
    float distance = max(length(point), 6378000);
    float horizon_angle = acos(6378000 / distance);
    normal = normalize(point);
    cos_sun_elevation = dot(normal, light);
    sun_elevation = acos(cos_sun_elevation);
    sun_elevation_index = elevation_to_index(sun_elevation, horizon_angle); // 2nd
    float cos_elevation = dot(normal, direction);
    float elevation = acos(cos_elevation);
    float elevation_index = elevation_to_index(elevation, horizon_angle) * 17 - 0.5; // 3rd
    height = distance - 6378000;
    float height_index = 16 * height / 100000.0; // 4th
    mat3 oriented = oriented_matrix(normal);
    vec3 direction_rotated = oriented * direction;
    vec3 light_rotated = oriented * light;
    float direction_azimuth = atan(direction_rotated.z, direction_rotated.y);
    float sun_azimuth = atan(light_rotated.z, light_rotated.y);
    float sun_heading = abs(clip_angle(sun_azimuth - direction_azimuth));
    float sun_heading_index = (sun_heading * 16 / M_PI + 0.5) / 17; // 1st
    float elevation_index_floor = floor(elevation_index);
    float height_index_floor = floor(height_index);

    vec4 indices;
    indices.s = sun_heading_index / 17 + elevation_index_floor / 17;
    indices.t = sun_heading_index / 17 + min(elevation_index_floor + 1, 16) / 17;
    indices.p = sun_elevation_index / 17 + height_index_floor / 17;
    indices.q = sun_elevation_index / 17 + min(height_index_floor + 1, 16) / 17;

    vec2 frac = vec2(fract(elevation_index), fract(height_index));
    vec3 atm_contrib = (texture(ray_scatter, indices.sp) * (1 - frac.s) * (1 - frac.t) +
                        texture(ray_scatter, indices.tp) *       frac.s * (1 - frac.t) +
                        texture(ray_scatter, indices.sq) * (1 - frac.s) *       frac.t +
                        texture(ray_scatter, indices.tq) *       frac.s *       frac.t).rgb;
    fragColor = (surf_contrib + atm_contrib) * 10.0;
  } else {
    if (air.y > 0) {
      vec3 point = orig + air.x * direction;
      vec3 normal = normalize(point);
      float distance = max(length(point), 6378000);
      float horizon_angle = acos(6378000 / distance);
      float cos_sun_elevation = dot(normal, light);
      float sun_elevation = acos(cos_sun_elevation);
      float sun_elevation_index = elevation_to_index(sun_elevation, horizon_angle); // 2nd
      float cos_elevation = dot(normal, direction);
      float elevation = acos(cos_elevation);
      float elevation_index = elevation_to_index(elevation, horizon_angle) * 17 - 0.5; // 3rd
      float height = distance - 6378000;
      float height_index = 16 * height / 100000.0; // 4th
      mat3 oriented = oriented_matrix(normal);
      vec3 direction_rotated = oriented * direction;
      vec3 light_rotated = oriented * light;
      float direction_azimuth = atan(direction_rotated.z, direction_rotated.y);
      float sun_azimuth = atan(light_rotated.z, light_rotated.y);
      float sun_heading = abs(clip_angle(sun_azimuth - direction_azimuth));
      float sun_heading_index = (sun_heading * 16 / M_PI + 0.5) / 17; // 1st

      float elevation_index_floor = floor(elevation_index);
      float height_index_floor = floor(height_index);

      vec4 indices;
      indices.s = sun_heading_index / 17 + elevation_index_floor / 17;
      indices.t = sun_heading_index / 17 + min(elevation_index_floor + 1, 16) / 17;
      indices.p = sun_elevation_index / 17 + height_index_floor / 17;
      indices.q = sun_elevation_index / 17 + min(height_index_floor + 1, 16) / 17;

      vec2 frac = vec2(fract(elevation_index), fract(height_index));
      vec2 uv = vec2((height_index + 0.5) / 17, (elevation_index + 0.5) / 17);
      vec3 l = max(0, pow(dot(direction, light), 5000)) * texture(transmittance, uv).rgb;
      fragColor = ((texture(ray_scatter, indices.sp) * (1 - frac.s) * (1 - frac.t) +
                    texture(ray_scatter, indices.tp) *       frac.s * (1 - frac.t) +
                    texture(ray_scatter, indices.sq) * (1 - frac.s) *       frac.t +
                    texture(ray_scatter, indices.tq) *       frac.s *       frac.t).rgb + l) * 10.0;
    } else {
      float l = max(0, pow(dot(direction, light), 5000));
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
                :fragment [shaders/ray-sphere fragment-source-atmosphere]))

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
(def position (atom (matrix [0 (* -0.6 radius) (* 1.0 6378000)])))
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
         (swap! light + (* 0.0002 0.1 dt))
         (Display/update)))

(destroy-texture surface-radiance)
(destroy-texture transmittance)
(destroy-texture ray-scatter)
(destroy-vertex-array-object vao)
(destroy-program program-atmosphere)
(Display/destroy)

(set! *unchecked-math* false)
