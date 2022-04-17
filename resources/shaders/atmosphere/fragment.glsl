#version 410 core

uniform sampler2D transmittance;
uniform sampler2D ray_scatter;
uniform vec3 light;
uniform float radius;
uniform float polar_radius;
uniform float max_height;
uniform float specular;
uniform float power;
uniform int height_size;
uniform int elevation_size;
uniform int size;
uniform float amplification;
uniform vec3 origin;

in VS_OUT
{
  highp vec3 direction;
} fs_in;

out lowp vec3 fragColor;

vec2 ray_sphere(vec3 centre, float radius, vec3 origin, vec3 direction);
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int heigh_size, int elevation_size,
                           float power, bool above_horizon);
vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, float radius, float max_height, int size,
                         float power, bool above_horizon);
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);

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
    vec2 transmittance_index = transmittance_forward(scaled_point, scaled_direction, radius, max_height, height_size,
                                                     elevation_size, power, true);
    vec4 ray_scatter_index = ray_scatter_forward(scaled_point, scaled_direction, scaled_light, radius, max_height, size, power,
                                                 true);
    vec3 atmospheric_contribution = interpolate_4d(ray_scatter, size, size, size, size, ray_scatter_index).rgb;// TODO: use correct shape
    vec3 remaining_glare = glare * interpolate_2d(transmittance, height_size, elevation_size, transmittance_index).rgb;
    fragColor = atmospheric_contribution * amplification + remaining_glare;
  } else {
    fragColor = vec3(glare, glare, glare);
  };
}
