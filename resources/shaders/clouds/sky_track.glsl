#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, float limit);
vec3 cloud_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);
vec3 attenuation_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming);

vec3 sky_track(vec3 light_direction, vec3 p, vec3 q, vec3 incoming)
{
  float dist = distance(p, q);
  if (dist > 0) {
    vec3 direction = (q - p) / dist;
    vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, p, direction);
    if (atmosphere_intersection.y > 0) {
      vec3 point = p + atmosphere_intersection.x * direction;
      if (cloud_top > cloud_bottom) {
        vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, point, direction);
        cloud_intersections = clip_shell_intersections(cloud_intersections, dist);
        if (cloud_intersections.y > 0) {
          vec3 a = point + cloud_intersections.x * direction;
          vec3 b = a + cloud_intersections.y * direction;
          if (cloud_intersections.w > 0) {
            vec3 c = point + cloud_intersections.z * direction;
            vec3 d = c + cloud_intersections.w * direction;
            incoming = attenuation_track(light_direction, d, q, incoming);
            incoming = cloud_track(light_direction, c, d, incoming);
            incoming = attenuation_track(light_direction, b, c, incoming);
          } else
            incoming = attenuation_track(light_direction, b, q, incoming);
          incoming = cloud_track(light_direction, a, b, incoming);
          incoming = attenuation_track(light_direction, point, a, incoming);
        } else
          incoming = attenuation_track(light_direction, point, q, incoming);
      } else
        incoming = attenuation_track(light_direction, point, q, incoming);
    };
  };
  return incoming;
}
