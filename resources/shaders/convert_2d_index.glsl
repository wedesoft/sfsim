#version 410 core

vec2 convert_2d_index(vec2 idx, int size)
{
  return (idx * (size - 1) + 0.5) / size;
}
