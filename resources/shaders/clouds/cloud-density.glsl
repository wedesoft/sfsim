#version 410 core

float cloud_cover(vec3 point);

float cloud_density(vec3 point, float lod)
{
  float cover = cloud_cover(point);
  return cover;
}
