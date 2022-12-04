#version 410 core

// Expand sampling index to expand to full NDC space
vec4 sample_shadow_index(vec4 idx, int size_y, int size_x)
{
  vec2 scaled = idx.xy * vec2(size_x, size_y) / (vec2(size_x, size_y) - 1);
  return vec4(scaled, idx.z, idx.w);
}
