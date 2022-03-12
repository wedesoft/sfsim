#version 410 core

vec2 convert_2d_index(vec2 idx, int size);

vec4 interpolate_2d(sampler2D table, int size, vec2 idx)
{
  vec2 pixel = idx * (size - 1);
  vec2 indices = convert_2d_index(pixel, size);
  return texture(table, indices);
}
