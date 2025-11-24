#version 410 core

vec4 attenuate(vec3 light_direction, vec3 start, vec3 point, vec4 incoming);

vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec4 incoming)
{
  vec3 p = origin + direction * a;
  vec3 q = origin + direction * b;
  return attenuate(light_direction, p, q, incoming);
}
