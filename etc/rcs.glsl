#version 450 core

#define MIN_DIVISOR 1.0e-6
#define NOZZLE 0.05
#define MIN_LIMIT 0.07
#define MAX_SLOPE 1.0
#define BASE_DENSITY 0.03
#define SPEED 50.0

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

bool inside(float a, float b, float origin)
{
  return a <= origin && origin <= b;
}

vec2 range(float a, float b, float origin, float direction)
{
  if (direction >= 0.0)
    return vec2(a - origin, b - origin) / direction;
  else
    return vec2(b - origin, a - origin) / direction;
}

vec2 intersect_range(vec2 range1, vec2 range2)
{
  return vec2(max(range1.x, range2.x), min(range1.y, range2.y));
}

vec2 ray_box(vec3 box_min, vec3 box_max, vec3 origin, vec3 direction)
{
  bool result_valid = false;
  vec2 result = vec2(0, 0);
  if (direction.x == 0.0) {
    if (!inside(box_min.x, box_max.x, origin.x))
      return vec2(0, 0);
  } else {
    result = range(box_min.x, box_max.x, origin.x, direction.x);
    result_valid = true;
  };

  if (direction.y == 0.0) {
    if (!inside(box_min.y, box_max.y, origin.y))
      return vec2(0, 0);
  } else {
    if (result_valid)
      result = intersect_range(result, range(box_min.y, box_max.y, origin.y, direction.y));
    else {
      result = range(box_min.y, box_max.y, origin.y, direction.y);
      result_valid = true;
    };
  };

  if (direction.z == 0.0) {
    if (!inside(box_min.z, box_max.z, origin.z))
      return vec2(0, 0);
  } else {
    if (result_valid)
      result = intersect_range(result, range(box_min.z, box_max.z, origin.z, direction.z));
    else {
      result = range(box_min.z, box_max.z, origin.z, direction.z);
      result_valid = true;
    };
  };

  result.x = max(result.x, 0.0);
  return vec2(result.x, max(result.y - result.x, 0.0));
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

float sdf_circle(vec2 point, vec2 center, float radius)
{
  return length(point - center) - radius;
}

float limit(float pressure)
{
  return MIN_LIMIT / max(sqrt(pressure), MIN_DIVISOR);
}

float bulge(float pressure, float x)
{
  float limit = limit(pressure);
  float range = NOZZLE - limit;
  float equilibrium = MIN_LIMIT * MIN_LIMIT / (NOZZLE * NOZZLE);
  float base = exp(-MAX_SLOPE * (equilibrium - pressure) / (equilibrium * limit));
  float decay = pow(base, x);
  return limit + range * decay;
}

vec4 rcs_transfer(vec3 point, float rcs_step, vec4 rcs_scatter)
{
  float pressure = pow(0.001, iMouse.y / iResolution.y);
  float radius = bulge(pressure, point.x);
  if (length(point.yz) <= radius) {
    float fade = mix(1.0, 0.0, point.x);
    float density = BASE_DENSITY / (radius * radius) * fade;
    vec3 scale = 100.0 * vec3(0.1, NOZZLE / radius, NOZZLE / radius);
    float attenuation = 0.7 + 0.3 * noise(point * scale + iTime * vec3(-SPEED, 0.0, 0.0));
    float emitting = density * rcs_step * attenuation;
    return vec4(rcs_scatter.rgb + emitting, 1.0);
  } else
    return rcs_scatter;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
  vec2 uv = fragCoord/iResolution.xy - vec2(0.0, 0.5);
  vec3 origin = vec3(uv, -1.0);
  vec3 direction = vec3(0, 0, 1);
  float s = 0.0;
  float rcs_step = 0.001;
  vec4 scatter = vec4(0, 0, 0, 0);
  while (s < 2.0) {
    vec3 point = origin + s * direction;
    scatter = rcs_transfer(point, rcs_step, scatter);
    s += rcs_step;
  };
  fragColor = scatter;
}
