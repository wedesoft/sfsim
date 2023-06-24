#version 410 core

uniform vec3 light_direction;
uniform float radius;
uniform float max_height;
uniform float specular;
uniform vec3 origin;

in VS_OUT
{
  vec3 direction;
} fs_in;

out vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 attenuation_outer(vec3 light_direction, vec3 origin, vec3 direction, float a, vec3 incoming);

vec3 sun_color(vec3 direction)
{
  float glare = pow(max(0, dot(direction, light_direction)), specular);
  return vec3(glare, glare, glare);
}

// Fragment shader to render atmosphere on a background quad.
void main()
{
  vec3 direction = normalize(fs_in.direction);
  vec3 incoming = sun_color(direction);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  if (atmosphere_intersection.y > 0) {
    incoming = attenuation_outer(light_direction, origin, direction, atmosphere_intersection.x, incoming);
  };
  fragColor = incoming;
}
