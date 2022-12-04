#version 410 core

uniform sampler2D transmittance;
uniform int transmittance_height_size;
uniform int transmittance_elevation_size;

vec2 transmittance_forward(vec3 point, vec3 direction, bool above_horizon);
vec3 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx);

vec3 transmittance_outer(vec3 point, vec3 direction)
{
  vec2 transmittance_index = transmittance_forward(point, direction, true);
  return interpolate_2d(transmittance, transmittance_height_size, transmittance_elevation_size, transmittance_index);
}
