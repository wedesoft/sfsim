#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;
uniform vec3 origin;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 sample_cloud(vec3 origin, vec3 start, vec3 direction, vec2 cloud_shell, vec4 cloud_scatter);

vec4 cloud_atmosphere(vec3 fs_in_direction)
{
  vec3 direction = normalize(fs_in_direction);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  vec4 cloud_scatter = vec4(0, 0, 0, 1);
  if (atmosphere_intersection.y > 0) {
    vec4 intersect = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    vec3 start = origin + atmosphere_intersection.x * direction;
    if (intersect.t > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersect.st, cloud_scatter);
    if (intersect.q > 0)
      cloud_scatter = sample_cloud(origin, start, direction, intersect.pq, cloud_scatter);
  };
  return vec4(cloud_scatter.rgb, 1 - cloud_scatter.a);
}
