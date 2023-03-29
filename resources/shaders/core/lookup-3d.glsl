#version 410 core

uniform sampler3D <%= sampler %>;

// Perform 3D texture lookup.
float lookup_3d(vec3 point)
{
  return texture(<%= sampler %>, point).r;
}
