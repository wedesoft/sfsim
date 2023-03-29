#version 410 core

uniform sampler3D <%= sampler %>;

// Perform 3D texture lookup.
float lookup_3d_lod(vec3 point, float lod)
{
  return textureLod(<%= sampler %>, point, lod).r;
}
