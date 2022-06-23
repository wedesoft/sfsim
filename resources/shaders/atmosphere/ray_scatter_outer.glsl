#version 410 core

uniform sampler2D ray_scatter;
uniform int height_size;
uniform int elevation_size;
uniform int light_elevation_size;
uniform int heading_size;

vec4 ray_scatter_forward(vec3 point, vec3 direction, vec3 light_direction, bool above_horizon);
vec4 interpolate_4d(sampler2D table, int size_w, int size_z, int size_y, int size_x, vec4 idx);

vec3 ray_scatter_outer(vec3 light_direction, vec3 point, vec3 direction)
{
  vec4 ray_scatter_index = ray_scatter_forward(point, direction, light_direction, true);
  return interpolate_4d(ray_scatter, height_size, elevation_size, light_elevation_size, heading_size, ray_scatter_index).rgb;
}
