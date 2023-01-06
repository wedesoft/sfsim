#version 410 core

uniform float radius;
uniform float max_height;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 transmittance_outer(vec3 point, vec3 direction);
float opacity_cascade_lookup(vec4 point);

vec3 cloud_shadow(vec3 point, vec3 light_direction, float lod)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, light_direction);
  if (atmosphere_intersection.y > 0) {
    if (dot(point, light_direction) < 0) {
      vec2 planet_intersection = ray_sphere(vec3(0, 0, 0), radius, point, light_direction);
      if (planet_intersection.y > 0) {
        return vec3(0, 0, 0);
      };
    };
    float shadow = opacity_cascade_lookup(vec4(point, 1));
    return transmittance_outer(point + atmosphere_intersection.x * light_direction, light_direction) * shadow;
  };
  return vec3(1, 1, 1);
}
