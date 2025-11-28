#version 450 core

uniform vec3 origin;
uniform vec3 light_direction;
uniform float radius;
uniform float max_height;

vec2 limit_interval(vec2 interval, float limit);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, vec2 segment, vec4 incoming);

vec4 attenuation_point(vec3 point, vec4 incoming)
{
  vec3 direction = normalize(point - origin);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere = limit_interval(atmosphere, distance(origin, point));
  return attenuation_track(light_direction, origin, direction, atmosphere, incoming);
}
