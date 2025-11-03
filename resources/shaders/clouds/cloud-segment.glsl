#version 410 core

uniform float radius;
uniform float cloud_bottom;
uniform float cloud_top;
uniform vec3 origin;

vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, vec2 clip);
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);

vec4 cloud_segment(vec3 direction, vec3 start, vec2 segment)
{
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  if (segment.y > 0) {
    vec4 intersection = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    intersection = clip_shell_intersections(intersection, segment);
    if (intersection.t > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.st, cloud_scatter);
    if (intersection.q > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersection.pq, cloud_scatter);
  };
  return vec4(cloud_scatter.rgb, 1 - cloud_scatter.a);
}
