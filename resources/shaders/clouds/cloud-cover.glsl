#version 450 core

uniform samplerCube cover;
uniform int cover_size;

float interpolate_float_cubemap(samplerCube cube, int size, vec3 idx);

float cloud_cover(vec3 idx)
{
  return interpolate_float_cubemap(cover, cover_size, idx);
}
