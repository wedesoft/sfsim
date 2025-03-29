#version 410 core

float lod_at_distance(float dist, float lod_offset)
{
  return log2(dist) + lod_offset;
}
