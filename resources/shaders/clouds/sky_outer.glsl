#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming);
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);

vec3 sky_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, direction);
  if (atmosphere_intersection.y > 0) {
    point = point + direction * atmosphere_intersection.x;
    if (cloud_top > cloud_bottom) {
      vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, point, direction);
      if (cloud_intersections.y > 0) {
        vec3 a = point + cloud_intersections.x * direction;
        vec3 b = a + cloud_intersections.y * direction;
        if (cloud_intersections.w > 0) {
          vec3 c = point + cloud_intersections.z * direction;
          vec3 d = c + cloud_intersections.w * direction;
          incoming = attenuation_outer(light_direction, d, direction, incoming);
          incoming = cloud_track(light_direction, c, d, incoming);
          incoming = attenuation_track(light_direction, b, c, incoming);
        } else
          incoming = attenuation_outer(light_direction, b, direction, incoming);
        incoming = cloud_track(light_direction, a, b, incoming);
        incoming = attenuation_track(light_direction, point, a, incoming);
      } else
        incoming = attenuation_outer(light_direction, point, direction, incoming);
    } else
      incoming = attenuation_outer(light_direction, point, direction, incoming);
  };
  return incoming;
}
