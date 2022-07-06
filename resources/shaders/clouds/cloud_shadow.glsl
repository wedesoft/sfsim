#version 410 core

uniform float radius;
uniform float max_height;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);

vec3 cloud_shadow(vec3 point, vec3 light_direction)
{
  vec3 incoming = vec3(1, 1, 1);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, light_direction);
  incoming = attenuation_track(light_direction, point, point + atmosphere_intersection.y * light_direction, incoming);
  return incoming;
}
