#version 410 core

uniform float radius;
uniform float max_height;
uniform vec3 light_direction;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 transmittance_outer(vec3 point, vec3 direction);

vec3 transmittance_point(vec3 point)
{
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, point, light_direction);
  if (atmosphere.y > 0)
    return transmittance_outer(point + atmosphere.x * light_direction, light_direction);
  else
    return vec3(1.0, 1.0, 1.0);
}
