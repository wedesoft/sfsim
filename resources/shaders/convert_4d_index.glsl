#version 410 core

vec4 convert_4d_index(vec4 idx, int size)
{
  float z_floor = floor(idx.z);
  float w_floor = floor(idx.w);
  return vec4(0.5 + idx.x + z_floor * size,
              0.5 + idx.x + min(z_floor + 1, size - 1) * size,
              0.5 + idx.y + w_floor * size,
              0.5 + idx.y + min(w_floor + 1, size - 1) * size) / (size * size);
}
