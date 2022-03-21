#version 410 core

uniform sampler2D transmittance;
uniform sampler2D ray_scatter;
uniform vec3 light;
uniform float radius;
uniform float polar_radius;
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
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power, bool sky);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size, float power);
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);
vec4 interpolate_4d(sampler2D table, int size, vec4 idx);

vec3 stretch(vec3 v)
{
  return vec3(v.x, v.y, v.z * radius / polar_radius);
}

void main()
{
  vec3 direction = normalize(fs_in.direction);
  float glare = pow(max(0, dot(direction, light)), specular);
  vec3 scaled_origin = stretch(origin);
  vec3 scaled_direction = normalize(stretch(direction));
  vec2 atmosphere_intersection = ray_sphere(vec3(0, 0, 0), radius + max_height, scaled_origin, scaled_direction);
  if (atmosphere_intersection.y > 0) {
    vec3 scaled_point = scaled_origin + atmosphere_intersection.x * scaled_direction;
    vec3 scaled_light = normalize(stretch(light));
    vec2 transmittance_index = transmittance_forward(scaled_point, scaled_direction, radius, max_height, size, power, true);
    vec4 ray_scatter_index = ray_scatter_forward(scaled_point, scaled_direction, scaled_light, radius, max_height, size, power);
    vec3 atmospheric_contribution = interpolate_4d(ray_scatter, size, ray_scatter_index).rgb;
    vec3 remaining_glare = glare * interpolate_2d(transmittance, size, transmittance_index).rgb;
    fragColor = atmospheric_contribution * amplification + remaining_glare;
  } else {
    fragColor = vec3(glare, glare, glare);
  };
}
