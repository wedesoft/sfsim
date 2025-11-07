#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;
<% (if (not outer) %>
uniform float depth;
<% ) %>

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, vec2 clip);
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);

vec4 cloud_<%= (if outer "outer" "point") %>(vec3 origin, vec3 direction<% (if (not outer) %>, vec3 point <% ) %>)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
<% (if (not outer) %>
  float dist = distance(origin, point);
  atmosphere_intersection.y = min(dist - atmosphere_intersection.x, min(depth, atmosphere_intersection.y));
<% ) %>
  vec3 start = origin + atmosphere_intersection.x * direction;
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
