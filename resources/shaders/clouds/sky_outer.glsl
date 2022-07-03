#version 410 core

uniform float radius;
uniform float max_height;
uniform float cloud_bottom;
uniform float cloud_top;

vec3 attenuation_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming);
vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);

vec3 sky_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming)
{
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, point, direction);
  if (atmosphere_intersection.y > 0) {
    return attenuation_outer(light_direction, point, direction, incoming);
  } else {
    return incoming;
  };
}
