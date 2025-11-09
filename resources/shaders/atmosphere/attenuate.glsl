#version 410 core

uniform float amplification;

vec3 transmittance_track(vec3 p, vec3 q);
vec3 ray_scatter_track(vec3 light_direction, vec3 p, vec3 q);

vec3 attenuate(vec3 light_direction, vec3 start, vec3 point, vec3 incoming)
{
  vec3 transmittance = transmittance_track(start, point);
  vec3 in_scatter = ray_scatter_track(light_direction, start, point) * amplification;
  return incoming * transmittance + in_scatter;
}

