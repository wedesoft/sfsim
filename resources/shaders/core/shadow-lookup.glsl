#version 410 core

uniform int shadow_size;

vec4 convert_shadow_index(vec4 idx, int size_y, int size_x);

float shadow_lookup(sampler2DShadow shadow_map, vec4 shadow_pos)
{
  vec4 shrinked_index = convert_shadow_index(shadow_pos, shadow_size, shadow_size);
  return textureProj(shadow_map, shrinked_index);
}
