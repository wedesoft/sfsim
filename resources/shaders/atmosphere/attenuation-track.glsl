#version 450 core

vec4 attenuate(vec3 light_direction, vec3 start, vec3 point, vec4 incoming);

vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming)
{
  if (segment.y > 0) {
    vec3 p = origin + direction * segment.x;
    vec3 q = origin + direction * (segment.x + segment.y);
    return attenuate(light_direction, p, q, incoming);
  } else
    return incoming;
}
