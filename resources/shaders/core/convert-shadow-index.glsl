#version 450 core

uniform float shadow_bias;

// Move shadow index out of clamping region
vec4 convert_shadow_index(vec4 idx, int size_y, int size_x)
{
  vec2 pixel = idx.xy * (vec2(size_x, size_y) - 1);
  return vec4((pixel + 0.5) / vec2(size_x, size_y), max(idx.z, 2 * shadow_bias), idx.w);
}
