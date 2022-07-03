#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming);
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);

vec3 sky_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, direction);
  if (atmosphere_intersection.y > 0) {
    point = point + direction * atmosphere_intersection.x;
    if (cloud_top > cloud_bottom) {
      vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, point, direction);
      if (cloud_intersections.y > 0) {
        vec3 next_point = point + cloud_intersections.y * direction;
        incoming = cloud_track(light_direction, point, next_point, incoming);
        point = next_point;
      };
    };
    incoming = attenuation_outer(light_direction, point, direction, incoming);
  };
  return incoming;
}
