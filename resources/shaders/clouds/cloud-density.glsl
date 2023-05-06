#version 410 core

uniform float cover_multiplier;

float cloud_cover(vec3 point);

float cloud_density(vec3 point, float lod)
{
  float cover = cloud_cover(point) * cover_multiplier;
  return cover;
}
