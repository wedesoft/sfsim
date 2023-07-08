#version 410 core

uniform float opacity_step;
uniform int shadow_size;
uniform int num_opacity_layers;

vec2 convert_2d_index(vec2 idx, int size_y, int size_x);
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);

float opacity_lookup(sampler3D layers, float depth, vec3 opacity_map_coords)
{
  vec2 offset_index = convert_2d_index(opacity_map_coords.xy, shadow_size, shadow_size);
  float offset_ = texture(layers, vec3(offset_index, 0.0)).r - opacity_map_coords.z;
  float z_index = max(offset_ * depth / opacity_step, 0) + 1;
  vec3 idx = vec3(opacity_map_coords.xy, z_index / num_opacity_layers);
  vec3 opacity_index = convert_3d_index(idx, num_opacity_layers + 1, shadow_size, shadow_size);
  float transmittance = texture(layers, opacity_index).r;
  return transmittance;
}
