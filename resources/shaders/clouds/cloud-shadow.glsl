#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec4 ray_shell(vec3 centre, float inner_radius, float outer_radius, vec3 origin, vec3 direction);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec3 incoming);
vec3 cloud_track_base(vec3 origin, vec3 direction, float a, float b, vec3 incoming, float lod);

vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec3 incoming = vec3(1, 1, 1);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, light_direction);
  if (atmosphere_intersection.y > 0) {
    vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
    float limit;
    if (planet_intersection.y > 0) {
      incoming = vec3(0, 0, 0);
      limit = planet_intersection.x;
    } else
      limit = atmosphere_intersection.x + atmosphere_intersection.y;
    if (cloud_top > cloud_bottom) {
      vec4 cloud_intersections = ray_shell(vec3(0, 0, 0), radius + cloud_bottom, radius + cloud_top, point, light_direction);
      if (cloud_intersections.y > 0) {
        float a = cloud_intersections.x;
        float b = cloud_intersections.x + cloud_intersections.y;
        if (planet_intersection.y == 0 && cloud_intersections.w > 0) {
          float c = cloud_intersections.z;
          float d = cloud_intersections.z + cloud_intersections.w;
          incoming = attenuation_track(light_direction, point, light_direction, d, limit, incoming);
          incoming = cloud_track_base(point, light_direction, c, d, incoming, lod);
          incoming = attenuation_track(light_direction, point, light_direction, b, c, incoming);
        } else
          incoming = attenuation_track(light_direction, point, light_direction, b, limit, incoming);
        incoming = cloud_track_base(point, light_direction, a, b, incoming, lod);
        incoming = attenuation_track(light_direction, point, light_direction, atmosphere_intersection.x, a, incoming);
      } else
        incoming = attenuation_track(light_direction, point, light_direction, atmosphere_intersection.x, limit, incoming);
    } else
      incoming = attenuation_track(light_direction, point, light_direction, atmosphere_intersection.x, limit, incoming);
  };
  return incoming;
}
