#version 450 core

uniform sampler3D <%= sampler %>;

// Perform 3D texture lookup.
float <%= method-name %>(vec3 point, float lod)
{
  return textureLod(<%= sampler %>, point, lod).r;
}
