#version 410 core

vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 ray_scatter_outer(vec3 light_direction, vec3 point, vec3 direction);

// Shader combining atmospheric transmittance and in-scattering.
vec3 attenuation_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming)
{
  vec3 atmospheric_contribution = ray_scatter_outer(light_direction, point, direction);
  vec3 remaining_incoming = incoming * transmittance_outer(point, direction);
  return atmospheric_contribution + remaining_incoming;
}

