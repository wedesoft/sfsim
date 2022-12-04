#version 410 core

// Perform 3D texture lookup.
float lookup_3d(sampler3D table, vec3 point)
{
  return texture(table, point).r;
}
