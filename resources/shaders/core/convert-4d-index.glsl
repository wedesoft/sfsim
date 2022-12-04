#version 410 core

// Convert 4D index to texture lookup coordinates with margin where texture is clamped.
vec4 convert_4d_index(vec4 idx, int size_w, int size_z, int size_y, int size_x)
{
  float z_floor = floor(idx.z);
  float w_floor = floor(idx.w);
  vec4 divisor = vec4(size_x * size_z, size_x * size_z, size_y * size_w, size_y * size_w);
  return vec4(0.5 + idx.x + z_floor * size_x,
              0.5 + idx.x + min(z_floor + 1, size_z - 1) * size_x,
              0.5 + idx.y + w_floor * size_y,
              0.5 + idx.y + min(w_floor + 1, size_w - 1) * size_y) / divisor;
}
