#version 410 core

uniform float amplification;

vec3 transmittance_outer(vec3 point, vec3 direction);
vec3 ray_scatter_outer(vec3 light_direction, vec3 point, vec3 direction);

// Shader combining atmospheric transmittance and in-scattering.
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming)
{
  vec3 point = origin + direction * a;
  vec3 transmittance = transmittance_outer(point, direction);
  incoming *= transmittance;
  vec3 ray_scatter = ray_scatter_outer(light_direction, point, direction) * amplification;
  return incoming + ray_scatter;
}
