#version 410 core

vec3 convert_3d_index(vec3 point, vec3 box_min, vec3 box_max);

// Perform 3D texture lookup using point and box boundaries.
float interpolate_3d(sampler3D table, vec3 point, vec3 box_min, vec3 box_max)
{
  vec3 indices = convert_3d_index(point, box_min, box_max);
  return texture(table, indices).r;
}
