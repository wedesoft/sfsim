#version 410 core

uniform sampler2D transmittance;
uniform sampler2D ray_scatter;
uniform vec3 light;
uniform float radius;
uniform float max_height;
uniform float specular;
uniform float power;
uniform int size;
uniform float amplification;
uniform vec3 origin;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size, float power);
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);
vec4 interpolate_4d(sampler2D table, int size, vec4 idx);

void main()
{
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light)), specular);
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, origin, direction);
  if (atmosphere_intersection.y > 0) {
    vec3 point = origin + atmosphere_intersection.x * direction;
    vec2 transmittance_index = transmittance_forward(point, direction, radius, max_height, size, power);
    vec4 ray_scatter_index = ray_scatter_forward(point, direction, light, radius, max_height, size, power);
    vec3 atmospheric_contribution = interpolate_4d(ray_scatter, size, ray_scatter_index).rgb;
    vec3 remaining_glare = glare * interpolate_2d(transmittance, size, transmittance_index).rgb;
    fragColor = atmospheric_contribution * amplification + remaining_glare;
  } else {
    fragColor = vec3(glare, glare, glare);
  };
}
