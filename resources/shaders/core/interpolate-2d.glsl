#version 410 core

vec2 convert_2d_index(vec2 idx, int size_y, int size_x);

// Perform 2D texture lookup using coordinates between 0 and 1.
vec4 interpolate_2d(sampler2D table, int size_y, int size_x, vec2 idx)
{
  vec2 pixel = idx * (vec2(size_x, size_y) - 1);
  vec2 indices = convert_2d_index(pixel, size_y, size_x);
  return texture(table, indices);
}
