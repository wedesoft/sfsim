#version 450 core

uniform sampler2D flood;
uniform float shockwave_radius;
uniform float shockwave_strength;
uniform float time;

float shockfront(float distance, float Rn);

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

vec4 shockwave_transfer(vec3 point, float shockwave_step, vec4 shockwave_scatter)
{
  vec4 apex = texture(flood, (point.xy / shockwave_radius + 1.0) / 2.0);
  float radial_distance = length(apex.xy - point.xy);
  float depth = apex.z + shockfront(radial_distance, apex.w);
  if (point.z <= depth) {
    float emission = shockwave_strength * shockwave_step * exp(1.0 * (point.z - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, radial_distance));
    emission *= noise(point * vec3(0.25, 0.25, 0.05) + time * vec3(0, 0, 10.0));
    shockwave_scatter += vec4(emission, emission, 0, 0);
  };
  return shockwave_scatter;
}
