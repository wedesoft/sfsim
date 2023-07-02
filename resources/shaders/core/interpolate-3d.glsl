#version 410 core

vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x);

// Perform 2D texture lookup using coordinates between 0 and 1.
float interpolate_3d(sampler3D table, int size_z, int size_y, int size_x, vec3 idx)
{
  vec3 indices = convert_3d_index(idx, size_z, size_y, size_x);
  return texture(table, indices).r;
}
