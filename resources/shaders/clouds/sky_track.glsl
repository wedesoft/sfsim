#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec4 clip_shell_intersections(vec4 intersections, float limit);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);

vec3 sky_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming)
{
  if (cloud_top > cloud_bottom) {
    vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
    cloud_intersections = clip_shell_intersections(cloud_intersections, b - a);
    if (cloud_intersections.y > 0) {
      float c = cloud_intersections.x;
      float d = cloud_intersections.x + cloud_intersections.y;
      if (cloud_intersections.w > 0) {
        float e = cloud_intersections.z;
        float f = cloud_intersections.z + cloud_intersections.w;
        incoming = attenuation_track(light_direction, origin, direction, f, b, incoming);
        incoming = cloud_track(light_direction, origin, direction, e, f, incoming);
        incoming = attenuation_track(light_direction, origin, direction, d, e, incoming);
        incoming = cloud_track(light_direction, origin, direction, c, d, incoming);
      } else {
        incoming = attenuation_track(light_direction, origin, direction, d, b, incoming);
        float m = 0.5 * (c + d);
        incoming = cloud_track(light_direction, origin, direction, m, d, incoming);
        incoming = cloud_track(light_direction, origin, direction, c, m, incoming);
      };
      incoming = attenuation_track(light_direction, origin, direction, a, c, incoming);
    } else
      incoming = attenuation_track(light_direction, origin, direction, a, b, incoming);
  } else
    incoming = attenuation_track(light_direction, origin, direction, a, b, incoming);
  return incoming;
}
