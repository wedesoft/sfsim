#version 410 core

uniform float opacity_step;

vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);

float opacity_lookup(sampler2D offsets, sampler3D layers, vec3 opacity_map_coords, int size_z, int size_y, int size_x)
{
  float offset_ = texture(offsets, convert_2d_index(opacity_map_coords.xy, size_y, size_x)).r - opacity_map_coords.z;
  vec3 idx = vec3(opacity_map_coords.xy, offset_ / (opacity_step * (size_z - 1)));
  float transmittance = texture(layers, convert_3d_index(idx, size_z, size_y, size_x)).r;
  return transmittance;
}
