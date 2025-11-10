#version 410 core

vec3 attenuate(vec3 light_direction, vec3 start, vec3 point, vec3 incoming);

vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  vec3 p = origin + direction * a;
  vec3 q = origin + direction * b;
  return attenuate(light_direction, p, q, incoming);
}
