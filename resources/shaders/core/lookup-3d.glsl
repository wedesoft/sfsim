#version 450 core

uniform sampler3D <%= sampler %>;

// Perform 3D texture lookup.
float <%= method-name %>(vec3 point)
{
  return texture(<%= sampler %>, point).r;
}
