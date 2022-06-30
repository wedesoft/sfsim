#version 410 core

// Convert 2D index to texture lookup coordinates with margin where the texture is clamped.
vec3 convert_3d_index(vec3 point, vec3 box_min, vec3 box_max)
{
  return (point - box_min) / (box_max - box_min);
}

