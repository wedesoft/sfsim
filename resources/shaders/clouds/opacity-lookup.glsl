#version 410 core

uniform float opacity_step;
uniform int opacity_size_x;
uniform int opacity_size_y;
uniform int opacity_size_z;

vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);

float opacity_lookup(sampler2D offsets, sampler3D layers, vec3 opacity_map_coords)
{
  vec2 offset_index = convert_2d_index(opacity_map_coords.xy, opacity_size_y, opacity_size_x);
  float offset_ = texture(offsets, offset_index).r - opacity_map_coords.z;
  vec3 idx = vec3(opacity_map_coords.xy, offset_ / (opacity_step * (opacity_size_z - 1)));
  vec3 opacity_index = convert_3d_index(idx, opacity_size_z, opacity_size_y, opacity_size_x);
  float transmittance = texture(layers, opacity_index).r;
  return transmittance;
}
