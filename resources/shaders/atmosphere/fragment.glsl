#version 410 core

uniform vec3 light_direction;
uniform float radius;
uniform float polar_radius;
uniform float max_height;
uniform float specular;
uniform float amplification;

in VS_OUT
{
  vec3 origin;
  vec3 direction;
} fs_in;

out vec3 fragColor;

vec2 ray_ellipsoid(vec3 centre, float radius, float polar_radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming);

// Fragment shader to render atmosphere on a background quad.
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light_direction)), specular);
  vec3 incoming = vec3(glare, glare, glare) / amplification;
  float equator_height = radius + max_height;
  float polar_height = polar_radius + max_height * polar_radius / radius;
  vec2 atmosphere_intersection = ray_ellipsoid(vec3(0, 0, 0), equator_height, polar_height, origin, direction);
  if (atmosphere_intersection.y > 0) {
    vec3 point = fs_in.origin + atmosphere_intersection.x * direction;
    fragColor = amplification * attenuation_outer(light_direction, point, direction, 0, incoming);
  } else {
    fragColor = amplification * incoming;
  };
}
