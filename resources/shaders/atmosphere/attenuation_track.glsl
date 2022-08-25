#version 410 core

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);

vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  return incoming;
  vec3 p = origin + direction * a;
  vec3 q = origin + direction * b;
  vec3 surface_transmittance = transmittance_track(p, q);
  vec3 in_scattering = ray_scatter_track(light_direction, p, q);
  return incoming * surface_transmittance + in_scattering;
}
