#version 410 core

float M_PI = 3.14159265358;

vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power);
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);

vec3 ground_radiance(float albedo, sampler2D transmittance, sampler2D surface_radiance, float radius, float max_height, int size,
                     float power, vec3 point, vec3 normal, vec3 light, vec3 color)
{
  float cos_light_angle = dot(normal, light);
  vec2 uv = transmittance_forward(point, light, radius, max_height, size, power);
  vec3 direct_light = interpolate_2d(transmittance, size, uv).rgb;
  vec3 ambient_light = interpolate_2d(surface_radiance, size, uv).rgb;
  return (albedo * color / M_PI) * (max(cos_light_angle, 0) * direct_light + ambient_light);
}
