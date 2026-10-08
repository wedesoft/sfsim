#version 450 core

uniform sampler2D flood;
uniform float shockwave_radius;
uniform float shockwave_strength;

float shockfront(float distance, float Rn);

vec4 shockwave_transfer(vec3 point, float shockwave_step, vec4 shockwave_scatter)
{
  vec4 apex = texture(flood, (point.xy / shockwave_radius + 1.0) / 2.0);
  float radial_distance = length(apex.xy - point.xy);
  float depth = apex.z + shockfront(radial_distance, apex.w);
  if (point.z <= depth) {
    float emission = shockwave_strength * shockwave_step * exp(1.0 * (point.z - depth)) * (1.0 - smoothstep(0.0, 0.25 * shockwave_radius, radial_distance));
    shockwave_scatter += vec4(emission, emission, 0, 0);
  };
  return shockwave_scatter;
};


