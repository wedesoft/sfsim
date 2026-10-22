#version 450 core

uniform sampler2D flood;
uniform float shockwave_radius;
uniform float shockwave_strength;
uniform float time;

float shockfront(float distance, float Rn);

float hash2d(vec2 p) {
  return fract(sin(dot(p, vec2(12.71, 311.7))) * 43758.5453123);
}

float interpolate_hermite(float value1, float value2, float factor)
{
  return mix(value1, value2, factor * factor * (3.0 - 2.0 * factor)); // Perform cubic Hermite interpolation
}

const vec2 vector01 = vec2(0.0, 1.0);

float noise(vec2 coordinates)
{
  vec2 fractional = fract(coordinates.xy);
  vec2 integral = floor(coordinates.xy);
  float hash00 = hash2d(integral);
  float hash10 = hash2d(integral + vector01.yx);
  float hash01 = hash2d(integral + vector01.xy);
  float hash11 = hash2d(integral + vector01.yy);

  return interpolate_hermite(interpolate_hermite(hash00, hash10, fractional.x), interpolate_hermite(hash01, hash11, fractional.x), fractional.y);
}

vec4 shockwave_transfer(vec3 point, float shockwave_step, vec4 shockwave_scatter)
{
  vec4 apex = texture(flood, (point.xy / shockwave_radius + 1.0) / 2.0);
  float radial_distance = length(apex.xy - point.xy);
  float depth = apex.z + shockfront(radial_distance, apex.w);
  if (point.z <= depth) {
    float emission = shockwave_strength * shockwave_step * exp(1.0 * (point.z - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, radial_distance));

    vec2 toApex = point.xy - apex.xy;
    float dist = length(toApex);
    vec2 dir = (dist > 0.0001) ? (toApex / dist) : vec2(0.0, 1.0);

    float noiseScale = 20.0;   // Frequency of the noise features
    float speed = 0.0;         // Speed of outward expansion

    // vec2 noiseUV = dir * (dist * noiseScale - time * speed);
    vec2 noiseUV = dir * noiseScale;

    float shockNoise = noise(noiseUV);

    emission *= shockNoise;
    shockwave_scatter += vec4(emission, emission, 0, 0);
  };
  return shockwave_scatter;
}
