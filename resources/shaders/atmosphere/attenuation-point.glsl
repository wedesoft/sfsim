#version 410 core

uniform vec3 origin;
uniform vec3 light_direction;
uniform float radius;
uniform float max_height;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 attenuation_track(vec3 light_direction, vec3 origin, vec3 direction, float a, float b, vec4 incoming);

vec3 attenuation_point(vec3 point, vec3 incoming)
{
  vec3 direction = normalize(point - origin);
  vec2 atmosphere = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  atmosphere.y = min(atmosphere.y, distance(origin, point) - atmosphere.x);
  if (atmosphere.y > 0)
    incoming = attenuation_track(light_direction, origin, direction, atmosphere.x, atmosphere.x + atmosphere.y, vec4(incoming, 1.0));
  return incoming;
}
