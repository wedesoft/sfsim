#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;
uniform float depth;
uniform vec3 origin;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);
vec4 clip_shell_intersections(vec4 intersections, vec2 clip);

vec4 cloud_point(vec3 point)
{
  vec3 direction = normalize(point - origin);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere_intersection.y = min(distance(origin, point) - atmosphere_intersection.x, depth);
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  if (atmosphere_intersection.y > 0) {
    vec4 intersection = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    intersection = clip_shell_intersections(intersection, atmosphere_intersection);
    vec3 start = origin + atmosphere_intersection.x * direction;
    if (intersection.t > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.st, cloud_scatter);
    if (intersection.q > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.pq, cloud_scatter);
  };
  return vec4(cloud_scatter.rgb, 1 - cloud_scatter.a);
}
