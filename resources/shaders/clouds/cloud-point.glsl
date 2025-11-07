#version 410 core

uniform float radius;
uniform float max_height;
uniform float depth;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_segment(vec3 origin, vec3 direction, vec2 segment);

vec4 cloud_point(vec3 origin, vec3 direction, vec3 point)
{
  float dist = distance(origin, point);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere_intersection.y = min(dist - atmosphere_intersection.x, min(depth, atmosphere_intersection.y));
  return cloud_segment(origin, direction, atmosphere_intersection);
}
