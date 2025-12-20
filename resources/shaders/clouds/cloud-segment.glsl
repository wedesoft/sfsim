#version 450 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;
<% (if (not outer) %>
uniform float depth;
<% ) %>

vec2 clip_interval(vec2 interval, vec2 clip);
vec2 limit_interval(vec2 interval, float limit);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, vec2 clip);
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);

<% (if outer %>
vec4 cloud_outer(vec3 origin, vec3 direction, float skip)
<% %>
vec4 cloud_point(vec3 origin, vec3 direction, vec2 segment)
<% ) %>
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec3 start = origin + atmosphere_intersection.x * direction;
<% (if outer %>
  atmosphere_intersection = clip_interval(atmosphere_intersection, vec2(skip, atmosphere_intersection.y));
<% %>
  atmosphere_intersection = clip_interval(atmosphere_intersection, limit_interval(segment, atmosphere_intersection.x + depth));
<% ) %>
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  if (atmosphere_intersection.y > 0) {
    vec4 intersection = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    intersection = clip_shell_intersections(intersection, atmosphere_intersection);
    if (intersection.t > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.st, cloud_scatter);
    if (intersection.q > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.pq, cloud_scatter);
  };
  return vec4(cloud_scatter.rgb, 1 - cloud_scatter.a);
}
