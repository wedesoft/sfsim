#version 410 core

vec2 convert_2d_index(vec2 idx, int size_y, int size_x)
{
  return (idx + 0.5) / vec2(size_x, size_y);
}
