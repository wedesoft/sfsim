#version 450 core

// Grow sampling index to cover full NDC space
vec4 grow_shadow_index(vec4 idx, int size_y, int size_x)
{
  vec2 scaled = idx.xy * vec2(size_x, size_y) / (vec2(size_x, size_y) - 1);
  return vec4(scaled, idx.z, idx.w);
}
