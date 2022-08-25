#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming);
vec3 cloud_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);

vec3 sky_outer(vec3 light_direction, vec3 origin, vec3 direction, vec3 incoming)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  if (atmosphere_intersection.y > 0) {
    if (cloud_top > cloud_bottom) {
      vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, origin, direction);
      if (cloud_intersections.y > 0) {
        float a = cloud_intersections.x;
        float b = cloud_intersections.x + cloud_intersections.y;
        // if (cloud_intersections.w > 0) {
        //   float c = cloud_intersections.z;
        //   float d = cloud_intersections.z + cloud_intersections.w;
        //   incoming = attenuation_outer(light_direction, origin, direction, d, incoming);
        //   incoming = cloud_track(light_direction, origin, direction, c, d, incoming);
        //   incoming = attenuation_track(light_direction, origin, direction, b, c, incoming);
        // } else
          incoming = attenuation_outer(light_direction, origin, direction, b, incoming);
        incoming = cloud_track(light_direction, origin, direction, a, b, incoming);
        incoming = attenuation_track(light_direction, origin, direction, atmosphere_intersection.x, a, incoming);
      } else
        incoming = attenuation_outer(light_direction, origin, direction, atmosphere_intersection.x, incoming);
    } else
      incoming = attenuation_outer(light_direction, origin, direction, atmosphere_intersection.x, incoming);
  };
  return incoming;
}
