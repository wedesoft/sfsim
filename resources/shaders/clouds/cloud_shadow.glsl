#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);
vec3 cloud_track_base(vec3 p, vec3 q, vec3 incoming);

vec3 cloud_shadow(vec3 point, vec3 light_direction)
{
  vec3 incoming = vec3(1, 1, 1);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, light_direction);
  if (atmosphere_intersection.y > 0) {
    point = point + atmosphere_intersection.x * light_direction;
    vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
    float limit;
    if (planet_intersection.y > 0) {
      incoming = vec3(0, 0, 0);
      limit = planet_intersection.x;
    } else
      limit = atmosphere_intersection.y;
    if (cloud_top > cloud_bottom) {
      vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, point, light_direction);
      if (cloud_intersections.y > 0) {
        vec3 a = point + cloud_intersections.x * light_direction;
        vec3 b = a + cloud_intersections.y * light_direction;
        if (cloud_intersections.w > 0) {
          vec3 c = point + cloud_intersections.z * light_direction;
          vec3 d = c + cloud_intersections.w * light_direction;
          incoming = attenuation_track(light_direction, d, point + limit * light_direction, incoming);
          incoming = cloud_track_base(c, d, incoming);
          incoming = attenuation_track(light_direction, b, c, incoming);
        } else
          incoming = attenuation_track(light_direction, b, point + limit * light_direction, incoming);
        incoming = cloud_track_base(a, b, incoming);
        incoming = attenuation_track(light_direction, point, a, incoming);
      } else
        incoming = attenuation_track(light_direction, point, point + limit * light_direction, incoming);
    } else
      incoming = attenuation_track(light_direction, point, point + limit * light_direction, incoming);
  };
  return incoming;
}
