#version 410 core

uniform vec3 light;
uniform float radius;
uniform float polar_radius;
uniform float max_height;
uniform float specular;
uniform vec3 origin;
uniform float amplification;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec3 sky_outer(vec3 light_direction, vec3 point, vec3 direction, vec3 incoming);

vec3 stretch(vec3 v)
{
  return vec3(v.x, v.y, v.z * radius / polar_radius);
}

// Fragment shader to render atmosphere on a background quad.
void main()
{
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light)), specular);
  vec3 incoming = vec3(glare, glare, glare) / amplification;
  vec3 scaled_origin = stretch(origin);
  vec3 scaled_direction = normalize(stretch(direction));
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, scaled_origin, scaled_direction);
  if (atmosphere_intersection.y > 0) {
    vec3 scaled_point = scaled_origin + atmosphere_intersection.x * scaled_direction;
    vec3 scaled_light_direction = normalize(stretch(light));
    fragColor = amplification * sky_outer(scaled_light_direction, scaled_point, scaled_direction, incoming);
  } else {
    fragColor = amplification * incoming;
  };
}
