#version 410 core

uniform float radius;
uniform float max_height;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_segment(vec3 origin, vec3 direction, vec2 segment);

vec4 cloud_outer(vec3 origin, vec3 direction)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  return cloud_segment(origin, direction, atmosphere_intersection);
}
