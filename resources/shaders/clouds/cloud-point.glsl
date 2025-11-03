#version 410 core

uniform float radius;
uniform float max_height;
uniform float depth;
uniform vec3 origin;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 cloud_segment(vec3 direction, vec3 start, vec2 segment);

vec4 cloud_point(vec3 point)
{
  vec3 direction = normalize(point - origin);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere_intersection.y = min(distance(origin, point) - atmosphere_intersection.x, depth);
  vec3 start = origin + atmosphere_intersection.x * direction;
  return cloud_segment(direction, start, atmosphere_intersection);
}
