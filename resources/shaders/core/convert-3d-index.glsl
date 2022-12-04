#version 410 core

// Convert 3D index to texture lookup coordinates with margin where the texture is clamped.
vec3 convert_3d_index(vec3 idx, int size_z, int size_y, int size_x)
{
  vec3 pixel = idx * (vec3(size_x, size_y, size_z) - 1);
  return (pixel + 0.5) / vec3(size_x, size_y, size_z);
}
