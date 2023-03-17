#version 410 core

vec3 convert_cubemap_index(vec3 idx, int size);

vec3 interpolate_cubemap(samplerCube cube, int size, vec3 idx)
{
  vec3 adapted = convert_cubemap_index(idx, size);
  return texture(cube, adapted).xyz;
}
