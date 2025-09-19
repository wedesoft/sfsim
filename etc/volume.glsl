#version 410 core

// References:
// Mach diamonds: https://www.shadertoy.com/view/WdGBDc
// fbm and domain warping: https://www.shadertoy.com/view/WsjGRz
// flame thrower: https://www.shadertoy.com/view/XsVSDW
// 3D perlin noise: https://www.shadertoy.com/view/4djXzz

#define M_PI 3.1415926535897932384626433832795

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

#define M_PI 3.1415926535897932384626433832795
#define FOV (70.0 * M_PI / 180.0)
#define F (1.0 / tan(FOV / 2.0))
#define DIST 1.0
#define NOZZLE 0.2
#define SAMPLES 100

// rotation around x axis
mat3 rotation_x(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    1, 0, 0,
    0, c, s,
    0, -s, c
  );
}

// rotation around y axis
mat3 rotation_y(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    c, 0, -s,
    0, 1, 0,
    s, 0, c
  );
}

// rotation around z axis
mat3 rotation_z(float angle) {
  float s = sin(angle);
  float c = cos(angle);
  return mat3(
    c, s, 0,
    -s, c, 0,
    0, 0, 1
  );
}

float hash3d(vec3 coordinates)
{
  float hashValue = coordinates.x + coordinates.y * 37.0 + coordinates.z * 521.0;
  return fract(sin(hashValue * 1.333) * 100003.9);
}

float interpolate_hermite(float value1, float value2, float factor)
{
    return mix(value1, value2, factor * factor * (3.0 - 2.0 * factor)); // Perform cubic Hermite interpolation
}

const vec2 vector01 = vec2(0.0, 1.0);

float noise(vec3 coordinates)
{
  vec3 fractional = fract(coordinates.xyz);
  vec3 integral = floor(coordinates.xyz);
  float hash000 = hash3d(integral);
  float hash100 = hash3d(integral + vector01.yxx);
  float hash010 = hash3d(integral + vector01.xyx);
  float hash110 = hash3d(integral + vector01.yyx);
  float hash001 = hash3d(integral + vector01.xxy);
  float hash101 = hash3d(integral + vector01.yxy);
  float hash011 = hash3d(integral + vector01.xyy);
  float hash111 = hash3d(integral + vector01.yyy);

  return interpolate_hermite(
      interpolate_hermite(interpolate_hermite(hash000, hash100, fractional.x), interpolate_hermite(hash010, hash110, fractional.x), fractional.y),
      interpolate_hermite(interpolate_hermite(hash001, hash101, fractional.x), interpolate_hermite(hash011, hash111, fractional.x), fractional.y),
      fractional.z);
}

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  vec3 factors1 = (box_min - origin) / direction;
  vec3 factors2 = (box_max - origin) / direction;
  vec3 intersections1 = min(factors1, factors2);
  vec3 intersections2 = max(factors1, factors2);
  float near = max(max(max(intersections1.x, intersections1.y), intersections1.z), 0);
  float far = min(min(intersections2.x, intersections2.y), intersections2.z);
  return vec2(near, max(far - near, 0));
}

float bumps(float x)
{
  float limit = 0.12;
  float bulge = NOZZLE - limit;
  float omega = 100.0 * bulge;
  float bumps = bulge * abs(cos(x * omega));
  return limit + bumps;
}

float diamond(vec2 uv)
{
  float limit = 0.12;
  float diamond;
  if (NOZZLE > limit) {
    float bulge = NOZZLE - limit;
    float omega = 100.0 * bulge;
    float phase = omega * uv.x + M_PI / 2.0;
    float diamond_longitudinal = mod(phase - 0.3 * M_PI, M_PI) - 0.7 * M_PI;
    float diamond_front_length = limit / (bulge * omega);
    float diamond_back_length = diamond_front_length * 0.7;
    float tail_start = 0.3 * diamond_front_length;
    float tail_length = 0.8 * diamond_front_length;
    float diamond_length = diamond_longitudinal > 0.0 ? diamond_back_length : diamond_front_length;
    float diamond_radius = limit * max(0.0, 1.0 - abs(diamond_longitudinal / omega) / diamond_length);
    float extent = 1.0;
    float decay = max(0.0, 1.0 - abs(diamond_longitudinal / extent));
    diamond = 0.1 / diamond_front_length * (1.0 - smoothstep(diamond_radius - 0.05, diamond_radius, abs(uv.y))) * decay;
  } else {
    diamond = 0.0;
  };
  return diamond;
}

void mainImage(out vec4 fragColor, in vec2 fragCoord)
{
  float aspect = iResolution.x / iResolution.y;
  vec2 uv = (fragCoord.xy / iResolution.xy * 2.0 - 1.0) * vec2(aspect, 1.0);
  vec2 mouse = iMouse.xy / iResolution.xy;
  mat3 rotation = rotation_z((0.5 - mouse.y) * M_PI) * rotation_y((mouse.x - 0.5) * 2.0 * M_PI);
  vec3 origin = rotation * vec3(0.0, 0.0, -DIST);
  vec3 direction = normalize(rotation * vec3(uv, F));
  vec2 box = ray_box(vec3(-0.5, -NOZZLE, -NOZZLE), vec3(0.5, NOZZLE, NOZZLE), origin, direction);
  float transparency = 1.0;
  float ds = box.y / SAMPLES;
  for (int i = 0; i <= SAMPLES; i++)
  {
    float s = box.x + i * ds;
    vec3 p = origin + direction * s;
    float dist = length(p.yz);
    vec2 uv = vec2(p.x + 0.5, dist);
    float radius = bumps(p.x + 0.5);
    vec3 scale = 20.0 * vec3(0.1, NOZZLE / radius, NOZZLE / radius);
    float diamond = diamond(uv);
    float density = NOZZLE * NOZZLE / (radius * radius) + diamond * 20.0;
    if (dist <= radius)
      transparency *= pow(0.4, density * ds * noise(p * scale + iTime * vec3(-10.0, 0.0, 0.0)));
  };
  float color = 1.0 - transparency;
  fragColor = vec4(color, color, color, 1.0);
}
